package dev.pschmitt.nyetbox.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.nyetbox.data.db.DeviceEntity
import dev.pschmitt.nyetbox.data.db.DeviceTypeEntity
import dev.pschmitt.nyetbox.data.repository.DeviceRepository
import dev.pschmitt.nyetbox.data.repository.DeviceTypeRepository
import dev.pschmitt.nyetbox.data.repository.FileDownloadRepository
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import dev.pschmitt.nyetbox.sync.SyncScheduler
import dev.pschmitt.nyetbox.sync.SyncStatusRepository
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
    settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    val objectTypeAccent =
        settingsRepository.objectTypeAccents
            .map { accents -> accents["api/dcim/devices"] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun refresh() {
        syncScheduler.syncNow()
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun localImageFile(url: String, filename: String): File? =
        fileDownloadRepository.persistentFile(url, filename)
}
