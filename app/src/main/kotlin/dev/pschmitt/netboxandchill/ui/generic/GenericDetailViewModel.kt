package dev.pschmitt.netboxandchill.ui.generic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.repository.CustomFieldRepository
import dev.pschmitt.netboxandchill.data.repository.DeleteSubmission
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.EditSubmission
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.JournalEntryRepository
import dev.pschmitt.netboxandchill.data.repository.PendingEditRepository
import dev.pschmitt.netboxandchill.data.repository.RackElevationRepository
import dev.pschmitt.netboxandchill.data.repository.RackFace
import dev.pschmitt.netboxandchill.data.repository.RecentVisitRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.data.repository.createChoiceSearchFields
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldPreferenceKey
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import dev.pschmitt.netboxandchill.sync.SyncStatusRepository
import dev.pschmitt.netboxandchill.ui.navigation.Route
import dev.pschmitt.netboxandchill.ui.common.REFRESH_QUEUED_TOAST
import dev.pschmitt.netboxandchill.ui.common.refreshCompletionToast
import dev.pschmitt.netboxandchill.ui.common.shouldShowRefreshQueuedToast
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// Mirrors NetBoxNavHost's/GlobalSearchRepository's DEVICES_ENDPOINT_PATH constant - kept local
// rather than shared to avoid a broader refactor while other agents are touching those files.
private const val DEVICES_ENDPOINT_PATH = "api/dcim/devices/"

