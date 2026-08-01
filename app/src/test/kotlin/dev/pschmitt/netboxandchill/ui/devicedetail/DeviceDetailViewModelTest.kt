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
}
