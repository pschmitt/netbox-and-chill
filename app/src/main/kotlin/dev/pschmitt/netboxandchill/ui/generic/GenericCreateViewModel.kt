package dev.pschmitt.netboxandchill.ui.generic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.CreateChoice
import dev.pschmitt.netboxandchill.data.repository.CreateFieldDefinition
import dev.pschmitt.netboxandchill.data.repository.CustomFieldDefinition
import dev.pschmitt.netboxandchill.data.repository.CustomFieldRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@HiltViewModel
class GenericCreateViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GenericObjectRepository,
    private val customFieldRepository: CustomFieldRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    val route: Route.GenericCreate = savedStateHandle.toRoute()

    private val _fields = MutableStateFlow<List<CreateFieldDefinition>>(emptyList())
    val fields: StateFlow<List<CreateFieldDefinition>> = _fields.asStateFlow()

    private val _referenceOptions = MutableStateFlow<Map<String, List<CreateChoice>>>(emptyMap())
    val referenceOptions: StateFlow<Map<String, List<CreateChoice>>> =
        _referenceOptions.asStateFlow()

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
            val customDefinitions = customFieldRepository.observeDefinitions().first()
            repository
                .createFieldDefinitions(route.endpointPath)
                .onSuccess { definitions ->
                    val fallback = fallbackCreateFieldDefinitions(route.endpointPath)
                    if (definitions.isEmpty() && fallback.isNotEmpty()) {
                        _errorMessage.value =
                            "NetBox did not provide form metadata; using the core fields"
                        initializeFields(withCustomFields(fallback, customDefinitions))
                    } else {
                        initializeFields(withCustomFields(definitions, customDefinitions))
                    }
                }
                .onFailure {
                    val fallback = fallbackCreateFieldDefinitions(route.endpointPath)
                    if (fallback.isEmpty()) {
                        _errorMessage.value = it.message ?: "Couldn't load the creation form"
                    } else {
                        _errorMessage.value =
                            "NetBox did not provide form metadata; using the core fields"
                        initializeFields(withCustomFields(fallback, customDefinitions))
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
                    repository
                        .createObject(route.endpointPath, body)
                        .onSuccess { objectJson ->
                            val id =
                                (objectJson["id"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                            if (id == null) {
                                _errorMessage.value =
                                    "NetBox created the item but returned no numeric ID"
                            } else {
                                _createdId.value = id
                                when (route.endpointPath) {
                                    "api/dcim/devices/" -> deviceRepository.refreshDevice(id)
                                    "api/dcim/device-types/" -> deviceTypeRepository.refresh(id)
                                }
                                syncScheduler.syncNow()
                            }
                        }
                        .onFailure {
                            _errorMessage.value = it.message ?: "Couldn't create ${route.label}"
                        }
                    _isSaving.value = false
                }
            }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private suspend fun loadReferenceOptions(definitions: List<CreateFieldDefinition>) {
        val options = buildMap {
            definitions
                .filter { it.referenceEndpointPath != null }
                .forEach { field ->
                    val values =
                        repository.cachedObjects(field.referenceEndpointPath!!).map {
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
        viewModelScope.launch { loadCustomChoices(definitions) }
    }

    private suspend fun loadCustomChoices(definitions: List<CreateFieldDefinition>) {
        if (settingsRepository.offlineMode.value) return
        val customDefinitions =
            customFieldRepository.observeDefinitions().first().associateBy { it.name }
        val choices = buildMap {
            definitions
                .filter {
                    it.customFieldName != null &&
                        it.type in setOf("select", "choice", "multiselect", "multi-select")
                }
                .forEach { field ->
                    val name = field.customFieldName ?: return@forEach
                    customDefinitions[name]?.let { definition ->
                        put(field.key, customFieldRepository.choicesFor(definition))
                    }
                }
        }
        if (choices.isNotEmpty()) {
            _fields.value =
                _fields.value.map { field ->
                    field.copy(choices = choices[field.key].orEmpty().ifEmpty { field.choices })
                }
        }
    }

    private fun withCustomFields(
        definitions: List<CreateFieldDefinition>,
        customDefinitions: List<CustomFieldDefinition>,
    ): List<CreateFieldDefinition> {
        val target = route.endpointPath.toObjectType()
        val customFields =
            customDefinitions
                .filter { definition ->
                    target == null ||
                        definition.objectTypes.isEmpty() ||
                        target in definition.objectTypes
                }
                .sortedWith(
                    compareBy<CustomFieldDefinition>(
                        { it.group.orEmpty() },
                        { it.weight },
                        { it.label ?: it.name },
                    )
                )
                .map { definition ->
                    val type = definition.type.lowercase()
                    CreateFieldDefinition(
                        key = "custom_fields.${definition.name}",
                        label = definition.label?.takeIf { it.isNotBlank() } ?: definition.name,
                        type = type,
                        required = false,
                        defaultValue = null,
                        choices = emptyList(),
                        referenceEndpointPath = null,
                        customFieldName = definition.name,
                        markdown = type in setOf("markdown", "longtext"),
                        multiple =
                            type in
                                setOf(
                                    "multiselect",
                                    "multi-select",
                                    "multiple-choice",
                                    "multiple_choice",
                                    "multiple-object",
                                ),
                    )
                }
        return (definitions.filterNot { it.key == "custom_fields" } + customFields).distinctBy {
            it.key
        }
    }

    private fun String.toObjectType(): String? {
        val parts = removePrefix("api/").trim('/').split('/')
        if (parts.size < 2) return null
        val model = parts.last().removeSuffix("s").replace("-", "")
        return "${parts.first()}.$model"
    }
}
