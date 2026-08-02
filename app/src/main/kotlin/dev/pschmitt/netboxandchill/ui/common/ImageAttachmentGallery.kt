package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentEntity
import java.io.File

private val AttachmentTileWidth = 176.dp
private val AttachmentTileHeight = 140.dp
private val AddAttachmentTileSize = 96.dp

/** Inline, cache-first image attachments gallery shared by typed and generic item pages. */
@Composable
fun ImageAttachmentGallery(
    attachments: List<ImageAttachmentEntity>,
    localImageFile: (url: String, filename: String) -> File?,
    onImageClick: (items: List<ImageViewerItem>, index: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewerItems = attachments.map { it.toImageViewerItem(localImageFile) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.UploadFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Image attachments", style = MaterialTheme.typography.labelMedium)
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    attachments.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                            .clickable {
                                onImageClick(viewerItems, index)
                            },
                    contentScale = ContentScale.Crop,
                )
            }
            item(key = "add-image-attachment") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier =
                        Modifier.size(AddAttachmentTileSize)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Add image attachment",
                                onClick = onAdd,
                            ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Add image", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
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

private fun ImageAttachmentEntity.displayName(): String =
    name?.takeIf { it.isNotBlank() }
        ?: display?.takeIf { it.isNotBlank() }
        ?: "Image attachment #$id"

private fun ImageAttachmentEntity.fileName(): String =
    name?.takeIf { it.isNotBlank() }
        ?: display?.takeIf { it.isNotBlank() }
        ?: "image-attachment-$id"
