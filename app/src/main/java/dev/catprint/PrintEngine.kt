package dev.catprint

import android.graphics.*
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Paper: 58 mm roll, 48 mm (384 dots @ 203 dpi) printable, centred. */
object Paper {
    const val PAPER_MM = 58f
    const val PRINTABLE_MM = 48f
    const val SIDE_MARGIN_MILS = 197   // (58-48)/2 mm
}

object PrintEngine {
    /** Send 1-bit [rows] to the printer over its (latched or on-demand) link. */
    fun sendRows(addr: String, rows: List<ByteArray>, cancelled: () -> Boolean = { false }) {
        PrinterManager.withLink(addr) { link ->
            link.requestStatus(); Thread.sleep(400)
            link.status?.takeIf { it.blocking }?.let { throw IOException(it.problems().joinToString()) }
            link.send(CatProtocol.begin(Prefs.darkness))
            for (grp in rows.chunked(8)) {
                if (cancelled()) { Dbg.d("Engine", "cancelled mid-send"); break }
                link.send(grp.fold(ByteArray(0)) { acc, r -> acc + CatProtocol.line(r) })
            }
            link.send(CatProtocol.end())
            val t0 = System.currentTimeMillis()
            Thread.sleep(1500)
            while (System.currentTimeMillis() - t0 < 8000) {
                link.requestStatus(); Thread.sleep(600)
                if (link.status?.busy != true) break
            }
            Dbg.d("Engine", "send finished in ${System.currentTimeMillis() - t0}ms")
        }
    }

    fun printTestPage(addr: String, printerName: String) {
        val bmp = TestPage.render(printerName)
        try { sendRows(addr, CatProtocol.toRows(bmp)) } finally { bmp.recycle() }
    }
}

object TestPage {
    private const val W = CatProtocol.WIDTH
    private const val DOTS_PER_MM = W / Paper.PRINTABLE_MM

    fun render(printer: String): Bitmap {
        val h = 620
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val text = Paint(ink).apply { typeface = Typeface.DEFAULT_BOLD }

        // Edge frame: all four sides visible = full width printed, nothing clipped.
        ink.style = Paint.Style.STROKE; ink.strokeWidth = 3f
        c.drawRect(1.5f, 1.5f, W - 1.5f, h - 1.5f, ink)
        ink.style = Paint.Style.FILL

        var y = 56f
        text.textSize = 40f; text.textAlign = Paint.Align.CENTER
        c.drawText("MeowSpool", W / 2f, y, text)
        text.typeface = Typeface.DEFAULT; text.textSize = 22f
        y += 32; c.drawText("Test page", W / 2f, y, text)
        text.textSize = 18f; text.textAlign = Paint.Align.LEFT
        y += 34; c.drawText("Printer: $printer", 16f, y, text)
        y += 24; c.drawText("Paper: ${Paper.PAPER_MM.toInt()} mm (${Paper.PRINTABLE_MM.toInt()} mm printable)", 16f, y, text)
        y += 24; c.drawText("Darkness: ${Prefs.darkness}%", 16f, y, text)
        y += 24; c.drawText(DateFormat.getDateTimeInstance().format(Date()), 16f, y, text)

        // Ruler: 1 tick per mm, longer every 5, numbered every 10.
        y += 30
        text.textSize = 14f; text.textAlign = Paint.Align.CENTER
        val base = y + 30
        c.drawLine(8f, base, W - 8f, base, ink)
        for (mm in 0..48) {
            val x = 8f + mm * (W - 16f) / 48f
            val len = when { mm % 10 == 0 -> 22f; mm % 5 == 0 -> 15f; else -> 8f }
            c.drawLine(x, base, x, base - len, ink)
            if (mm % 10 == 0) c.drawText("${mm / 10 * 10}", x, base + 18f, text)
        }

        // Grey ramp (checks darkness/dithering).
        y = base + 44
        val steps = 8; val bw = (W - 32f) / steps
        for (i in 0 until steps) {
            val g = 255 - i * 255 / (steps - 1)
            ink.color = Color.rgb(g, g, g)
            c.drawRect(16 + i * bw, y, 16 + (i + 1) * bw, y + 46, ink)
        }
        ink.color = Color.BLACK

        // Text sizes.
        text.textAlign = Paint.Align.LEFT
        y += 46 + 34; text.textSize = 14f; c.drawText("Small text 14 – The quick brown fox jumps", 16f, y, text)
        y += 30; text.textSize = 20f; c.drawText("Medium 20 – The quick brown fox", 16f, y, text)
        y += 38; text.textSize = 28f; text.typeface = Typeface.DEFAULT_BOLD; c.drawText("Large 28 Bold", 16f, y, text)

        // Fine line pattern (checks head alignment / banding).
        y += 26
        for (i in 0 until 6) c.drawRect(16f, y + i * 6f, W - 16f, y + i * 6f + (i + 1), ink)
        y += 6 * 6 + 40
        text.typeface = Typeface.DEFAULT; text.textSize = 20f; text.textAlign = Paint.Align.CENTER
        c.drawText("If you can read this, printing works!", W / 2f, y.coerceAtMost(h - 16f), text)
        return bmp
    }
}
