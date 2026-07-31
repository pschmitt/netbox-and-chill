package dev.pschmitt.netboxandchill.ui.generic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.ui.navigation.Route
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GenericListViewModel
@Inject
constructor(savedStateHandle: SavedStateHandle, private val repository: GenericObjectRepository) :
    ViewModel() {

    val route: Route.GenericList = savedStateHandle.toRoute()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val objects: StateFlow<List<NetBoxObjectEntity>> =
        _query
            .flatMapLatest {
                repository.observeObjects(route.endpointPath, it, route.filterKey, route.filterValue)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository
                .syncAll(
                    route.endpointPath,
                    filters =
                        route.filterKey?.let { key ->
                            route.filterValue?.let { value -> mapOf("${key}_id" to value.toString()) }
                        } ?: emptyMap(),
                )
                .onFailure { _errorMessage.value = it.message ?: "Sync failed - showing cached data" }
            _isRefreshing.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }
}
