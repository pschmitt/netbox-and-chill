package dev.pschmitt.netboxandchill.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.ScannerLens
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.scanner.NetBoxUrlParser
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanResultState {
    data object Scanning : ScanResultState

    data object Resolving : ScanResultState

    data class Found(val target: NetBoxTarget) : ScanResultState

    data class NotRecognized(val raw: String) : ScanResultState

    data class NotFound(val assetTag: String) : ScanResultState
}

@HiltViewModel
class ScannerViewModel
@Inject
constructor(
    private val deviceRepository: DeviceRepository,
    private val genericObjectRepository: GenericObjectRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanResultState>(ScanResultState.Scanning)
    val state: StateFlow<ScanResultState> = _state.asStateFlow()

    val scannerLens: StateFlow<ScannerLens> = settingsRepository.scannerLens

    private var handled = false

    fun onCodeScanned(raw: String) {
        if (handled) return
        val target = NetBoxUrlParser.parse(raw)
        if (target == null) {
            val assetTag = NetBoxUrlParser.parseAssetTag(raw)
            if (assetTag == null) {
                _state.value = ScanResultState.NotRecognized(raw)
                return
            }
            handled = true
            _state.value = ScanResultState.Resolving
            viewModelScope.launch {
                val device = deviceRepository.findByAssetTag(assetTag)
                _state.value =
                    device?.let { ScanResultState.Found(NetBoxTarget.Device(it.id)) }
                        ?: ScanResultState.NotFound(assetTag)
            }
            return
        }
        handled = true
        _state.value = ScanResultState.Resolving
        viewModelScope.launch {
            // Best-effort refresh so a freshly scanned object is up to date - the detail screen
            // still works from the Room cache either way if this fails offline.
            when (target) {
                is NetBoxTarget.Device -> deviceRepository.refreshDevice(target.id)
                is NetBoxTarget.Object ->
                    genericObjectRepository.refreshObject(target.endpointPath, target.id)
                is NetBoxTarget.Setup -> Unit
            }
            _state.value = ScanResultState.Found(target)
        }
    }

    fun reset() {
        handled = false
        _state.value = ScanResultState.Scanning
    }

    fun setScannerLens(lens: ScannerLens) {
        settingsRepository.setScannerLens(lens)
    }
}
