package dev.pschmitt.nyetbox.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.nyetbox.data.api.GenericNetBoxApi
import dev.pschmitt.nyetbox.data.backup.SettingsBackupManager
import dev.pschmitt.nyetbox.data.backup.SettingsBackupPasswordRequiredException
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import dev.pschmitt.nyetbox.sync.SyncScheduler
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

    data class PasswordRequired(val uri: Uri) : OnboardingUiState

    data class Success(val restoredBackup: Boolean = false) : OnboardingUiState
}

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
    private val settingsRepository: SettingsRepository,
    private val api: GenericNetBoxApi,
    private val syncScheduler: SyncScheduler,
    private val backupManager: SettingsBackupManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun connect(baseUrl: String, token: String, restoredBackup: Boolean = false) {
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
                    _uiState.value = OnboardingUiState.Success(restoredBackup)
                }
                .onFailure {
                    settingsRepository.clear()
                    _uiState.value = OnboardingUiState.Error(it.connectionMessage())
                }
        }
    }

    fun restoreBackup(uri: Uri, password: String? = null) {
        _uiState.value = OnboardingUiState.Validating
        viewModelScope.launch {
            try {
                val envelope = backupManager.restore(uri, password)
                val profile =
                    envelope.settings.serverProfiles.firstOrNull()
                        ?: error("This backup does not contain a NetBox connection")
                // A backup already contains validated connection credentials. Reuse the normal
                // lightweight connection check so restore goes straight to the dashboard instead
                // of leaving the user on the login screen with a second manual Submit step.
                connect(profile.baseUrl, profile.token, restoredBackup = true)
            } catch (_: SettingsBackupPasswordRequiredException) {
                _uiState.value = OnboardingUiState.PasswordRequired(uri)
            } catch (error: Exception) {
                _uiState.value =
                    OnboardingUiState.Error(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: "Could not restore the settings backup"
                    )
            }
        }
    }

    fun consumeRestoredBackup() {
        if (_uiState.value is OnboardingUiState.PasswordRequired) {
            _uiState.value = OnboardingUiState.Idle
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
