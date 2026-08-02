package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal data class JournalKindOption(
    val value: String,
    val label: String,
    val icon: ImageVector,
)

internal data class JournalKindPresentation(
    val option: JournalKindOption,
    val foreground: Color,
    val container: Color,
)

internal val journalKindOptions =
    listOf(
        JournalKindOption("info", "Info", Icons.Default.Info),
        JournalKindOption("success", "Success", Icons.Default.CheckCircle),
        JournalKindOption("warning", "Warning", Icons.Default.Warning),
        JournalKindOption("danger", "Danger", Icons.Default.Error),
    )

internal fun journalKindOption(kind: String): JournalKindOption =
    when (kind.lowercase()) {
        "success" -> journalKindOptions[1]
        "warning" -> journalKindOptions[2]
        "danger",
        "failed",
        "failure",
        "error" -> journalKindOptions[3]
        else -> journalKindOptions[0]
    }

@Composable
internal fun journalKindPresentation(kind: String): JournalKindPresentation {
    val darkTheme = isSystemInDarkTheme()
    val option = journalKindOption(kind)
    val (foreground, container) =
        when (option.value) {
            "success" ->
                if (darkTheme) {
                    Color(0xFFA5D6A7) to Color(0xFF1B3A1F)
                } else {
                    Color(0xFF2E7D32) to Color(0xFFD7F0D7)
                }
            "warning" ->
                if (darkTheme) {
                    Color(0xFFFFB86B) to Color(0xFF4A2800)
                } else {
                    Color(0xFFB45309) to Color(0xFFFFE4C1)
                }
            "danger" ->
                if (darkTheme) {
                    Color(0xFFFFB4AB) to Color(0xFF5C1616)
                } else {
                    Color(0xFFC62828) to Color(0xFFFFDAD6)
                }
            else ->
                if (darkTheme) {
                    Color(0xFF9ECAFF) to Color(0xFF103354)
                } else {
                    Color(0xFF1565C0) to Color(0xFFD6E4FF)
                }
        }
    return JournalKindPresentation(option, foreground, container)
}
