package dev.meowspool

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CatProtocolTest {
    @Test fun packetFraming() {
        val p = CatProtocol.packet(0xA1, byteArrayOf(0x10, 0x00))
        assertEquals(0x51, p[0].toInt() and 0xFF)
        assertEquals(0x78, p[1].toInt() and 0xFF)
        assertEquals(0xA1, p[2].toInt() and 0xFF)
        assertEquals(2, p[4].toInt() and 0xFF)   // length lo
        assertEquals(0, p[5].toInt() and 0xFF)   // length hi
        assertEquals(0xFF, p.last().toInt() and 0xFF)
        assertEquals(6 + 2 + 2, p.size)
    }

    @Test fun emptyPayloadCrcIsZero() {
        val p = CatProtocol.packet(0xA3, ByteArray(0))
        assertEquals(0, p[p.size - 2].toInt())
    }

    @Test fun feedAndRetractEncodeLittleEndian() {
        val f = CatProtocol.feed(0x0102)
        assertArrayEquals(byteArrayOf(0x02, 0x01), f.copyOfRange(6, 8))
        val r = CatProtocol.retract(5)
        assertArrayEquals(byteArrayOf(0x05, 0x00), r.copyOfRange(6, 8))
    }
}
