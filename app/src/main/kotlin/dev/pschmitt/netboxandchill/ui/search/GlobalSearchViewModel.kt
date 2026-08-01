package dev.pschmitt.netboxandchill.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.DirectoryRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GlobalSearchRepository
import dev.pschmitt.netboxandchill.data.repository.RecentVisitRepository
import dev.pschmitt.netboxandchill.data.repository.SearchHit
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.data.repository.rankSearchHits
import dev.pschmitt.netboxandchill.data.repository.recentVisitsToSearchHits
import dev.pschmitt.netboxandchill.data.repository.queryRemainderAfterTypeSelection
import dev.pschmitt.netboxandchill.data.repository.typeFilterSuggestions
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchThumbnail(val url: String, val filename: String)

/**
 * Backs [GlobalSearchScreen] (NBC-13) - debounced free-text search, cache-first like every other
 * screen in this app (see [GlobalSearchRepository]'s doc comment). [results] reads straight from
 * Room so it's instant and offline-capable; [refresh] is a best-effort network pass that widens
 * results and feeds the cache, mirroring `GenericListViewModel`'s `objects`/`refresh()` split.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class GlobalSearchViewModel
@Inject
constructor(
    private val searchRepository: GlobalSearchRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    directoryRepository: DirectoryRepository,
    private val settingsRepository: SettingsRepository,
    recentVisitRepository: RecentVisitRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _typeFilter = MutableStateFlow<NetBoxModelEntity?>(null)
    val typeFilter: StateFlow<NetBoxModelEntity?> = _typeFilter.asStateFlow()

    private val debouncedQuery: StateFlow<String> =
        _query
            .debounce(300)
            .map { it.trim() }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /**
     * Cache-first, offline-capable - re-emits automatically once [refresh] (or any other sync)
     * upserts new rows into Room, the same "Flow straight from the DAO" shape
     * `GenericListViewModel` uses, not a one-shot network call.
     */
    private val cachedResults =
        combine(debouncedQuery, _typeFilter) { text, model -> text to model?.endpointPath }
            .flatMapLatest { (text, endpointPath) ->
                if (text.isBlank() && endpointPath == null) {
                    flowOf(emptyList())
                } else {
                    searchRepository.observeCached(text, endpointPath)
                }
            }

    val results: StateFlow<List<SearchHit>> =
        kotlinx.coroutines.flow
            .combine(debouncedQuery, cachedResults) { text, hits ->
                rankSearchHits(text, hits)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentResults: StateFlow<List<SearchHit>> =
        recentVisitRepository
            .observeRecent()
            .map(::recentVisitsToSearchHits)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devicesById: StateFlow<Map<Int, DeviceEntity>> =
        deviceRepository
            .observeDevices("")
            .map { devices -> devices.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val deviceTypesById: StateFlow<Map<Int, DeviceTypeEntity>> =
        deviceTypeRepository
            .observeAll()
            .map { types -> types.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val knownModels: StateFlow<List<NetBoxModelEntity>> =
        directoryRepository
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val typeSuggestions: StateFlow<List<NetBoxModelEntity>> =
        combine(_query, _typeFilter, knownModels) { text, selected, models ->
                if (selected == null) typeFilterSuggestions(text, models) else emptyList()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Every discovered cached model is available here so result rows and type completions can use
    // the same humanized labels and app icons as the sidebar.
    val modelsByEndpointPath: StateFlow<Map<String, NetBoxModelEntity>> =
        knownModels
            .map { models -> models.associateBy { it.endpointPath } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Best-effort network refresh per debounced query - purely additive: [results] above
        // already answers from the cache regardless of this succeeding, so a failure (offline,
        // ...) only gets a quiet message, never a blocking error - same "never gate on network"
        // rule NBC-18 established for the rest of the app.
        viewModelScope.launch {
            debouncedQuery.collectLatest { text ->
                if (text.isBlank()) return@collectLatest
                _isRefreshing.value = true
                val endpointPaths =
                    typeFilter.value?.endpointPath?.let(::listOf)
                        ?: (GlobalSearchRepository.BASELINE_ENDPOINT_PATHS +
                                settingsRepository.pinnedModelPaths.value)
                            .distinct()
                try {
                    searchRepository.refresh(text, endpointPaths)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _errorMessage.value = "Live search refresh failed - showing cached results"
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun selectType(model: NetBoxModelEntity) {
        _typeFilter.value = model
        _query.value = queryRemainderAfterTypeSelection(_query.value)
    }

    fun clearTypeFilter() {
        _typeFilter.value = null
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun thumbnailFor(
        hit: SearchHit,
        devicesById: Map<Int, DeviceEntity>,
        deviceTypesById: Map<Int, DeviceTypeEntity>,
    ): SearchThumbnail? =
        when (hit.endpointPath) {
            GlobalSearchRepository.DEVICE_TYPES_ENDPOINT_PATH ->
                deviceTypesById[hit.id]?.frontImageUrl?.takeIf(String::isNotBlank)?.let { url ->
                    SearchThumbnail(url, "device-type-${hit.id}-front")
                }
            GlobalSearchRepository.DEVICES_ENDPOINT_PATH ->
                devicesById[hit.id]?.deviceTypeId?.let { deviceTypeId ->
                    deviceTypesById[deviceTypeId]?.frontImageUrl?.takeIf(String::isNotBlank)?.let {
                        url ->
                        SearchThumbnail(url, "device-type-$deviceTypeId-front")
                    }
                }
            else -> null
        }

    fun localImageFile(thumbnail: SearchThumbnail): File? =
        fileDownloadRepository.persistentFile(thumbnail.url, thumbnail.filename)
}
