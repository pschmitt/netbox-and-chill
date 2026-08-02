package dev.pschmitt.netboxandchill.ui.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.TopologyRepository
import dev.pschmitt.netboxandchill.data.topology.TopologyGraph
import dev.pschmitt.netboxandchill.ui.common.CacheFirstRefreshState
import dev.pschmitt.netboxandchill.ui.common.runCacheFirstRefresh
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TopologyUiState(
    val graph: TopologyGraph? = null,
    val cachedAt: Long? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class TopologyViewModel
@Inject
constructor(private val repository: TopologyRepository) : ViewModel() {
    private val _contentState = MutableStateFlow(TopologyUiState())
    private val _refreshState = MutableStateFlow(CacheFirstRefreshState())
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
            repository.cached().fold(
                onSuccess = { cached ->
                    _contentState.update {
                        it.copy(
                            graph = cached?.graph,
                            cachedAt = cached?.cachedAt,
                            isLoading = false,
                        )
                    }
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
                    _contentState.update {
                        it.copy(
                            graph = snapshot.graph,
                            cachedAt = snapshot.cachedAt,
                            isLoading = false,
                        )
                    }
                }
        }
    }
}
