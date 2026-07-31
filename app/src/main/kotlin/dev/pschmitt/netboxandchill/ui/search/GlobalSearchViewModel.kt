package dev.pschmitt.netboxandchill.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.repository.DirectoryRepository
import dev.pschmitt.netboxandchill.data.repository.GlobalSearchRepository
import dev.pschmitt.netboxandchill.data.repository.SearchHit
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs [GlobalSearchScreen] (NBC-13) - debounced free-text search fanned out across a curated
 * set of NetBox object types, since there's no server-side global-search endpoint to call into
 * (see [GlobalSearchRepository]'s doc comment for how that was confirmed). */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class GlobalSearchViewModel
@Inject
constructor(
    private val searchRepository: GlobalSearchRepository,
    directoryRepository: DirectoryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchHit>>(emptyList())
    val results: StateFlow<List<SearchHit>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

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
        viewModelScope.launch {
            _query
                .debounce(300)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { text ->
                    if (text.isBlank()) {
                        _results.value = emptyList()
                        _isSearching.value = false
                        return@collectLatest
                    }
                    _isSearching.value = true
                    val endpointPaths =
                        (GlobalSearchRepository.BASELINE_ENDPOINT_PATHS + settingsRepository.pinnedModelPaths.value)
                            .distinct()
                    runCatching { searchRepository.search(text, endpointPaths) }
                        .onSuccess { hits -> _results.value = hits.sortedBy { it.display.lowercase() } }
                        .onFailure { _errorMessage.value = it.message ?: "Search failed" }
                    _isSearching.value = false
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
