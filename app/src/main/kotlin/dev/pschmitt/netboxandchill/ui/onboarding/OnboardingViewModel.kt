package dev.pschmitt.netboxandchill.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import java.net.SocketTimeoutException
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
constructor(
    private val settingsRepository: SettingsRepository,
    private val api: GenericNetBoxApi,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun connect(baseUrl: String, token: String) {
        if (baseUrl.isBlank() || token.isBlank()) {
            _uiState.value =
                OnboardingUiState.Error("Both the NetBox URL and API token are required")
            return
        }
        _uiState.value = OnboardingUiState.Validating
        // Saved before the validation call so the dynamic base-url/auth interceptors pick it up.
        settingsRepository.save(baseUrl, token)
        viewModelScope.launch {
            // Validate only the lightweight API root here. Walking every app/model during login
            // made setup QR scans look broken on slower devices because one late app discovery
            // could time out the whole onboarding flow. The full directory and object cache are
            // populated by the normal background sync after the credentials are accepted.
            runCatching { api.getApiRoot() }
                .onSuccess {
                    syncScheduler.scheduleStartup()
                    _uiState.value = OnboardingUiState.Success
                }
                .onFailure {
                    settingsRepository.clear()
                    _uiState.value = OnboardingUiState.Error(it.connectionMessage())
                }
        }
    }

    private fun Throwable.connectionMessage(): String =
        when {
            this is SocketTimeoutException ||
                message?.contains("timed out", ignoreCase = true) == true ->
                "NetBox did not respond in time. Check the URL and network connection, then retry."
            message?.contains("401") == true || message?.contains("403") == true ->
                "NetBox rejected this API token. Check that it is still valid and try again."
            else -> message?.takeIf { it.isNotBlank() } ?: "Couldn't reach that NetBox instance"
        }
}
