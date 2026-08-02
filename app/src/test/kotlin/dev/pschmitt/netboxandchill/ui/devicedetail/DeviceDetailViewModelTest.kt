package dev.pschmitt.netboxandchill.ui.devicedetail

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDetailViewModelTest {
    @Test
    fun `preserves IPv6 interface address and prefix from cached JSON`() {
        val result =
            parseInterfaceIpAddress(
                objectId = 91,
                rawJson =
                    """{
                        "assigned_object_type":"dcim.interface",
                        "assigned_object_id":17,
                        "address":"2001:db8:2::91/64"
                    }""",
            )

        assertEquals(
            ParsedInterfaceIpAddress(
                interfaceId = 17,
                ipAddress = InterfaceIpAddress(91, "2001:db8:2::91/64"),
            ),
            result,
        )
    }

    @Test
    fun `resolves manufacturer id from cached device type JSON`() {
        assertEquals(
            42,
            parseManufacturerId(
                "{\"id\":7,\"model\":\"Example\",\"manufacturer\":{\"id\":42,\"name\":\"Example Corp\"}}"
            ),
        )
    }

    @Test
    fun `does not invent manufacturer id when the cached relation is absent`() {
        assertEquals(null, parseManufacturerId("{\"id\":7,\"model\":\"Example\"}"))
    }
}
