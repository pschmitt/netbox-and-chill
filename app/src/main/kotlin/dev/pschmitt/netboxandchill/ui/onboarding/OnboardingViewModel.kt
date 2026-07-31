package dev.pschmitt.netboxandchill.ui.onboarding

import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pschmitt.netboxandchill.data.api.NetBoxApi
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OnboardingUiState {
    data object Idle : OnboardingUiState

    data object Validating : OnboardingUiState

    data class Error(val message: String) : OnboardingUiState

    data object Success : OnboardingUiState
}

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(private val settingsRepository: SettingsRepository, private val api: NetBoxApi) :
    ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun connect(baseUrl: String, token: String) {
        if (baseUrl.isBlank() || token.isBlank()) {
            _uiState.value = OnboardingUiState.Error("Both the NetBox URL and API token are required")
            return
        }
        _uiState.value = OnboardingUiState.Validating
        // Saved before the validation call so the dynamic base-url/auth interceptors pick it up.
        settingsRepository.save(baseUrl, token)
        viewModelScope.launch {
            runCatching { api.listDevices(limit = 1) }
                .onSuccess { _uiState.value = OnboardingUiState.Success }
                .onFailure {
                    settingsRepository.clear()
                    _uiState.value =
                        OnboardingUiState.Error(it.message ?: "Couldn't reach that NetBox instance")
                }
        }
    }
}
