package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One row in an [ActionSheetDialog] - a labeled, icon-led action, optionally destructive. */
data class ActionSheetAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Shared "long-press action menu" shape: a leading icon next to the title, an optional preview
 * block above the action list, and one flat, full-width [TextButton] per [ActionSheetAction]
 * (destructive actions tinted with the error color). Every long-press dialog in the app (field
 * actions, image-attachment actions, document actions) renders through this so they all look the
 * same.
 */
@Composable
fun ActionSheetDialog(
    title: String,
    actions: List<ActionSheetAction>,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                content?.invoke(this)
                actions.forEach { action ->
                    TextButton(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            if (action.destructive) {
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            } else {
                                ButtonDefaults.textButtonColors()
                            },
                    ) {
                        Icon(action.icon, contentDescription = null)
                        Text(action.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
