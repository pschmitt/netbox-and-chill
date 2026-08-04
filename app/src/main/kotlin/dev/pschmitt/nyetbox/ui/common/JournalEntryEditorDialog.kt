package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.ui.generic.JournalEntryUi
import dev.pschmitt.nyetbox.ui.generic.JournalMutationUiState
import dev.pschmitt.nyetbox.ui.generic.MarkdownEditor

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
    val kindPresentation = journalKindPresentation(kind)

    AlertDialog(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        icon = { Icon(Icons.Default.History, contentDescription = null) },
        title = { Text(if (entry == null) "Add journal entry" else "Edit journal entry") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedButton(
                    onClick = { kindMenuExpanded = true },
                    enabled = !state.isSaving,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = kindPresentation.container,
                            contentColor = kindPresentation.foreground,
                        ),
                    border = BorderStroke(1.dp, kindPresentation.foreground),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(kindPresentation.option.icon, contentDescription = null)
                    Text(
                        kindPresentation.option.label,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                DropdownMenu(
                    expanded = kindMenuExpanded,
                    onDismissRequest = { kindMenuExpanded = false },
                ) {
                    journalKindOptions.forEach { option ->
                        val optionPresentation = journalKindPresentation(option.value)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    color = optionPresentation.foreground,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    option.icon,
                                    contentDescription = null,
                                    tint = optionPresentation.foreground,
                                )
                            },
                            onClick = {
                                kind = option.value
                                kindMenuExpanded = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                MarkdownEditor(
                    value = comments,
                    onValueChange = { comments = it },
                    label = "Comments",
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
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
