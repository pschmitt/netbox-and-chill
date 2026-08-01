package dev.pschmitt.netboxandchill.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchLabelsTest {
    @Test
    fun usesDirectoryLabelWhenAvailable() {
        assertEquals(
            "Device Types",
            searchObjectTypeLabel("Device Types", "api/dcim/device-types/"),
        )
    }

    @Test
    fun humanizesEndpointModelWhenDirectoryHasNotLoaded() {
        assertEquals(
            "IP Addresses",
            searchObjectTypeLabel(null, "api/ipam/ip-addresses/"),
        )
    }
}
