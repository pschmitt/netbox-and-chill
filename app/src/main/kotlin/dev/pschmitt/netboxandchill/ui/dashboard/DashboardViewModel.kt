package dev.pschmitt.netboxandchill.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.BookmarkEntity
import dev.pschmitt.netboxandchill.data.db.DashboardStatEntity
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import dev.pschmitt.netboxandchill.data.db.ObjectChangeEntity
import dev.pschmitt.netboxandchill.data.repository.DashboardRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GlobalSearchRepository
import dev.pschmitt.netboxandchill.data.repository.PendingEditRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import dev.pschmitt.netboxandchill.sync.SyncStatusRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardThumbnail(val url: String, val filename: String)

@HiltViewModel
class DashboardViewModel
@Inject
constructor(
    private val repository: DashboardRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    pendingEditRepository: PendingEditRepository,
    settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    val offlineMode: StateFlow<Boolean> = settingsRepository.offlineMode
    val syncIssue = settingsRepository.syncIssue
    val lastSuccessfulSyncAt = settingsRepository.lastSuccessfulSyncAt

    val isRefreshing: StateFlow<Boolean> =
        syncStatusRepository.isSyncing.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val stats: StateFlow<List<DashboardStatEntity>> =
        repository
            .observeStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        repository
            .observeBookmarks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val changelog: StateFlow<List<ObjectChangeEntity>> =
        repository
            .observeChangelog()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devicesById: StateFlow<Map<Int, DeviceEntity>> =
        deviceRepository
            .observeDevices("")
            .map { devices -> devices.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val deviceTypesById: StateFlow<Map<Int, DeviceTypeEntity>> =
        deviceTypeRepository
            .observeAll()
            .map { types -> types.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val conflictCount: StateFlow<Int> =
        pendingEditRepository
            .observeConflictCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingChangeCount: StateFlow<Int> =
        pendingEditRepository
            .observeQueuedMutationCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun refresh() {
        if (!offlineMode.value) syncScheduler.syncNow()
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun retrySync() {
        if (!offlineMode.value) syncScheduler.syncNow()
    }

    fun thumbnailFor(
        endpointPath: String,
        id: Int,
        devicesById: Map<Int, DeviceEntity>,
        deviceTypesById: Map<Int, DeviceTypeEntity>,
    ): DashboardThumbnail? =
        when (endpointPath) {
            GlobalSearchRepository.DEVICE_TYPES_ENDPOINT_PATH ->
                deviceTypesById[id]?.frontImageUrl?.takeIf(String::isNotBlank)?.let { url ->
                    DashboardThumbnail(url, "device-type-$id-front")
                }
            GlobalSearchRepository.DEVICES_ENDPOINT_PATH ->
                devicesById[id]?.deviceTypeId?.let { deviceTypeId ->
                    deviceTypesById[deviceTypeId]?.frontImageUrl?.takeIf(String::isNotBlank)?.let {
                        url ->
                        DashboardThumbnail(url, "device-type-$deviceTypeId-front")
                    }
                }
            else -> null
        }

    fun localImageFile(thumbnail: DashboardThumbnail): File? =
        fileDownloadRepository.persistentFile(thumbnail.url, thumbnail.filename)
}
