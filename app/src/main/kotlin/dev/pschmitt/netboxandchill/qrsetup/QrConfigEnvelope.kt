package dev.pschmitt.netboxandchill.qrsetup

import kotlinx.serialization.Serializable

/** The deliberately small, explicit payload carried by a NetBox and Chill setup QR code. */
@Serializable
data class QrConfigEnvelope(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long,
    val baseUrl: String,
    val token: String,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
