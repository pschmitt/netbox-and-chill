package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.TopologyApi
import dev.pschmitt.netboxandchill.data.topology.TopologyGraph
import dev.pschmitt.netboxandchill.data.topology.parseTopologyXml
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TopologySnapshot(
    val graph: TopologyGraph,
    val cachedAt: Long,
)

/** Cache-first access to the optional netbox-topology-views XML export. */
@Singleton
class TopologyRepository
@Inject
constructor(
    private val api: TopologyApi,
    private val fileDownloadRepository: FileDownloadRepository,
) {

    suspend fun cached(): Result<TopologySnapshot?> =
        withContext(Dispatchers.IO) {
            runCatching {
                fileDownloadRepository
                    .persistentFile(CACHE_KEY, CACHE_FILENAME)
                    ?.let { file ->
                        TopologySnapshot(parseTopologyXml(file.readText()), file.lastModified())
                    }
            }
        }

    suspend fun refresh(): Result<TopologySnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val xml = api.export(EXPORT_URL).use { it.string() }
                check(xml.contains("<mxfile")) { "Topology export was not valid draw.io XML" }
                val graph = parseTopologyXml(xml)
                val file =
                    fileDownloadRepository
                        .writeToPersistent(CACHE_KEY, CACHE_FILENAME, xml)
                        .getOrThrow()
                TopologySnapshot(graph, file.lastModified())
            }
        }

    companion object {
        const val PLUGIN_APP_KEY = "plugins/netbox_topology_views"

        private const val CACHE_KEY = "netbox-topology-views/xml-export"
        private const val CACHE_FILENAME = "topology.xml"
        private const val EXPORT_URL =
            "api/plugins/netbox_topology_views/xml-export/?" +
                "show_cables=True&show_unconnected=True&show_logical_connections=True&" +
                "show_neighbors=True&show_circuit=True&show_power=True&show_wireless=True&" +
                "draw_termination_labels=True&draw_cable_labels=True&node_label_items=devicename&" +
                "node_label_items=devicetype&node_label_items=site&draw_init=True"
    }
}
