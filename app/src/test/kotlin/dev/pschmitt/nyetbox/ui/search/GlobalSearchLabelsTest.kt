package dev.pschmitt.nyetbox.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchLabelsTest {
    @Test
    fun usesDirectoryLabelWhenAvailable() {
        assertEquals(
            "Device Type",
            searchObjectTypeLabel("Device Types", "api/dcim/device-types/"),
        )
    }

    @Test
    fun humanizesEndpointModelWhenDirectoryHasNotLoaded() {
        assertEquals(
            "IP Address",
            searchObjectTypeLabel(null, "api/ipam/ip-addresses/"),
        )
    }

    @Test
    fun preservesAcronymsWhenSingularizingLabels() {
        assertEquals("MAC Address", searchObjectTypeLabel("MAC Addresses", "api/ipam/ip-addresses/"))
    }
}
