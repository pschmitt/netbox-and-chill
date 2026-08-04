package dev.pschmitt.nyetbox.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.nyetbox.data.backup.BackupScheduler
import dev.pschmitt.nyetbox.data.backup.SettingsBackupFormatException
import dev.pschmitt.nyetbox.data.backup.SettingsBackupManager
import dev.pschmitt.nyetbox.data.backup.SettingsBackupPasswordRequiredException
import dev.pschmitt.nyetbox.data.backup.SettingsBackupWrongPasswordException
import dev.pschmitt.nyetbox.data.repository.BackupFrequency
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupOperationState {
    data object Idle : BackupOperationState

    data object Working : BackupOperationState

    data class PasswordRequired(val uri: Uri) : BackupOperationState

    data class Success(val message: String) : BackupOperationState

    data class Error(val message: String) : BackupOperationState
}

@HiltViewModel
class SettingsBackupViewModel
@Inject
constructor(
    val settingsRepository: SettingsRepository,
    private val backupManager: SettingsBackupManager,
    private val backupScheduler: BackupScheduler,
) : ViewModel() {
    private val _operation = MutableStateFlow<BackupOperationState>(BackupOperationState.Idle)
    val operation: StateFlow<BackupOperationState> = _operation.asStateFlow()

    fun export(uri: Uri, password: String?) {
        viewModelScope.launch {
            _operation.value = BackupOperationState.Working
            runCatching { backupManager.write(uri, password?.takeIf { it.isNotEmpty() }) }
                .onSuccess {
                    settingsRepository.recordBackupSuccess()
                    _operation.value = BackupOperationState.Success("Settings backup created")
                }
                .onFailure { _operation.value = BackupOperationState.Error(it.userMessage()) }
        }
    }

    fun restore(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            _operation.value = BackupOperationState.Working
            try {
                val envelope = backupManager.restore(uri, password?.takeIf { it.isNotEmpty() })
                backupScheduler.schedule()
                _operation.value =
                    BackupOperationState.Success(
                        "Restored settings from Nyetbox ${envelope.appVersionName}"
                    )
            } catch (_: SettingsBackupPasswordRequiredException) {
                _operation.value = BackupOperationState.PasswordRequired(uri)
            } catch (error: Exception) {
                _operation.value = BackupOperationState.Error(error.userMessage())
            }
        }
    }

    fun setScheduledBackupEnabled(enabled: Boolean) {
        settingsRepository.setScheduledBackupEnabled(enabled)
        backupScheduler.schedule()
    }

    fun setScheduledBackupFrequency(frequency: BackupFrequency) {
        settingsRepository.setScheduledBackupFrequency(frequency)
        backupScheduler.schedule()
    }

    fun setScheduledBackupFolderUri(uri: String?) {
        settingsRepository.setScheduledBackupFolderUri(uri)
        backupScheduler.schedule()
    }

    fun setScheduledBackupPassword(password: String?) {
        settingsRepository.setScheduledBackupPassword(password)
        backupScheduler.schedule()
    }

    fun dismissOperation() {
        _operation.value = BackupOperationState.Idle
    }

    private fun Throwable.userMessage(): String =
        when (this) {
            is SettingsBackupWrongPasswordException -> message ?: "Incorrect backup password"
            is SettingsBackupFormatException -> message ?: "Invalid settings backup"
            else -> message?.takeIf { it.isNotBlank() } ?: "Could not process the settings backup"
        }
}
