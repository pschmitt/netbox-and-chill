package dev.pschmitt.netboxandchill.ui.generic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.ui.common.CommentCard
import dev.pschmitt.netboxandchill.ui.common.ImageViewerDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerItem
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.fileViewIntent
import dev.pschmitt.netboxandchill.ui.common.PrintLabelDialog
import dev.pschmitt.netboxandchill.ui.common.PrintLabelRequest
import dev.pschmitt.netboxandchill.ui.common.shareIntent
import androidx.core.content.getSystemService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericDetailScreen(
    onBack: () -> Unit,
    onNavigateToReference: (endpointPath: String, id: Int) -> Unit,
    onNavigateToList: (endpointPath: String, label: String, filterKey: String, filterValue: Int) -> Unit,
    viewModel: GenericDetailViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val editableFields by viewModel.editableFields.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val refreshedMessage by viewModel.refreshedMessage.collectAsStateWithLifecycle()
    val webUrl by viewModel.webUrl.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val fileToOpen by viewModel.fileToOpen.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var editValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var copiedMessage by remember { mutableStateOf<String?>(null) }
    var printRequest by remember { mutableStateOf<PrintLabelRequest?>(null) }
    var imageViewerItem by remember { mutableStateOf<ImageViewerItem?>(null) }
    LaunchedEffect(isEditing) {
        if (isEditing) editValues = editableFields.associate { it.key to it.value }
    }

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        // While editing, the bold inline banner in EditForm below is the primary signal - a
        // Snackbar on top of that (and, on smaller screens, behind the open keyboard) is both
        // redundant and easy to miss, and dismissing it here would also clear the banner before
        // the user has a chance to read it. Outside of editing (e.g. a refresh failure), the
        // Snackbar is still the only signal.
        if (!isEditing) {
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    LaunchedEffect(refreshedMessage) {
        refreshedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.refreshedMessageShown()
        }
    }

    LaunchedEffect(copiedMessage) {
        copiedMessage?.let {
            snackbarHostState.showSnackbar(it)
            copiedMessage = null
        }
    }

    val onCopyValue: (String, String) -> Unit = { label, value ->
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(label, value))
        copiedMessage = "Copied $label"
    }

    LaunchedEffect(fileToOpen) {
        val file = fileToOpen ?: return@LaunchedEffect
        runCatching { context.startActivity(fileViewIntent(context, file)) }
            .onFailure { snackbarHostState.showSnackbar("No app found to open ${file.name}") }
        viewModel.fileOpened()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Details") },
                navigationIcon = {
                    IconButton(onClick = if (isEditing) viewModel::cancelEditing else onBack) {
                        Icon(
                            if (isEditing) Icons.Default.Close
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) "Cancel" else "Back",
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    val kindByKey = editableFields.associateBy { it.key }
                                    // Only the fields actually changed from their original value -
                                    // editValues holds the *entire* form's current state (it's
                                    // seeded from every editable field when entering edit mode),
                                    // so PATCHing all of it back unconditionally resends untouched
                                    // fields too. Beyond the unnecessary noise in NetBox's own
                                    // change log, this can outright break the save: a field NetBox
                                    // computes itself (e.g. an absolute media URL) may reject being
                                    // resent as-is even when nothing about it changed.
                                    val edits = editValues.mapNotNull { (key, value) ->
                                        kindByKey[key]?.let { field ->
                                            if (value != field.value) key to (field.kind to value) else null
                                        }
                                    }
                                    viewModel.save(edits.toMap())
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Save")
                            }
                        }
                    } else {
                        IconButton(onClick = { viewModel.refresh(showConfirmation = true) }, enabled = !isRefreshing) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        if (viewModel.isPrintableDevice) {
                            IconButton(
                                onClick = {
                                    webUrl?.let { url ->
                                        printRequest =
                                            PrintLabelRequest(
                                                objectUrl = url,
                                                labelText = title.orEmpty(),
                                            )
                                    }
                                },
                                enabled = webUrl != null,
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print label")
                            }
                        }
                        if (editableFields.isNotEmpty()) {
                            IconButton(onClick = viewModel::startEditing) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                        webUrl?.let { url ->
                            IconButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.OpenInBrowser,
                                    contentDescription = "Open in browser",
                                )
                            }
                            IconButton(onClick = { context.startActivity(shareIntent(url)) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            title == null ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isRefreshing) "Loading…" else "Not cached yet - connect and refresh",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            isEditing ->
                EditForm(
                    fields = editableFields,
                    values = editValues,
                    onValueChange = { key, value -> editValues = editValues + (key to value) },
                    errorMessage = errorMessage,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            else -> {
                var selectedTab by remember { mutableStateOf(0) }
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Text(
                        title ?: "Object #${viewModel.route.id}",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    if (journalEntries.isNotEmpty()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Details") },
                                icon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Journal") },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                            )
                        }
                    }
                    if (selectedTab == 0 || journalEntries.isEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            fields.forEach { row ->
                                fieldRow(
                                    row,
                                    onNavigateToReference,
                                    onNavigateToList,
                                    onOpenUrl = { url ->
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        )
                                    },
                                    onDownloadAttachment = viewModel::downloadAttachment,
                                    localAttachmentFile = viewModel::localAttachmentFile,
                                    onImageClick = { imageViewerItem = it },
                                    isDownloading = isDownloading,
                                    onCopyValue = onCopyValue,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            items(journalEntries, key = { it.id }) { entry ->
                                JournalEntryItem(entry)
                            }
                        }
                    }
                }
            }
        }
    }
    printRequest?.let { request ->
        PrintLabelDialog(request = request, onDismiss = { printRequest = null })
    }
    imageViewerItem?.let { item ->
        ImageViewerDialog(items = listOf(item), initialIndex = 0, onDismiss = { imageViewerItem = null })
    }
}

