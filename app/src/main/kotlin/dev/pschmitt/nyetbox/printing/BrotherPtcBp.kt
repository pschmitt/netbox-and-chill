package dev.pschmitt.nyetbox.printing

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pure PTCBP packet and raster encoding, ported from the printlabel CLI. */
object BrotherPtcBp {
    const val RASTER_WIDTH = 128
    private const val RASTER_BYTES = RASTER_WIDTH / 8

    fun reset(): ByteArray = byteArrayOf(0x1b, 0x40)

    fun getStatus(): ByteArray = byteArrayOf(0x1b, 0x69, 0x53)

    fun useCommandSetPtcBp(): ByteArray = byteArrayOf(0x1b, 0x69, 0x61, 0x01)

    fun setPrintParameters(
        mediaType: Int,
        widthMm: Int,
        lengthMm: Int,
        rasterLines: Int,
    ): ByteArray =
        ByteBuffer.allocate(13)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(0x1b, 0x69, 0x7a.toByte()))
            .put(0xc4.toByte()) // width + quality + recovery fields
            .put(mediaType.toByte())
            .put(widthMm.toByte())
            .put(lengthMm.toByte())
            .putInt(rasterLines)
            .put(0.toByte())
            .put(0.toByte())
            .array()

    fun setPageModeAdvancedNoChaining(): ByteArray = byteArrayOf(0x1b, 0x69, 0x4b, 0x08)

    fun setPageMode(): ByteArray = byteArrayOf(0x1b, 0x69, 0x4d, 0x00)

    fun setPageMargin(margin: Int = 0): ByteArray =
        ByteBuffer.allocate(5)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(0x1b, 0x69, 0x64))
            .putShort(margin.toShort())
            .array()

    fun setCompressionRle(): ByteArray = byteArrayOf(0x4d, 0x02)

    fun print(): ByteArray = byteArrayOf(0x1a)

    fun encodeRasterLines(raster: ByteArray): List<ByteArray> {
        require(raster.size % RASTER_BYTES == 0) {
            "Raster data must contain complete 128-dot lines"
        }
        val packets = ArrayList<ByteArray>(raster.size / RASTER_BYTES)
        for (offset in raster.indices step RASTER_BYTES) {
            val line = raster.copyOfRange(offset, offset + RASTER_BYTES)
            if (line.all { it == 0.toByte() }) {
                packets += byteArrayOf(0x5a)
            } else {
                val compressed = packBits(line)
                packets +=
                    ByteBuffer.allocate(3 + compressed.size)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .put(0x47.toByte())
                        .putShort(compressed.size.toShort())
                        .put(compressed)
                        .array()
            }
        }
        return packets
    }

    /** PackBits encoding used by Brother's PTCBP raster transfer. */
    fun packBits(data: ByteArray): ByteArray {
        if (data.isEmpty()) return byteArrayOf()
        val encoded = ByteArrayOutputStream()
        var index = 0
        while (index < data.size) {
            var runLength = 1
            while (
                index + runLength < data.size &&
                    data[index + runLength] == data[index] &&
                    runLength < 128
            ) {
                runLength++
            }
            if (runLength >= 3) {
                encoded.write((257 - runLength) and 0xff)
                encoded.write(data[index].toInt())
                index += runLength
                continue
            }

            val literalStart = index
            var literalLength = 0
            while (index < data.size && literalLength < 128) {
                runLength = 1
                while (
                    index + runLength < data.size &&
                        data[index + runLength] == data[index] &&
                        runLength < 128
                ) {
                    runLength++
                }
                if (runLength >= 3) break
                index++
                literalLength++
            }
            encoded.write(literalLength - 1)
            encoded.write(data, literalStart, literalLength)
        }
        return encoded.toByteArray()
    }
}

data class BrotherPrinterStatus(
    val errorFlags: Int,
    val tapeWidthMm: Int,
    val tapeType: Int,
    val tapeLengthMm: Int,
    val statusType: Int,
    val phaseType: Int,
    val phase: Int,
) {
    val isReady: Boolean
        get() = errorFlags == 0 && phaseType == 0 && phase == 0
}

fun parseBrotherPrinterStatus(bytes: ByteArray): BrotherPrinterStatus {
    require(bytes.size == 32) { "Brother status must be exactly 32 bytes" }
    require(bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x80.toByte(), 0x20, 0x42, 0x30))) {
        "Invalid Brother printer status response"
    }
    fun unsigned(index: Int): Int = bytes[index].toInt() and 0xff
    fun bigEndianShort(index: Int): Int = (unsigned(index) shl 8) or unsigned(index + 1)
    return BrotherPrinterStatus(
        errorFlags = bigEndianShort(8),
        tapeWidthMm = unsigned(12),
        tapeType = unsigned(13),
        tapeLengthMm = unsigned(17),
        statusType = unsigned(18),
        phaseType = unsigned(19),
        phase = bigEndianShort(20),
    )
}
