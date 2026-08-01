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
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.ImageAttachmentRepository
import dev.pschmitt.netboxandchill.data.repository.JournalEntryRepository
import dev.pschmitt.netboxandchill.data.repository.RecentVisitRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldPreferenceKey
import dev.pschmitt.netboxandchill.ui.generic.JournalEntryUi
import dev.pschmitt.netboxandchill.ui.generic.toJournalEntryUi
import java.io.File
import dev.pschmitt.netboxandchill.ui.navigation.Route
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

private const val DEVICE_OBJECT_TYPE = "dcim.device"
const val JOURNAL_TAB_ENDPOINT_PATH = "__journal__"

data class DeviceRelatedTab(val label: String, val endpointPath: String)

val DEVICE_RELATED_TABS =
    listOf(
        DeviceRelatedTab("Journal", JOURNAL_TAB_ENDPOINT_PATH),
        DeviceRelatedTab("Interfaces", "api/dcim/interfaces/"),
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
    private val imageAttachmentRepository: ImageAttachmentRepository,
    private val journalEntryRepository: JournalEntryRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val genericObjectRepository: GenericObjectRepository,
    private val recentVisitRepository: RecentVisitRepository,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val deviceId: Int = savedStateHandle.toRoute<Route.DeviceDetail>().deviceId

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val hiddenFieldKeys: StateFlow<Set<String>> = settingsRepository.hiddenFieldKeys

    fun hideField(label: String) {
        settingsRepository.addHiddenField(hiddenFieldPreferenceKey("api/dcim/devices/", label))
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _refreshedMessage = MutableStateFlow<String?>(null)
    val refreshedMessage: StateFlow<String?> = _refreshedMessage.asStateFlow()

    val device: StateFlow<DeviceEntity?> =
        deviceRepository
            .observeDevice(deviceId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // device.url is the *API* url (e.g. https://host/api/dcim/devices/393/) - the actual web page
    // mirrors that path with the "/api" prefix dropped.
    val webUrl: StateFlow<String?> =
        device
            .map { entity -> entity?.url?.toHttpUrlOrNull()?.let { apiUrl -> apiUrlToWebUrl(apiUrl) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Scheme+host(+port) only, e.g. https://netbox.example.com - fed into the `printlabel
    // --netbox-url` flag for NBC-10's "share a print command" action so it works even if the
    // user's shell doesn't already have NETBOX_URL exported.
    val netboxBaseUrl: StateFlow<String?> =
        device
            .map { entity -> entity?.url?.toHttpUrlOrNull()?.let { apiUrl -> apiUrlToBaseUrl(apiUrl) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deviceType: StateFlow<DeviceTypeEntity?> =
        device
            .flatMapLatest { entity -> entity?.deviceTypeId?.let { deviceTypeRepository.observe(it) } ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val imageAttachments: StateFlow<List<ImageAttachmentEntity>> =
        imageAttachmentRepository
            .observeFor(DEVICE_OBJECT_TYPE, deviceId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _journalEntries = MutableStateFlow<List<JournalEntryUi>>(emptyList())
    val journalEntries: StateFlow<List<JournalEntryUi>> = _journalEntries.asStateFlow()

    val relatedObjects: Map<String, StateFlow<List<NetBoxObjectEntity>>> =
        DEVICE_RELATED_TABS.associate { tab ->
            tab.endpointPath to
                if (tab.endpointPath == JOURNAL_TAB_ENDPOINT_PATH) {
                    flowOf(emptyList())
                } else {
                    genericObjectRepository
                        .observeObjects(tab.endpointPath, "", "device", deviceId)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    init {
        refresh()
        refreshJournal()
        viewModelScope.launch {
            imageAttachmentRepository
                .refresh(DEVICE_OBJECT_TYPE, deviceId)
                .onFailure { Timber.w(it, "Couldn't refresh image attachments for device %d", deviceId) }
        }
        viewModelScope.launch {
            device.filterNotNull().take(1).collect { recentVisitRepository.record(it) }
        }
        // The first cached device row can predate device-type synchronization and have a null
        // deviceTypeId. React to the Room row changing after refresh rather than only inspecting
        // the first emission, otherwise the stock front/rear photos never get loaded.
        viewModelScope.launch {
            device
                .map { it?.deviceTypeId }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { id ->
                    if (!settingsRepository.offlineMode.value) deviceTypeRepository.refresh(id)
                }
        }
    }

    fun refresh(showConfirmation: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            deviceRepository
                .refreshDevice(deviceId)
                .onSuccess { if (showConfirmation) _refreshedMessage.value = "Refreshed" }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't refresh - showing cached data" }
            _isRefreshing.value = false
        }
    }

    fun refreshRelated() {
        if (!settingsRepository.offlineMode.value) syncScheduler.syncNow()
    }

    fun refreshJournal() {
        viewModelScope.launch {
            journalEntryRepository
                .fetchJournalEntries("api/dcim/devices/", deviceId)
                .onSuccess { entries -> _journalEntries.value = entries.mapNotNull { it.toJournalEntryUi() } }
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun refreshedMessageShown() {
        _refreshedMessage.value = null
    }

    fun localImageFile(url: String, filename: String): File? =
        fileDownloadRepository.persistentFile(url, filename)

    private fun apiUrlToWebUrl(apiUrl: HttpUrl): String =
        apiUrl.newBuilder().encodedPath(apiUrl.encodedPath.removePrefix("/api")).build().toString()

    private fun apiUrlToBaseUrl(apiUrl: HttpUrl): String =
        apiUrl.newBuilder().encodedPath("/").build().toString().removeSuffix("/")
}
