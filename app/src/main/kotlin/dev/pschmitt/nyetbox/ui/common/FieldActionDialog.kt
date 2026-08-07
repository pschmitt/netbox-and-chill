package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FieldActionDialog(
    fieldLabel: String,
    fieldValue: String? = null,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onCopy: (() -> Unit)? = null,
    onChangelog: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    editLabel: String = "Edit field",
    showHide: Boolean = true,
) {
    val actions =
        listOfNotNull(
            if (fieldValue != null && onCopy != null) {
                ActionSheetAction(
                    label = "Copy value",
                    icon = Icons.Default.ContentCopy,
                    onClick = onCopy,
                )
            } else null,
            onChangelog?.let {
                ActionSheetAction(label = "View changelog", icon = Icons.Default.History, onClick = it)
            },
            ActionSheetAction(
                label = editLabel,
                icon = Icons.Default.Edit,
                enabled = canEdit,
                onClick = onEdit,
            ),
            if (showHide) {
                ActionSheetAction(
                    label = "Hide by default",
                    icon = Icons.Default.VisibilityOff,
                    onClick = onHide,
                )
            } else null,
        )
    ActionSheetDialog(
        title = fieldLabel,
        actions = actions,
        onDismiss = onDismiss,
    ) {
        Text(
            "Choose what to do with this field.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        fieldValue?.let { value ->
            Text(
                "Value",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(value, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        }
    }
}
