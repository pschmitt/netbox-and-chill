package dev.pschmitt.netboxandchill.ui.conflicts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.PendingEditEntity
import dev.pschmitt.netboxandchill.ui.generic.ConflictChoice
import dev.pschmitt.netboxandchill.ui.generic.ConflictField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditConflictsScreen(
    onBack: () -> Unit,
    viewModel: EditConflictsViewModel = hiltViewModel(),
) {
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val resolvedMessage by viewModel.resolvedMessage.collectAsStateWithLifecycle()
    val isResolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<PendingEditEntity?>(null) }
    var choices by remember { mutableStateOf<Map<String, ConflictChoice>>(emptyMap()) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }
    LaunchedEffect(resolvedMessage) {
        resolvedMessage?.let {
            snackbarHostState.showSnackbar(it)
            selected = null
            viewModel.resolvedMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit conflicts") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (conflicts.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No unresolved edit conflicts", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    Text(
                        "The app kept your local values and the newer server values. Open a conflict to choose field by field.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(conflicts, key = { "${it.endpointPath}-${it.id}" }) { conflict ->
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        headlineContent = { Text("${conflict.endpointPath} #${conflict.id}") },
                        supportingContent = { Text("Choose which changes to keep") },
                        modifier = Modifier.clickable {
                            selected = conflict
                            choices = defaultChoices(viewModel.fields(conflict))
                        },
                    )
                }
            }
        }
    }

    selected?.let { conflict ->
        val fields = viewModel.fields(conflict)
        AlertDialog(
            onDismissRequest = { if (!isResolving) selected = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Resolve #${conflict.id}") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "Base is what was last synced. Local is your edit; server is the newer remote value.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    items(fields, key = { it.key }) { field ->
                        ConflictFieldRow(
                            field = field,
                            choice = choices[field.key] ?: ConflictChoice.SERVER,
                            onChoice = { choices = choices + (field.key to it) },
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }, enabled = !isResolving) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = { viewModel.resolve(conflict, choices) }, enabled = !isResolving) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(if (isResolving) "Resolving…" else "Apply choices")
                }
            },
        )
    }
}

@Composable
private fun ConflictFieldRow(
    field: ConflictField,
    choice: ConflictChoice,
    onChoice: (ConflictChoice) -> Unit,
) {
    Column {
        Text(field.label, style = MaterialTheme.typography.titleSmall)
        Text("Before: ${field.base}", style = MaterialTheme.typography.bodySmall)
        Text("Local: ${field.local}", style = MaterialTheme.typography.bodySmall)
        Text("Server: ${field.server}", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = choice == ConflictChoice.LOCAL, onClick = { onChoice(ConflictChoice.LOCAL) })
            Text("Keep local")
            RadioButton(selected = choice == ConflictChoice.SERVER, onClick = { onChoice(ConflictChoice.SERVER) })
            Text("Keep server")
        }
    }
}

private fun defaultChoices(fields: List<ConflictField>): Map<String, ConflictChoice> =
    fields.associate { field ->
        field.key to
            if (field.local != field.base) ConflictChoice.LOCAL else ConflictChoice.SERVER
    }
