package dev.pschmitt.netboxandchill.ui.devicedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentEntity
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.repository.CustomFieldRepository
import dev.pschmitt.netboxandchill.data.repository.DeleteSubmission
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.ImageAttachmentRepository
import dev.pschmitt.netboxandchill.data.repository.JournalEntryRepository
import dev.pschmitt.netboxandchill.data.repository.PendingEditRepository
import dev.pschmitt.netboxandchill.data.repository.RecentVisitRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldPreferenceKey
import dev.pschmitt.netboxandchill.ui.generic.FieldRow
import dev.pschmitt.netboxandchill.ui.generic.JournalEntryUi
import dev.pschmitt.netboxandchill.ui.generic.JournalMutationUiState
import dev.pschmitt.netboxandchill.ui.generic.buildFieldRows
import dev.pschmitt.netboxandchill.ui.generic.toJournalEntryUi
import dev.pschmitt.netboxandchill.ui.common.REFRESH_QUEUED_TOAST
import dev.pschmitt.netboxandchill.ui.common.refreshCompletionToast
import dev.pschmitt.netboxandchill.ui.common.shouldShowRefreshQueuedToast
import dev.pschmitt.netboxandchill.ui.navigation.Route
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

private const val DEVICE_OBJECT_TYPE = "dcim.device"
const val JOURNAL_TAB_ENDPOINT_PATH = "__journal__"
const val INTERFACES_TAB_ENDPOINT_PATH = NetBoxRef.INTERFACES_ENDPOINT_PATH
const val DEVICE_TYPES_ENDPOINT_PATH = NetBoxRef.DEVICE_TYPES_ENDPOINT_PATH
private const val IP_ADDRESSES_ENDPOINT_PATH = NetBoxRef.IP_ADDRESSES_ENDPOINT_PATH
private val ipAddressJson = Json { ignoreUnknownKeys = true }

data class DeviceRelatedTab(val label: String, val endpointPath: String)

data class InterfaceIpAddress(val id: Int, val address: String)

internal data class ParsedInterfaceIpAddress(
    val interfaceId: Int,
    val ipAddress: InterfaceIpAddress,
)

internal fun parseInterfaceIpAddress(
    objectId: Int,
    rawJson: String,
): ParsedInterfaceIpAddress? {
    val objectJson =
        runCatching {
                ipAddressJson.decodeFromString(JsonObject.serializer(), rawJson)
            }
            .getOrNull() ?: return null
    if (objectJson["assigned_object_type"]?.jsonPrimitive?.contentOrNull != "dcim.interface") {
        return null
    }
    val interfaceId = objectJson["assigned_object_id"]?.jsonPrimitive?.intOrNull ?: return null
    val address =
        objectJson["address"]?.jsonPrimitive?.contentOrNull
            ?: objectJson["display"]?.jsonPrimitive?.contentOrNull
    if (address.isNullOrBlank()) return null
    return ParsedInterfaceIpAddress(interfaceId, InterfaceIpAddress(objectId, address))
}