data class RackDevicePreview(
    val deviceTypeId: Int,
    val frontUrl: String?,
    val rearUrl: String?,
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GenericDetailViewModel
@Inject
constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: GenericObjectRepository,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val journalEntryRepository: JournalEntryRepository,
    private val customFieldRepository: CustomFieldRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val pendingEditRepository: PendingEditRepository,
    private val recentVisitRepository: RecentVisitRepository,
    private val rackElevationRepository: RackElevationRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusRepository: SyncStatusRepository,
    private val json: Json,
    private val api: GenericNetBoxApi,
) : ViewModel() {

    val route: Route.Generic = savedStateHandle.toRoute()

    // NBC-10: "Print label" only makes sense for devices (printlabel's --netbox mode prints a
    // device's QR/asset-tag sticker) - other object types don't have a label to (re)print.
    val isPrintableDevice: Boolean = route.endpointPath == DEVICES_ENDPOINT_PATH

    val isRefreshing: StateFlow<Boolean> =
        syncStatusRepository.isSyncing.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteResult = MutableStateFlow<DeleteSubmission?>(null)
    val deleteResult: StateFlow<DeleteSubmission?> = _deleteResult.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    // Keep the full edit draft in the ViewModel so opening a linked-item create form cannot lose
    // unrelated unsaved fields when navigation temporarily removes this composable.
    private val _editDraftValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val editDraftValues: StateFlow<Map<String, String>> = _editDraftValues.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Positive-confirmation Snackbar text - shared by a manual refresh ("Refreshed") and a
    // successful save ("<item> updated!"), both simple "your action worked" acknowledgements.
    private val _refreshedMessage = MutableStateFlow<String?>(null)
    val refreshedMessage: StateFlow<String?> = _refreshedMessage.asStateFlow()

    private val _refreshToastMessage = MutableStateFlow<String?>(null)
    val refreshToastMessage: StateFlow<String?> = _refreshToastMessage.asStateFlow()
    private var awaitingRefreshCompletion = false

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _fileToOpen = MutableStateFlow<File?>(null)
    val fileToOpen: StateFlow<File?> = _fileToOpen.asStateFlow()

    private val _journalEntries = MutableStateFlow<List<JournalEntryUi>>(emptyList())
    val journalEntries: StateFlow<List<JournalEntryUi>> = _journalEntries.asStateFlow()

    private val objectFlow = repository.observeObject(route.endpointPath, route.id)

    private val objectEntity =
        objectFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var editBaseJson: String? = null

    private val decodedObject: StateFlow<JsonObject?> =
        objectEntity
            .map { entity -> entity?.let { decode(it.json) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val title: StateFlow<String?> =
        objectEntity
            .map { it?.display }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val fields: StateFlow<List<FieldRow>> =
        combine(decodedObject, customFieldRepository.observeDefinitions()) { obj, definitions ->
                obj?.let { buildFieldRows(it, definitions, route.endpointPath) } ?: emptyList()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editableFields: StateFlow<List<EditableField>> =
        combine(decodedObject, customFieldRepository.observeDefinitions()) { obj, definitions ->
                obj?.let { buildEditableFields(it, definitions) } ?: emptyList()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _referenceOptions = MutableStateFlow<Map<String, List<EditOption>>>(emptyMap())
    val referenceOptions: StateFlow<Map<String, List<EditOption>>> = _referenceOptions.asStateFlow()

    private val _choiceOptions = MutableStateFlow<Map<String, List<EditOption>>>(emptyMap())
    val choiceOptions: StateFlow<Map<String, List<EditOption>>> = _choiceOptions.asStateFlow()

    private val linkedCreateResultRaw =
        savedStateHandle.getStateFlow<String?>(LINKED_CREATE_RESULT_KEY, null)
    val linkedCreateResult: StateFlow<LinkedCreateResult?> =
        linkedCreateResultRaw
            .map(::decodeLinkedCreateResult)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hiddenFieldKeys: StateFlow<Set<String>> = settingsRepository.hiddenFieldKeys

    private val _relatedTarget = MutableStateFlow<CountTarget?>(null)
    val relatedTarget: StateFlow<CountTarget?> = _relatedTarget.asStateFlow()

    val isRelatedRefreshing: StateFlow<Boolean> = isRefreshing

    val relatedObjects: StateFlow<List<NetBoxObjectEntity>> =
        _relatedTarget
            .flatMapLatest { target ->
                target?.let {
                    repository.observeObjects(it.endpointPath, "", it.relationKey, it.parentId)
                } ?: flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val relatedPreviewUrls: StateFlow<Map<Int, String>> =
        combine(relatedObjects, deviceTypeRepository.observeAll()) { objects, deviceTypes ->
                val typeImages = deviceTypes.associate {
                    it.id to (it.frontImageUrl ?: it.rearImageUrl)
                }
                objects
                    .mapNotNull { objectEntity ->
                        previewImageUrl(objectEntity, typeImages)?.let { objectEntity.id to it }
                    }
                    .toMap()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isRack: Boolean = route.endpointPath == "api/dcim/racks/"

    val frontElevation =
        rackElevationRepository
            .observe(route.id, RackFace.FRONT)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )

    val rearElevation =
        rackElevationRepository
            .observe(route.id, RackFace.REAR)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )

    /** Device-type stock photos keyed by device id, for the rack elevation blocks. */
    val rackDevicePreviews: StateFlow<Map<Int, RackDevicePreview>> =
        combine(
                repository.observeObjects(DEVICES_ENDPOINT_PATH, "", "rack", route.id),
                deviceTypeRepository.observeAll(),
            ) { devices, deviceTypes ->
                val types = deviceTypes.associateBy { it.id }
                devices
                    .mapNotNull { entity ->
                        val objectJson = decode(entity.json) ?: return@mapNotNull null
                        val deviceTypeId =
                            (objectJson["device_type"] as? JsonObject)?.let {
                                (it["id"] as? JsonPrimitive)?.intOrNull
                            } ?: return@mapNotNull null
                        val deviceType = types[deviceTypeId] ?: return@mapNotNull null
                        entity.id to
                            RackDevicePreview(
                                deviceTypeId = deviceTypeId,
                                frontUrl = deviceType.frontImageUrl,
                                rearUrl = deviceType.rearImageUrl,
                            )
                    }
                    .toMap()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun hideField(label: String) {
        settingsRepository.addHiddenField(hiddenFieldPreferenceKey(route.endpointPath, label))
    }

    fun showRelatedItems(target: CountTarget) {
        _relatedTarget.value = target
    }

    fun dismissRelatedItems() {
        _relatedTarget.value = null
    }

    // Web URL mirrors the API path structure with "api/" dropped, e.g. api/dcim/racks/5/ ->
    // <base>/dcim/racks/5/.
    val webUrl: StateFlow<String?> =
        objectEntity
            .combine(settingsRepository.credentials) { entity, credentials ->
                entity to credentials
            }
            .map { (entity, credentials) ->
                if (entity == null || credentials.baseUrl.isBlank()) null
                else
                    "${credentials.baseUrl}/${route.endpointPath.removePrefix("api/")}${entity.id}/"
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Fed into the `printlabel --netbox-url` flag for the "Print label" share action - see
    // PrintLabelIntent.kt.
    val netboxBaseUrl: StateFlow<String?> =
        settingsRepository.credentials
            .map { it.baseUrl.ifBlank { null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            objectFlow.filterNotNull().take(1).collect { recentVisitRepository.record(it) }
        }
        viewModelScope.launch {
            syncStatusRepository.manualSyncState.drop(1).distinctUntilChanged().collect { state ->
                if (awaitingRefreshCompletion && state?.isFinished == true) {
                    awaitingRefreshCompletion = false
                    _refreshToastMessage.value = refreshCompletionToast(state)
                }
            }
        }
    }

    fun refresh(showConfirmation: Boolean = false) {
        if (!settingsRepository.offlineMode.value) {
            if (shouldShowRefreshQueuedToast(showConfirmation, offlineMode = false)) {
                awaitingRefreshCompletion = true
                _refreshToastMessage.value = REFRESH_QUEUED_TOAST
            }
            syncScheduler.syncNow()
            loadJournalEntries()
        }
    }

    private fun loadJournalEntries() {
        viewModelScope.launch {
            journalEntryRepository.fetchJournalEntries(route.endpointPath, route.id).onSuccess {
                entries ->
                _journalEntries.value = entries.mapNotNull { it.toJournalEntryUi() }
            }
            // Silently no-op on failure - the journal is a secondary panel, not core object data,
            // and content-type resolution can legitimately come up empty for unusual object types.
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun refreshedMessageShown() {
        _refreshedMessage.value = null
    }

    fun refreshToastShown() {
        _refreshToastMessage.value = null
    }

    fun delete() {
        if (_isDeleting.value) return
        viewModelScope.launch {
            _isDeleting.value = true
            pendingEditRepository
                .deleteObject(
                    endpointPath = route.endpointPath,
                    id = route.id,
                    offline = settingsRepository.offlineMode.value,
                )
                .onSuccess { result ->
                    if (route.endpointPath == DEVICES_ENDPOINT_PATH) {
                        deviceRepository.removeCachedDevice(route.id)
                    }
                    _deleteResult.value = result
                }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't delete item" }
            _isDeleting.value = false
        }
    }

    fun deleteResultShown() {
        _deleteResult.value = null
    }

    fun startEditing() {
        _errorMessage.value = null
        editBaseJson = objectEntity.value?.json
        _editDraftValues.value = editableFields.value.associate { it.key to it.value }
        _isEditing.value = true
        _referenceOptions.value = emptyMap()
        _choiceOptions.value = emptyMap()
        viewModelScope.launch { loadEditOptions(editableFields.value) }
    }

    /** Prepare the existing save path for a focused edit dialog without opening the full form. */
    fun startFieldEditing(fieldKey: String) {
        val field = editableFields.value.firstOrNull { it.key == fieldKey } ?: return
        _errorMessage.value = null
        editBaseJson = objectEntity.value?.json
        _isEditing.value = false
        _referenceOptions.value = emptyMap()
        _choiceOptions.value = emptyMap()
        viewModelScope.launch { loadEditOptions(listOf(field)) }
    }

    fun cancelFieldEditing() {
        _errorMessage.value = null
        editBaseJson = null
        _referenceOptions.value = emptyMap()
        _choiceOptions.value = emptyMap()
    }

    fun cancelEditing() {
        _errorMessage.value = null
        editBaseJson = null
        _editDraftValues.value = emptyMap()
        _isEditing.value = false
        _referenceOptions.value = emptyMap()
        _choiceOptions.value = emptyMap()
    }

    /** [edits] maps field key -> (kind, edited text), one entry per changed field. */
    fun save(edits: Map<String, Pair<EditFieldKind, String>>) {
        if (edits.isEmpty()) {
            _editDraftValues.value = emptyMap()
            _isEditing.value = false
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val baseJson = editBaseJson ?: objectEntity.value?.json
            if (baseJson == null) {
                _errorMessage.value = "Couldn't save: the original object is no longer cached"
            } else {
                pendingEditRepository
                    .submitEdit(route.endpointPath, route.id, baseJson, buildPatchBody(edits))
                    .onSuccess { submission ->
                        _isEditing.value = false
                        editBaseJson = null
                        _editDraftValues.value = emptyMap()
                        when (submission) {
                            EditSubmission.Updated -> {
                                _refreshedMessage.value = "${title.value ?: "Item"} updated!"
                                // Refreshes the wider offline cache (and, if enabled, synced
                                // attachments) so an edit's side effects elsewhere in NetBox
                                // aren't only reflected here.
                                syncScheduler.syncNow()
                            }
                            EditSubmission.Queued ->
                                _refreshedMessage.value = "Saved locally; will sync when online"
                            EditSubmission.ConflictDetected ->
                                _errorMessage.value =
                                    "Conflict detected - review it from the dashboard"
                        }
                    }
                    .onFailure { _errorMessage.value = it.message ?: "Couldn't save changes" }
            }
            _isSaving.value = false
        }
    }

    fun initializeEditDraftIfNeeded() {
        if (_editDraftValues.value.isEmpty() && editableFields.value.isNotEmpty()) {
            _editDraftValues.value = editableFields.value.associate { it.key to it.value }
        }
    }

    fun setEditDraftValue(key: String, value: String) {
        _editDraftValues.value = _editDraftValues.value + (key to value)
    }

    fun addReferenceOption(fieldKey: String, option: EditOption) {
        val options = _referenceOptions.value[fieldKey].orEmpty()
        _referenceOptions.value =
            _referenceOptions.value +
                (fieldKey to (options + option).distinctBy { it.value })
    }

    fun consumeLinkedCreateResult() {
        savedStateHandle[LINKED_CREATE_RESULT_KEY] = null
    }

    private suspend fun loadEditOptions(fields: List<EditableField>) {
        val references = mutableMapOf<String, List<EditOption>>()
        fields
            .filter { it.kind in setOf(EditFieldKind.REFERENCE, EditFieldKind.MULTI_REFERENCE) }
            .forEach { field ->
                val endpoint = field.referenceEndpointPath ?: return@forEach
                val cached = repository.cachedObjects(endpoint)
                val options =
                    buildList {
                            field.currentDisplay?.let { add(EditOption(field.value, it)) }
                            addAll(
                                cached.map { entity ->
                                    val objectJson = decode(entity.json)
                                    EditOption(
                                        value = entity.id.toString(),
                                        label = entity.display,
                                        frontImageUrl =
                                            (objectJson?.get("front_image") as? JsonPrimitive)
                                                ?.contentOrNull,
                                        rearImageUrl =
                                            (objectJson?.get("rear_image") as? JsonPrimitive)
                                                ?.contentOrNull,
                                        searchFields = objectJson?.createChoiceSearchFields().orEmpty(),
                                    )
                                }
                            )
                        }
                        .distinctBy { it.value }
                references[field.key] = options
            }
        _referenceOptions.value = references

        val choices = mutableMapOf<String, List<EditOption>>()
        if (
            fields.any { it.kind == EditFieldKind.CHOICE } && !settingsRepository.offlineMode.value
        ) {
            choices.putAll(
                runCatching { api.getObjectOptions("${route.endpointPath}${route.id}/") }
                    .getOrNull()
                    ?.let(::parseChoiceOptions)
                    .orEmpty()
            )
        }
        if (!settingsRepository.offlineMode.value) {
            val definitions = customFieldRepository.observeDefinitions().first()
            fields
                .filter {
                    it.customFieldName != null &&
                        it.kind in setOf(EditFieldKind.CHOICE, EditFieldKind.MULTI_CHOICE)
                }
                .forEach { field ->
                    val definition =
                        definitions.firstOrNull { it.name == field.customFieldName }
                            ?: return@forEach
                    val url = definition.choiceSetUrl ?: return@forEach
                    runCatching { api.getObject(url) }
                        .getOrNull()
                        ?.let(::parseCustomChoiceOptions)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { choices[field.key] = it }
                }
        }
        _choiceOptions.value = choices
    }

    fun downloadAttachment(url: String, filename: String) {
        if (_isDownloading.value) return
        fileDownloadRepository.persistentFile(url, filename)?.let {
            _fileToOpen.value = it
            return
        }
        viewModelScope.launch {
            _isDownloading.value = true
            fileDownloadRepository
                .downloadToCache(url, filename)
                .onSuccess { _fileToOpen.value = it }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't download $filename" }
            _isDownloading.value = false
        }
    }

    fun fileOpened() {
        _fileToOpen.value = null
    }

    fun localAttachmentFile(url: String, filename: String): File? =
        fileDownloadRepository.persistentFile(url, filename)

    private fun previewImageUrl(
        entity: NetBoxObjectEntity,
        deviceTypeImages: Map<Int, String?>,
    ): String? {
        val obj = decode(entity.json) ?: return null
        listOf("front_image", "image", "rear_image").forEach { key ->
            (obj[key] as? JsonPrimitive)
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    return it
                }
        }
        val deviceType = obj["device_type"] as? JsonObject
        val deviceTypeId = (deviceType?.get("id") as? JsonPrimitive)?.intOrNull
        return deviceTypeId?.let { deviceTypeImages[it] }
    }

    private fun decode(rawJson: String): JsonObject? =
        runCatching { json.decodeFromString(JsonObject.serializer(), rawJson) }.getOrNull()
}

internal fun parseChoiceOptions(response: JsonObject): Map<String, List<EditOption>> {
    val actions = response["actions"] as? JsonObject
    val editableFields = ((actions?.get("PATCH") ?: actions?.get("PUT")) as? JsonObject).orEmpty()
    return editableFields
        .mapNotNull { (key, definition) ->
            val choices =
                (definition as? JsonObject)?.get("choices") as? JsonArray ?: return@mapNotNull null
            val options = choices.mapNotNull { choice ->
                val obj = choice as? JsonObject ?: return@mapNotNull null
                val value =
                    (obj["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val label =
                    (obj["display_name"] as? JsonPrimitive)?.contentOrNull
                        ?: (obj["display"] as? JsonPrimitive)?.contentOrNull
                        ?: value
                EditOption(value, label)
            }
            key to options
        }
        .toMap()
}

internal fun parseCustomChoiceOptions(response: JsonObject): List<EditOption> =
    listOf("base_choices", "extra_choices")
        .flatMap { key ->
            (response[key] as? JsonArray).orEmpty().mapNotNull { choice ->
                val pair = choice as? JsonArray ?: return@mapNotNull null
                val value =
                    (pair.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val label = (pair.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: value
                EditOption(value, label)
            }
        }
        .distinctBy { it.value }
