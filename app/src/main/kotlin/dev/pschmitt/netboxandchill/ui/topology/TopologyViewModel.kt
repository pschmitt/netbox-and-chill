package dev.pschmitt.netboxandchill.ui.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.TopologyRepository
import dev.pschmitt.netboxandchill.data.topology.TopologyGraph
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _state = MutableStateFlow(TopologyUiState())
    val state: StateFlow<TopologyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.cached().fold(
                onSuccess = { cached ->
                    _state.update {
                        it.copy(
                            graph = cached?.graph,
                            cachedAt = cached?.cachedAt,
                            isLoading = false,
                        )
                    }
                    if (cached == null) refresh()
                },
                onFailure = { error ->
                    _state.update {
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
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            repository.refresh().fold(
                onSuccess = { snapshot ->
                    _state.value =
                        TopologyUiState(
                            graph = snapshot.graph,
                            cachedAt = snapshot.cachedAt,
                            isLoading = false,
                        )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "Couldn't refresh topology",
                        )
                    }
                },
            )
        }
    }
}
