package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.netboxandchill.ui.generic.JournalEntryUi
import dev.pschmitt.netboxandchill.ui.generic.JournalMutationUiState

private val JournalKinds =
    listOf(
        "info" to "Info",
        "success" to "Success",
        "warning" to "Warning",
        "danger" to "Danger",
    )

@Composable
fun JournalEntryEditorDialog(
    entry: JournalEntryUi?,
    state: JournalMutationUiState,
    onDismiss: () -> Unit,
    onSave: (kind: String, comments: String) -> Unit,
) {
    var kind by remember(entry?.id) { mutableStateOf(entry?.kind ?: "info") }
    var comments by remember(entry?.id) { mutableStateOf(entry?.comments.orEmpty()) }
    var kindMenuExpanded by remember { mutableStateOf(false) }
    val kindLabel = JournalKinds.firstOrNull { it.first == kind }?.second ?: "Info"

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        icon = { Icon(Icons.Default.History, contentDescription = null) },
        title = { Text(if (entry == null) "Add journal entry" else "Edit journal entry") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                OutlinedButton(
                    onClick = { kindMenuExpanded = true },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Text(kindLabel, modifier = Modifier.padding(start = 8.dp))
                }
                DropdownMenu(
                    expanded = kindMenuExpanded,
                    onDismissRequest = { kindMenuExpanded = false },
                ) {
                    JournalKinds.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                kind = value
                                kindMenuExpanded = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    enabled = !state.isSaving,
                    label = { Text("Comments") },
                    supportingText = { Text("Markdown is supported") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
                if (state.isSaving) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = { onSave(kind, comments.trim()) },
                enabled = !state.isSaving && comments.isNotBlank(),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text("Save", modifier = Modifier.padding(start = 8.dp))
            }
        },
    )
}
