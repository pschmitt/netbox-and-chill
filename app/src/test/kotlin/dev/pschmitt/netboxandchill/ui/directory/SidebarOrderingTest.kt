package dev.pschmitt.netboxandchill.ui.directory

import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarOrderingTest {
    @Test
    fun `default app order follows the familiar netbox groups`() {
        assertEquals(
            listOf("tenancy", "dcim", "ipam", "plugins/example"),
            orderSidebarAppKeys(listOf("plugins/example", "ipam", "dcim", "tenancy"), emptyList()),
        )
    }

    @Test
    fun `saved app order takes precedence while new groups are retained`() {
        assertEquals(
            listOf("ipam", "dcim", "extras"),
            orderSidebarAppKeys(listOf("dcim", "extras", "ipam"), listOf("ipam", "dcim")),
        )
    }

    @Test
    fun `default model order is used inside a group`() {
        val models =
            listOf(
                model("devices", "Devices"),
                model("sites", "Sites"),
                model("racks", "Racks"),
            )
        assertEquals(
            listOf("sites", "racks", "devices"),
            orderSidebarModels("dcim", models, emptyList()).map { it.modelKey },
        )
    }

    @Test
    fun `saved model order takes precedence`() {
        val models = listOf(model("devices", "Devices"), model("sites", "Sites"), model("racks", "Racks"))
        assertEquals(
            listOf("devices", "sites", "racks"),
            orderSidebarModels("dcim", models, listOf("devices", "sites", "racks")).map { it.modelKey },
        )
    }

    private fun model(key: String, label: String) =
        NetBoxModelEntity("dcim", "Devices", key, label, "api/dcim/$key/")
}
