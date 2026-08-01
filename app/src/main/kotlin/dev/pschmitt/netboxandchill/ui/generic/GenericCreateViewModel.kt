package dev.pschmitt.netboxandchill.ui.generic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.CreateChoice
import dev.pschmitt.netboxandchill.data.repository.CreateFieldDefinition
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.data.repository.buildCreateBody
import dev.pschmitt.netboxandchill.data.repository.fallbackCreateFieldDefinitions
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import dev.pschmitt.netboxandchill.ui.navigation.Route
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@HiltViewModel
class GenericCreateViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GenericObjectRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    val route: Route.GenericCreate = savedStateHandle.toRoute()

    private val _fields = MutableStateFlow<List<CreateFieldDefinition>>(emptyList())
    val fields: StateFlow<List<CreateFieldDefinition>> = _fields.asStateFlow()

    private val _referenceOptions = MutableStateFlow<Map<String, List<CreateChoice>>>(emptyMap())
    val referenceOptions: StateFlow<Map<String, List<CreateChoice>>> = _referenceOptions.asStateFlow()

    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _createdId = MutableStateFlow<Int?>(null)
    val createdId: StateFlow<Int?> = _createdId.asStateFlow()

    init {
        viewModelScope.launch {
            repository.createFieldDefinitions(route.endpointPath)
                .onSuccess { definitions ->
                    val fallback = fallbackCreateFieldDefinitions(route.endpointPath)
                    if (definitions.isEmpty() && fallback.isNotEmpty()) {
                        _errorMessage.value = "NetBox did not provide form metadata; using the core fields"
                        initializeFields(fallback)
                    } else {
                        initializeFields(definitions)
                    }
                }
                .onFailure {
                    val fallback = fallbackCreateFieldDefinitions(route.endpointPath)
                    if (fallback.isEmpty()) {
                        _errorMessage.value = it.message ?: "Couldn't load the creation form"
                    } else {
                        _errorMessage.value = "NetBox did not provide form metadata; using the core fields"
                        initializeFields(fallback)
                    }
                }
            _isLoading.value = false
        }
    }

    fun setValue(key: String, value: String) {
        _values.value = _values.value + (key to value)
    }

    fun create() {
        if (_isSaving.value) return
        if (settingsRepository.offlineMode.value) {
            _errorMessage.value = "Turn off offline mode before creating an item"
            return
        }
        buildCreateBody(_fields.value, _values.value)
            .onFailure { _errorMessage.value = it.message }
            .onSuccess { body ->
                viewModelScope.launch {
                    _isSaving.value = true
                    repository.createObject(route.endpointPath, body)
                        .onSuccess { objectJson ->
                            val id = (objectJson["id"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                            if (id == null) {
                                _errorMessage.value = "NetBox created the item but returned no numeric ID"
                            } else {
                                _createdId.value = id
                                when (route.endpointPath) {
                                    "api/dcim/devices/" -> deviceRepository.refreshDevice(id)
                                    "api/dcim/device-types/" -> deviceTypeRepository.refresh(id)
                                }
                                syncScheduler.syncNow()
                            }
                        }
                        .onFailure { _errorMessage.value = it.message ?: "Couldn't create ${route.label}" }
                    _isSaving.value = false
                }
            }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private suspend fun loadReferenceOptions(definitions: List<CreateFieldDefinition>) {
        val options = buildMap {
            definitions.filter { it.referenceEndpointPath != null }.forEach { field ->
                val values = repository.cachedObjects(field.referenceEndpointPath!!).map {
                    CreateChoice(it.id.toString(), it.display)
                }
                if (values.isNotEmpty()) put(field.key, values)
            }
        }
        _referenceOptions.value = options
    }

    private fun initializeFields(definitions: List<CreateFieldDefinition>) {
        _fields.value = definitions
        _values.value = definitions.associate { field ->
            field.key to ((field.defaultValue as? JsonPrimitive)?.contentOrNull ?: "")
        }
        viewModelScope.launch { loadReferenceOptions(definitions) }
    }
}
