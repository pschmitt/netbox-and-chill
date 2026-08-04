package dev.pschmitt.nyetbox.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class SettingsBackupWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: SettingsBackupManager,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val folder =
                settingsRepository.scheduledBackupFolderUri.value?.let(Uri::parse)
                    ?: return@withContext Result.failure()
            try {
                val fileUri =
                    DocumentsContract.createDocument(
                        applicationContext.contentResolver,
                        folder,
                        "application/octet-stream",
                        settingsBackupFileName(),
                    ) ?: error("Could not create a backup file in the selected directory")
                backupManager.write(fileUri, settingsRepository.scheduledBackupPassword())
                settingsRepository.recordBackupSuccess()
                Result.success()
            } catch (error: Exception) {
                Timber.e(error, "Scheduled settings backup failed")
                settingsRepository.recordBackupError(
                    error.message?.takeIf { it.isNotBlank() } ?: "Could not create a backup"
                )
                Result.retry()
            }
        }
}
