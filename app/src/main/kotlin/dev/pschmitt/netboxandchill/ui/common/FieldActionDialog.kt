package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
    canEdit: Boolean,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fieldLabel) },
        text = {
            Column {
                Text("Choose what to do with this field.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = onEdit,
                    enabled = canEdit,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("Edit field", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = onHide,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                    Text("Hide by default", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
