package dev.pschmitt.netboxandchill.data.schema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetBoxEndpointCatalogTest {
    @Test
    fun `core metadata identifies typed device models`() {
        val devices = NetBoxEndpointCatalog.forPath(NetBoxRef.DEVICES_ENDPOINT_PATH)

        assertEquals("Devices", devices?.label)
        assertEquals("dcim", devices?.appKey)
        assertTrue(devices?.typedDetail == true)
        assertTrue(devices?.supportsDeviceTypeImages == true)
    }

    @Test
    fun `plugin paths retain generic fallback behavior`() {
        assertNull(NetBoxEndpointCatalog.forPath("api/plugins/example/widgets/"))
        assertEquals("plugins/example", NetBoxRef.appKeyFromEndpointPath("api/plugins/example/widgets/"))
    }
}
