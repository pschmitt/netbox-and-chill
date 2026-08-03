package dev.pschmitt.netboxandchill.ui.generic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Label
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
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentEntity
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.db.RackElevationEntity
import dev.pschmitt.netboxandchill.data.repository.RackFace
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldObjectKey
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldPreferenceKey
import dev.pschmitt.netboxandchill.data.repository.choiceSearchHint
import dev.pschmitt.netboxandchill.data.schema.Humanize
import dev.pschmitt.netboxandchill.ui.common.CollapsibleCommentCard
import dev.pschmitt.netboxandchill.ui.common.CommentCard
import dev.pschmitt.netboxandchill.ui.common.DetailTrailingActions
import dev.pschmitt.netboxandchill.ui.common.DocumentsSection
import dev.pschmitt.netboxandchill.ui.common.FieldActionDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerItem
import dev.pschmitt.netboxandchill.ui.common.ImageAttachmentGallery
import dev.pschmitt.netboxandchill.ui.common.displayName
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
import dev.pschmitt.netboxandchill.ui.directory.AppIcons

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
    var imageAttachmentAction by remember { mutableStateOf<ImageAttachmentEntity?>(null) }
    var imageAttachmentToEdit by remember { mutableStateOf<ImageAttachmentEntity?>(null) }
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
    val deviceTypePhotoRows =
        if (viewModel.isDeviceType) visibleOverviewFields.filterIsInstance<FieldRow.Image>()
        else emptyList()
    val detailOverviewFields =
        visibleOverviewFields.filterNot { viewModel.isDeviceType && it is FieldRow.Image }
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
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = detailAccent,
                        actionIconContentColor = detailAccent,
                    ),
                title = {
                    Column {
                        Text(title ?: modelLabel, maxLines = 1)
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
                                                Intent(Intent.ACTION_VIEW, url.toUri())
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
                        val tabCount =
                            1 +
                                (if (viewModel.isRack) 1 else 0) +
                                (if (hasJournal) 1 else 0)
                        val visibleSelectedTab = selectedTab.coerceIn(0, tabCount - 1)
                        LaunchedEffect(viewModel.isRack, hasJournal) {
                            selectedTab = visibleSelectedTab
                        }
                        val tabs =
                            buildList {
                                add(ItemDetailTab("Overview", Icons.Default.Info))
                                if (viewModel.isRack) {
                                    add(ItemDetailTab("Elevation", Icons.Default.Storage))
                                }
                                if (hasJournal) {
                                    add(
                                        ItemDetailTab(
                                            "Journal",
                                            Icons.Default.History,
                                            journalEntries.size,
                                        )
                                    )
                                }
                            }
                        Column(Modifier.fillMaxSize()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                            ) {
                                ItemDetailTabs(
                                    tabs = tabs,
                                    selectedTab = visibleSelectedTab,
                                    onTabSelected = { selectedTab = it },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                            if (visibleSelectedTab == 0) {
                            LazyColumn(
                                modifier =
                                    Modifier.fillMaxWidth().weight(1f).itemTabSwipe(
                                        visibleSelectedTab,
                                        tabCount,
                                    ) { selectedTab = it },
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                item {
                                        GenericDetailIdentityCard(
                                            id = viewModel.route.id,
                                            endpointPath = viewModel.route.endpointPath,
                                            statusField = statusField,
                                            detailAccent = detailAccent,
                                        onStatusLongPress = { fieldActionLabel = statusField?.label },
                                    )
                                }
                                item { Spacer(Modifier.height(8.dp)) }
                                deviceTypePhotos(
                                    rows = deviceTypePhotoRows,
                                    title = title,
                                    localImageFile = viewModel::localAttachmentFile,
                                    onImageClick = { items, index -> imageViewer = items to index },
                                    onLongPress = { fieldActionLabel = it },
                                )
                                item {
                                    ImageAttachmentGallery(
                                        attachments = imageAttachments,
                                        localImageFile = viewModel::localAttachmentFile,
                                        onImageClick = { items, index ->
                                            imageViewer = items to index
                                        },
                                        onAdd = {
                                            mediaUploadInitialKind = MediaUploadKind.ImageAttachment
                                            showMediaUpload = true
                                        },
                                        onAttachmentLongPress = { imageAttachmentAction = it },
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
                                                        Intent(Intent.ACTION_VIEW, url.toUri())
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
                                        Text("Details", style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                                detailOverviewFields.forEach { row ->
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
                                                Intent(Intent.ACTION_VIEW, url.toUri())
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
                        } else if (viewModel.isRack && visibleSelectedTab == 1) {
                            LazyColumn(
                                modifier =
                                    Modifier.fillMaxWidth().weight(1f).itemTabSwipe(
                                        visibleSelectedTab,
                                        tabCount,
                                    ) { selectedTab = it },
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                item {
                                    GenericDetailIdentityCard(
                                        id = viewModel.route.id,
                                        endpointPath = viewModel.route.endpointPath,
                                        statusField = statusField,
                                        detailAccent = detailAccent,
                                        onStatusLongPress = { fieldActionLabel = statusField?.label },
                                    )
                                }
                                item { Spacer(Modifier.height(8.dp)) }
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
                        } else {
                            LazyColumn(
                                modifier =
                                    Modifier.fillMaxWidth().weight(1f).itemTabSwipe(
                                        visibleSelectedTab,
                                        tabCount,
                                    ) { selectedTab = it },
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                item {
                                    GenericDetailIdentityCard(
                                        id = viewModel.route.id,
                                        endpointPath = viewModel.route.endpointPath,
                                        statusField = statusField,
                                        detailAccent = detailAccent,
                                        onStatusLongPress = { fieldActionLabel = statusField?.label },
                                    )
                                }
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
            onDismiss = {
                showMediaUpload = false
                imageAttachmentToEdit = null
                mediaUploadInitialKind = null
            },
            onUploaded = {
                showMediaUpload = false
                imageAttachmentToEdit = null
                mediaUploadInitialKind = null
                viewModel.refresh(showConfirmation = false)
            },
            initialKind = mediaUploadInitialKind,
            imageAttachmentId = imageAttachmentToEdit?.id,
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
            canEdit =
                deviceTypePhotoUploadKind(label) != null ||
                    editableFields.any { it.label == label },
            onCopy = {
                fields.firstOrNull { it.label == label }?.actionValue()?.let {
                    onCopyValue(label, it)
                }
                fieldActionLabel = null
            },
            onEdit = {
                val photoKind = deviceTypePhotoUploadKind(label)
                val field = editableFields.firstOrNull { it.label == label }
                fieldActionLabel = null
                if (photoKind != null) {
                    mediaUploadInitialKind = photoKind
                    showMediaUpload = true
                } else if (field != null) {
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
    imageAttachmentAction?.let { attachment ->
        FieldActionDialog(
            fieldLabel = attachment.displayName(),
            fieldValue = attachment.imageUrl,
            canEdit = true,
            editLabel = "Edit image",
            showHide = false,
            onCopy = {
                attachment.imageUrl?.let { onCopyValue(attachment.displayName(), it) }
                imageAttachmentAction = null
            },
            onEdit = {
                imageAttachmentAction = null
                imageAttachmentToEdit = attachment
                mediaUploadInitialKind = MediaUploadKind.ImageAttachment
                showMediaUpload = true
            },
            onHide = { imageAttachmentAction = null },
            onDismiss = { imageAttachmentAction = null },
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

internal fun String.attachmentFilename(): String =
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
internal fun FieldLabel(text: String, onLongPress: (() -> Unit)? = null) {
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