@Composable
private fun EditForm(
    fields: List<EditableField>,
    values: Map<String, String>,
    onValueChange: (key: String, value: String) -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A bold, persistent banner rather than a Snackbar - a toast is easy to miss entirely
        // while the keyboard is open (it can render behind it), and a save failure here is
        // exactly the moment the user most needs to notice something went wrong.
        errorMessage?.let { message ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Error, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        items(fields, key = { it.key }) { field ->
            val value = values[field.key] ?: field.value
            when (field.kind) {
                EditFieldKind.BOOLEAN ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(field.label, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = value.toBooleanStrictOrNull() ?: false,
                            onCheckedChange = { onValueChange(field.key, it.toString()) },
                        )
                    }
                EditFieldKind.NUMBER ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onValueChange(field.key, it) },
                        label = { Text(field.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                EditFieldKind.STRING ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onValueChange(field.key, it) },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
            }
        }
    }
}

private fun LazyListScope.fieldRow(
    row: FieldRow,
    onNavigateToReference: (String, Int) -> Unit,
    onNavigateToList: (String, String, String, Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownloadAttachment: (url: String, filename: String) -> Unit,
    localAttachmentFile: (url: String, filename: String) -> java.io.File?,
    onImageClick: (ImageViewerItem) -> Unit,
    isDownloading: Boolean,
    onCopyValue: (label: String, value: String) -> Unit,
) {
    when (row) {
        is FieldRow.CustomGroup ->
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        is FieldRow.PlainText ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(row.value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (row.copyable) {
                            IconButton(onClick = { onCopyValue(row.label, row.value) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy ${row.label}")
                            }
                        }
                    }
                }
            }
        is FieldRow.Count ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                onNavigateToList(
                                    row.target.endpointPath,
                                    row.target.listLabel,
                                    row.target.relationKey,
                                    row.target.parentId,
                                )
                            },
                    ) {
                        Text(
                            row.value,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.FilterList, contentDescription = "Filter ${row.label}")
                    }
                }
            }
        is FieldRow.Markdown ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    CommentCard(content = row.content, modifier = Modifier.fillMaxWidth())
                }
            }
        is FieldRow.Reference ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            row.target.display,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToReference(row.target.endpointPath, row.target.id)
                            },
                        )
                        if (row.copyable) {
                            IconButton(onClick = { onCopyValue(row.label, row.target.display) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy ${row.label}")
                            }
                        }
                    }
                }
            }
        is FieldRow.Image ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    RemoteThumbnail(
                        imageUrl = row.url,
                        contentDescription = row.label,
                        localFile = localAttachmentFile(row.url, row.url.attachmentFilename()),
                        modifier =
                            Modifier.fillMaxWidth().height(160.dp).padding(top = 4.dp).clickable {
                                onImageClick(
                                    ImageViewerItem(
                                        url = row.url,
                                        title = row.label,
                                        localFile = localAttachmentFile(row.url, row.url.attachmentFilename()),
                                    )
                                )
                            },
                        contentScale = ContentScale.Fit,
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
                                Modifier.clickable {
                                        onNavigateToReference(target.endpointPath, target.id)
                                    }
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
        is FieldRow.ExternalLink ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenUrl(row.url) },
                    ) {
                        Text(
                            shortenDisplayedUrl(row.url),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        is FieldRow.FileAttachment ->
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxWidth().clickable(enabled = !isDownloading) {
                                onDownloadAttachment(row.url, row.filename)
                            },
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            row.filename,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Download and open")
                        }
                    }
                }
            }
    }
}

private fun String.attachmentFilename(): String =
    substringAfterLast('/').substringBefore('?').ifBlank { "attachment" }

@Composable
private fun JournalEntryItem(entry: JournalEntryUi) {
    val (icon, tint) =
        when (entry.kind) {
            "success" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
            "warning" -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiary
            "danger" -> Icons.Default.Error to MaterialTheme.colorScheme.error
            else -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = entry.kindLabel,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${entry.kindLabel} · ${entry.created}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        CommentCard(content = entry.comments, modifier = Modifier.fillMaxWidth())
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
