package dev.pschmitt.nyetbox.ui.common

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import coil3.compose.AsyncImage
import dev.pschmitt.nyetbox.data.repository.CachedDocument
import dev.pschmitt.nyetbox.data.schema.documentTypePresentation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentsSection(
    documents: List<CachedDocument>,
    onOpenDocument: (CachedDocument) -> Unit,
    onAddDocument: (() -> Unit)? = null,
    localFileFor: ((CachedDocument) -> File?)? = null,
    onDeleteDocument: ((CachedDocument) -> Unit)? = null,
) {
    var actionDocument by
        androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<CachedDocument?>(null)
        }
    var deleteDocument by
        androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<CachedDocument?>(null)
        }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        NetBoxSectionHeader(
            Icons.Default.Description,
            "Documents",
            trailingContent = {
                if (documents.isNotEmpty()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(documents.size.toString())
                    }
                }
            },
        )
        if (documents.isNotEmpty()) {
            documents.forEach { document ->
                val canOpen =
                    !document.documentUrl.isNullOrBlank() || !document.externalUrl.isNullOrBlank()
                val localFile = localFileFor?.invoke(document)
                NyetboxCard(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = { if (canOpen) onOpenDocument(document) },
                                onLongClick =
                                    if (onDeleteDocument != null) {
                                        { actionDocument = document }
                                    } else null,
                            )
                ) {
                    NyetboxListItem(
                        headlineContent = { Text(document.name) },
                        supportingContent = {
                            Column {
                                document.documentType?.let { type -> DocumentTypeBadge(type) }
                                if (localFile?.isFile == true) {
                                    CachedDocumentBadge()
                                }
                            }
                        },
                        leadingContent = {
                            DocumentPreview(
                                document = document,
                                localFile = localFile,
                            )
                        },
                    )
                }
            }
        }
        onAddDocument?.let { onAdd ->
            MediaAddButton(
                label = "Add document",
                onClick = onAdd,
                icon = Icons.Default.UploadFile,
                modifier = Modifier.padding(top = 6.dp),
            )
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
                                onOpenDocument(document)
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
            confirmButton = {
                TextButton(onClick = { actionDocument = null }) { Text("Cancel") }
            },
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
            dismissButton = {
                TextButton(onClick = { deleteDocument = null }) { Text("Cancel") }
            },
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
private fun DocumentPreview(document: CachedDocument, localFile: File?) {
    val pdfPreview by
        produceState<Bitmap?>(
            initialValue = null,
            localFile,
            document.filename,
            document.documentUrl,
        ) {
            value =
                withContext(Dispatchers.IO) {
                    renderPdfPreview(localFile, document.filename, document.documentUrl)
                }
        }
    val extension = document.filename.substringAfterLast('.', "").uppercase()
    val isImage = extension in setOf("AVIF", "BMP", "GIF", "JPEG", "JPG", "PNG", "WEBP")

    Box(
        modifier =
            Modifier.size(width = 72.dp, height = 92.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            pdfPreview != null ->
                androidx.compose.foundation.Image(
                    bitmap = pdfPreview!!.asImageBitmap(),
                    contentDescription = "Preview of ${document.name}",
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    contentScale = ContentScale.Crop,
                )
            isImage && localFile != null ->
                AsyncImage(
                    model = localFile,
                    contentDescription = "Preview of ${document.name}",
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    contentScale = ContentScale.Crop,
                )
            else -> {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                if (extension.isNotBlank()) {
                    Text(
                        extension.take(5),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
                    )
                }
            }
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

private fun renderPdfPreview(file: File?, filename: String, url: String?): Bitmap? {
    if (file == null || !file.isFile || !looksLikePdf(file, filename, url)) return null
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return@runCatching null
                val page = renderer.openPage(0)
                try {
                    val scale = minOf(1f, 240f / page.width, 320f / page.height)
                    val bitmap =
                        createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }
        }
    }
        .getOrNull()
}

private fun looksLikePdf(file: File, filename: String, url: String?): Boolean {
    if (filename.hasPdfExtension() || url.orEmpty().substringBefore('?').hasPdfExtension()) {
        return true
    }
    if (file.extension.equals("pdf", ignoreCase = true)) return true
    return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(5)
                input.read(header) == header.size &&
                    header.contentEquals("%PDF-".encodeToByteArray())
            }
        }
        .getOrDefault(false)
}

private fun String.hasPdfExtension(): Boolean = substringAfterLast('.', "").equals("pdf", true)
