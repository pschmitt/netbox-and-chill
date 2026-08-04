package dev.pschmitt.nyetbox.data.backup

import android.os.Build
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun settingsBackupFileName(now: LocalDateTime = LocalDateTime.now()): String {
    val deviceName =
        "${Build.MANUFACTURER}-${Build.MODEL}"
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "android-device" }
    return "nyetbox-settings-$deviceName-${now.format(SETTINGS_BACKUP_TIME_FORMAT)}.nyetbox-backup"
}

private val SETTINGS_BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
