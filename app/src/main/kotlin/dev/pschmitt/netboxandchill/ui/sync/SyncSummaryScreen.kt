package dev.pschmitt.netboxandchill.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSummaryScreen(summary: String, onBack: () -> Unit) {
    val changes = summary.lines().filter { it.isNotBlank() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uploaded changes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text("NetBox is up to date") },
                    supportingContent = {
                        Text("The following local changes were reconciled successfully.")
                    },
                )
            }
            if (changes.isEmpty()) {
                item { Text("No uploaded changes were recorded.") }
            } else {
                items(changes) { change ->
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Default.Done, contentDescription = null)
                        },
                        headlineContent = { Text(change) },
                    )
                }
            }
        }
    }
}
