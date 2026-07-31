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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dev.pschmitt.netboxandchill.ui.common.CommentCard
import dev.pschmitt.netboxandchill.ui.common.FieldActionDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerDialog
import dev.pschmitt.netboxandchill.ui.common.ImageViewerItem
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.StatusChip
import dev.pschmitt.netboxandchill.ui.common.PrintLabelDialog
import dev.pschmitt.netboxandchill.ui.common.PrintLabelRequest
import dev.pschmitt.netboxandchill.ui.common.shareIntent
import dev.pschmitt.netboxandchill.data.repository.hiddenFieldPreferenceKey
import java.text.DateFormat
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceDetailScreen(
    deviceId: Int,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onDeviceTypeClick: (Int) -> Unit,
    onReferenceClick: (endpointPath: String, id: Int) -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val webUrl by viewModel.webUrl.collectAsStateWithLifecycle()
    val deviceType by viewModel.deviceType.collectAsStateWithLifecycle()
    val imageAttachments by viewModel.imageAttachments.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val refreshedMessage by viewModel.refreshedMessage.collectAsStateWithLifecycle()
    val hiddenFieldKeys by viewModel.hiddenFieldKeys.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Full-screen image viewer state (NBC-20) - which item list + which index within it is open,
    // shared by both the device-type front/rear photos and the image-attachment row below.
    var imageViewer by remember { mutableStateOf<Pair<List<ImageViewerItem>, Int>?>(null) }
    var copiedMessage by remember { mutableStateOf<String?>(null) }
    var printRequest by remember { mutableStateOf<PrintLabelRequest?>(null) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var showHiddenFields by remember { mutableStateOf(false) }
    var fieldActionLabel by remember { mutableStateOf<String?>(null) }
    val hiddenFieldsForDevice = hiddenFieldKeys.filter { it.startsWith("device/") }
    val isFieldVisible: (String) -> Boolean = { label ->
        showHiddenFields || hiddenFieldPreferenceKey("api/dcim/devices/", label) !in hiddenFieldKeys
    }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Device") },
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
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                enabled = !isRefreshing,
                                onClick = {
                                    viewModel.refresh(showConfirmation = true)
                                    actionMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                enabled = device != null,
                                onClick = {
                                    onEditClick()
                                    actionMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Print label") },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                enabled = device != null && webUrl != null,
                                onClick = {
                                    val current = device
                                    val url = webUrl
                                    if (current != null && url != null) {
                                        printRequest =
                                            PrintLabelRequest(
                                                objectUrl = url,
                                                labelText = current.assetTag?.takeIf { it.isNotBlank() } ?: current.name,
                                            )
                                    }
                                    actionMenuExpanded = false
                                },
                            )
                            webUrl?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        actionMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        context.startActivity(shareIntent(url))
                                        actionMenuExpanded = false
                                    },
                                )
                            }
                            if (hiddenFieldsForDevice.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Show hidden fields") },
                                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
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
            isRefreshing = isRefreshing,
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
                    Text(current.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    if (isFieldVisible("status")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(label = current.statusLabel, value = current.statusValue)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                deviceTypePhotos(deviceType, viewModel::localImageFile) { items, index -> imageViewer = items to index }
                imageAttachmentRow(imageAttachments, viewModel::localImageFile) { items, index -> imageViewer = items to index }
                if (isFieldVisible("site")) detailField("Site", current.siteName, onClick = current.siteId?.let { id -> { onReferenceClick("api/dcim/sites/", id) } }, onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("rack")) detailField("Rack", current.rackName, onClick = current.rackId?.let { id -> { onReferenceClick("api/dcim/racks/", id) } }, onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("position")) detailField("Position", current.position?.toString(), onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("role")) detailField("Role", current.roleName, onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("manufacturer")) detailField("Manufacturer", current.manufacturerName, onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("model")) detailField("Model", current.deviceTypeModel, onClick = deviceType?.id?.let { id -> { onDeviceTypeClick(id) } }, onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("serial")) detailField("Serial", current.serial, copyable = true, onCopyValue = onCopyValue, onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("asset_tag")) detailField("Asset tag", current.assetTag, copyable = true, onCopyValue = onCopyValue, onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("primary_ip")) detailField("Primary IP", current.primaryIp, copyable = true, onCopyValue = onCopyValue, onFieldLongPress = { fieldActionLabel = it })
                if (isFieldVisible("comments")) detailMarkdownField("Comments", current.comments, onFieldLongPress = { fieldActionLabel = it })
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Last synced ${DateFormat.getDateTimeInstance().format(Date(current.syncedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
            }
        }
    }

    imageViewer?.let { (items, index) ->
        ImageViewerDialog(items = items, initialIndex = index, onDismiss = { imageViewer = null })
    }
    printRequest?.let { request ->
        PrintLabelDialog(request = request, onDismiss = { printRequest = null })
    }
    fieldActionLabel?.let { label ->
        FieldActionDialog(
            fieldLabel = label,
            canEdit = true,
            onEdit = {
                fieldActionLabel = null
                onEditClick()
            },
            onHide = {
                viewModel.hideField(label)
                fieldActionLabel = null
            },
            onDismiss = { fieldActionLabel = null },
        )
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
            front.takeUnless { it.isNullOrBlank() }?.let {
                ImageViewerItem(
                    url = it,
                    title = "Front of $model",
                    localFile = localImageFile(it, "device-type-${deviceType.id}-front"),
                )
            },
            rear.takeUnless { it.isNullOrBlank() }?.let {
                ImageViewerItem(
                    url = it,
                    title = "Rear of $model",
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
                    modifier = Modifier.weight(1f).height(140.dp).clickable { onImageClick(items, 0) },
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
                itemsIndexed(attachments, key = { _, attachment -> attachment.id }) { index, attachment ->
                    RemoteThumbnail(
                        imageUrl = attachment.imageUrl,
                        contentDescription = attachment.name,
                        localFile =
                            attachment.imageUrl?.let {
                                localImageFile(it, attachment.fileName())
                            },
                        modifier = Modifier.size(100.dp).padding(end = 8.dp).clickable { onImageClick(items, index) },
                    )
                }
            }
        }
    }
}

/** Maps the real `extras.ImageAttachment` fields NetBox actually returns (confirmed live, see
 * NBC-20) into the viewer's generic metadata rows - no `size`/`content_type` field exists on this
 * serializer to show, unlike the TODO's original wishlist. */
private fun ImageAttachmentEntity.toViewerItem(localImageFile: (String, String) -> File?): ImageViewerItem {
    val title = name?.takeIf { it.isNotBlank() } ?: display?.takeIf { it.isNotBlank() } ?: "Image attachment #$id"
    val metadata = buildList {
        if (!description.isNullOrBlank()) add("Description" to description)
        if (imageWidth != null && imageHeight != null) add("Dimensions" to "$imageWidth × $imageHeight")
        created?.takeIf { it.isNotBlank() }?.let { add("Created" to formatIsoTimestamp(it)) }
        lastUpdated?.takeIf { it.isNotBlank() && it != created }?.let { add("Last updated" to formatIsoTimestamp(it)) }
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

/** "2026-07-25T16:33:05.946712Z" -> "2026-07-25 16:33" - same good-enough, no-timezone-conversion
 * format used elsewhere in the app (see `DashboardScreen.formatTimestamp`); not shared as a common
 * util to keep this change scoped to NBC-20. */
private fun formatIsoTimestamp(iso: String): String = iso.take(16).replace('T', ' ')

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
        Column(
            Modifier
                .padding(vertical = 6.dp)
                .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onFieldLongPress(label) }),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (copyable) {
                    IconButton(onClick = { onCopyValue(label, value) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label")
                    }
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open $label")
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
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onFieldLongPress(label) }),
            )
            CommentCard(content = value, modifier = Modifier.fillMaxWidth())
        }
    }
}
