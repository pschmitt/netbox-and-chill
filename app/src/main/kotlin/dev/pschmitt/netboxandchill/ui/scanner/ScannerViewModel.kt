package dev.pschmitt.netboxandchill.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.scanner.DeviceUrlParser
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanResultState {
    data object Scanning : ScanResultState

    data object Resolving : ScanResultState

    data class Found(val deviceId: Int) : ScanResultState

    data class NotRecognized(val raw: String) : ScanResultState
}

@HiltViewModel
class ScannerViewModel @Inject constructor(private val deviceRepository: DeviceRepository) :
    ViewModel() {

    private val _state = MutableStateFlow<ScanResultState>(ScanResultState.Scanning)
    val state: StateFlow<ScanResultState> = _state.asStateFlow()

    private var handled = false

    fun onCodeScanned(raw: String) {
        if (handled) return
        val deviceId = DeviceUrlParser.parseDeviceId(raw)
        if (deviceId == null) {
            _state.value = ScanResultState.NotRecognized(raw)
            return
        }
        handled = true
        _state.value = ScanResultState.Resolving
        viewModelScope.launch {
            // Best-effort refresh so a freshly scanned device is up to date - DeviceDetailScreen
            // still works from the Room cache either way if this fails offline.
            deviceRepository.refreshDevice(deviceId)
            _state.value = ScanResultState.Found(deviceId)
        }
    }

    fun reset() {
        handled = false
        _state.value = ScanResultState.Scanning
    }
}
