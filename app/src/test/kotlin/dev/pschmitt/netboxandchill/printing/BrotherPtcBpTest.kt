package dev.pschmitt.netboxandchill.printing

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherPtcBpTest {
    @Test
    fun `packbits compresses long runs and literals`() {
        assertArrayEquals(
            byteArrayOf(0xfe.toByte(), 7, 2, 1, 2, 3),
            BrotherPtcBp.packBits(byteArrayOf(7, 7, 7, 1, 2, 3)),
        )
    }

    @Test
    fun `raster uses zerofill for blank lines`() {
        val raster = ByteArray(BrotherPtcBp.RASTER_WIDTH / 8 * 2).also { it[16] = 1 }
        val packets = BrotherPtcBp.encodeRasterLines(raster)
        assertArrayEquals(byteArrayOf(0x5a), packets[0])
        assertTrue(packets[1].size > 3)
        assertEquals(0x47, packets[1][0].toInt())
    }

    @Test
    fun `status parser accepts ready response`() {
        val bytes = ByteArray(32)
        bytes[0] = 0x80.toByte()
        bytes[1] = 0x20
        bytes[2] = 0x42
        bytes[3] = 0x30
        bytes[12] = 12
        bytes[13] = 0x4a
        bytes[17] = 0
        val status = parseBrotherPrinterStatus(bytes)
        assertTrue(status.isReady)
        assertEquals(12, status.tapeWidthMm)
        assertEquals(0x4a, status.tapeType)
    }

    @Test
    fun `set print parameters uses protocol little endian layout`() {
        val packet = BrotherPtcBp.setPrintParameters(0x4a, 12, 0, 320)
        assertEquals(13, packet.size)
        assertEquals(0x1b, packet[0].toInt())
        assertEquals(0xc4.toByte(), packet[3])
        assertEquals(
            320,
            ByteBuffer.wrap(packet, 7, 4).order(ByteOrder.LITTLE_ENDIAN).int,
        )
        assertFalse(packet.copyOfRange(4, 8).contentEquals(byteArrayOf(0, 0, 0, 0)))
    }
}
