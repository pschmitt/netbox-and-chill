package dev.pschmitt.netboxandchill.ui.generic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.ui.navigation.Route
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@HiltViewModel
class GenericDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GenericObjectRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : ViewModel() {

    val route: Route.Generic = savedStateHandle.toRoute()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val objectFlow = repository.observeObject(route.endpointPath, route.id)

    val title: StateFlow<String?> =
        objectFlow
            .map { it?.display }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val fields: StateFlow<List<FieldRow>> =
        objectFlow
            .map { entity -> entity?.let { decode(it.json) }?.let(::buildFieldRows) ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Web URL mirrors the API path structure with "api/" dropped, e.g. api/dcim/racks/5/ ->
    // <base>/dcim/racks/5/.
    val webUrl: StateFlow<String?> =
        objectFlow
            .combine(settingsRepository.credentials) { entity, credentials -> entity to credentials }
            .map { (entity, credentials) ->
                if (entity == null || credentials.baseUrl.isBlank()) null
                else "${credentials.baseUrl}/${route.endpointPath.removePrefix("api/")}${entity.id}/"
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository
                .refreshObject(route.endpointPath, route.id)
                .onFailure { _errorMessage.value = it.message ?: "Couldn't refresh - showing cached data" }
            _isRefreshing.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private fun decode(rawJson: String): JsonObject? =
        runCatching { json.decodeFromString(JsonObject.serializer(), rawJson) }.getOrNull()
}
