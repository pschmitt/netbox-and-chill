package dev.pschmitt.netboxandchill.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.ui.common.formatNetBoxDateTime
import dev.pschmitt.netboxandchill.ui.common.CommentCard
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectChangeDiffScreen(
    onBack: () -> Unit,
    onOpenChangedObject: (endpointPath: String, id: Int) -> Unit = { _, _ -> },
    viewModel: ObjectChangeDiffViewModel = hiltViewModel(),
) {
    val diff by viewModel.diff.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
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
                title = { Text(diff?.objectRepr ?: "Change") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading && diff == null ->
                Column(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            diff == null ->
                Column(
                    modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Couldn't load this change",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else ->
                DiffContent(
                    diff = diff!!,
                    onOpenChangedObject = onOpenChangedObject,
                    localImageFile = viewModel::localImageFile,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
        }
    }
}

@Composable
private fun DiffContent(
    diff: ObjectChangeDiffUi,
    onOpenChangedObject: (endpointPath: String, id: Int) -> Unit,
    localImageFile: (ChangeImage) -> java.io.File?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "${diff.actionLabel} by ${diff.userDisplay}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatNetBoxDateTime(diff.time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        val targetEndpointPath = diff.targetEndpointPath
        val targetId = diff.targetId
        if (targetEndpointPath != null && targetId != null) {
            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable { onOpenChangedObject(targetEndpointPath, targetId) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("${diff.objectRepr}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Open changed item",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (diff.deviceTypeImages.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                diff.deviceTypeImages.forEach { image ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        RemoteThumbnail(
                                            imageUrl = image.url,
                                            contentDescription = "${diff.objectRepr} ${image.label.lowercase()} image",
                                            localFile = localImageFile(image),
                                            modifier = Modifier.size(96.dp),
                                        )
                                        Text(
                                            "${image.label.lowercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (diff.rows.isEmpty()) {
            item {
                Text(
                    "No field-level differences recorded for this change",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(
                diff.rows,
                key = { index, row -> "${row.section.orEmpty()}:${row.label}:$index" },
            ) { index, row ->
                if (row.section != null &&
                    (index == 0 || diff.rows[index - 1].section != row.section)
                ) {
                    Text(
                        row.section,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                }
                DiffRowCard(row) { reference ->
                    onOpenChangedObject(reference.endpointPath, reference.id)
                }
            }
        }
    }
}

@Composable
private fun DiffRowCard(
    row: DiffRow,
    onOpenReference: (DiffReference) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(row.label, style = MaterialTheme.typography.labelLarge)
            if (row.before != null) {
                DiffValue(
                    prefix = "−",
                    value = row.before,
                    color = MaterialTheme.colorScheme.error,
                    markdown = row.markdown,
                    reference = row.beforeReference,
                    onOpenReference = onOpenReference,
                )
            }
            if (row.after != null) {
                DiffValue(
                    prefix = "+",
                    value = row.after,
                    color = MaterialTheme.colorScheme.primary,
                    markdown = row.markdown,
                    reference = row.afterReference,
                    onOpenReference = onOpenReference,
                )
            }
        }
    }
}

@Composable
private fun DiffValue(
    prefix: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    markdown: Boolean,
    reference: DiffReference?,
    onOpenReference: (DiffReference) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = 6.dp)
                .then(
                    if (reference == null) Modifier
                    else Modifier.clickable { onOpenReference(reference) }
                ),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        Text(
            prefix,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (markdown) {
            CommentCard(content = value, modifier = Modifier.weight(1f))
        } else {
            Text(
                value.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier.weight(1f),
            )
        }
        if (reference != null) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open ${value.ifBlank { "linked item" }}",
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
