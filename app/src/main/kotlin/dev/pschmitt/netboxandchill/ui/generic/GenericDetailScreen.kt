package dev.pschmitt.netboxandchill.ui.generic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.db.RackElevationEntity
import dev.pschmitt.netboxandchill.data.repository.RackFace
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldObjectKey
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldPreferenceKey
import dev.pschmitt.netboxandchill.data.repository.choiceSearchHint
import dev.pschmitt.netboxandchill.data.schema.Humanize
import dev.pschmitt.netboxandchill.ui.common.CollapsibleCommentCard
import dev.pschmitt.netboxandchill.ui.common.CommentCard
import dev.pschmitt.netboxandchill.ui.common.CachedBadge
import dev.pschmitt.netboxandchill.ui.common.DetailTrailingActions
import dev.pschmitt.netboxandchill.ui.common.DocumentsSection
import dev.pschmitt.netboxandchill.ui.common.FieldActionDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerItem
import dev.pschmitt.netboxandchill.ui.common.ImageAttachmentGallery
import dev.pschmitt.netboxandchill.ui.common.ItemDetailTab
import dev.pschmitt.netboxandchill.ui.common.ItemDetailTabs
import dev.pschmitt.netboxandchill.ui.common.JournalEntryEditorDialog
import dev.pschmitt.netboxandchill.ui.common.journalKindPresentation
import dev.pschmitt.netboxandchill.ui.common.MatterPairingCodeDialog
import dev.pschmitt.netboxandchill.ui.common.MediaUploadDialog
import dev.pschmitt.netboxandchill.ui.common.MediaUploadKind
import dev.pschmitt.netboxandchill.ui.common.itemTabSwipe
import dev.pschmitt.netboxandchill.ui.common.PrintLabelDialog
import dev.pschmitt.netboxandchill.ui.common.PrintLabelRequest
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.StatusChip
import dev.pschmitt.netboxandchill.ui.common.detailAccentFor
import dev.pschmitt.netboxandchill.ui.common.fileViewIntent
import dev.pschmitt.netboxandchill.ui.common.formatNetBoxDateTime
import dev.pschmitt.netboxandchill.ui.common.shareIntent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GenericDetailScreen(
    highlightDeviceId: Int? = null,
    onBack: () -> Unit,
    onNavigateToReference: (endpointPath: String, id: Int, breadcrumb: String?) -> Unit,
    onCreateLinkedItem: (
        fieldKey: String,
        endpointPath: String,
        label: String,
        reopenFocusedEditor: Boolean,
    ) -> Unit,
    viewModel: GenericDetailViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val editableFields by viewModel.editableFields.collectAsStateWithLifecycle()
    val referenceOptions by viewModel.referenceOptions.collectAsStateWithLifecycle()
    val choiceOptions by viewModel.choiceOptions.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleteResult by viewModel.deleteResult.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val refreshedMessage by viewModel.refreshedMessage.collectAsStateWithLifecycle()
    val refreshToastMessage by viewModel.refreshToastMessage.collectAsStateWithLifecycle()
    val webUrl by viewModel.webUrl.collectAsStateWithLifecycle()
    val netboxBaseUrl by viewModel.netboxBaseUrl.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val fileToOpen by viewModel.fileToOpen.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val imageAttachments by viewModel.imageAttachments.collectAsStateWithLifecycle()
    val journalMutationState by viewModel.journalMutationState.collectAsStateWithLifecycle()
    val hiddenFieldKeys by viewModel.hiddenFieldKeys.collectAsStateWithLifecycle()
    val objectTypeAccent by viewModel.objectTypeAccent.collectAsStateWithLifecycle()
    val frontElevation by viewModel.frontElevation.collectAsStateWithLifecycle()
    val rearElevation by viewModel.rearElevation.collectAsStateWithLifecycle()
    val rackDevicePreviews by viewModel.rackDevicePreviews.collectAsStateWithLifecycle()
    val relatedTarget by viewModel.relatedTarget.collectAsStateWithLifecycle()
    val relatedObjects by viewModel.relatedObjects.collectAsStateWithLifecycle()
    val relatedPreviewUrls by viewModel.relatedPreviewUrls.collectAsStateWithLifecycle()
    val isRelatedRefreshing by viewModel.isRelatedRefreshing.collectAsStateWithLifecycle()
    val editDraftValues by viewModel.editDraftValues.collectAsStateWithLifecycle()
    val linkedCreateResult by viewModel.linkedCreateResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var copiedMessage by remember { mutableStateOf<String?>(null) }
    var printRequest by remember { mutableStateOf<PrintLabelRequest?>(null) }
    var showMediaUpload by remember { mutableStateOf(false) }
    var mediaUploadInitialKind by remember { mutableStateOf<MediaUploadKind?>(null) }
    var showJournalEditor by remember { mutableStateOf(false) }
    var journalEditorEntry by remember { mutableStateOf<JournalEntryUi?>(null) }
    var imageViewer by remember { mutableStateOf<Pair<List<ImageViewerItem>, Int>?>(null) }
    var matterPairingCode by remember { mutableStateOf<String?>(null) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showHiddenFields by remember { mutableStateOf(false) }
    var fieldActionLabel by remember { mutableStateOf<String?>(null) }
    var pendingEdits by remember { mutableStateOf<Map<String, Pair<EditFieldKind, String>>?>(null) }
    var pendingEditFieldKey by remember { mutableStateOf<String?>(null) }
    var focusedEditFieldKey by remember { mutableStateOf<String?>(null) }
    var focusedEditValue by remember { mutableStateOf("") }
    var routeFocusHandled by remember { mutableStateOf(false) }
    var automaticEditStarted by remember { mutableStateOf(false) }
    val hiddenObjectKey = hiddenFieldObjectKey(viewModel.route.endpointPath)
    val isRouteFocusedEditor = viewModel.route.focusFieldKey != null
    val hiddenFieldsForObject = hiddenFieldKeys.filter { it.startsWith("$hiddenObjectKey/") }
    val visibleFields =
        visibleFieldRows(
            fields,
            viewModel.route.endpointPath,
            hiddenFieldKeys,
            showHiddenFields,
        )
    val statusField =
        visibleFields.firstOrNull { it.label.equals("Status", ignoreCase = true) }
            as? FieldRow.PlainText
    val visibleOverviewFields = visibleFields.filterNot { it == statusField }
    val modelLabel = endpointModelLabel(viewModel.route.endpointPath)
    val detailAccent =
        MaterialTheme.colorScheme.detailAccentFor(viewModel.route.endpointPath, objectTypeAccent)
    val focusedEditField = focusedEditFieldKey?.let { key ->
        editableFields.firstOrNull { it.key == key }
    }
    LaunchedEffect(isEditing, editableFields) {
        if (isEditing) viewModel.initializeEditDraftIfNeeded()
    }
    LaunchedEffect(linkedCreateResult, editableFields) {
        val result = linkedCreateResult ?: return@LaunchedEffect
        val field = editableFields.firstOrNull { it.key == result.fieldKey }
        if (field != null && field.referenceEndpointPath == result.endpointPath) {
            val nextValue =
                if (field.kind == EditFieldKind.MULTI_REFERENCE) {
                    selectedValuesToJson(
                        (selectedValuesFromJson(editDraftValues[field.key] ?: field.value) +
                                result.id.toString())
                            .distinct()
                    )
                } else {
                    result.id.toString()
                }
            viewModel.addReferenceOption(
                field.key,
                EditOption(result.id.toString(), result.display),
            )
            if (result.reopenFocusedEditor) {
                focusedEditFieldKey = field.key
                focusedEditValue = nextValue
                viewModel.startFieldEditing(field.key)
            } else {
                viewModel.setEditDraftValue(field.key, nextValue)
            }
        }
        viewModel.consumeLinkedCreateResult()
    }
    LaunchedEffect(viewModel.route.focusFieldKey, editableFields) {
        val fieldKey = viewModel.route.focusFieldKey ?: return@LaunchedEffect
        if (
            shouldLaunchRouteFocusedEditor(
                routeFocusHandled = routeFocusHandled,
                focusFieldKey = fieldKey,
                focusedEditFieldKey = focusedEditFieldKey,
                hasPendingEdits = pendingEdits != null,
            )
        ) {
            editableFields
                .firstOrNull { it.key == fieldKey }
                ?.let { field ->
                    routeFocusHandled = true
                    focusedEditFieldKey = field.key
                    focusedEditValue = field.value
                    viewModel.startFieldEditing(field.key)
                }
        }
    }
    LaunchedEffect(viewModel.route.startInEdit, title, editableFields) {
        if (
            viewModel.route.startInEdit &&
                !automaticEditStarted &&
                title != null &&
                editableFields.isNotEmpty()
        ) {
            automaticEditStarted = true
            viewModel.startEditing()
        }
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

    LaunchedEffect(refreshToastMessage) {
        refreshToastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.refreshToastShown()
        }
    }

    LaunchedEffect(journalMutationState.message) {
        journalMutationState.message?.let {
            showJournalEditor = false
            snackbarHostState.showSnackbar(it)
            viewModel.journalMutationMessageShown()
        }
    }

    LaunchedEffect(deleteResult) {
        if (deleteResult != null) {
            showDeleteConfirmation = false
            viewModel.deleteResultShown()
            onBack()
        }
    }

    LaunchedEffect(copiedMessage) {
        copiedMessage?.let {
            snackbarHostState.showSnackbar(it)
            copiedMessage = null
        }
    }

    val onCopyValue: (String, String) -> Unit = { label, value ->
        context
            .getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText(label, value))
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = detailAccent.copy(alpha = 0.12f),
                        navigationIconContentColor = detailAccent,
                        actionIconContentColor = detailAccent,
                    ),
                title = {
                    Column {
                        Text(modelLabel, maxLines = 1)
                        if (viewModel.route.breadcrumb != null) {
                            Text(
                                "from ${viewModel.route.breadcrumb}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
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
                                    // editDraftValues holds the *entire* form's current state (it's
                                    // seeded from every editable field when entering edit mode),
                                    // so PATCHing all of it back unconditionally resends untouched
                                    // fields too. Beyond the unnecessary noise in NetBox's own
                                    // change log, this can outright break the save: a field NetBox
                                    // computes itself (e.g. an absolute media URL) may reject being
                                    // resent as-is even when nothing about it changed.
                                    val edits =
                                        editDraftValues
                                            .mapNotNull { (key, value) ->
                                                kindByKey[key]?.let { field ->
                                                    if (value != field.value)
                                                        key to (field.kind to value)
                                                    else null
                                                }
                                            }
                                            .toMap()
                                    if (edits.isEmpty()) {
                                        viewModel.save(emptyMap())
                                    } else {
                                        pendingEdits = edits
                                        pendingEditFieldKey = null
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Save")
                            }
                        }
                    } else {
                        Box {
                            IconButton(onClick = { actionMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = actionMenuExpanded,
                                onDismissRequest = { actionMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                    },
                                    enabled = !isRefreshing,
                                    onClick = {
                                        viewModel.refresh(showConfirmation = true)
                                        actionMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Upload media") },
                                    leadingIcon = {
                                        Icon(Icons.Default.UploadFile, contentDescription = null)
                                    },
                                    enabled = !isRefreshing,
                                    onClick = {
                                        showMediaUpload = true
                                        mediaUploadInitialKind = null
                                        actionMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Add journal entry") },
                                    leadingIcon = {
                                        Icon(Icons.Default.History, contentDescription = null)
                                    },
                                    enabled = !isRefreshing,
                                    onClick = {
                                        journalEditorEntry = null
                                        showJournalEditor = true
                                        actionMenuExpanded = false
                                    },
                                )
                                if (viewModel.isPrintableDevice) {
                                    DropdownMenuItem(
                                        text = { Text("Print label") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Print, contentDescription = null)
                                        },
                                        enabled = webUrl != null,
                                        onClick = {
                                            webUrl?.let { url ->
                                                printRequest =
                                                    PrintLabelRequest(
                                                        objectUrl = url,
                                                        labelText = title.orEmpty(),
                                                        longLabelText =
                                                            buildList {
                                                                    title
                                                                        ?.takeIf { it.isNotBlank() }
                                                                        ?.let(::add)
                                                                    fields
                                                                        .filterIsInstance<
                                                                            FieldRow.PlainText
                                                                        >()
                                                                        .firstOrNull {
                                                                            it.label.equals(
                                                                                "Asset tag",
                                                                                ignoreCase = true,
                                                                            )
                                                                        }
                                                                        ?.value
                                                                        ?.takeIf { it.isNotBlank() }
                                                                        ?.let(::add)
                                                                    fields
                                                                        .filterIsInstance<
                                                                            FieldRow.PlainText
                                                                        >()
                                                                        .firstOrNull {
                                                                            it.label.equals(
                                                                                "Serial",
                                                                                ignoreCase = true,
                                                                            )
                                                                        }
                                                                        ?.value
                                                                        ?.takeIf { it.isNotBlank() }
                                                                        ?.let(::add)
                                                                }
                                                                .joinToString("\n")
                                                                .takeIf { it.isNotBlank() },
                                                    )
                                            }
                                            actionMenuExpanded = false
                                        },
                                    )
                                }
                                if (editableFields.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Edit, contentDescription = null)
                                        },
                                        onClick = {
                                            viewModel.startEditing()
                                            actionMenuExpanded = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    enabled = title != null && !isDeleting,
                                    onClick = {
                                        showDeleteConfirmation = true
                                        actionMenuExpanded = false
                                    },
                                )
                                webUrl?.let { url ->
                                    DropdownMenuItem(
                                        text = { Text("Open in browser") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.OpenInBrowser,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            )
                                            actionMenuExpanded = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Share, contentDescription = null)
                                        },
                                        onClick = {
                                            context.startActivity(shareIntent(url))
                                            actionMenuExpanded = false
                                        },
                                    )
                                }
                                if (hiddenFieldsForObject.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Show hidden fields") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Visibility,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            showHiddenFields = true
                                            actionMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (isEditing) {
            EditForm(
                fields = editableFields,
                values = editDraftValues,
                referenceOptions = referenceOptions,
                choiceOptions = choiceOptions,
                onValueChange = viewModel::setEditDraftValue,
                errorMessage = errorMessage,
                onCreateLinkedItem = { field ->
                    field.referenceEndpointPath?.let { endpoint ->
                        onCreateLinkedItem(field.key, endpoint, field.label, false)
                    }
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            PullToRefreshBox(
                // Sync has a global progress bar and Android notification; avoid the large
                // circular indicator over the item while that background work is running.
                isRefreshing = false,
                onRefresh = { viewModel.refresh(showConfirmation = true) },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                when {
                    title == null ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (isRefreshing) "Loading…"
                                else "Not cached yet - connect and refresh",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    else -> {
                        val hasJournal = journalEntries.isNotEmpty()
                        var selectedTab by remember { mutableStateOf(0) }
                        val tabCount = if (hasJournal) 2 else 1
                        val visibleSelectedTab = selectedTab.coerceIn(0, tabCount - 1)
                        LaunchedEffect(hasJournal) {
                            selectedTab = visibleSelectedTab
                        }
                        Column(
                            Modifier.fillMaxSize().itemTabSwipe(visibleSelectedTab, tabCount) {
                                selectedTab = it
                            }
                        ) {
                            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = detailAccent.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.size(52.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Outlined.Category,
                                                    contentDescription = null,
                                                    tint = detailAccent,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }
                                        }
                                        Column(Modifier.padding(start = 14.dp)) {
                                            Text(
                                                title ?: "Object #${viewModel.route.id}",
                                                style = MaterialTheme.typography.headlineSmall,
                                            )
                                            Text(
                                                "ID #${viewModel.route.id}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CachedBadge()
                                        statusField?.let { status ->
                                            Box(
                                                modifier =
                                                    Modifier.combinedClickable(
                                                        onClick = {},
                                                        onLongClick = {
                                                            fieldActionLabel = status.label
                                                        },
                                                    )
                                            ) {
                                                StatusChip(
                                                    label = status.value,
                                                    value = status.value.lowercase(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ItemDetailTabs(
                                tabs =
                                    listOf(
                                        ItemDetailTab("Overview", Icons.Default.Info),
                                    ) +
                                        if (hasJournal) {
                                            listOf(
                                                ItemDetailTab(
                                                    "Journal",
                                                    Icons.Default.History,
                                                    journalEntries.size,
                                                )
                                            )
                                        } else {
                                            emptyList()
                                        },
                                selectedTab = visibleSelectedTab,
                                onTabSelected = { selectedTab = it },
                            )
                            if (visibleSelectedTab == 0) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                ) {
                                    if (viewModel.isRack) {
                                        item {
                                            RackElevationOverview(
                                                front = frontElevation,
                                                rear = rearElevation,
                                                previews = rackDevicePreviews,
                                                localImageFile = viewModel::localAttachmentFile,
                                                highlightDeviceId = highlightDeviceId,
                                                onDeviceClick = { id ->
                                                    onNavigateToReference(
                                                        "api/dcim/devices/",
                                                        id,
                                                        title ?: modelLabel,
                                                    )
                                                },
                                            )
                                        }
                                    }
                        item {
                            ImageAttachmentGallery(
                                attachments = imageAttachments,
                                localImageFile = viewModel::localAttachmentFile,
                                onImageClick = { items, index -> imageViewer = items to index },
                                onAdd = {
                                    mediaUploadInitialKind = MediaUploadKind.ImageAttachment
                                    showMediaUpload = true
                                },
                            )
                        }
                        item {
                            DocumentsSection(
                                            documents = documents,
                                            onOpenDocument = { document ->
                                                document.documentUrl?.let { url ->
                                                    viewModel.downloadAttachment(url, document.filename)
                                                }
                                                    ?: document.externalUrl?.let { url ->
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                        )
                                                    }
                                            },
                                            onAddDocument = {
                                                mediaUploadInitialKind = MediaUploadKind.Document
                                                showMediaUpload = true
                                            },
                                            localFileFor = { document ->
                                                document.documentUrl?.let {
                                                    viewModel.localAttachmentFile(it, document.filename)
                                                }
                                            },
                                        )
                                    }
                                    item {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Details",
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                        }
                                    }
                                    visibleOverviewFields.forEach { row ->
                                        fieldRow(
                                            row,
                                            onNavigateToReference = { endpointPath, id ->
                                                onNavigateToReference(
                                                    endpointPath,
                                                    id,
                                                    title ?: modelLabel,
                                                )
                                            },
                                            viewModel::showRelatedItems,
                                            onOpenUrl = { url ->
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                )
                                            },
                                            netboxBaseUrl = netboxBaseUrl,
                                            onDownloadAttachment = viewModel::downloadAttachment,
                                            localAttachmentFile = viewModel::localAttachmentFile,
                                            onImageClick = { imageViewer = listOf(it) to 0 },
                                            isDownloading = isDownloading,
                                            onCopyValue = onCopyValue,
                                            onFieldLongPress = { fieldActionLabel = it },
                                            onMatterPairingCode = { matterPairingCode = it },
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                ) {
                                    if (journalEntries.isEmpty()) {
                                        item {
                                            Text(
                                                "No journal entries found for this item.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontStyle = FontStyle.Italic,
                                                modifier = Modifier.padding(vertical = 16.dp),
                                            )
                                        }
                                    } else {
                                        items(journalEntries, key = { it.id }) { entry ->
                                            JournalEntryItem(
                                                entry,
                                                onEdit = {
                                                    journalEditorEntry = entry
                                                    showJournalEditor = true
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmation = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Delete ${title ?: "item"}?") },
            text = {
                Text(
                    "This removes the item from NetBox. The cached copy will be removed now; " +
                        "if you are offline, the deletion will be uploaded when sync resumes."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::delete,
                    enabled = !isDeleting,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !isDeleting,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
    printRequest?.let { request ->
        PrintLabelDialog(request = request, onDismiss = { printRequest = null })
    }
    if (showMediaUpload) {
        MediaUploadDialog(
            endpointPath = viewModel.route.endpointPath,
            objectId = viewModel.route.id,
            onDismiss = { showMediaUpload = false },
            onUploaded = { viewModel.refresh(showConfirmation = false) },
            initialKind = mediaUploadInitialKind,
        )
    }
    if (showJournalEditor) {
        JournalEntryEditorDialog(
            entry = journalEditorEntry,
            state = journalMutationState,
            onDismiss = {
                if (!journalMutationState.isSaving) showJournalEditor = false
            },
            onSave = { kind, comments ->
                viewModel.saveJournalEntry(journalEditorEntry, kind, comments)
            },
        )
    }
    imageViewer?.let { (items, index) ->
        ImageViewerDialog(
            items = items,
            initialIndex = index,
            onDismiss = { imageViewer = null },
        )
    }
    matterPairingCode?.let { code ->
        MatterPairingCodeDialog(code = code, onDismiss = { matterPairingCode = null })
    }
    fieldActionLabel?.let { label ->
        FieldActionDialog(
            fieldLabel = label,
            fieldValue = fields.firstOrNull { it.label == label }?.actionValue(),
            canEdit = editableFields.any { it.label == label },
            onCopy = {
                fields.firstOrNull { it.label == label }?.actionValue()?.let {
                    onCopyValue(label, it)
                }
                fieldActionLabel = null
            },
            onEdit = {
                val field = editableFields.firstOrNull { it.label == label }
                fieldActionLabel = null
                if (field != null) {
                    focusedEditFieldKey = field.key
                    focusedEditValue = field.value
                    viewModel.startFieldEditing(field.key)
                }
            },
            onHide = {
                viewModel.hideField(label)
                fieldActionLabel = null
            },
            onDismiss = { fieldActionLabel = null },
        )
    }
    focusedEditField?.let { field ->
        FocusedEditFieldDialog(
            field = field,
            value = focusedEditValue,
            referenceOptions = referenceOptions,
            choiceOptions = choiceOptions,
            onCreateLinkedItem = { linkedField ->
                linkedField.referenceEndpointPath?.let { endpoint ->
                    onCreateLinkedItem(
                        linkedField.key,
                        endpoint,
                        linkedField.label,
                        true,
                    )
                }
            },
            onValueChange = { focusedEditValue = it },
            onDismiss = {
                routeFocusHandled = true
                viewModel.cancelFieldEditing()
                focusedEditFieldKey = null
                if (isRouteFocusedEditor) onBack()
            },
            onReview = { editedValue ->
                // The route is retained for back-stack/breadcrumb state, but it must not relaunch
                // the focused editor after this review has been confirmed.
                routeFocusHandled = true
                if (editedValue == field.value) {
                    viewModel.cancelFieldEditing()
                    focusedEditFieldKey = null
                    if (isRouteFocusedEditor) onBack()
                } else {
                    pendingEdits = mapOf(field.key to (field.kind to editedValue))
                    pendingEditFieldKey = field.key
                    focusedEditFieldKey = null
                }
            },
        )
    }
    pendingEdits?.let { edits ->
        EditDiffDialog(
            fields = editableFields,
            edits = edits,
            referenceOptions = referenceOptions,
            choiceOptions = choiceOptions,
            onDismiss = {
                pendingEdits = null
                val wasFocusedEdit = pendingEditFieldKey != null
                if (wasFocusedEdit) viewModel.cancelFieldEditing()
                pendingEditFieldKey = null
                if (wasFocusedEdit && isRouteFocusedEditor) onBack()
            },
            onConfirm = {
                viewModel.save(edits)
                // Confirmation ends the focused-edit session. The route keeps its focus key for
                // breadcrumb/back-stack state, so clear the local guard explicitly as well; a
                // cache update from save must not reopen the editor through the route effect.
                routeFocusHandled = true
                focusedEditFieldKey = null
                pendingEdits = null
                pendingEditFieldKey = null
            },
        )
    }
    relatedTarget?.let { target ->
        RelatedItemsBottomSheet(
            target = target,
            objects = relatedObjects,
            previewUrls = relatedPreviewUrls,
            isRefreshing = isRelatedRefreshing,
            onObjectClick = { id ->
                viewModel.dismissRelatedItems()
                onNavigateToReference(target.endpointPath, id, title ?: modelLabel)
            },
            onDismiss = viewModel::dismissRelatedItems,
        )
    }
}

@Composable
private fun EditDiffDialog(
    fields: List<EditableField>,
    edits: Map<String, Pair<EditFieldKind, String>>,
    referenceOptions: Map<String, List<EditOption>>,
    choiceOptions: Map<String, List<EditOption>>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val fieldsByKey = fields.associateBy { it.key }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text("Review changes") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                edits.forEach { (key, valueWithKind) ->
                    val field = fieldsByKey[key]
                    val before =
                        displayEditValue(
                            field,
                            field?.value,
                            referenceOptions,
                            choiceOptions,
                        )
                    val after =
                        displayEditValue(
                            field,
                            valueWithKind.second,
                            referenceOptions,
                            choiceOptions,
                        )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                        Text(
                            field?.label ?: key,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        DiffValueRow(
                            prefix = "− Before",
                            value = before,
                            background = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        DiffValueRow(
                            prefix = "+ After",
                            value = after,
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        }
                    }
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
            TextButton(onClick = onConfirm) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Confirm changes")
            }
        },
    )
}

@Composable
private fun DiffValueRow(
    prefix: String,
    value: String,
    background: Color,
    contentColor: Color,
) {
    Surface(
        color = background,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                prefix,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(72.dp),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RackElevationOverview(
    front: List<RackElevationEntity>,
    rear: List<RackElevationEntity>,
    previews: Map<Int, RackDevicePreview>,
    localImageFile: (String, String) -> java.io.File?,
    highlightDeviceId: Int? = null,
    onDeviceClick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Rack elevation", style = MaterialTheme.typography.titleLarge)
        }
        if (front.isEmpty() && rear.isEmpty()) {
            Text(
                "No elevation data cached yet - refresh while online",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RackFaceOverview(
                RackFace.FRONT,
                front,
                previews,
                localImageFile,
                highlightDeviceId,
                onDeviceClick,
            )
            RackFaceOverview(
                RackFace.REAR,
                rear,
                previews,
                localImageFile,
                highlightDeviceId,
                onDeviceClick,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RackFaceOverview(
    face: RackFace,
    slots: List<RackElevationEntity>,
    previews: Map<Int, RackDevicePreview>,
    localImageFile: (String, String) -> java.io.File?,
    highlightDeviceId: Int?,
    onDeviceClick: (Int) -> Unit,
) {
    Column {
        Text(face.label, style = MaterialTheme.typography.titleMedium)
        if (slots.isEmpty()) {
            Text(
                "No ${face.label.lowercase()} elevation cached",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp, horizontal = 6.dp)) {
                mergeRackSlots(slots).forEach { block ->
                    val firstSlot = block.slots.first()
                    val lastSlot = block.slots.last()
                    val deviceId = block.deviceId
                    val highlighted = deviceId != null && deviceId == highlightDeviceId
                    val preview = deviceId?.let(previews::get)
                    val imageUrl =
                        if (face == RackFace.FRONT) preview?.frontUrl ?: preview?.rearUrl
                        else preview?.rearUrl ?: preview?.frontUrl
                    val imageFilename =
                        preview?.deviceTypeId?.let { typeId ->
                            "device-type-$typeId-${face.apiValue}"
                        }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(28.dp * block.slots.size).fillMaxWidth(),
                    ) {
                        Text(
                            if (firstSlot.slotName == lastSlot.slotName) firstSlot.slotName
                            else "${firstSlot.slotName}–${lastSlot.slotName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(48.dp),
                        )
                        Surface(
                            color =
                                if (deviceId != null) rackDeviceColor(deviceId)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier =
                                Modifier.weight(1f).fillMaxHeight().clickable(
                                    enabled = deviceId != null
                                ) {
                                    deviceId?.let(onDeviceClick)
                                }.then(
                                    if (highlighted) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp),
                                        )
                                    } else Modifier
                                ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            ) {
                                if (deviceId != null) {
                                    RemoteThumbnail(
                                        imageUrl = imageUrl,
                                        contentDescription = firstSlot.deviceDisplay,
                                        localFile =
                                            imageUrl?.let { url ->
                                                imageFilename?.let { filename ->
                                                    localImageFile(url, filename)
                                                }
                                            },
                                        modifier = Modifier.size(44.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        firstSlot.deviceDisplay ?: "Device #$deviceId",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF263238),
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RackElevationBlock(
    val deviceId: Int?,
    val slots: List<RackElevationEntity>,
)

private fun mergeRackSlots(slots: List<RackElevationEntity>): List<RackElevationBlock> {
    val blocks = mutableListOf<RackElevationBlock>()
    slots.forEach { slot ->
        val current = blocks.lastOrNull()
        if (current != null && current.deviceId == slot.deviceId) {
            blocks[blocks.lastIndex] = current.copy(slots = current.slots + slot)
        } else {
            blocks += RackElevationBlock(slot.deviceId, listOf(slot))
        }
    }
    return blocks
}

private fun rackDeviceColor(deviceId: Int): Color {
    val palette =
        listOf(
            Color(0xFFDDEBFF),
            Color(0xFFE3F4E7),
            Color(0xFFFFE5D0),
            Color(0xFFEDE0FF),
            Color(0xFFFFF0B3),
            Color(0xFFD9F4F0),
            Color(0xFFFFDDE4),
            Color(0xFFE4E8F0),
        )
    return palette[Math.floorMod(deviceId, palette.size)]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelatedItemsBottomSheet(
    target: CountTarget,
    objects: List<NetBoxObjectEntity>,
    previewUrls: Map<Int, String>,
    isRefreshing: Boolean,
    onObjectClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                target.listLabel,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when {
                isRefreshing && objects.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                objects.isEmpty() ->
                    Text(
                        "No related items cached yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(24.dp),
                    )
                else ->
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        items(objects, key = { it.id }) { objectEntity ->
                            ListItem(
                                leadingContent = {
                                    RemoteThumbnail(
                                        imageUrl = previewUrls[objectEntity.id],
                                        contentDescription = objectEntity.display,
                                        modifier = Modifier.size(56.dp),
                                    )
                                },
                                headlineContent = { Text(objectEntity.display) },
                                supportingContent =
                                    objectEntity.secondaryLine?.let { line -> { Text(line) } },
                                modifier = Modifier.clickable { onObjectClick(objectEntity.id) },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun EditForm(
    fields: List<EditableField>,
    values: Map<String, String>,
    referenceOptions: Map<String, List<EditOption>>,
    choiceOptions: Map<String, List<EditOption>>,
    onValueChange: (key: String, value: String) -> Unit,
    errorMessage: String?,
    onCreateLinkedItem: (EditableField) -> Unit,
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
            val changed = value != field.value
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (changed) {
                                Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(6.dp)
                            } else {
                                Modifier
                            }
                        )
            ) {
                EditFieldControl(
                    field = field,
                    value = value,
                    referenceOptions = referenceOptions,
                    choiceOptions = choiceOptions,
                    onCreateLinkedItem = {
                        if (field.referenceEndpointPath != null) onCreateLinkedItem(field)
                    },
                    onValueChange = { onValueChange(field.key, it) },
                )
            }
        }
    }
}

@Composable
private fun EditFieldControl(
    field: EditableField,
    value: String,
    referenceOptions: Map<String, List<EditOption>>,
    choiceOptions: Map<String, List<EditOption>>,
    onCreateLinkedItem: () -> Unit,
    onValueChange: (String) -> Unit,
) {
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
                    onCheckedChange = { onValueChange(it.toString()) },
                )
            }
        EditFieldKind.NUMBER,
        EditFieldKind.INTEGER ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        EditFieldKind.LONG_TEXT ->
            if (field.markdown) {
                MarkdownEditor(
                    value = value,
                    label = field.label,
                    onValueChange = onValueChange,
                )
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(field.label) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        EditFieldKind.JSON ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                minLines = 3,
                maxLines = 8,
                supportingText = { Text("Enter a valid JSON value") },
                modifier = Modifier.fillMaxWidth(),
            )
        EditFieldKind.STRING ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                modifier = Modifier.fillMaxWidth(),
            )
        EditFieldKind.REFERENCE ->
            EditPickerField(
                field = field,
                value = value,
                options = referenceOptions[field.key].orEmpty(),
                onValueChange = { _, next -> onValueChange(next) },
                allowClear = true,
                onCreateLinkedItem = onCreateLinkedItem,
            )
        EditFieldKind.CHOICE ->
            EditPickerField(
                field = field,
                value = value,
                options = choiceOptions[field.key].orEmpty(),
                onValueChange = { _, next -> onValueChange(next) },
                allowClear = field.customFieldName != null,
            )
        EditFieldKind.MULTI_REFERENCE,
        EditFieldKind.MULTI_CHOICE ->
            if (
                (if (field.kind == EditFieldKind.MULTI_REFERENCE) {
                    referenceOptions[field.key].orEmpty()
                } else {
                    choiceOptions[field.key].orEmpty()
                })
                    .isEmpty()
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(field.label) },
                    minLines = 2,
                    supportingText = { Text("Enter comma-separated values or a JSON array") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                EditMultiPickerField(
                    field = field,
                    value = value,
                    options =
                        if (field.kind == EditFieldKind.MULTI_REFERENCE) {
                            referenceOptions[field.key].orEmpty()
                        } else {
                            choiceOptions[field.key].orEmpty()
                        },
                    onValueChange = { _, next -> onValueChange(next) },
                    onCreateLinkedItem =
                        if (field.kind == EditFieldKind.MULTI_REFERENCE) onCreateLinkedItem
                        else null,
                )
            }
    }
}

@Composable
private fun FocusedEditFieldDialog(
    field: EditableField,
    value: String,
    referenceOptions: Map<String, List<EditOption>>,
    choiceOptions: Map<String, List<EditOption>>,
    onCreateLinkedItem: (EditableField) -> Unit,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onReview: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text("Edit ${field.label}") },
        text = {
            EditFieldControl(
                field = field,
                value = value,
                referenceOptions = referenceOptions,
                choiceOptions = choiceOptions,
                onCreateLinkedItem = {
                    if (field.referenceEndpointPath != null) onCreateLinkedItem(field)
                },
                onValueChange = onValueChange,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = { onReview(value) }) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Review")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMultiPickerField(
    field: EditableField,
    value: String,
    options: List<EditOption>,
    onValueChange: (key: String, value: String) -> Unit,
    onCreateLinkedItem: (() -> Unit)? = null,
) {
    var expanded by remember(field.key) { mutableStateOf(false) }
    var query by remember(field.key) { mutableStateOf("") }
    val selected = selectedValuesFromJson(value).toSet()
    val filteredOptions = filterEditOptions(options, query)
    val selectedLabel =
        options
            .filter { it.value in selected }
            .joinToString(", ") { it.label }
            .takeIf { it.isNotBlank() }
            ?: field.currentDisplay?.takeIf { it.isNotBlank() }
            ?: "None"
    Column {
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(field.label) },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier =
                    Modifier.matchParentSize().clickable(
                        onClickLabel = "Choose ${field.label}",
                        role = Role.Button,
                    ) {
                        query = ""
                        expanded = true
                    }
            )
        }
        if (expanded) {
            ModalBottomSheet(
                onDismissRequest = {
                    expanded = false
                    query = ""
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(field.label, style = MaterialTheme.typography.headlineSmall)
                    EditOptionSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        label = "Search ${field.label}",
                    )
                    onCreateLinkedItem?.let { createLinkedItem ->
                        ListItem(
                            headlineContent = { Text("Create new ${field.label}") },
                            leadingContent = {
                                Icon(Icons.Default.Add, contentDescription = null)
                            },
                            modifier =
                                Modifier.clickable {
                                    expanded = false
                                    query = ""
                                    createLinkedItem()
                                },
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Clear all") },
                        leadingContent = { Icon(Icons.Default.Clear, contentDescription = null) },
                        modifier =
                            Modifier.clickable {
                                onValueChange(field.key, selectedValuesToJson(emptyList()))
                            },
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        items(filteredOptions, key = { it.value }) { option ->
                            val matchHint =
                                choiceSearchHint(
                                    label = option.label,
                                    value = option.value,
                                    searchFields = option.searchFields,
                                    query = query,
                                )
                            ListItem(
                                headlineContent = { Text(option.label) },
                                supportingContent =
                                    matchHint?.let { hint ->
                                        { Text("Matched $hint", color = MaterialTheme.colorScheme.primary) }
                                    },
                                leadingContent = { EditOptionPreview(option) },
                                trailingContent = {
                                    Checkbox(
                                        checked = option.value in selected,
                                        onCheckedChange = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        val next =
                                            if (option.value in selected) selected - option.value
                                            else selected + option.value
                                        onValueChange(field.key, selectedValuesToJson(next))
                                    },
                            )
                        }
                        if (filteredOptions.isEmpty()) {
                            item {
                                Text(
                                    if (options.isEmpty()) "No choices available" else "No matches",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPickerField(
    field: EditableField,
    value: String,
    options: List<EditOption>,
    onValueChange: (key: String, value: String) -> Unit,
    allowClear: Boolean,
    onCreateLinkedItem: (() -> Unit)? = null,
) {
    var expanded by remember(field.key) { mutableStateOf(false) }
    var query by remember(field.key) { mutableStateOf("") }
    val selectedLabel =
        options.firstOrNull { it.value == value }?.label
            ?: field.currentDisplay
            ?: value.takeIf { it.isNotBlank() }
            ?: "None"
    Column {
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(field.label) },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier =
                    Modifier.matchParentSize().clickable(
                        onClickLabel = "Choose ${field.label}",
                        role = Role.Button,
                    ) {
                        query = ""
                        expanded = true
                    }
            )
        }
        if (expanded) {
            val filteredOptions = filterEditOptions(options, query)
            ModalBottomSheet(
                onDismissRequest = {
                    expanded = false
                    query = ""
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(field.label, style = MaterialTheme.typography.headlineSmall)
                    EditOptionSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        label = "Search ${field.label}",
                    )
                    onCreateLinkedItem?.let { createLinkedItem ->
                        ListItem(
                            headlineContent = { Text("Create new ${field.label}") },
                            leadingContent = {
                                Icon(Icons.Default.Add, contentDescription = null)
                            },
                            modifier =
                                Modifier.clickable {
                                    expanded = false
                                    query = ""
                                    createLinkedItem()
                                },
                        )
                    }
                    if (allowClear) {
                        ListItem(
                            headlineContent = { Text("None") },
                            leadingContent = {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            },
                            modifier =
                                Modifier.clickable {
                                    onValueChange(field.key, "")
                                    expanded = false
                                },
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        items(filteredOptions, key = { it.value }) { option ->
                            val matchHint =
                                choiceSearchHint(
                                    label = option.label,
                                    value = option.value,
                                    searchFields = option.searchFields,
                                    query = query,
                                )
                            ListItem(
                                headlineContent = { Text(option.label) },
                                supportingContent =
                                    matchHint?.let { hint ->
                                        { Text("Matched $hint", color = MaterialTheme.colorScheme.primary) }
                                    },
                                leadingContent = { EditOptionPreview(option) },
                                modifier =
                                    Modifier.clickable {
                                        onValueChange(field.key, option.value)
                                        expanded = false
                                    },
                            )
                        }
                        if (filteredOptions.isEmpty()) {
                            item {
                                Text(
                                    if (options.isEmpty()) "No choices available" else "No matches",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun visibleFieldRows(
    rows: List<FieldRow>,
    endpointPath: String,
    hiddenFieldKeys: Set<String>,
    showHiddenFields: Boolean,
): List<FieldRow> {
    if (showHiddenFields) return rows
    val filtered = rows.filterNot { row ->
        row !is FieldRow.Section &&
            row !is FieldRow.CustomGroup &&
            hiddenFieldPreferenceKey(endpointPath, row.label) in hiddenFieldKeys
    }
    return buildList {
        val pendingHeaders = mutableListOf<FieldRow>()
        filtered.forEach { row ->
            if (row is FieldRow.Section || row is FieldRow.CustomGroup) {
                pendingHeaders += row
            } else {
                addAll(pendingHeaders)
                pendingHeaders.clear()
                add(row)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.detailCard(
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .then(
                        onLongPress?.let {
                            Modifier.combinedClickable(onClick = {}, onLongClick = it)
                        } ?: Modifier
                    )
            ) {
                content()
            }
        }
    }
}

private fun endpointModelLabel(endpointPath: String): String =
    endpointPath
        .trimEnd('/')
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() }
        ?.let(Humanize::label) ?: "Details"

internal fun LazyListScope.fieldRow(
    row: FieldRow,
    onNavigateToReference: (String, Int) -> Unit,
    onRelatedItems: (CountTarget) -> Unit,
    onOpenUrl: (String) -> Unit,
    netboxBaseUrl: String?,
    onDownloadAttachment: (url: String, filename: String) -> Unit,
    localAttachmentFile: (url: String, filename: String) -> java.io.File?,
    onImageClick: (ImageViewerItem) -> Unit,
    isDownloading: Boolean,
    onCopyValue: (label: String, value: String) -> Unit,
    onFieldLongPress: (label: String) -> Unit,
    onMatterPairingCode: (String) -> Unit,
) {
    when (row) {
        is FieldRow.Section ->
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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
        is FieldRow.Metadata ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                row.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatNetBoxDateTime(row.value),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        is FieldRow.PlainText ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            row.value,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (row.copyable) {
                            DetailTrailingActions(
                                copyLabel = row.label,
                                onCopy = { onCopyValue(row.label, row.value) },
                            )
                        }
                        if (row.matterPairingCode) {
                            IconButton(
                                onClick = { onMatterPairingCode(row.value) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Show Matter pairing QR code",
                                )
                            }
                        }
                    }
                }
            }
        is FieldRow.BooleanValue ->
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (row.value) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onFieldLongPress(row.label) },
                            ),
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (row.value) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = if (row.value) "Enabled" else "Disabled",
                            tint =
                                if (row.value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            FieldLabel(row.label) { onFieldLongPress(row.label) }
                            Text(
                                if (row.value) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (row.value) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        is FieldRow.Count ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onRelatedItems(row.target) },
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Badge { Text(row.value) }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Show ${row.label.lowercase()}",
                    )
                }
            }
        is FieldRow.Markdown ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    CollapsibleCommentCard(content = row.content, modifier = Modifier.fillMaxWidth())
                }
            }
        is FieldRow.Reference ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            row.target.display,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier.weight(1f).clickable {
                                    onNavigateToReference(row.target.endpointPath, row.target.id)
                                },
                        )
                        DetailTrailingActions(
                            copyLabel = row.label.takeIf { row.copyable },
                            onCopy =
                                { onCopyValue(row.label, row.target.display) }.takeIf {
                                    row.copyable
                                },
                            openLabel = row.label,
                            onOpen = {
                                onNavigateToReference(row.target.endpointPath, row.target.id)
                            },
                        )
                    }
                }
            }
        is FieldRow.Image ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
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
                                        localFile =
                                            localAttachmentFile(
                                                row.url,
                                                row.url.attachmentFilename(),
                                            ),
                                    )
                                )
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        is FieldRow.ReferenceList ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
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
        is FieldRow.TagList ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        FieldLabel(row.label) { onFieldLongPress(row.label) }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        row.targets.forEach { target ->
                            AssistChip(
                                onClick = {
                                    onNavigateToReference(target.endpointPath, target.id)
                                },
                                label = { Text(target.display) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        is FieldRow.ChipList ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Text(row.values.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                }
            }
        is FieldRow.ExternalLink ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenUrl(row.url) },
                    ) {
                        Text(
                            shortenDisplayedUrl(row.url, netboxBaseUrl),
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
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
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
private fun JournalEntryItem(entry: JournalEntryUi, onEdit: () -> Unit) {
    val kindPresentation = journalKindPresentation(entry.kind)
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                kindPresentation.option.icon,
                contentDescription = entry.kindLabel,
                tint = kindPresentation.foreground,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${entry.kindLabel} · ${formatNetBoxDateTime(entry.created)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit journal entry")
            }
        }
        Spacer(Modifier.height(4.dp))
        CommentCard(content = entry.comments, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldLabel(text: String, onLongPress: (() -> Unit)? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            onLongPress?.let {
                Modifier.combinedClickable(onClick = {}, onLongClick = it)
            } ?: Modifier,
    )
}
