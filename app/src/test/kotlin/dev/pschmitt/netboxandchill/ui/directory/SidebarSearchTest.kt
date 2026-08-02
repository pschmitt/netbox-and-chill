package dev.pschmitt.netboxandchill.ui.directory

import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.repository.TopologyRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarSearchTest {
    @Test
    fun `special topology action keeps its collapsed plugin group visible`() {
        val models =
            mapOf(
                "dcim" to listOf(model("devices", "Devices")),
                TopologyRepository.PLUGIN_APP_KEY to emptyList(),
            )

        assertEquals(
            setOf(TopologyRepository.PLUGIN_APP_KEY),
            sidebarVisibleAppKeysForSearch(models, "topology", emptySet()),
        )
    }

    @Test
    fun `regular model search only keeps groups with matching models`() {
        val models =
            mapOf(
                "dcim" to listOf(model("devices", "Devices")),
                "ipam" to listOf(model("ip-addresses", "IP addresses")),
            )

        assertEquals(setOf("ipam"), sidebarVisibleAppKeysForSearch(models, "address", emptySet()))
    }

    private fun model(key: String, label: String) =
        NetBoxModelEntity("dcim", "DCIM", key, label, "api/dcim/$key/")
}
