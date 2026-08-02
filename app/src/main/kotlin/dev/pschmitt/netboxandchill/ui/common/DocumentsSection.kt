package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.pschmitt.netboxandchill.data.repository.CachedDocument

@Composable
fun DocumentsSection(
    documents: List<CachedDocument>,
    onOpenDocument: (CachedDocument) -> Unit,
    onAddDocument: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Documents", style = MaterialTheme.typography.titleLarge)
        }
        if (documents.isEmpty()) {
            Text(
                "No documents attached.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
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
                            Text(
                                listOfNotNull(document.documentType, document.filename)
                                    .distinct()
                                    .joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Description, contentDescription = null)
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
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Upload document")
            }
        }
    }
}
