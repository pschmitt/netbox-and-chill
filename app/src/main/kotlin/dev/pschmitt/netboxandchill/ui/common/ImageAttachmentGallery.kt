package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentEntity
import java.io.File

private val AttachmentTileWidth = 176.dp
private val AttachmentTileHeight = 140.dp

/** Inline, cache-first image attachments gallery shared by typed and generic item pages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageAttachmentGallery(
    attachments: List<ImageAttachmentEntity>,
    localImageFile: (url: String, filename: String) -> File?,
    onImageClick: (items: List<ImageViewerItem>, index: Int) -> Unit,
    onAdd: () -> Unit,
    onAttachmentLongPress: (ImageAttachmentEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewerItems = attachments.map { it.toImageViewerItem(localImageFile) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        NetBoxSectionHeader(
            Icons.Default.UploadFile,
            "Image attachments",
            trailingContent = {
                if (attachments.isNotEmpty()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(attachments.size.toString())
                    }
                }
            },
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(attachments, key = { _, attachment -> attachment.id }) { index, attachment ->
                RemoteThumbnail(
                    imageUrl = attachment.imageUrl,
                    contentDescription = attachment.displayName(),
                    localFile =
                        attachment.imageUrl?.let {
                            localImageFile(it, attachment.fileName())
                        },
                    modifier =
                        Modifier.width(AttachmentTileWidth)
                            .height(AttachmentTileHeight)
                            .combinedClickable(
                                onClick = { onImageClick(viewerItems, index) },
                                onLongClick = { onAttachmentLongPress(attachment) },
                            ),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        MediaAddButton(
            label = "Add image",
            onClick = onAdd,
            icon = Icons.Default.UploadFile,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

fun ImageAttachmentEntity.toImageViewerItem(
    localImageFile: (url: String, filename: String) -> File?
): ImageViewerItem {
    val title = displayName()
    val metadata = buildList {
        if (!description.isNullOrBlank()) add("Description" to description)
        if (imageWidth != null && imageHeight != null) {
            add("Dimensions" to "$imageWidth × $imageHeight")
        }
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

fun ImageAttachmentEntity.displayName(): String =
    name?.takeIf { it.isNotBlank() }
        ?: display?.takeIf { it.isNotBlank() }
        ?: "Image attachment #$id"

private fun ImageAttachmentEntity.fileName(): String =
    name?.takeIf { it.isNotBlank() }
        ?: display?.takeIf { it.isNotBlank() }
        ?: "image-attachment-$id"
