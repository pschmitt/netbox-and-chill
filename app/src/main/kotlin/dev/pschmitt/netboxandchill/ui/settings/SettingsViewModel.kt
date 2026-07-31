package dev.pschmitt.netboxandchill.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    val settingsRepository: SettingsRepository,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _cachedDeviceCount = MutableStateFlow(0)
    val cachedDeviceCount: StateFlow<Int> = _cachedDeviceCount.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch { _cachedDeviceCount.value = deviceRepository.cachedDeviceCount() }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            deviceRepository
                .syncAll()
                .onFailure { _errorMessage.value = it.message ?: "Sync failed - showing cached data" }
            _cachedDeviceCount.value = deviceRepository.cachedDeviceCount()
            _isSyncing.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun setSyncAttachmentsToDisk(enabled: Boolean) {
        settingsRepository.setSyncAttachmentsToDisk(enabled)
    }

    fun logOut() {
        settingsRepository.clear()
    }
}
