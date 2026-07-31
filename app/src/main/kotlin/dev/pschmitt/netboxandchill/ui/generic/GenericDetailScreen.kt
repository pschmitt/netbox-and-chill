package dev.pschmitt.netboxandchill.ui.generic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericDetailScreen(
    onBack: () -> Unit,
    onNavigateToReference: (endpointPath: String, id: Int) -> Unit,
    viewModel: GenericDetailViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val webUrl by viewModel.webUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title ?: "Object #${viewModel.route.id}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    webUrl?.let { url ->
                        IconButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (title == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isRefreshing) "Loading…" else "Not cached yet - connect and refresh",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                fields.forEach { row -> fieldRow(row, onNavigateToReference) }
            }
        }
    }
}

private fun LazyListScope.fieldRow(row: FieldRow, onNavigateToReference: (String, Int) -> Unit) {
    when (row) {
        is FieldRow.PlainText ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    Text(row.value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        is FieldRow.Reference ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    Text(
                        row.target.display,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier.clickable {
                                onNavigateToReference(row.target.endpointPath, row.target.id)
                            },
                    )
                }
            }
        is FieldRow.ReferenceList ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    row.targets.forEach { target ->
                        Text(
                            "• " + target.display,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier.clickable { onNavigateToReference(target.endpointPath, target.id) }
                                    .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        is FieldRow.ChipList ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    Text(row.values.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                }
            }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
