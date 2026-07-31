package dev.pschmitt.netboxandchill.qrsetup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrConfigCodecTest {
    private val envelope =
        QrConfigEnvelope(
            createdAt = 1_725_000_000_000,
            baseUrl = "https://netbox.example.test",
            token = "nbt_example.secret-value",
        )

    @Test
    fun `round trip preserves url and token`() {
        val payload = QrConfigCodec.encodePayload(envelope)

        assertTrue(payload.startsWith("netboxandchill://setup?p="))
        assertEquals(envelope, QrConfigCodec.decodePayload(payload))
    }

    @Test
    fun `payload is url safe and self identifying`() {
        val payload = QrConfigCodec.encodePayload(envelope)

        assertTrue(QrConfigCodec.looksLikeQrConfigUri(payload))
        val encoded = payload.substringAfter("?p=")
        assertFalse(encoded.contains('+'))
        assertFalse(encoded.contains('/'))
        assertFalse(QrConfigCodec.looksLikeQrConfigUri("https://netbox.example.test/dcim/devices/1/"))
    }

    @Test(expected = QrConfigCodec.InvalidPayloadException::class)
    fun `rejects malformed payload`() {
        QrConfigCodec.decodePayload("netboxandchill://setup?p=not-valid")
    }

    @Test(expected = QrConfigCodec.UnsupportedVersionException::class)
    fun `rejects newer payload versions`() {
        val newer = envelope.copy(version = QrConfigEnvelope.CURRENT_VERSION + 1)
        QrConfigCodec.decodePayload(QrConfigCodec.encodePayload(newer))
    }
}
