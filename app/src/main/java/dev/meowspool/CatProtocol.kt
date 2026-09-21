package dev.meowspool

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

/**
 * Cat printer (GB01/GB02/GB03/... "AE30" family) framing:
 * 51 78 <cmd> 00 <len lo> <len hi> <data...> <crc8(data)> FF
 */
object CatProtocol {
    const val WIDTH = 384            // dots per line (48 bytes)
    private const val BYTES = WIDTH / 8

    private fun crc8(d: ByteArray): Int {
        var c = 0
        for (b in d) {
            c = c xor (b.toInt() and 0xFF)
            repeat(8) { c = if ((c and 0x80) != 0) ((c shl 1) xor 0x07) and 0xFF else (c shl 1) and 0xFF }
        }
        return c
    }

    fun packet(cmd: Int, data: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(0x51); o.write(0x78); o.write(cmd); o.write(0x00)
        o.write(data.size and 0xFF); o.write((data.size shr 8) and 0xFF)
        o.write(data); o.write(crc8(data)); o.write(0xFF)
        return o.toByteArray()
    }

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** darkness 0..100 */
    fun begin(darkness: Int): ByteArray {
        val energy = 8000 + darkness.coerceIn(0, 100) * 160     // 8000..24000
        val o = ByteArrayOutputStream()
        o.write(packet(0xA3, b(0x00)))                           // get state
        o.write(packet(0xA4, b(0x32)))                           // quality
        o.write(packet(0xA6, b(0xAA, 0x55, 0x17, 0x38, 0x44, 0x5F, 0x5F, 0x5F, 0x44, 0x38, 0x2C))) // lattice start
        o.write(packet(0xAF, b(energy and 0xFF, energy shr 8)))  // energy
        o.write(packet(0xBD, b(32)))                             // speed
        o.write(packet(0xBE, b(0x01)))                           // apply energy
        return o.toByteArray()
    }

    fun end(feedLines: Int = 96): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(packet(0xA1, b(feedLines and 0xFF, (feedLines shr 8) and 0xFF))) // feed
        o.write(packet(0xA6, b(0xAA, 0x55, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x17))) // lattice end
        o.write(packet(0xA3, b(0x00)))
        return o.toByteArray()
    }

    fun line(row: ByteArray) = packet(0xA2, row)

    /** Advance blank paper without printing (button-triggered, not part of a print job). */
    fun feed(lines: Int) = packet(0xA1, b(lines and 0xFF, (lines shr 8) and 0xFF))
    /** Pull paper back in (button-triggered). */
    fun retract(lines: Int) = packet(0xA0, b(lines and 0xFF, (lines shr 8) and 0xFF))

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Solid or dashed horizontal rule (with breathing room) as raw rows, for tear-off lines. */
    fun separator(dashed: Boolean): List<ByteArray> {
        val blank = ByteArray(BYTES)
        val line = ByteArray(BYTES)
        for (x in 0 until WIDTH) if (!dashed || x % 24 < 16) line[x shr 3] = (line[x shr 3].toInt() or (1 shl (x and 7))).toByte()
        return List(6) { blank } + List(3) { line } + List(6) { blank }
    }

    /** Inverse of [toRows]: 1-bit rows back to a black/white bitmap (for previews). */
    fun rowsToBitmap(rows: List<ByteArray>): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, rows.size.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val px = IntArray(WIDTH)
        rows.forEachIndexed { y, r ->
            for (x in 0 until WIDTH) px[x] = if (((r[x shr 3].toInt() shr (x and 7)) and 1) == 1) Color.BLACK else Color.WHITE
            bmp.setPixels(px, 0, WIDTH, 0, y, WIDTH, 1)
        }
        return bmp
    }

    /** Dither a WIDTH-wide bitmap into 1-bit rows (LSB = leftmost, 1 = black). */
    fun toRows(src: Bitmap, mode: Dither = Dither.fromPref()): List<ByteArray> {
        val h = src.height
        val px = IntArray(WIDTH * h)
        src.getPixels(px, 0, WIDTH, 0, 0, WIDTH, h)
        val g = FloatArray(WIDTH * h) { val p = px[it]; 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p) }
        val rows = ArrayList<ByteArray>(h)
        for (y in 0 until h) {
            val row = ByteArray(BYTES)
            for (x in 0 until WIDTH) {
                val i = y * WIDTH + x
                val old = g[i]
                val t = if (mode == Dither.ORDERED) (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) * 255f / 16f else 128f
                val nw = if (old < t) 0f else 255f
                if (nw == 0f) row[x shr 3] = (row[x shr 3].toInt() or (1 shl (x and 7))).toByte()
                if (mode == Dither.FLOYD) {
                    val err = old - nw
                    if (x + 1 < WIDTH) g[i + 1] += err * 7 / 16
                    if (y + 1 < h) {
                        if (x > 0) g[i + WIDTH - 1] += err * 3 / 16
                        g[i + WIDTH] += err * 5 / 16
                        if (x + 1 < WIDTH) g[i + WIDTH + 1] += err / 16
                    }
                }
            }
            rows.add(row)
        }
        return rows
    }
}

enum class Dither(val label: String, val hint: String) {
    FLOYD("Smooth", "Error diffusion (Floyd–Steinberg): best for photos and gradients."),
    THRESHOLD("Sharp", "Pure black & white: best for text, barcodes and QR codes."),
    ORDERED("Pattern", "Regular dot pattern: even tone with no noise.");
    companion object { fun fromPref() = values().firstOrNull { it.name == Prefs.dither } ?: FLOYD }
}
