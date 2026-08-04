package dev.pschmitt.nyetbox.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.nyetbox.data.db.PendingEditEntity
import dev.pschmitt.nyetbox.ui.common.NyetboxCard
import dev.pschmitt.nyetbox.ui.common.NyetboxListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingChangesScreen(
    onBack: () -> Unit,
    viewModel: PendingChangesViewModel = hiltViewModel(),
) {
    val changes by viewModel.changes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<PendingEditEntity?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pending changes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (changes.isNotEmpty()) {
                        IconButton(onClick = { confirmAll = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Revert all changes",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (changes.isEmpty()) {
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No pending offline changes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "These changes are stored only on this device until sync is allowed. Reverting removes them locally and does not contact NetBox.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item {
                    Button(onClick = { confirmAll = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Text(" Revert all")
                    }
                }
                items(changes, key = { "${it.endpointPath}:${it.id}" }) { change ->
                    NyetboxCard(modifier = Modifier.padding(vertical = 4.dp)) {
                        NyetboxListItem(
                            leadingContent = {
                                Icon(
                                    if (change.state == PendingEditEntity.CREATE_QUEUED) {
                                        Icons.Default.AddCircle
                                    } else if (change.state == PendingEditEntity.DELETE_QUEUED) {
                                        Icons.Default.Delete
                                    } else Icons.Default.Edit,
                                    contentDescription = null,
                                )
                            },
                            headlineContent = { Text(viewModel.display(change)) },
                            supportingContent = {
                                Text("${viewModel.kind(change)} · ${change.endpointPath}")
                            },
                            trailingContent = {
                                IconButton(onClick = { selected = change }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Revert this change",
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    selected?.let { change ->
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
            title = { Text("Revert this change?") },
            text = { Text("${viewModel.kind(change)}: ${viewModel.display(change)}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revert(change)
                        selected = null
                    }
                ) {
                    Text("Revert")
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Revert all changes?") },
            text = {
                Text(
                    "This removes all pending offline creates, edits, and deletions from this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revertAll()
                        confirmAll = false
                    }
                ) {
                    Text("Revert all")
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
        )
    }
}
