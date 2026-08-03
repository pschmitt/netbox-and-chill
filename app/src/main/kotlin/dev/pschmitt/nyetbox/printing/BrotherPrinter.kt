package dev.pschmitt.nyetbox.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PairedPrinter(val name: String, val address: String, val device: BluetoothDevice)

data class NearbyPrinter(val name: String, val address: String, val device: BluetoothDevice)

object BrotherPrinter {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun pairedPrinters(adapterDevices: Set<BluetoothDevice>): List<PairedPrinter> =
        adapterDevices.mapNotNull(::pairedPrinter).sortedBy { it.name.lowercase() }

    @SuppressLint("MissingPermission")
    fun nearbyPrinter(device: BluetoothDevice): NearbyPrinter? =
        device.takeIf(::isBrotherPrinter)?.let {
            NearbyPrinter(it.name ?: "Brother printer", it.address, it)
        }

    @SuppressLint("MissingPermission")
    private fun pairedPrinter(device: BluetoothDevice): PairedPrinter? =
        device
            .takeIf { it.bondState == BluetoothDevice.BOND_BONDED && isBrotherPrinter(it) }
            ?.let {
                PairedPrinter(it.name ?: "Brother printer", it.address, it)
            }

    @SuppressLint("MissingPermission")
    private fun isBrotherPrinter(device: BluetoothDevice): Boolean {
        val name = device.name.orEmpty()
        return name.contains("brother", ignoreCase = true) ||
            name.contains("p-touch", ignoreCase = true) ||
            name.startsWith("PT-", ignoreCase = true)
    }

    @SuppressLint("MissingPermission")
    suspend fun print(printer: PairedPrinter, label: BrotherLabelRaster): Result<Unit> =
        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                socket = connectSocket(printer.device)
                val input = socket.inputStream
                val output = socket.outputStream
                output.writePacket(ByteArray(64))
                output.writePacket(BrotherPtcBp.reset())
                output.writePacket(BrotherPtcBp.useCommandSetPtcBp())
                output.writePacket(BrotherPtcBp.getStatus())
                val initial = parseBrotherPrinterStatus(input.readExactly(32))
                check(initial.isReady) {
                    "Printer is not ready (error 0x${initial.errorFlags.toString(16)})"
                }
                output.writePacket(
                    BrotherPtcBp.setPrintParameters(
                        mediaType = initial.tapeType,
                        widthMm = initial.tapeWidthMm,
                        lengthMm = initial.tapeLengthMm,
                        rasterLines = label.rasterLines,
                    )
                )
                output.writePacket(BrotherPtcBp.setPageModeAdvancedNoChaining())
                output.writePacket(BrotherPtcBp.setPageMode())
                output.writePacket(BrotherPtcBp.setPageMargin())
                output.writePacket(BrotherPtcBp.setCompressionRle())
                BrotherPtcBp.encodeRasterLines(label.bytes).forEach { packet ->
                    output.writePacket(packet)
                }
                output.writePacket(BrotherPtcBp.print())
                parseBrotherPrinterStatus(input.readExactly(32))
                Result.success(Unit)
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                runCatching { socket?.outputStream?.writePacket(BrotherPtcBp.reset()) }
                runCatching { socket?.close() }
            }
        }

    /**
     * P-touch devices expose the standard SPP UUID, but some Android Bluetooth stacks reject the
     * secure service-record connection even for a bonded printer. Retry the connection with the
     * public insecure SPP API before touching the print protocol. Keeping this fallback here means
     * a failed negotiation cannot replay a partially transmitted label.
     */
    @SuppressLint("MissingPermission")
    private fun connectSocket(device: BluetoothDevice): BluetoothSocket {
        var secureSocket: BluetoothSocket? = null
        try {
            val candidate = device.createRfcommSocketToServiceRecord(SPP_UUID)
            secureSocket = candidate
            candidate.connect()
            return candidate
        } catch (secureError: Exception) {
            runCatching { secureSocket?.close() }
            var insecureSocket: BluetoothSocket? = null
            return try {
                val candidate = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                insecureSocket = candidate
                candidate.connect()
                candidate
            } catch (insecureError: Exception) {
                runCatching { insecureSocket?.close() }
                secureError.addSuppressed(insecureError)
                throw secureError
            }
        }
    }

    private fun OutputStream.writePacket(bytes: ByteArray) {
        write(bytes)
        flush()
    }

    private fun InputStream.readExactly(length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(result, offset, length - offset)
            check(read >= 0) { "Printer closed the connection while sending status" }
            offset += read
        }
        return result
    }
}
