package dev.meowspool

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.Closeable
import java.io.File
import java.io.IOException

/** Everything adjustable for one "print a file" job. Defaults come from the global print settings. */
data class PrintJobSettings(
    val sizePct: Int = 100,
    val sideMm: Int = Prefs.marginSideMm,
    val vertMm: Int = Prefs.marginVertMm,
    val rotation: Int = 0,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val invert: Boolean = false,
    val dither: Dither = Dither.fromPref(),
    val darkness: Int = Prefs.darkness,
    val copies: Int = 1,
    val feedMm: Int = Prefs.feedMm,
    val lineBefore: Boolean = Prefs.lineBefore,
    val lineAfter: Boolean = Prefs.lineAfter,
    val firstPage: Int = 0,
    val lastPage: Int = 0,
) {
    fun options() = PrintOptions(darkness, feedMm, lineBefore, lineAfter, Prefs.lineDashed)
}

/** A picked photo or PDF. Pages are rendered lazily (PDF) and the latest one is cached. */
class DocSource private constructor(
    val name: String, val isPdf: Boolean, val info: String, private val image: Bitmap?,
    private val renderer: PdfRenderer?, private val pfd: ParcelFileDescriptor?, private val tmp: File?,
) : Closeable {
    val pageCount get() = renderer?.pageCount ?: 1
    private var cache: Pair<Int, Bitmap>? = null
    private var closed = false

    fun defaults() = PrintJobSettings(dither = if (isPdf) Dither.THRESHOLD else Dither.fromPref(), lastPage = pageCount - 1)

    @Synchronized fun page(i: Int): Bitmap {
        check(!closed) { "closed" }
        image?.let { return it }
        cache?.takeIf { it.first == i }?.let { return it.second }
        val r = renderer!!
        val p = r.openPage(i.coerceIn(0, r.pageCount - 1))
        try {
            val w = 768
            val h = (p.height * w / p.width.toFloat()).toInt().coerceIn(1, 16000)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            cache = i to bmp
            return bmp
        } finally { p.close() }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer?.close() }; runCatching { pfd?.close() }; tmp?.delete()
    }

    companion object {
        fun open(ctx: Context, uri: Uri): DocSource {
            val cr = ctx.contentResolver
            val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Untitled"
            if (cr.getType(uri) == "application/pdf" || name.endsWith(".pdf", true)) {
                val tmp = File.createTempFile("direct", ".pdf", ctx.cacheDir)
                try {
                    (cr.openInputStream(uri) ?: throw IOException("Can't open file")).use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = try { PdfRenderer(pfd) } catch (e: Throwable) { pfd.close(); throw e }
                    if (r.pageCount == 0) { r.close(); pfd.close(); throw IOException("Empty PDF") }
                    return DocSource(name, true, "PDF · ${r.pageCount} page${if (r.pageCount == 1) "" else "s"}", null, r, pfd, tmp)
                } catch (e: Throwable) { tmp.delete(); throw e }
            }
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (cr.openInputStream(uri) ?: throw IOException("Can't open file")).use { BitmapFactory.decodeStream(it, null, b) }
            var sample = 1
            while (maxOf(b.outWidth, b.outHeight) / sample > 2400) sample *= 2
            var bmp = (cr.openInputStream(uri) ?: throw IOException("Can't open file")).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IOException("Not a supported image")
            val orient = cr.openInputStream(uri)?.use { runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrNull() } ?: 1
            val m = Matrix()
            when (orient) {
                6 -> m.postRotate(90f); 3 -> m.postRotate(180f); 8 -> m.postRotate(270f)
                2 -> m.postScale(-1f, 1f); 4 -> m.postScale(1f, -1f)
                5 -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                7 -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            return DocSource(name, false, "Image · ${bmp.width}×${bmp.height}", bmp, null, null, null)
        }
    }
}

/** Turns a source page into a 384-dot-wide bitmap with size, margins, rotation and tone adjustments applied. */
object Composer {
    private const val W = CatProtocol.WIDTH
    private const val DOTS_PER_MM = W / Paper.PRINTABLE_MM

    fun compose(src: Bitmap, s: PrintJobSettings): Bitmap {
        val side = (s.sideMm * DOTS_PER_MM).toInt().coerceIn(0, W / 2 - 24)
        val vert = (s.vertMm * DOTS_PER_MM).toInt()
        val rotated = s.rotation % 180 != 0
        val rw = (if (rotated) src.height else src.width).toFloat()
        val rh = (if (rotated) src.width else src.height).toFloat()
        var scale = (W - 2 * side) * s.sizePct.coerceIn(5, 100) / 100f / rw
        if (rh * scale > 12000 - 2 * vert) scale = (12000 - 2 * vert) / rh
        val dh = rh * scale
        // Halve big sources first so the final shrink doesn't alias.
        var cur = src
        while (scale * src.width / cur.width < 0.5f && cur.width > 4 && cur.height > 4) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
        }
        val f = src.width.toFloat() / cur.width
        val h = (dh + 2 * vert).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        val m = Matrix().apply {
            postTranslate(-cur.width / 2f, -cur.height / 2f)
            postRotate(s.rotation.toFloat())
            postScale(scale * f, scale * f)
            postTranslate(W / 2f, vert + dh / 2f)
        }
        Canvas(out).drawBitmap(cur, m, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(tone(s)) })
        return out
    }

    private fun tone(s: PrintJobSettings): ColorMatrix {
        val c = 1f + s.contrast / 100f
        val t = s.brightness * 2.55f + 128f * (1f - c)
        val cm = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ))
        if (s.invert) cm.postConcat(ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )))
        return cm
    }
}
