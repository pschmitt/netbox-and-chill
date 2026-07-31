package dev.pschmitt.netboxandchill.scanner

import dev.pschmitt.netboxandchill.qrsetup.QrConfigCodec
import dev.pschmitt.netboxandchill.qrsetup.QrConfigEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetBoxUrlParserTest {

    @Test
    fun `parses device id from full url with trailing slash`() {
        assertEquals(
            NetBoxTarget.Device(393),
            NetBoxUrlParser.parse("https://netbox.brkn.lol/dcim/devices/393/"),
        )
    }

    @Test
    fun `parses device id from url without trailing slash`() {
        assertEquals(
            NetBoxTarget.Device(393),
            NetBoxUrlParser.parse("https://netbox.brkn.lol/dcim/devices/393"),
        )
    }

    @Test
    fun `parses bare numeric id as a device`() {
        assertEquals(NetBoxTarget.Device(393), NetBoxUrlParser.parse("393"))
    }

    @Test
    fun `parses a setup QR payload without treating it as a NetBox object`() {
        val payload =
            QrConfigCodec.encodePayload(
                QrConfigEnvelope(
                    createdAt = 1,
                    baseUrl = "https://netbox.example.test",
                    token = "nbt_key.secret",
                )
            )

        assertEquals(
            NetBoxTarget.Setup("https://netbox.example.test", "nbt_key.secret"),
            NetBoxUrlParser.parse(payload),
        )
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(NetBoxTarget.Device(393), NetBoxUrlParser.parse("  393  \n"))
    }

    @Test
    fun `parses a non-device object url into its endpoint path and id`() {
        assertEquals(
            NetBoxTarget.Object("api/dcim/racks/", 12),
            NetBoxUrlParser.parse("https://netbox.brkn.lol/dcim/racks/12/"),
        )
    }

    @Test
    fun `parses an ipam object url`() {
        assertEquals(
            NetBoxTarget.Object("api/ipam/prefixes/", 7),
            NetBoxUrlParser.parse("https://netbox.brkn.lol/ipam/prefixes/7/"),
        )
    }

    @Test
    fun `returns null for unrelated text`() {
        assertNull(NetBoxUrlParser.parse("not a netbox url"))
    }
}
