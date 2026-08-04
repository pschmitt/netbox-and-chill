package dev.pschmitt.nyetbox.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.nyetbox.data.db.BookmarkEntity
import dev.pschmitt.nyetbox.data.db.DashboardStatEntity
import dev.pschmitt.nyetbox.data.db.DeviceEntity
import dev.pschmitt.nyetbox.data.db.ObjectChangeEntity
import dev.pschmitt.nyetbox.data.db.NewsItemEntity
import dev.pschmitt.nyetbox.data.db.RecentVisitEntity
import dev.pschmitt.nyetbox.data.repository.DashboardRepository
import dev.pschmitt.nyetbox.data.repository.DeviceRepository
import dev.pschmitt.nyetbox.data.repository.FileDownloadRepository
import dev.pschmitt.nyetbox.data.repository.GenericObjectRepository
import dev.pschmitt.nyetbox.data.repository.GlobalSearchRepository
import dev.pschmitt.nyetbox.data.repository.PendingEditRepository
import dev.pschmitt.nyetbox.data.repository.RecentVisitRepository
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import dev.pschmitt.nyetbox.data.schema.frontImageUrlFromRawJson
import dev.pschmitt.nyetbox.sync.SyncScheduler
import dev.pschmitt.nyetbox.sync.SyncStatusRepository
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
    private val genericObjectRepository: GenericObjectRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    pendingEditRepository: PendingEditRepository,
    recentVisitRepository: RecentVisitRepository,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    val offlineMode: StateFlow<Boolean> = settingsRepository.offlineMode
    val syncIssue = settingsRepository.syncIssue
    val lastSuccessfulSyncAt = settingsRepository.lastSuccessfulSyncAt
    val dashboardSectionOrder = settingsRepository.dashboardSectionOrder
    val hiddenDashboardSections = settingsRepository.hiddenDashboardSections
    val objectTypeAccents = settingsRepository.objectTypeAccents

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

    val news: StateFlow<List<NewsItemEntity>> =
        repository
            .observeNews()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentVisits: StateFlow<List<RecentVisitEntity>> =
        recentVisitRepository
            .observeRecent(limit = 50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devicesById: StateFlow<Map<Int, DeviceEntity>> =
        deviceRepository
            .observeDevices("")
            .map { devices -> devices.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Sourced from the device type's own generically-synced object, not the DeviceTypeEntity
    // cache table - that table is only populated for device types referenced by a synced Device,
    // so a device type with zero devices (e.g. one just added to NetBox) would never get a
    // thumbnail here otherwise.
    val deviceTypeFrontImagesById: StateFlow<Map<Int, String>> =
        genericObjectRepository
            .observeObjects(GlobalSearchRepository.DEVICE_TYPES_ENDPOINT_PATH, "")
            .map { types -> types.mapNotNull { t -> frontImageUrlFromRawJson(t.json)?.let { t.id to it } }.toMap() }
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

    fun setDashboardSectionOrder(order: List<String>) {
        settingsRepository.setDashboardSectionOrder(order)
    }

    fun setDashboardSectionHidden(sectionKey: String, hidden: Boolean) {
        settingsRepository.setDashboardSectionHidden(sectionKey, hidden)
    }

    fun thumbnailFor(
        endpointPath: String,
        id: Int,
        devicesById: Map<Int, DeviceEntity>,
        deviceTypeFrontImagesById: Map<Int, String>,
    ): DashboardThumbnail? =
        when (endpointPath) {
            GlobalSearchRepository.DEVICE_TYPES_ENDPOINT_PATH ->
                deviceTypeFrontImagesById[id]?.let { url -> DashboardThumbnail(url, "device-type-$id-front") }
            GlobalSearchRepository.DEVICES_ENDPOINT_PATH ->
                devicesById[id]?.deviceTypeId?.let { deviceTypeId ->
                    deviceTypeFrontImagesById[deviceTypeId]?.let { url ->
                        DashboardThumbnail(url, "device-type-$deviceTypeId-front")
                    }
                }
            else -> null
        }

    fun localImageFile(thumbnail: DashboardThumbnail): File? =
        fileDownloadRepository.persistentFile(thumbnail.url, thumbnail.filename)
}
