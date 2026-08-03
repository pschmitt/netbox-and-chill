package dev.pschmitt.netboxandchill.ui.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.TopologyRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.data.repository.GlobalSearchRepository
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import dev.pschmitt.netboxandchill.data.topology.TopologyGraph
import dev.pschmitt.netboxandchill.data.topology.TopologyPosition
import dev.pschmitt.netboxandchill.data.topology.withPositions
import dev.pschmitt.netboxandchill.ui.common.CacheFirstRefreshState
import dev.pschmitt.netboxandchill.ui.common.runCacheFirstRefresh
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TopologyUiState(
    val graph: TopologyGraph? = null,
    val nodeInfo: Map<String, TopologyNodeInfo> = emptyMap(),
    val focusedNodeId: String? = null,
    val showDeviceTypeImages: Boolean = true,
    val deviceSearchResults: List<TopologyNodeInfo> = emptyList(),
    val cachedAt: Long? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

data class TopologyNodeInfo(
    val nodeId: String,
    val deviceId: Int?,
    val displayName: String,
    val deviceTypeModel: String?,
    val statusLabel: String?,
    val frontImageUrl: String?,
    val localImageFile: java.io.File?,
    val searchValues: Map<String, String> = emptyMap(),
)

@HiltViewModel
class TopologyViewModel
@Inject
constructor(
    private val repository: TopologyRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val globalSearchRepository: GlobalSearchRepository,
) : ViewModel() {
    private val _contentState = MutableStateFlow(TopologyUiState())
    private val _refreshState = MutableStateFlow(CacheFirstRefreshState())
    private var rawGraph: TopologyGraph? = null
    private var devices: List<DeviceEntity> = emptyList()
    private var deviceTypes: List<DeviceTypeEntity> = emptyList()
    private var requestedFocusDeviceId: Int? = null
    private var deviceSearchQuery = ""
    val state: StateFlow<TopologyUiState> =
        combine(_contentState, _refreshState) { content, refresh ->
                content.copy(
                    isRefreshing = refresh.isRefreshing,
                    errorMessage = refresh.errorMessage ?: content.errorMessage,
                )
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TopologyUiState())

    init {
        viewModelScope.launch {
            combine(
                    deviceRepository.observeDevices(""),
                    deviceTypeRepository.observeAll(),
                    settingsRepository.topologyNodePositions,
                    settingsRepository.showTopologyDeviceTypeImages,
                ) { cachedDevices, cachedTypes, positions, showImages ->
                    devices = cachedDevices
                    deviceTypes = cachedTypes
                    _contentState.update { it.copy(showDeviceTypeImages = showImages) }
                    rebuildGraph(positions)
                }
                .collectLatest {}
        }
        viewModelScope.launch {
            repository.cached().fold(
                onSuccess = { cached ->
                    rawGraph = cached?.graph
                    rebuildGraph(settingsRepository.topologyNodePositions.value)
                    _contentState.update { it.copy(cachedAt = cached?.cachedAt, isLoading = false) }
                    if (cached == null) refresh()
                },
                onFailure = { error ->
                    _contentState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Couldn't read the cached topology",
                        )
                    }
                    refresh()
                },
            )
        }
    }

    fun refresh() {
        if (_refreshState.value.isRefreshing) return
        viewModelScope.launch {
            _contentState.update { it.copy(errorMessage = null) }
            _refreshState
                .runCacheFirstRefresh(
                    operation = { repository.refresh() },
                    errorMessage = { it.message ?: "Couldn't refresh topology" },
                )
                ?.onSuccess { snapshot ->
                    rawGraph = snapshot.graph
                    rebuildGraph(settingsRepository.topologyNodePositions.value)
                    _contentState.update {
                        it.copy(cachedAt = snapshot.cachedAt, isLoading = false)
                    }
                }
        }
    }

    fun focusDevice(deviceId: Int?) {
        requestedFocusDeviceId = deviceId
        _contentState.update { it.copy(focusedNodeId = findDeviceNodeId(deviceId, it.graph)) }
    }

    fun searchDevices(query: String) {
        deviceSearchQuery = query
        viewModelScope.launch {
            val hits =
                if (query.isBlank()) {
                    emptyList()
                } else {
                    globalSearchRepository
                        .observeCached(query, NetBoxRef.DEVICES_ENDPOINT_PATH, limitPerSource = 100)
                        .first()
                }
            val info = _contentState.value.nodeInfo
            _contentState.update {
                it.copy(
                    deviceSearchResults =
                        if (query.isBlank()) info.values.filter { node -> node.deviceId != null }
                        else hits.mapNotNull { hit -> info.values.firstOrNull { it.deviceId == hit.id } },
                )
            }
        }
    }

    fun moveNode(nodeId: String, delta: TopologyPosition) {
        val graph = _contentState.value.graph ?: return
        val node = graph.nodes.firstOrNull { it.id == nodeId } ?: return
        val position = TopologyPosition(node.x + delta.x, node.y + delta.y)
        settingsRepository.setTopologyNodePosition(nodeId, position)
        _contentState.update { it.copy(graph = graph.withPositions(mapOf(nodeId to position))) }
    }

    private fun rebuildGraph(positions: Map<String, TopologyPosition>) {
        val graph = rawGraph?.withPositions(positions) ?: return
        val devicesByName = devices.associateBy { it.name.trim().lowercase() }
        val typesById = deviceTypes.associateBy { it.id }
        val info =
            graph.nodes.associate { node ->
                val displayName = node.label.lineSequence().firstOrNull()?.trim().orEmpty()
                val device = devicesByName[displayName.lowercase()]
                val type = device?.deviceTypeId?.let(typesById::get)
                val imageUrl = type?.frontImageUrl
                node.id to
                    TopologyNodeInfo(
                        nodeId = node.id,
                        deviceId = device?.id,
                        displayName = device?.name ?: displayName.ifBlank { node.id },
                        deviceTypeModel = device?.deviceTypeModel ?: type?.model,
                        statusLabel = device?.statusLabel,
                        frontImageUrl = imageUrl,
                        localImageFile =
                            imageUrl?.let {
                                fileDownloadRepository.persistentFile(
                                    it,
                                    "device-type-${type.id}-front",
                                )
                            },
                        searchValues =
                            mapOf(
                                    "name" to (device?.name ?: displayName),
                                    "display" to (device?.name ?: displayName),
                                    "asset" to device?.assetTag,
                                    "asset_tag" to device?.assetTag,
                                    "serial" to device?.serial,
                                    "model" to (device?.deviceTypeModel ?: type?.model),
                                    "device_type" to (device?.deviceTypeModel ?: type?.model),
                                    "manufacturer" to device?.manufacturerName,
                                    "site" to device?.siteName,
                                    "rack" to device?.rackName,
                                    "status" to device?.statusLabel,
                                    "ip" to device?.primaryIp,
                                    "primary_ip" to device?.primaryIp,
                                )
                                .mapValues { it.value.orEmpty() }
                                .filterValues(String::isNotBlank),
                    )
            }
        _contentState.update {
            it.copy(
                graph = graph,
                nodeInfo = info,
                focusedNodeId = findDeviceNodeId(requestedFocusDeviceId, graph),
            )
        }
        searchDevices(deviceSearchQuery)
    }

    private fun findDeviceNodeId(deviceId: Int?, graph: TopologyGraph?): String? {
        if (deviceId == null || graph == null) return null
        val name = devices.firstOrNull { it.id == deviceId }?.name ?: return null
        return graph.nodes.firstOrNull {
            it.label.lineSequence().firstOrNull()?.trim().equals(name, ignoreCase = true)
        }?.id
    }
}
