package dev.pschmitt.netboxandchill.ui.devicedetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentEntity
import dev.pschmitt.netboxandchill.ui.common.CommentCard
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.StatusChip
import dev.pschmitt.netboxandchill.ui.common.shareIntent
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: Int,
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val webUrl by viewModel.webUrl.collectAsStateWithLifecycle()
    val deviceType by viewModel.deviceType.collectAsStateWithLifecycle()
    val imageAttachments by viewModel.imageAttachments.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
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
                title = { Text(device?.name ?: "Device #$deviceId") },
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
                        IconButton(onClick = { context.startActivity(shareIntent(url)) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = device
        if (current == null) {
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
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(label = current.statusLabel, value = current.statusValue)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                deviceTypePhotos(deviceType)
                imageAttachmentRow(imageAttachments)
                detailField("Site", current.siteName)
                detailField("Rack", current.rackName)
                detailField("Position", current.position?.toString())
                detailField("Role", current.roleName)
                detailField("Manufacturer", current.manufacturerName)
                detailField("Model", current.deviceTypeModel)
                detailField("Serial", current.serial)
                detailField("Asset tag", current.assetTag)
                detailField("Primary IP", current.primaryIp)
                detailMarkdownField("Comments", current.comments)
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

/** Device-type stock photos (front/rear) - side by side when both are present. */
private fun LazyListScope.deviceTypePhotos(deviceType: DeviceTypeEntity?) {
    val front = deviceType?.frontImageUrl
    val rear = deviceType?.rearImageUrl
    val model = deviceType?.model
    if (front.isNullOrBlank() && rear.isNullOrBlank()) return
    item {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            if (!front.isNullOrBlank()) {
                RemoteThumbnail(
                    imageUrl = front,
                    contentDescription = "Front of $model",
                    modifier = Modifier.weight(1f).height(140.dp),
                )
            }
            if (!front.isNullOrBlank() && !rear.isNullOrBlank()) Spacer(Modifier.width(8.dp))
            if (!rear.isNullOrBlank()) {
                RemoteThumbnail(
                    imageUrl = rear,
                    contentDescription = "Rear of $model",
                    modifier = Modifier.weight(1f).height(140.dp),
                )
            }
        }
    }
}

/** Uploaded `extras.ImageAttachment` photos for this device - tap to open full-size in the browser. */
private fun LazyListScope.imageAttachmentRow(attachments: List<ImageAttachmentEntity>) {
    if (attachments.isEmpty()) return
    item {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                "Photos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val context = LocalContext.current
            LazyRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                items(attachments, key = { it.id }) { attachment ->
                    RemoteThumbnail(
                        imageUrl = attachment.imageUrl,
                        contentDescription = attachment.name,
                        modifier =
                            Modifier.size(100.dp).padding(end = 8.dp).clickableIfUrl(attachment.imageUrl) { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                    )
                }
            }
        }
    }
}

private fun Modifier.clickableIfUrl(url: String?, onClick: (String) -> Unit): Modifier =
    if (url.isNullOrBlank()) this else this.then(Modifier.clickable { onClick(url) })

private fun LazyListScope.detailField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    item {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** NetBox's "comments" field supports Markdown - rendered, not shown as literal text. */
private fun LazyListScope.detailMarkdownField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    item {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CommentCard(content = value, modifier = Modifier.fillMaxWidth())
        }
    }
}
