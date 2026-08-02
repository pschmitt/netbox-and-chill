package dev.pschmitt.netboxandchill.ui.common

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import dev.pschmitt.netboxandchill.data.schema.documentTypePresentation
import coil3.compose.AsyncImage
import dev.pschmitt.netboxandchill.data.repository.CachedDocument
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentsSection(
    documents: List<CachedDocument>,
    onOpenDocument: (CachedDocument) -> Unit,
    onAddDocument: (() -> Unit)? = null,
    localFileFor: ((CachedDocument) -> File?)? = null,
) {
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
                val canOpen = !document.documentUrl.isNullOrBlank() || !document.externalUrl.isNullOrBlank()
                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (canOpen) Modifier.clickable { onOpenDocument(document) }
                                else Modifier
                            )
                ) {
                    ListItem(
                        headlineContent = { Text(document.name) },
                        supportingContent = {
                            document.documentType?.let { type -> DocumentTypeBadge(type) }
                        },
                        leadingContent = {
                            DocumentPreview(
                                document = document,
                                localFile = localFileFor?.invoke(document),
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { onOpenDocument(document) },
                                enabled = canOpen,
                            ) {
                                Icon(
                                    if (document.documentUrl != null) Icons.Default.Download
                                    else Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription =
                                        if (document.documentUrl != null) "Download document"
                                        else "Open document",
                                )
                            }
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
        Text(
            presentation.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun DocumentPreview(document: CachedDocument, localFile: File?) {
    val pdfPreview by
        produceState<Bitmap?>(initialValue = null, localFile, document.filename) {
            value = withContext(Dispatchers.IO) { renderPdfPreview(localFile) }
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
        if (localFile?.isFile == true) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Text("Cached", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun renderPdfPreview(file: File?): Bitmap? {
    if (file == null || !file.isFile || !file.extension.equals("pdf", ignoreCase = true)) {
        return null
    }
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
    }.getOrNull()
}
