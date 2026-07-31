package dev.pschmitt.netboxandchill.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceUrlParserTest {

    @Test
    fun `parses device id from full url with trailing slash`() {
        assertEquals(393, DeviceUrlParser.parseDeviceId("https://netbox.brkn.lol/dcim/devices/393/"))
    }

    @Test
    fun `parses device id from url without trailing slash`() {
        assertEquals(393, DeviceUrlParser.parseDeviceId("https://netbox.brkn.lol/dcim/devices/393"))
    }

    @Test
    fun `parses bare numeric id`() {
        assertEquals(393, DeviceUrlParser.parseDeviceId("393"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(393, DeviceUrlParser.parseDeviceId("  393  \n"))
    }

    @Test
    fun `returns null for unrelated text`() {
        assertNull(DeviceUrlParser.parseDeviceId("not a netbox url"))
    }

    @Test
    fun `returns null for a different netbox object url`() {
        assertNull(DeviceUrlParser.parseDeviceId("https://netbox.brkn.lol/dcim/racks/12/"))
    }
}
