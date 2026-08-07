package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.nyetbox.data.repository.CachedDocument
import dev.pschmitt.nyetbox.ui.generic.DiffValueRow

/**
 * Edits a document's NetBox metadata (type, comments) - the only long-press target that couldn't be
 * edited at all before. Two steps, mirroring the generic field editor's edit-then-review shape
 * (`FocusedEditFieldDialog`/`EditDiffDialog` in `ui/generic/GenericDetailEditing.kt`) without
 * coupling to that flow's generic `EditableField` data model, which a document isn't part of.
 */
@Composable
fun DocumentEditDialog(
    document: CachedDocument,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MediaUploadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val documentTypeOptions by viewModel.documentTypeOptions.collectAsStateWithLifecycle()
    var documentTypeValue by remember { mutableStateOf(document.documentTypeValue) }
    var comments by remember { mutableStateOf(document.comments.orEmpty()) }
    var documentTypeMenuExpanded by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }

    val typeChanged = documentTypeValue != document.documentTypeValue
    val commentsChanged = comments != document.comments.orEmpty()
    val hasChanges = typeChanged || commentsChanged

    fun typeLabel(value: String?): String =
        documentTypeOptions.firstOrNull { it.value == value }?.label ?: value ?: "—"

    if (showReview) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            title = { Text("Review changes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (typeChanged) {
                        Text("Type", style = MaterialTheme.typography.titleSmall)
                        DiffValueRow(
                            prefix = "− Before",
                            value = typeLabel(document.documentTypeValue),
                            background = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        DiffValueRow(
                            prefix = "+ After",
                            value = typeLabel(documentTypeValue),
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    if (commentsChanged) {
                        Text("Comments", style = MaterialTheme.typography.titleSmall)
                        DiffValueRow(
                            prefix = "− Before",
                            value = document.comments?.takeIf(String::isNotBlank) ?: "—",
                            background = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        DiffValueRow(
                            prefix = "+ After",
                            value = comments.takeIf(String::isNotBlank) ?: "—",
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Revert")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editDocument(document, documentTypeValue, comments, onSaved)
                    },
                    enabled = !state.isUploading,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm changes")
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!state.isUploading) onDismiss() },
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text("Edit document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { documentTypeMenuExpanded = true },
                        enabled = !state.isUploading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(typeLabel(documentTypeValue))
                    }
                    DropdownMenu(
                        expanded = documentTypeMenuExpanded,
                        onDismissRequest = { documentTypeMenuExpanded = false },
                    ) {
                        documentTypeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    documentTypeValue = option.value
                                    documentTypeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Comments") },
                    enabled = !state.isUploading,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isUploading) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { showReview = true },
                enabled = hasChanges && !state.isUploading,
            ) {
                Text("Review changes")
            }
        },
    )
}
