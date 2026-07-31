package dev.pschmitt.netboxandchill.ui.devicedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.ui.navigation.Route
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DeviceDetailViewModel
@Inject
constructor(savedStateHandle: SavedStateHandle, private val deviceRepository: DeviceRepository) :
    ViewModel() {

    private val deviceId: Int = savedStateHandle.toRoute<Route.DeviceDetail>().deviceId

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val device: StateFlow<DeviceEntity?> =
        deviceRepository
            .observeDevice(deviceId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            deviceRepository
                .refreshDevice(deviceId)
                .onFailure { _errorMessage.value = it.message ?: "Couldn't refresh - showing cached data" }
            _isRefreshing.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }
}