internal fun parseManufacturerId(rawJson: String): Int? {
    val objectJson =
        runCatching {
                ipAddressJson.decodeFromString(JsonObject.serializer(), rawJson)
            }
            .getOrNull() ?: return null
    return (objectJson["manufacturer"] as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull
}

val DEVICE_RELATED_TABS =
    listOf(
        DeviceRelatedTab("Journal", JOURNAL_TAB_ENDPOINT_PATH),
        DeviceRelatedTab("Interfaces", INTERFACES_TAB_ENDPOINT_PATH),
        DeviceRelatedTab("Front ports", "api/dcim/front-ports/"),
        DeviceRelatedTab("Rear ports", "api/dcim/rear-ports/"),
        DeviceRelatedTab("Power ports", "api/dcim/power-ports/"),
        DeviceRelatedTab("Console ports", "api/dcim/console-ports/"),
        DeviceRelatedTab("Power outlets", "api/dcim/power-outlets/"),
        DeviceRelatedTab("Module bays", "api/dcim/module-bays/"),
    )

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeviceDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceRepository: DeviceRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val customFieldRepository: CustomFieldRepository,
    private val imageAttachmentRepository: ImageAttachmentRepository,
    private val journalEntryRepository: JournalEntryRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val genericObjectRepository: GenericObjectRepository,
    private val pendingEditRepository: PendingEditRepository,
    private val recentVisitRepository: RecentVisitRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val deviceId: Int = savedStateHandle.toRoute<Route.DeviceDetail>().deviceId

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteResult = MutableStateFlow<DeleteSubmission?>(null)
    val deleteResult: StateFlow<DeleteSubmission?> = _deleteResult.asStateFlow()

    val hiddenFieldKeys: StateFlow<Set<String>> = settingsRepository.hiddenFieldKeys

    val objectTypeAccent: StateFlow<dev.pschmitt.netboxandchill.data.repository.ThemeAccent?> =
        settingsRepository.objectTypeAccents
            .map { it["api/dcim/devices"] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun hideField(label: String) {
        settingsRepository.addHiddenField(hiddenFieldPreferenceKey("api/dcim/devices/", label))
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _refreshedMessage = MutableStateFlow<String?>(null)
    val refreshedMessage: StateFlow<String?> = _refreshedMessage.asStateFlow()

    private val _refreshToastMessage = MutableStateFlow<String?>(null)
    val refreshToastMessage: StateFlow<String?> = _refreshToastMessage.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _fileToOpen = MutableStateFlow<File?>(null)
    val fileToOpen: StateFlow<File?> = _fileToOpen.asStateFlow()

    val device: StateFlow<DeviceEntity?> =
        deviceRepository
            .observeDevice(deviceId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // device.url is the *API* url (e.g. https://host/api/dcim/devices/393/) - the actual web page
    // mirrors that path with the "/api" prefix dropped.
    val webUrl: StateFlow<String?> =
        device
            .map { entity ->
                entity?.url?.toHttpUrlOrNull()?.let { apiUrl -> apiUrlToWebUrl(apiUrl) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Scheme+host(+port) only, e.g. https://netbox.example.com - fed into the `printlabel
    // --netbox-url` flag for NBC-10's "share a print command" action so it works even if the
    // user's shell doesn't already have NETBOX_URL exported.
    val netboxBaseUrl: StateFlow<String?> =
        device
            .map { entity ->
                entity?.url?.toHttpUrlOrNull()?.let { apiUrl -> apiUrlToBaseUrl(apiUrl) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deviceType: StateFlow<DeviceTypeEntity?> =
        device
            .flatMapLatest { entity ->
                entity?.deviceTypeId?.let { deviceTypeRepository.observe(it) } ?: flowOf(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Manufacturer references live in the cached generic device-type object. */
    val manufacturerId: StateFlow<Int?> =
        device
            .flatMapLatest { entity ->
                entity?.deviceTypeId?.let { deviceTypeId ->
                    genericObjectRepository
                        .observeObject(DEVICE_TYPES_ENDPOINT_PATH, deviceTypeId)
                        .map { objectEntity -> objectEntity?.json?.let(::parseManufacturerId) }
                } ?: flowOf(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Custom fields are stored with the typed device row so this remains usable offline. */
    val customFieldRows: StateFlow<List<FieldRow>> =
        combine(device, customFieldRepository.observeDefinitions()) { entity, definitions ->
                val customFields =
                    entity?.customFieldsJson?.let { raw ->
                        runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
                    } ?: return@combine emptyList()
                buildFieldRows(
                    JsonObject(mapOf("custom_fields" to customFields)),
                    definitions,
                    "api/dcim/devices/",
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val imageAttachments: StateFlow<List<ImageAttachmentEntity>> =
        imageAttachmentRepository
            .observeFor(DEVICE_OBJECT_TYPE, deviceId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val journalEntries: StateFlow<List<JournalEntryUi>> =
        journalEntryRepository
            .observeJournalEntries("api/dcim/devices/", deviceId)
            .map { entries -> entries.mapNotNull { it.toJournalEntryUi() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _journalMutationState = MutableStateFlow(JournalMutationUiState())
    val journalMutationState: StateFlow<JournalMutationUiState> =
        _journalMutationState.asStateFlow()

    val relatedObjects: Map<String, StateFlow<List<NetBoxObjectEntity>>> =
        DEVICE_RELATED_TABS.associate { tab ->
            tab.endpointPath to
                if (tab.endpointPath == JOURNAL_TAB_ENDPOINT_PATH) {
                        flowOf(emptyList())
                    } else {
                        genericObjectRepository.observeObjects(
                            tab.endpointPath,
                            "",
                            "device",
                            deviceId,
                        )
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    val interfaceIpAddresses: StateFlow<Map<Int, List<InterfaceIpAddress>>> =
        genericObjectRepository
            .observeObjects(IP_ADDRESSES_ENDPOINT_PATH, "")
            .map { objects ->
                buildMap {
                        objects.forEach { objectEntity ->
                            parseInterfaceIpAddress(objectEntity.id, objectEntity.json)?.let {
                                assignment ->
                                getOrPut(assignment.interfaceId) { mutableListOf() }
                                    .add(assignment.ipAddress)
                            }
                        }
                    }
                    .mapValues { (_, addresses) -> addresses.distinctBy { it.id } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            device.filterNotNull().take(1).collect { recentVisitRepository.record(it) }
        }
        viewModelScope.launch { refreshJournal() }
    }

    fun refresh(showConfirmation: Boolean = false) {
        if (settingsRepository.offlineMode.value) return
        viewModelScope.launch {
            if (shouldShowRefreshQueuedToast(showConfirmation, offlineMode = false)) {
                _refreshToastMessage.value = REFRESH_QUEUED_TOAST
            }
            _isRefreshing.value = true
            deviceRepository
                .refreshDevice(deviceId)
                .onSuccess {
                    if (showConfirmation) {
                        _refreshToastMessage.value =
                            refreshCompletionToast(androidx.work.WorkInfo.State.SUCCEEDED)
                    }
                }
                .onFailure {
                    if (showConfirmation) {
                        _refreshToastMessage.value =
                            refreshCompletionToast(androidx.work.WorkInfo.State.FAILED)
                    }
                    _errorMessage.value = it.message ?: "Couldn't refresh - showing cached data"
                }
            refreshJournal()
            imageAttachmentRepository.refresh(DEVICE_OBJECT_TYPE, deviceId).onFailure {
                Timber.w(it, "Couldn't refresh image attachments for device %d", deviceId)
            }
            _isRefreshing.value = false
        }
    }

    fun refreshJournal() {
        viewModelScope.launch {
            journalEntryRepository.fetchJournalEntries("api/dcim/devices/", deviceId)
        }
    }

    fun saveJournalEntry(entry: JournalEntryUi?, kind: String, comments: String) {
        if (_journalMutationState.value.isSaving) return
        viewModelScope.launch {
            _journalMutationState.value = JournalMutationUiState(isSaving = true)
            val result =
                if (entry == null) {
                    journalEntryRepository.createJournalEntry(
                        endpointPath = "api/dcim/devices/",
                        objectId = deviceId,
                        kind = kind,
                        comments = comments,
                        offline = settingsRepository.offlineMode.value,
                    )
                } else {
                    journalEntryRepository.updateJournalEntry(
                        id = entry.id,
                        baseJson = entry.baseJson,
                        kind = kind,
                        comments = comments,
                    )
                }
            result
                .onSuccess { mutation ->
                    _journalMutationState.value =
                        JournalMutationUiState(
                            message =
                                if (mutation.queued) {
                                    "Journal entry saved locally; will sync when online"
                                } else {
                                    "Journal entry saved"
                                }
                        )
                }
                .onFailure { error ->
                    _journalMutationState.value =
                        JournalMutationUiState(
                            error = error.message ?: "Couldn't save journal entry"
                        )
                }
        }
    }

    fun journalMutationMessageShown() {
        _journalMutationState.update { it.copy(message = null, error = null) }
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
                    endpointPath = "api/dcim/devices/",
                    id = deviceId,
                    offline = settingsRepository.offlineMode.value,
                )
                .onSuccess { result ->
                    deviceRepository.removeCachedDevice(deviceId)
                    _deleteResult.value = result
                }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't delete device" }
            _isDeleting.value = false
        }
    }

    fun deleteResultShown() {
        _deleteResult.value = null
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

    fun localImageFile(url: String, filename: String): File? =
        fileDownloadRepository.persistentFile(url, filename)

    private fun apiUrlToWebUrl(apiUrl: HttpUrl): String =
        apiUrl.newBuilder().encodedPath(apiUrl.encodedPath.removePrefix("/api")).build().toString()

    private fun apiUrlToBaseUrl(apiUrl: HttpUrl): String =
        apiUrl.newBuilder().encodedPath("/").build().toString().removeSuffix("/")
}
