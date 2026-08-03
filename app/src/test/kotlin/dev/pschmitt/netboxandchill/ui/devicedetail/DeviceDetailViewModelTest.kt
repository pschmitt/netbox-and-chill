package dev.pschmitt.netboxandchill.ui.devicedetail

import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.topology.TopologyEdge
import dev.pschmitt.netboxandchill.data.topology.TopologyGraph
import dev.pschmitt.netboxandchill.data.topology.TopologyNode
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

    @Test
    fun `resolves cached topology neighbors to device rows`() {
        val current = device(1, "router")
        val neighbor = device(2, "switch")
        val unrelated = device(3, "server")
        val graph =
            TopologyGraph(
                nodes = listOf(node("router"), node("switch"), node("server")),
                edges = listOf(TopologyEdge("router-node", "switch-node", "#808080")),
            )

        assertEquals(
            listOf(neighbor),
            connectedDevicesFor(current, listOf(unrelated, neighbor, current), graph),
        )
    }

    private fun node(name: String) =
        TopologyNode(
            id = "$name-node",
            label = name,
            x = 0f,
            y = 0f,
            width = 50f,
            height = 50f,
        )

    private fun device(id: Int, name: String) =
        DeviceEntity(
            id = id,
            name = name,
            url = "https://example.invalid/api/dcim/devices/$id/",
            statusValue = null,
            statusLabel = "Active",
            siteName = null,
            siteId = null,
            rackName = null,
            rackId = null,
            position = null,
            roleName = null,
            manufacturerName = null,
            deviceTypeModel = null,
            deviceTypeId = null,
            serial = null,
            assetTag = null,
            primaryIp = null,
            comments = null,
            lastUpdated = null,
            syncedAt = 0L,
        )
}
