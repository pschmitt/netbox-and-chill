package dev.pschmitt.netboxandchill.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.AppDatabase
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DirectoryRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GestureAction
import dev.pschmitt.netboxandchill.data.repository.ScannerLens
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import dev.pschmitt.netboxandchill.sync.SyncStatusRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    val settingsRepository: SettingsRepository,
    private val deviceRepository: DeviceRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusRepository: SyncStatusRepository,
    private val directoryRepository: DirectoryRepository,
    private val appDatabase: AppDatabase,
    private val fileDownloadRepository: FileDownloadRepository,
) : ViewModel() {

    val isSyncing: StateFlow<Boolean> =
        syncStatusRepository.isSyncing.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    private val _isUpdatingBaseUrl = MutableStateFlow(false)
    val isUpdatingBaseUrl: StateFlow<Boolean> = _isUpdatingBaseUrl.asStateFlow()

    private val _cachedDeviceCount = MutableStateFlow(0)
    val cachedDeviceCount: StateFlow<Int> = _cachedDeviceCount.asStateFlow()

    private val _cachedObjectCount = MutableStateFlow(0)
    val cachedObjectCount: StateFlow<Int> = _cachedObjectCount.asStateFlow()

    private val _cachedImageCount = MutableStateFlow(0)
    val cachedImageCount: StateFlow<Int> = _cachedImageCount.asStateFlow()

    private val _persistentCacheBytes = MutableStateFlow(0L)
    val persistentCacheBytes: StateFlow<Long> = _persistentCacheBytes.asStateFlow()

    private val _persistentCacheFiles = MutableStateFlow(0)
    val persistentCacheFiles: StateFlow<Int> = _persistentCacheFiles.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refreshCacheCounts()
        viewModelScope.launch {
            isSyncing.drop(1).distinctUntilChanged().collect { syncing ->
                if (!syncing) refreshCacheCounts()
            }
        }
    }

    fun syncNow() {
        if (settingsRepository.offlineMode.value) return
        syncScheduler.syncNow()
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private fun refreshCacheCounts() {
        viewModelScope.launch {
            _cachedDeviceCount.value = deviceRepository.cachedDeviceCount()
            _cachedObjectCount.value = appDatabase.netBoxObjectDao().countAll()
            _cachedImageCount.value =
                appDatabase.deviceTypeDao().getAll().count {
                    it.frontImageUrl != null || it.rearImageUrl != null
                } + appDatabase.imageAttachmentDao().getAll().size
            fileDownloadRepository.persistentStats().let { stats ->
                _persistentCacheBytes.value = stats.bytes
                _persistentCacheFiles.value = stats.fileCount
            }
        }
    }

    fun setSyncAttachmentsToDisk(enabled: Boolean) {
        settingsRepository.setSyncAttachmentsToDisk(enabled)
        if (enabled) syncNow()
    }

    fun setSyncOnlyOnWifi(enabled: Boolean) {
        settingsRepository.setSyncOnlyOnWifi(enabled)
        syncScheduler.schedulePeriodic()
    }

    fun setSyncWhileRoaming(enabled: Boolean) {
        settingsRepository.setSyncWhileRoaming(enabled)
        syncScheduler.schedulePeriodic()
    }

    fun setGestureAction(action: GestureAction) {
        settingsRepository.setGestureAction(action)
    }

    fun setScannerLens(lens: ScannerLens) {
        settingsRepository.setScannerLens(lens)
    }

    fun setOfflineMode(enabled: Boolean) {
        settingsRepository.setOfflineMode(enabled)
    }

    fun addHiddenField(key: String) {
        settingsRepository.addHiddenField(key)
    }

    fun removeHiddenField(key: String) {
        settingsRepository.removeHiddenField(key)
    }

    /**
     * Switches the configured NetBox server. Saves eagerly (the dynamic base-URL interceptor reads
     * from [SettingsRepository] reactively, so there's no other way to actually test the new URL)
     * then validates reachability, reverting back to the previous URL/token on failure rather than
     * leaving the app pointed at an unreachable instance - mirrors
     * `OnboardingViewModel.connect()`'s save-then-validate shape. On success, wipes the local cache
     * (`AppDatabase.clearAllTables()`) since cached rows are tied to the *previous* server - ids
     * from two different NetBox instances aren't the same objects, so keeping them around would
     * silently mix data from both.
     */
    fun updateBaseUrl(newBaseUrl: String) {
        val previous = settingsRepository.credentials.value
        val trimmed = newBaseUrl.trim().trimEnd('/')
        if (trimmed.isBlank() || trimmed == previous.baseUrl) return
        viewModelScope.launch {
            _isUpdatingBaseUrl.value = true
            settingsRepository.save(trimmed, previous.token)
            directoryRepository
                .refresh()
                .onSuccess {
                    withContext(Dispatchers.IO) { appDatabase.clearAllTables() }
                    refreshCacheCounts()
                }
                .onFailure {
                    settingsRepository.save(previous.baseUrl, previous.token)
                    _errorMessage.value =
                        it.message ?: "Couldn't reach that NetBox instance - reverted"
                }
            _isUpdatingBaseUrl.value = false
        }
    }

    fun logOut() {
        settingsRepository.clear()
    }
}
