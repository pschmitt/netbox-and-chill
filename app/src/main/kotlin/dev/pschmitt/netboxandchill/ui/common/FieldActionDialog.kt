package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onDismiss: () -> Unit,
    editLabel: String = "Edit field",
    showHide: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fieldLabel) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
                    Text(value, modifier = Modifier.padding(top = 2.dp))
                    if (onCopy != null) {
                        OutlinedButton(
                            onClick = onCopy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Text("Copy value", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Button(
                    onClick = onEdit,
                    enabled = canEdit,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text(editLabel, modifier = Modifier.padding(start = 8.dp))
                }
                if (showHide) {
                    OutlinedButton(
                        onClick = onHide,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null)
                        Text("Hide by default", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
