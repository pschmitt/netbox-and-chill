package dev.pschmitt.netboxandchill.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.repository.DirectoryRepository
import dev.pschmitt.netboxandchill.data.repository.GlobalSearchRepository
import dev.pschmitt.netboxandchill.data.repository.RecentVisitRepository
import dev.pschmitt.netboxandchill.data.repository.SearchHit
import dev.pschmitt.netboxandchill.data.repository.recentVisitsToSearchHits
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.data.repository.rankSearchHits
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Backs [GlobalSearchScreen] (NBC-13) - debounced free-text search, cache-first like every other
 * screen in this app (see [GlobalSearchRepository]'s doc comment). [results] reads straight from
 * Room so it's instant and offline-capable; [refresh] is a best-effort network pass that widens
 * results and feeds the cache, mirroring `GenericListViewModel`'s `objects`/`refresh()` split. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class GlobalSearchViewModel
@Inject
constructor(
    private val searchRepository: GlobalSearchRepository,
    directoryRepository: DirectoryRepository,
    private val settingsRepository: SettingsRepository,
    recentVisitRepository: RecentVisitRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val debouncedQuery: StateFlow<String> =
        _query
            .debounce(300)
            .map { it.trim() }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Cache-first, offline-capable - re-emits automatically once [refresh] (or any other sync)
     * upserts new rows into Room, the same "Flow straight from the DAO" shape `GenericListViewModel`
     * uses, not a one-shot network call. */
    private val cachedResults =
        debouncedQuery
            .flatMapLatest { text ->
                if (text.isBlank()) flowOf(emptyList()) else searchRepository.observeCached(text)
            }

    val results: StateFlow<List<SearchHit>> =
        kotlinx.coroutines.flow.combine(debouncedQuery, cachedResults) { text, hits ->
                rankSearchHits(text, hits)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentResults: StateFlow<List<SearchHit>> =
        recentVisitRepository
            .observeRecent()
            .map(::recentVisitsToSearchHits)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Baseline models unioned with whatever the user has pinned (they've told us those matter to
    // them) - resolved to their NetBoxModelEntity so result rows can show a proper humanized model
    // label + the same appKey the sidebar/list rows use for AppIcons.forAppKey.
    val modelsByEndpointPath: StateFlow<Map<String, NetBoxModelEntity>> =
        settingsRepository.pinnedModelPaths
            .map { pinned -> (GlobalSearchRepository.BASELINE_ENDPOINT_PATHS + pinned).toSet() }
            .flatMapLatest { paths -> directoryRepository.observePinned(paths) }
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
                    (GlobalSearchRepository.BASELINE_ENDPOINT_PATHS + settingsRepository.pinnedModelPaths.value)
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

    fun errorShown() {
        _errorMessage.value = null
    }
}
