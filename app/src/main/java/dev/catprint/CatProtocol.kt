package dev.catprint

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

    fun end(): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(packet(0xA1, b(0x60, 0x00)))                     // feed ~96 lines
        o.write(packet(0xA6, b(0xAA, 0x55, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x17))) // lattice end
        o.write(packet(0xA3, b(0x00)))
        return o.toByteArray()
    }

    fun line(row: ByteArray) = packet(0xA2, row)

    /** Floyd–Steinberg dither of a WIDTH-wide bitmap into 1-bit rows (LSB = leftmost, 1 = black). */
    fun toRows(src: Bitmap): List<ByteArray> {
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
                val nw = if (old < 128f) 0f else 255f
                val err = old - nw
                if (nw == 0f) row[x shr 3] = (row[x shr 3].toInt() or (1 shl (x and 7))).toByte()
                if (x + 1 < WIDTH) g[i + 1] += err * 7 / 16
                if (y + 1 < h) {
                    if (x > 0) g[i + WIDTH - 1] += err * 3 / 16
                    g[i + WIDTH] += err * 5 / 16
                    if (x + 1 < WIDTH) g[i + WIDTH + 1] += err / 16
                }
            }
            rows.add(row)
        }
        return rows
    }
}
