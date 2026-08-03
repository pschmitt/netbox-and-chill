package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.api.dto.PagedResponseDto
import dev.pschmitt.netboxandchill.data.db.NetBoxModelDao
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DirectoryRepositoryTest {

    @Test
    fun `discovery keeps paginated collections and skips action routes`() = runTest {
        val dao = FakeModelDao()
        val api = FakeDirectoryApi()
        val result =
            DirectoryRepository(api, dao, dev.pschmitt.netboxandchill.sync.SyncIssueReporter())
                .refresh()

        assertEquals(1, result.getOrThrow())
        assertEquals(listOf("api/dcim/racks/"), dao.models.map(NetBoxModelEntity::endpointPath))
        assertEquals(
            listOf(
                "api/dcim/racks/",
                "api/dcim/connected-device/",
                "api/dcim/background-queues/",
                "api/plugins/netbox_topology_views/xml-export/",
            ),
            api.probedPaths,
        )
    }

    @Test
    fun `failed app discovery preserves the previous complete model cache`() = runTest {
        val dao =
            FakeModelDao().apply {
                models += NetBoxModelEntity("dcim", "DCIM", "racks", "Racks", "api/dcim/racks/")
            }
        val api = FailingDirectoryApi()

        val result =
            DirectoryRepository(api, dao, dev.pschmitt.netboxandchill.sync.SyncIssueReporter())
                .refresh()

        assertFalse(result.isSuccess)
        assertEquals(listOf("api/dcim/racks/"), dao.models.map(NetBoxModelEntity::endpointPath))
    }

    @Test
    fun `optional plugin capability predicates are specific and cache friendly`() {
        assertEquals(
            true,
            isTopologyPluginModel(
                NetBoxModelEntity(
                    "plugins/netbox_topology_views",
                    "NetBox Topology Views",
                    "topology",
                    "Topology",
                    "api/plugins/netbox_topology_views/topology/",
                )
            ),
        )
        assertEquals(
            true,
            isDocumentsPluginModel(
                NetBoxModelEntity(
                    "plugins/netbox_documents",
                    "NetBox Documents",
                    "documents",
                    "Documents",
                    "api/plugins/netbox_documents/documents/",
                )
            ),
        )
        assertEquals(
            false,
            isDocumentsPluginModel(
                NetBoxModelEntity(
                    "extras",
                    "Extras",
                    "documents",
                    "Documents",
                    "api/extras/documents/",
                )
            ),
        )
    }
}

private open class FakeDirectoryApi : GenericNetBoxApi {
    val probedPaths = mutableListOf<String>()

    override suspend fun getApiRoot(): Map<String, String> =
        mapOf(
            "dcim" to "https://netbox.example/api/dcim/",
            "plugins" to "https://netbox.example/api/plugins/",
        )

    override suspend fun getUrlMap(url: String): Map<String, String> =
        when (url) {
            "api/dcim/" ->
                mapOf(
                    "racks" to "https://netbox.example/api/dcim/racks/",
                    "connected-device" to "https://netbox.example/api/dcim/connected-device/",
                    "background-queues" to "https://netbox.example/api/dcim/background-queues/",
                )
            "api/plugins/" ->
                mapOf(
                    "netbox_topology_views" to
                        "https://netbox.example/api/plugins/netbox_topology_views/"
                )
            "api/plugins/netbox_topology_views/" ->
                mapOf(
                    "xml-export" to
                        "https://netbox.example/api/plugins/netbox_topology_views/xml-export/"
                )
            else -> error("Unexpected URL map request: $url")
        }

    override suspend fun listObjects(
        url: String,
        query: Map<String, String>,
    ): PagedResponseDto<JsonObject> {
        probedPaths += url
        if (url == "api/dcim/background-queues/") {
            return PagedResponseDto(
                results = listOf(JsonObject(mapOf("name" to JsonPrimitive("running"))))
            )
        }
        if (url != "api/dcim/racks/") throw IOException("not a paginated collection")
        return PagedResponseDto()
    }

    override suspend fun getObject(url: String): JsonObject = error("unused")

    override suspend fun getObjectOptions(url: String): JsonObject = error("unused")

    override suspend fun patchObject(url: String, body: JsonObject): JsonObject = error("unused")

    override suspend fun createObject(url: String, body: JsonObject): JsonObject = error("unused")

    override suspend fun deleteObject(url: String) = error("unused")

    override suspend fun getJournalEntryOptions(): JsonObject = error("unused")
}

private class FakeModelDao : NetBoxModelDao {
    val models = mutableListOf<NetBoxModelEntity>()

    override fun observeAll(): Flow<List<NetBoxModelEntity>> = flowOf(models)

    override fun observeByPaths(endpointPaths: Set<String>): Flow<List<NetBoxModelEntity>> =
        flowOf(emptyList())

    override suspend fun count(): Int = models.size

    override suspend fun getAll(): List<NetBoxModelEntity> = models.toList()

    override suspend fun upsertAll(models: List<NetBoxModelEntity>) {
        this.models.clear()
        this.models.addAll(models)
    }

    override suspend fun clear() {
        models.clear()
    }
}

private class FailingDirectoryApi : FakeDirectoryApi() {
    override suspend fun getUrlMap(url: String): Map<String, String> = error("temporary outage")
}
