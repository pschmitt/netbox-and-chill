package dev.pschmitt.netboxandchill.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import dev.pschmitt.netboxandchill.sync.SyncStatusRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeviceListViewModel
@Inject
constructor(
    private val deviceRepository: DeviceRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val isRefreshing: StateFlow<Boolean> =
        syncStatusRepository.isSyncing.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val requestedDeviceTypeIds = mutableSetOf<Int>()

    val devices: StateFlow<List<DeviceEntity>> =
        _query
            .flatMapLatest { deviceRepository.observeDevices(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Device-type id -> cached stock-photo entity, for list-row thumbnails. */
    val deviceTypeImages: StateFlow<Map<Int, DeviceTypeEntity>> =
        deviceTypeRepository
            .observeAll()
            .map { types -> types.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        refresh()
    }

    /** Backfill stock photos only for rows currently visible in the lazy list. */
    fun ensureDeviceTypeImages(ids: Set<Int>) {
        val newIds = synchronized(requestedDeviceTypeIds) {
            ids.filter { requestedDeviceTypeIds.add(it) }
        }
        if (newIds.isNotEmpty()) syncScheduler.syncNow()
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun refresh() {
        synchronized(requestedDeviceTypeIds) { requestedDeviceTypeIds.clear() }
        syncScheduler.syncNow()
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun localImageFile(url: String, filename: String): File? =
        fileDownloadRepository.persistentFile(url, filename)
}
