package dev.pschmitt.nyetbox.ui.common

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.pschmitt.nyetbox.data.db.ImageAttachmentEntity
import dev.pschmitt.nyetbox.data.repository.CachedDocument
import dev.pschmitt.nyetbox.data.schema.documentTypePresentation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CarouselTileHeight = 200.dp
private val CarouselItemWidth = 200.dp

/**
 * Single Material 3 hero carousel merging image attachments and documents into one scrollable
 * "Media" widget, replacing the previously separate image-attachment gallery and documents list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaCarousel(
    attachments: List<ImageAttachmentEntity>,
    documents: List<CachedDocument>,
    onImageClick: (index: Int) -> Unit,
    onDocumentClick: (CachedDocument) -> Unit,
    onAddMedia: (uri: Uri, defaultKind: MediaUploadKind) -> Unit,
    modifier: Modifier = Modifier,
    supportsImageAttachments: Boolean = true,
    supportsDocuments: Boolean = false,
    onAttachmentLongPress: (ImageAttachmentEntity) -> Unit = {},
    localFileFor: ((CachedDocument) -> File?)? = null,
    onDeleteDocument: ((CachedDocument) -> Unit)? = null,
) {
    val context = LocalContext.current
    var actionDocument by remember { mutableStateOf<CachedDocument?>(null) }
    var deleteDocument by remember { mutableStateOf<CachedDocument?>(null) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    val totalCount = attachments.size + documents.size

    fun defaultKindFor(isImage: Boolean): MediaUploadKind =
        when {
            isImage && supportsImageAttachments -> MediaUploadKind.ImageAttachment
            supportsDocuments -> MediaUploadKind.Document
            else -> MediaUploadKind.ImageAttachment
        }
    val takePhoto =
        rememberCameraCaptureLauncher(
            onCaptured = { uri -> onAddMedia(uri, defaultKindFor(isImage = true)) }
        )
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val mimeType = context.contentResolver.getType(it)
                val isImage =
                    isSharedImage(
                        mimeType,
                        filename = null,
                        uriLastPathSegment = it.lastPathSegment,
                    )
                onAddMedia(it, defaultKindFor(isImage))
            }
        }

    NyetboxSectionCard(
        title = "Media",
        icon = Icons.Default.PermMedia,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        trailingContent = {
            if (totalCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(totalCount.toString())
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
            if (totalCount > 0) {
                val carouselState = rememberCarouselState { totalCount }
                HorizontalMultiBrowseCarousel(
                    state = carouselState,
                    preferredItemWidth = CarouselItemWidth,
                    itemSpacing = 8.dp,
                    modifier =
                        Modifier.fillMaxWidth().height(CarouselTileHeight).padding(top = 4.dp),
                ) { index ->
                    val tileModifier =
                        Modifier.fillMaxWidth()
                            .height(CarouselTileHeight)
                            .maskClip(MaterialTheme.shapes.extraLarge)
                    if (index < attachments.size) {
                        val attachment = attachments[index]
                        ImageCarouselTile(
                            attachment = attachment,
                            modifier = tileModifier,
                            onClick = { onImageClick(index) },
                            onLongClick = { onAttachmentLongPress(attachment) },
                        )
                    } else {
                        val document = documents[index - attachments.size]
                        val localFile = localFileFor?.invoke(document)
                        DocumentCarouselTile(
                            document = document,
                            localFile = localFile,
                            modifier = tileModifier,
                            onClick = { onDocumentClick(document) },
                            onLongClick =
                                if (onDeleteDocument != null) {
                                    { actionDocument = document }
                                } else null,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                MediaAddButton(
                    label = "Add media",
                    onClick = { addMenuExpanded = true },
                    icon = Icons.Default.Add,
                )
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Take photo") },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        onClick = {
                            addMenuExpanded = false
                            takePhoto()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Upload file") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        onClick = {
                            addMenuExpanded = false
                            filePicker.launch("*/*")
                        },
                    )
                }
            }
        }
    }
    actionDocument?.let { document ->
        val canOpen = !document.documentUrl.isNullOrBlank() || !document.externalUrl.isNullOrBlank()
        AlertDialog(
            onDismissRequest = { actionDocument = null },
            icon = { Icon(Icons.Default.Description, contentDescription = null) },
            title = { Text(document.name) },
            text = {
                Column {
                    if (canOpen) {
                        TextButton(
                            onClick = {
                                actionDocument = null
                                onDocumentClick(document)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Text("Open document", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    TextButton(
                        onClick = {
                            actionDocument = null
                            deleteDocument = document
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("Delete document", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionDocument = null }) { Text("Cancel") } },
        )
    }
    deleteDocument?.let { document ->
        AlertDialog(
            onDismissRequest = { deleteDocument = null },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Delete document?") },
            text = {
                Text(
                    "Delete ${document.name} from NetBox? The cached copy is removed immediately; " +
                        "offline deletions are uploaded when sync resumes."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDocument = null
                        onDeleteDocument?.invoke(document)
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Delete", modifier = Modifier.padding(start = 8.dp))
                }
            },
            dismissButton = { TextButton(onClick = { deleteDocument = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageCarouselTile(
    attachment: ImageAttachmentEntity,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        AsyncImage(
            model = attachment.imageUrl,
            contentDescription = attachment.displayName(),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        CarouselTileCaption(attachment.displayName())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCarouselTile(
    document: CachedDocument,
    localFile: File?,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val pdfPreview by
        produceState<Bitmap?>(
            initialValue = null,
            localFile,
            document.filename,
            document.documentUrl,
        ) {
            value =
                withContext(Dispatchers.IO) {
                    renderPdfPage(
                        localFile,
                        document.filename,
                        document.documentUrl,
                        maxWidth = 600,
                        maxHeight = 800,
                    )
                }
        }
    val extension = document.filename.substringAfterLast('.', "").uppercase()
    val isImage = extension in setOf("AVIF", "BMP", "GIF", "JPEG", "JPG", "PNG", "WEBP")

    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        when {
            pdfPreview != null ->
                Image(
                    bitmap = pdfPreview!!.asImageBitmap(),
                    contentDescription = "Preview of ${document.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            isImage && localFile != null ->
                AsyncImage(
                    model = localFile,
                    contentDescription = "Preview of ${document.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            else -> {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                )
                if (extension.isNotBlank()) {
                    Text(
                        extension.take(5),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.Center).padding(top = 40.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            document.documentType?.let { type -> DocumentTypeBadge(type) }
            if (localFile?.isFile == true) {
                CachedDocumentBadge()
            }
        }
        CarouselTileCaption(document.name)
    }
}

@Composable
private fun BoxScope.CarouselTileCaption(title: String) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DocumentTypeBadge(rawType: String) {
    val presentation = documentTypePresentation(rawType) ?: return
    val colors = documentTypeBadgeColors(presentation.key)
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector =
                    when (presentation.key) {
                        "manual" -> Icons.AutoMirrored.Filled.MenuBook
                        "purchaseorder" -> Icons.Default.ShoppingCart
                        "floorplan" -> Icons.Default.Map
                        else -> Icons.Default.Description
                    },
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text(presentation.label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CachedDocumentBadge() {
    Badge(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
            Text("Cached", style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun ImageAttachmentEntity.toImageViewerItem(sourceLabel: String? = null): ImageViewerItem {
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
        sourceLabel = sourceLabel,
    )
}

fun ImageAttachmentEntity.displayName(): String =
    name?.takeIf { it.isNotBlank() }
        ?: display?.takeIf { it.isNotBlank() }
        ?: "Image attachment #$id"

/**
 * Builds a viewer entry for [this] document when it's a cached, readable PDF - `null` for anything
 * else (not yet cached locally, or not a PDF), in which case the caller falls back to
 * opening/downloading it externally instead of showing it in [ImageViewerDialog].
 */
fun CachedDocument.toDocumentViewerItem(localFile: File?): ImageViewerItem? {
    if (localFile == null || !looksLikePdf(localFile, filename, documentUrl)) return null
    val pageCount = pdfPageCount(localFile, filename, documentUrl)
    val metadata = buildList {
        documentType?.let { type ->
            documentTypePresentation(type)?.let { add("Type" to it.label) }
        }
        if (pageCount != null && pageCount > 1) add("Pages" to "1 of $pageCount")
        if (!comments.isNullOrBlank()) add("Comments" to comments)
    }
    return ImageViewerItem(
        url = "",
        title = name,
        metadata = metadata,
        sourceLabel = "Document",
        pdfFile = localFile,
    )
}
