package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.ui.common.ImageViewerItem
import dev.pschmitt.nyetbox.ui.common.MediaUploadKind
import dev.pschmitt.nyetbox.ui.common.RemoteThumbnail

/** Device-type stock photos belong near the identity card on generic detail pages. */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.deviceTypePhotos(
    rows: List<FieldRow.Image>,
    title: String?,
    localImageFile: (String, String) -> java.io.File?,
    onImageClick: (List<ImageViewerItem>, Int) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val photoRows = rows.filter { deviceTypePhotoUploadKind(it.label) != null }
    if (photoRows.isEmpty()) return
    val itemTitle = title?.takeIf { it.isNotBlank() } ?: "Device type"
    val viewerItems = deviceTypePhotoItems(photoRows, itemTitle, localImageFile)
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            photoRows.forEachIndexed { index, row ->
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .combinedClickable(
                                onClick = { onImageClick(viewerItems, index) },
                                onLongClick = { onLongPress(row.label) },
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RemoteThumbnail(
                        imageUrl = row.url,
                        contentDescription = "${row.label} of $itemTitle",
                        localFile = localImageFile(row.url, row.url.attachmentFilename()),
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        row.label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color =
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

internal fun deviceTypePhotoItems(
    rows: List<FieldRow.Image>,
    title: String?,
    localImageFile: (String, String) -> java.io.File?,
): List<ImageViewerItem> {
    val itemTitle = title?.takeIf { it.isNotBlank() } ?: "Device type"
    return rows
        .filter { deviceTypePhotoUploadKind(it.label) != null }
        .map { row ->
            ImageViewerItem(
                url = row.url,
                title = "${row.label} of $itemTitle",
                metadata = listOf("View" to row.label),
                localFile = localImageFile(row.url, row.url.attachmentFilename()),
            )
        }
}

internal fun deviceTypePhotoUploadKind(label: String): MediaUploadKind? =
    when {
        label.contains("front", ignoreCase = true) -> MediaUploadKind.DeviceTypeFront
        label.contains("rear", ignoreCase = true) -> MediaUploadKind.DeviceTypeRear
        else -> null
    }
