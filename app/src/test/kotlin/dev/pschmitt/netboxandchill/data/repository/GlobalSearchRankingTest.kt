package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.db.RecentVisitEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchRankingTest {
    @Test
    fun `recent visits become navigable search hits in visit order`() {
        val hits =
            recentVisitsToSearchHits(
                listOf(
                    RecentVisitEntity("api/dcim/devices/", 1, "Router", "active", 2),
                    RecentVisitEntity("api/dcim/sites/", 4, "HQ", null, 1),
                )
            )

        assertEquals(listOf("Router", "HQ"), hits.map { it.display })
        assertEquals("api/dcim/sites/", hits[1].endpointPath)
    }

    @Test
    fun `ranks exact and prefix display matches before substring and secondary matches`() {
        val hits =
            listOf(
                SearchHit("api/dcim/devices/", 1, "Router Office", "active"),
                SearchHit("api/dcim/devices/", 2, "Router", "active"),
                SearchHit("api/dcim/devices/", 3, "Main Switch", "router"),
                SearchHit("api/dcim/devices/", 4, "Backup Router", "active"),
            )

        assertEquals(listOf(2, 1, 4, 3), rankSearchHits("router", hits).map { it.id })
    }

    @Test
    fun `deduplicates hits from typed and generic caches`() {
        val hits =
            listOf(
                SearchHit("api/dcim/devices/", 7, "Router", null),
                SearchHit("api/dcim/devices/", 7, "Router", null),
            )

        assertEquals(1, rankSearchHits("router", hits).size)
    }

    @Test
    fun typePrefixSuggestionsMatchLabelsAndPreserveRemainingQuery() {
        val suggestions =
            typeFilterSuggestions(
                "tena office",
                listOf(
                    model("tenants", "Tenants", "api/tenancy/tenants/"),
                    model("devices", "Devices", "api/dcim/devices/"),
                ),
            )

        assertEquals(listOf("Tenants"), suggestions.map { it.modelLabel })
        assertEquals("office", queryRemainderAfterTypeSelection("tena office"))
    }

    @Test
    fun interfaceAndAssignedIpReferencesResolveToTheirDevice() {
        val interfaceObject =
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(44),
                    "display" to JsonPrimitive("Ethernet"),
                    "device" to JsonObject(mapOf("id" to JsonPrimitive(7))),
                )
            )
        val ipObject =
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(88),
                    "address" to JsonPrimitive("192.0.2.7/24"),
                    "assigned_object_type" to JsonPrimitive("dcim.interface"),
                    "assigned_object_id" to JsonPrimitive(44),
                )
            )

        assertEquals(
            NetworkDeviceMatch(7, "Interface Ethernet"),
            networkDeviceMatch(
                GlobalSearchRepository.INTERFACES_ENDPOINT_PATH,
                interfaceObject,
                emptyMap(),
            ),
        )
        assertEquals(
            NetworkDeviceMatch(7, "IP 192.0.2.7/24"),
            networkDeviceMatch(
                GlobalSearchRepository.IP_ADDRESSES_ENDPOINT_PATH,
                ipObject,
                mapOf(44 to 7),
            ),
        )
    }

    private fun model(modelKey: String, label: String, endpointPath: String) =
        NetBoxModelEntity("app", "App", modelKey, label, endpointPath)
}
