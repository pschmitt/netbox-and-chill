package dev.pschmitt.netboxandchill.ui.devicedetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentEntity
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldPreferenceKey
import dev.pschmitt.netboxandchill.ui.common.CommentCard
import dev.pschmitt.netboxandchill.ui.common.DetailTrailingActions
import dev.pschmitt.netboxandchill.ui.common.FieldActionDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerItem
import dev.pschmitt.netboxandchill.ui.common.MatterPairingCodeDialog
import dev.pschmitt.netboxandchill.ui.common.PrintLabelDialog
import dev.pschmitt.netboxandchill.ui.common.PrintLabelRequest
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.StatusChip
import dev.pschmitt.netboxandchill.ui.common.detailAccentFor
import dev.pschmitt.netboxandchill.ui.common.fileViewIntent
import dev.pschmitt.netboxandchill.ui.common.formatNetBoxDateTime
import dev.pschmitt.netboxandchill.ui.common.shareIntent
import dev.pschmitt.netboxandchill.ui.generic.FieldRow
import dev.pschmitt.netboxandchill.ui.generic.JournalEntryUi
import dev.pschmitt.netboxandchill.ui.generic.actionValue
import dev.pschmitt.netboxandchill.ui.generic.fieldRow
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

private val interfaceJson = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceDetailScreen(
    deviceId: Int,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onEditFieldClick: (fieldKey: String) -> Unit,
    onDeviceTypeClick: (id: Int, breadcrumb: String) -> Unit,
    onReferenceClick: (endpointPath: String, id: Int, breadcrumb: String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val webUrl by viewModel.webUrl.collectAsStateWithLifecycle()
    val deviceType by viewModel.deviceType.collectAsStateWithLifecycle()
    val imageAttachments by viewModel.imageAttachments.collectAsStateWithLifecycle()
    val interfaceIpAddresses by viewModel.interfaceIpAddresses.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val customFieldRows by viewModel.customFieldRows.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val fileToOpen by viewModel.fileToOpen.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(-1) }
    val selectedRelatedObjects =
        if (selectedTab >= 0) {
            val endpointPath = DEVICE_RELATED_TABS[selectedTab].endpointPath
            viewModel.relatedObjects[endpointPath]?.collectAsStateWithLifecycle()?.value.orEmpty()
        } else {
            emptyList()
        }
    val relatedCounts = DEVICE_RELATED_TABS.map { tab ->
        if (tab.endpointPath == JOURNAL_TAB_ENDPOINT_PATH) {
            journalEntries.size
        } else {
            viewModel.relatedObjects[tab.endpointPath]?.collectAsStateWithLifecycle()?.value?.size
                ?: 0
        }
    }
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleteResult by viewModel.deleteResult.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val refreshedMessage by viewModel.refreshedMessage.collectAsStateWithLifecycle()
    val hiddenFieldKeys by viewModel.hiddenFieldKeys.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Full-screen image viewer state (NBC-20) - which item list + which index within it is open,
    // shared by both the device-type front/rear photos and the image-attachment row below.
    var imageViewer by remember { mutableStateOf<Pair<List<ImageViewerItem>, Int>?>(null) }
    var matterPairingCode by remember { mutableStateOf<String?>(null) }
    var copiedMessage by remember { mutableStateOf<String?>(null) }
    var printRequest by remember { mutableStateOf<PrintLabelRequest?>(null) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showHiddenFields by remember { mutableStateOf(false) }
    var fieldActionLabel by remember { mutableStateOf<String?>(null) }
    val hiddenFieldsForDevice = hiddenFieldKeys.filter { it.startsWith("device/") }
    val isFieldVisible: (String) -> Boolean = { label ->
        showHiddenFields || hiddenFieldPreferenceKey("api/dcim/devices/", label) !in hiddenFieldKeys
    }
    val visibleCustomFieldRows =
        visibleDeviceCustomFieldRows(customFieldRows, hiddenFieldKeys, showHiddenFields)
    val detailAccent = MaterialTheme.colorScheme.detailAccentFor("api/dcim/devices/")

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    LaunchedEffect(refreshedMessage) {
        refreshedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.refreshedMessageShown()
        }
    }

    LaunchedEffect(deleteResult) {
        if (deleteResult != null) {
            showDeleteConfirmation = false
            viewModel.deleteResultShown()
            onDeleted()
        }
    }

    LaunchedEffect(copiedMessage) {
        copiedMessage?.let {
            snackbarHostState.showSnackbar(it)
            copiedMessage = null
        }
    }

    LaunchedEffect(fileToOpen) {
        val file = fileToOpen ?: return@LaunchedEffect
        runCatching { context.startActivity(fileViewIntent(context, file)) }
            .onFailure { snackbarHostState.showSnackbar("No app found to open ${file.name}") }
        viewModel.fileOpened()
    }

    val onCopyValue: (String, String) -> Unit = { label, value ->
        context
            .getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText(label, value))
        copiedMessage = "Copied $label"
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
                    Text("Device", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                                text = { Text("Edit") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                enabled = device != null,
                                onClick = {
                                    onEditClick()
                                    actionMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                },
                                enabled = device != null && !isDeleting,
                                onClick = {
                                    showDeleteConfirmation = true
                                    actionMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Print label") },
                                leadingIcon = {
                                    Icon(Icons.Default.Print, contentDescription = null)
                                },
                                enabled = device != null && webUrl != null,
                                onClick = {
                                    val current = device
                                    val url = webUrl
                                    if (current != null && url != null) {
                                        printRequest =
                                            PrintLabelRequest(
                                                objectUrl = url,
                                                labelText =
                                                    current.assetTag?.takeIf { it.isNotBlank() }
                                                        ?: current.name,
                                                longLabelText =
                                                    listOfNotNull(
                                                            current.name,
                                                            current.assetTag,
                                                            current.serial,
                                                        )
                                                        .filter(String::isNotBlank)
                                                        .joinToString("\n"),
                                            )
                                    }
                                    actionMenuExpanded = false
                                },
                            )
                            webUrl?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    leadingIcon = {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
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
                            if (hiddenFieldsForDevice.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Show hidden fields") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Visibility, contentDescription = null)
                                    },
                                    onClick = {
                                        showHiddenFields = true
                                        actionMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = device
        PullToRefreshBox(
            // Sync has a global progress bar and Android notification; avoid the large circular
            // indicator over the device while that background work is running.
            isRefreshing = false,
            onRefresh = { viewModel.refresh(showConfirmation = true) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isRefreshing) "Loading…" else "Not cached yet - connect and refresh",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    color = detailAccent.copy(alpha = 0.18f),
                                    shape =
                                        androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                    modifier = Modifier.size(52.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Cable,
                                            contentDescription = null,
                                            tint = detailAccent,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                    Text(
                                        current.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                    current.deviceTypeModel?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isFieldVisible("Status")) {
                                    Box(
                                        modifier =
                                            Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClick = { fieldActionLabel = "Status" },
                                            )
                                    ) {
                                        StatusChip(
                                            label = current.statusLabel,
                                            value = current.statusValue,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    item {
                        ScrollableTabRow(
                            selectedTabIndex = selectedTab + 1,
                            edgePadding = 0.dp,
                        ) {
                            Tab(
                                selected = selectedTab == -1,
                                onClick = { selectedTab = -1 },
                                text = { Text("Overview") },
                            )
                            DEVICE_RELATED_TABS.forEachIndexed { index, tab ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = {
                                        selectedTab = index
                                    },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                tabIcon(tab),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("${tab.label} (${relatedCounts[index]})")
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (selectedTab == -1) {
                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                        deviceTypePhotos(deviceType, viewModel::localImageFile) { items, index ->
                            imageViewer = items to index
                        }
                        imageAttachmentRow(imageAttachments, viewModel::localImageFile) {
                            items,
                            index ->
                            imageViewer = items to index
                        }
                        if (isFieldVisible("site"))
                            detailField(
                                "Site",
                                current.siteName,
                                onClick =
                                    current.siteId?.let { id ->
                                        { onReferenceClick("api/dcim/sites/", id, current.name) }
                                    },
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("rack"))
                            detailField(
                                "Rack",
                                current.rackName,
                                onClick =
                                    current.rackId?.let { id ->
                                        { onReferenceClick("api/dcim/racks/", id, current.name) }
                                    },
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("position"))
                            detailField(
                                "Position",
                                current.position?.toString(),
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("role"))
                            detailField(
                                "Role",
                                current.roleName,
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("manufacturer"))
                            detailField(
                                "Manufacturer",
                                current.manufacturerName,
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("model"))
                            detailField(
                                "Model",
                                current.deviceTypeModel,
                                onClick =
                                    deviceType?.id?.let { id ->
                                        { onDeviceTypeClick(id, current.name) }
                                    },
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("serial"))
                            detailField(
                                "Serial",
                                current.serial,
                                copyable = true,
                                onCopyValue = onCopyValue,
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("asset_tag"))
                            detailField(
                                "Asset tag",
                                current.assetTag,
                                copyable = true,
                                onCopyValue = onCopyValue,
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("primary_ip"))
                            detailField(
                                "Primary IP",
                                current.primaryIp,
                                copyable = true,
                                onCopyValue = onCopyValue,
                                onClick =
                                    current.primaryIpId?.let { id ->
                                        {
                                            onReferenceClick(
                                                "api/ipam/ip-addresses/",
                                                id,
                                                current.name,
                                            )
                                        }
                                    },
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        if (isFieldVisible("comments"))
                            detailMarkdownField(
                                "Comments",
                                current.comments,
                                onFieldLongPress = { fieldActionLabel = it },
                            )
                        visibleCustomFieldRows.forEach { row ->
                            fieldRow(
                                row = row,
                                onNavigateToReference = { endpointPath, id ->
                                    onReferenceClick(endpointPath, id, current.name)
                                },
                                onRelatedItems = {},
                                onOpenUrl = { url ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                },
                                onDownloadAttachment = viewModel::downloadAttachment,
                                localAttachmentFile = viewModel::localImageFile,
                                onImageClick = { item -> imageViewer = listOf(item) to 0 },
                                isDownloading = isDownloading,
                                onCopyValue = onCopyValue,
                                onFieldLongPress = { fieldActionLabel = it },
                                onMatterPairingCode = { matterPairingCode = it },
                            )
                        }
                        item {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "Last synced ${DateFormat.getDateTimeInstance().format(Date(current.syncedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val tab = DEVICE_RELATED_TABS[selectedTab]
                        item {
                            if (tab.endpointPath == JOURNAL_TAB_ENDPOINT_PATH) {
                                DeviceJournalEntries(journalEntries)
                            } else {
                                DeviceRelatedObjects(
                                    tab = tab,
                                    objects = selectedRelatedObjects,
                                    interfaceIpAddresses = interfaceIpAddresses,
                                    onObjectClick = { objectId ->
                                        onReferenceClick(tab.endpointPath, objectId, current.name)
                                    },
                                    onIpClick = { ipAddress ->
                                        onReferenceClick(
                                            "api/ipam/ip-addresses/",
                                            ipAddress.id,
                                            current.name,
                                        )
                                    },
                                    onCopyValue = onCopyValue,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    imageViewer?.let { (items, index) ->
        ImageViewerDialog(items = items, initialIndex = index, onDismiss = { imageViewer = null })
    }
    matterPairingCode?.let { code ->
        MatterPairingCodeDialog(code = code, onDismiss = { matterPairingCode = null })
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
            title = { Text("Delete ${device?.name ?: "device"}?") },
            text = {
                Text(
                    "This removes the device from NetBox. The cached copy will be removed now; " +
                        "if you are offline, the deletion will be uploaded when sync resumes."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
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
                androidx.compose.material3.TextButton(
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
    fieldActionLabel?.let { label ->
        FieldActionDialog(
            fieldLabel = label,
            fieldValue = deviceFieldActionValue(device, customFieldRows, label),
            canEdit = true,
            onCopy = {
                deviceFieldActionValue(device, customFieldRows, label)?.let {
                    onCopyValue(label, it)
                }
                fieldActionLabel = null
            },
            onEdit = {
                fieldActionLabel = null
                onEditFieldClick(deviceEditFieldKey(label))
            },
            onHide = {
                viewModel.hideField(label)
                fieldActionLabel = null
            },
            onDismiss = { fieldActionLabel = null },
        )
    }
}

private fun deviceFieldActionValue(
    device: dev.pschmitt.netboxandchill.data.db.DeviceEntity?,
    customFieldRows: List<FieldRow>,
    label: String,
): String? {
    val current = device
    if (current == null) return customFieldRows.firstOrNull { it.label == label }?.actionValue()
    return when (label) {
        "Status" -> current.statusLabel ?: current.statusValue
        "Site" -> current.siteName
        "Rack" -> current.rackName
        "Position" -> current.position?.toString()
        "Role" -> current.roleName
        "Manufacturer" -> current.manufacturerName
        "Model" -> current.deviceTypeModel
        "Serial" -> current.serial
        "Asset tag" -> current.assetTag
        "Primary IP" -> current.primaryIp
        "Comments" -> current.comments
        else -> customFieldRows.firstOrNull { it.label == label }?.actionValue()
    }
}

private fun visibleDeviceCustomFieldRows(
    rows: List<FieldRow>,
    hiddenFieldKeys: Set<String>,
    showHiddenFields: Boolean,
): List<FieldRow> {
    if (showHiddenFields) return rows
    val filtered = rows.filterNot { row ->
        row !is FieldRow.Section &&
            row !is FieldRow.CustomGroup &&
            hiddenFieldPreferenceKey("api/dcim/devices/", row.label) in hiddenFieldKeys
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

private fun deviceEditFieldKey(label: String): String =
    when (label) {
        "Model" -> "device_type"
        "Asset tag" -> "asset_tag"
        "Primary IP" -> "primary_ip"
        else -> label.replace(' ', '_').lowercase()
    }

@Composable
private fun tabIcon(tab: DeviceRelatedTab) =
    if (tab.endpointPath == JOURNAL_TAB_ENDPOINT_PATH) Icons.Default.History
    else Icons.Default.Cable

@Composable
private fun DeviceRelatedObjects(
    tab: DeviceRelatedTab,
    objects: List<dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity>,
    interfaceIpAddresses: Map<Int, List<InterfaceIpAddress>> = emptyMap(),
    onObjectClick: (Int) -> Unit,
    onIpClick: (InterfaceIpAddress) -> Unit,
    onCopyValue: (String, String) -> Unit,
) {
    if (objects.isEmpty()) {
        Text(
            "No cached ${tab.label.lowercase()} for this device. Refresh while online to load them.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }
    objects.forEach { objectEntity ->
        val ipAddresses =
            if (tab.endpointPath == INTERFACES_TAB_ENDPOINT_PATH) {
                interfaceIpAddresses[objectEntity.id].orEmpty()
            } else {
                emptyList()
            }
        val macAddresses =
            if (tab.endpointPath == INTERFACES_TAB_ENDPOINT_PATH) {
                objectEntity.interfaceMacAddresses()
            } else {
                emptyList()
            }
        ListItem(
            leadingContent = { Icon(Icons.Default.Cable, contentDescription = null) },
            headlineContent = { Text(objectEntity.display) },
            supportingContent = {
                if (ipAddresses.isNotEmpty() || macAddresses.isNotEmpty()) {
                    Column {
                        ipAddresses.forEach { ipAddress ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "IP: ${ipAddress.address}",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier =
                                        Modifier.weight(1f).clickable {
                                            onIpClick(ipAddress)
                                        },
                                )
                                DetailTrailingActions(
                                    copyLabel = "IP address",
                                    onCopy = {
                                        onCopyValue("IP address", ipAddress.address)
                                    },
                                    openLabel = "IP address",
                                    onOpen = { onIpClick(ipAddress) },
                                )
                            }
                        }
                        macAddresses.forEach { macAddress ->
                            Text("MAC: $macAddress")
                        }
                    }
                } else {
                    (if (tab.endpointPath == INTERFACES_TAB_ENDPOINT_PATH) {
                            objectEntity.interfaceSubtitle(emptyList())
                        } else {
                            objectEntity.secondaryLine
                        })
                        ?.let { Text(it) }
                }
            },
            modifier = Modifier.clickable { onObjectClick(objectEntity.id) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun DeviceJournalEntries(entries: List<JournalEntryUi>) {
    if (entries.isEmpty()) {
        Text(
            "No journal entries found for this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }
    Column {
        entries.forEach { entry ->
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
                        "${entry.kindLabel} · ${formatNetBoxDateTime(entry.created)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                CommentCard(content = entry.comments, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Device-type stock photos (front/rear) - side by side when both are present. Tapping either opens
 * the same full-screen zoomable viewer as the image attachments below (NBC-20); these carry no
 * `ImageAttachment` metadata of their own (just a URL + the device type's model name), so their
 * viewer instance only gets a title, no metadata rows - see NBC-20's TODO entry for why they get
 * the popup treatment at all despite that.
 */
private fun LazyListScope.deviceTypePhotos(
    deviceType: DeviceTypeEntity?,
    localImageFile: (String, String) -> File?,
    onImageClick: (List<ImageViewerItem>, Int) -> Unit,
) {
    val front = deviceType?.frontImageUrl
    val rear = deviceType?.rearImageUrl
    val model = deviceType?.model
    if (front.isNullOrBlank() && rear.isNullOrBlank()) return
    val items =
        listOfNotNull(
            front
                .takeUnless { it.isNullOrBlank() }
                ?.let {
                    ImageViewerItem(
                        url = it,
                        title = "Front of $model",
                        metadata = deviceTypeImageMetadata(deviceType, "Front"),
                        localFile = localImageFile(it, "device-type-${deviceType.id}-front"),
                    )
                },
            rear
                .takeUnless { it.isNullOrBlank() }
                ?.let {
                    ImageViewerItem(
                        url = it,
                        title = "Rear of $model",
                        metadata = deviceTypeImageMetadata(deviceType, "Rear"),
                        localFile = localImageFile(it, "device-type-${deviceType.id}-rear"),
                    )
                },
        )
    item {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            if (!front.isNullOrBlank()) {
                RemoteThumbnail(
                    imageUrl = front,
                    contentDescription = "Front of $model",
                    localFile = localImageFile(front, "device-type-${deviceType.id}-front"),
                    modifier =
                        Modifier.weight(1f).height(140.dp).clickable { onImageClick(items, 0) },
                    // Fit, not the default Crop - these are stock product photos with varying
                    // aspect ratios; cropping to fill a fixed square/rect chops off real content
                    // (e.g. a wide rack-mount unit's edges), unlike the row/grid thumbnails below
                    // where a uniform crop is the point.
                    contentScale = ContentScale.Fit,
                )
            }
            if (!front.isNullOrBlank() && !rear.isNullOrBlank()) Spacer(Modifier.width(8.dp))
            if (!rear.isNullOrBlank()) {
                RemoteThumbnail(
                    imageUrl = rear,
                    contentDescription = "Rear of $model",
                    localFile = localImageFile(rear, "device-type-${deviceType.id}-rear"),
                    modifier =
                        Modifier.weight(1f).height(140.dp).clickable {
                            onImageClick(items, if (front.isNullOrBlank()) 0 else 1)
                        },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private fun deviceTypeImageMetadata(
    deviceType: DeviceTypeEntity,
    view: String,
): List<Pair<String, String>> = buildList {
    deviceType.model?.takeIf { it.isNotBlank() }?.let { add("Model" to it) }
    add("View" to view)
    add("Device type" to "#${deviceType.id}")
}

/**
 * Uploaded `extras.ImageAttachment` image attachments for this device - tap one to open the
 * full-screen zoomable viewer (NBC-20), opened to the tapped index with horizontal swipe between
 * the rest of the row.
 */
private fun LazyListScope.imageAttachmentRow(
    attachments: List<ImageAttachmentEntity>,
    localImageFile: (String, String) -> File?,
    onImageClick: (List<ImageViewerItem>, Int) -> Unit,
) {
    if (attachments.isEmpty()) return
    val items = attachments.map { it.toViewerItem(localImageFile) }
    item {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                "Image attachments",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                itemsIndexed(attachments, key = { _, attachment -> attachment.id }) {
                    index,
                    attachment ->
                    RemoteThumbnail(
                        imageUrl = attachment.imageUrl,
                        contentDescription = attachment.name,
                        localFile =
                            attachment.imageUrl?.let {
                                localImageFile(it, attachment.fileName())
                            },
                        modifier =
                            Modifier.size(100.dp).padding(end = 8.dp).clickable {
                                onImageClick(items, index)
                            },
                    )
                }
            }
        }
    }
}

/**
 * Maps the real `extras.ImageAttachment` fields NetBox actually returns (confirmed live, see
 * NBC-20) into the viewer's generic metadata rows - no `size`/`content_type` field exists on this
 * serializer to show, unlike the TODO's original wishlist.
 */
private fun ImageAttachmentEntity.toViewerItem(
    localImageFile: (String, String) -> File?
): ImageViewerItem {
    val title =
        name?.takeIf { it.isNotBlank() }
            ?: display?.takeIf { it.isNotBlank() }
            ?: "Image attachment #$id"
    val metadata = buildList {
        if (!description.isNullOrBlank()) add("Description" to description)
        if (imageWidth != null && imageHeight != null)
            add("Dimensions" to "$imageWidth × $imageHeight")
        created?.takeIf { it.isNotBlank() }?.let { add("Created" to formatNetBoxDateTime(it)) }
        lastUpdated
            ?.takeIf { it.isNotBlank() && it != created }
            ?.let { add("Last updated" to formatNetBoxDateTime(it)) }
    }
    val url = imageUrl.orEmpty()
    return ImageViewerItem(
        url = url,
        title = title,
        metadata = metadata,
        localFile = imageUrl?.let { localImageFile(it, fileName()) },
    )
}

private fun ImageAttachmentEntity.fileName(): String =
    name?.takeIf { it.isNotBlank() }
        ?: display?.takeIf { it.isNotBlank() }
        ?: "image-attachment-$id"

/** Pull the useful network identity into interface list subtitles when NetBox includes it. */
private fun dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity.interfaceMacAddresses(): List<String> {
    val objectJson =
        runCatching { interfaceJson.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return emptyList()
    return buildList {
            listOf("mac_address", "primary_mac_address").forEach { key ->
                objectJson[key]?.displayValue()?.let(::add)
            }
            (objectJson["mac_addresses"] as? JsonArray).orEmpty().forEach { element ->
                element.displayValue()?.let(::add)
            }
        }
        .distinct()
}

private fun dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity.interfaceSubtitle(
    cachedIpAddresses: List<String>
): String? {
    val objectJson =
        runCatching { interfaceJson.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return secondaryLine
    val addresses =
        buildList {
                val ipList = objectJson["ip_addresses"] as? JsonArray
                ipList.orEmpty().forEach { element -> element.displayValue()?.let(::add) }
                listOf("primary_ip4", "primary_ip6", "ip_address").forEach { key ->
                    objectJson[key]?.displayValue()?.let(::add)
                }
                addAll(cachedIpAddresses)
            }
            .distinct()
    val macAddresses = interfaceMacAddresses()
    val networkParts = buildList {
        if (addresses.isNotEmpty()) add("IP: ${addresses.joinToString(", ")}")
        if (macAddresses.isNotEmpty()) add("MAC: ${macAddresses.joinToString(", ")}")
    }
    return networkParts.joinToString(" · ").takeIf { it.isNotBlank() } ?: secondaryLine
}

private fun JsonElement.displayValue(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject ->
            listOf("address", "display", "cidr", "value")
                .asSequence()
                .mapNotNull { key -> this[key]?.displayValue() }
                .firstOrNull()
        else -> null
    }

private fun LazyListScope.detailField(
    label: String,
    value: String?,
    copyable: Boolean = false,
    onCopyValue: (label: String, value: String) -> Unit = { _, _ -> },
    onClick: (() -> Unit)? = null,
    onFieldLongPress: (label: String) -> Unit = {},
) {
    if (value.isNullOrBlank()) return
    item {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    .combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = { onFieldLongPress(label) },
                    )
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    DetailTrailingActions(
                        copyLabel = label.takeIf { copyable },
                        onCopy = { onCopyValue(label, value) }.takeIf { copyable },
                        openLabel = label.takeIf { onClick != null },
                        onOpen = onClick,
                    )
                }
            }
        }
    }
}

/** NetBox's "comments" field supports Markdown - rendered, not shown as literal text. */
private fun LazyListScope.detailMarkdownField(
    label: String,
    value: String?,
    onFieldLongPress: (label: String) -> Unit = {},
) {
    if (value.isNullOrBlank()) return
    item {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    .combinedClickable(onClick = {}, onLongClick = { onFieldLongPress(label) })
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CommentCard(content = value, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
