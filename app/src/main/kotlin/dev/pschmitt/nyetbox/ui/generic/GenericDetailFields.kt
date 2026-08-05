package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.repository.hiddenFieldPreferenceKey
import dev.pschmitt.nyetbox.data.schema.Humanize
import dev.pschmitt.nyetbox.ui.common.CollapsibleCommentCard
import dev.pschmitt.nyetbox.ui.common.DetailTrailingActions
import dev.pschmitt.nyetbox.ui.common.ImageViewerItem
import dev.pschmitt.nyetbox.ui.common.NyetboxCard
import dev.pschmitt.nyetbox.ui.common.RemoteThumbnail
import dev.pschmitt.nyetbox.ui.common.formatNetBoxDateTime
import dev.pschmitt.nyetbox.ui.directory.AppIcons

internal fun visibleFieldRows(
    rows: List<FieldRow>,
    endpointPath: String,
    hiddenFieldKeys: Set<String>,
    showHiddenFields: Boolean,
): List<FieldRow> {
    if (showHiddenFields) return rows
    val filtered = rows.filterNot { row ->
        row !is FieldRow.Section &&
            row !is FieldRow.CustomGroup &&
            hiddenFieldPreferenceKey(endpointPath, row.label) in hiddenFieldKeys
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

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.detailCard(
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item {
        NyetboxCard(
            modifier =
                Modifier.padding(vertical = 4.dp)
                    .then(
                        if (onClick != null || onLongPress != null) {
                            Modifier.combinedClickable(
                                onClick = { onClick?.invoke() },
                                onLongClick = { onLongPress?.invoke() },
                            )
                        } else Modifier
                    )
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

internal fun endpointModelLabel(endpointPath: String): String =
    endpointPath
        .trimEnd('/')
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() }
        ?.let(Humanize::label) ?: "Details"

internal fun LazyListScope.fieldRow(
    row: FieldRow,
    onNavigateToReference: (String, Int) -> Unit,
    onRelatedItems: (CountTarget) -> Unit,
    onOpenUrl: (String) -> Unit,
    netboxBaseUrl: String?,
    onDownloadAttachment: (url: String, filename: String) -> Unit,
    onImageClick: (ImageViewerItem) -> Unit,
    isDownloading: Boolean,
    onCopyValue: (label: String, value: String) -> Unit,
    onFieldLongPress: (label: String) -> Unit,
    onMatterPairingCode: (String) -> Unit,
) {
    when (row) {
        is FieldRow.Section ->
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        is FieldRow.CustomGroup ->
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        is FieldRow.Metadata ->
            item {
                NyetboxCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                row.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatNetBoxDateTime(row.value),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        is FieldRow.PlainText ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            row.value,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (row.copyable) {
                            DetailTrailingActions(
                                copyLabel = row.label,
                                onCopy = { onCopyValue(row.label, row.value) },
                            )
                        }
                        if (row.matterPairingCode) {
                            IconButton(
                                onClick = { onMatterPairingCode(row.value) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Show Matter pairing QR code",
                                )
                            }
                        }
                    }
                }
            }
        is FieldRow.BooleanValue ->
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (row.value) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onFieldLongPress(row.label) },
                            ),
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (row.value) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = if (row.value) "Enabled" else "Disabled",
                            tint =
                                if (row.value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            FieldLabel(row.label) { onFieldLongPress(row.label) }
                            Text(
                                if (row.value) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (row.value) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        is FieldRow.Count ->
            detailCard(
                onClick = { onRelatedItems(row.target) },
                onLongPress = { onFieldLongPress(row.label) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Badge { Text(row.value) }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Show ${row.label.lowercase()}",
                    )
                }
            }
        is FieldRow.Markdown ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    CollapsibleCommentCard(
                        content = row.content,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        is FieldRow.Reference ->
            detailCard(
                onClick = { onNavigateToReference(row.target.endpointPath, row.target.id) },
                onLongPress = { onFieldLongPress(row.label) },
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            AppIcons.forEndpointPath(row.target.endpointPath),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            row.target.display,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        DetailTrailingActions(
                            copyLabel = row.label.takeIf { row.copyable },
                            onCopy =
                                { onCopyValue(row.label, row.target.display) }.takeIf {
                                    row.copyable
                                },
                            openLabel = row.label,
                            onOpen = {
                                onNavigateToReference(row.target.endpointPath, row.target.id)
                            },
                        )
                    }
                }
            }
        is FieldRow.Image ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    RemoteThumbnail(
                        imageUrl = row.url,
                        contentDescription = row.label,
                        modifier =
                            Modifier.fillMaxWidth().height(160.dp).padding(top = 4.dp).clickable {
                                onImageClick(ImageViewerItem(url = row.url, title = row.label))
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        is FieldRow.ReferenceList ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    row.targets.forEach { target ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.clickable {
                                        onNavigateToReference(target.endpointPath, target.id)
                                    }
                                    .padding(vertical = 2.dp),
                        ) {
                            Icon(
                                AppIcons.forEndpointPath(target.endpointPath),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                target.display,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        is FieldRow.TagList ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        FieldLabel(row.label) { onFieldLongPress(row.label) }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        row.targets.forEach { target ->
                            AssistChip(
                                onClick = { onNavigateToReference(target.endpointPath, target.id) },
                                label = { Text(target.display) },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        is FieldRow.ChipList ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Text(row.values.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                }
            }
        is FieldRow.ExternalLink ->
            detailCard(
                onClick = { onOpenUrl(row.url) },
                onLongPress = { onFieldLongPress(row.label) },
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            shortenDisplayedUrl(row.url, netboxBaseUrl),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        is FieldRow.FileAttachment ->
            detailCard(onLongPress = { onFieldLongPress(row.label) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    FieldLabel(row.label) { onFieldLongPress(row.label) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxWidth().clickable(enabled = !isDownloading) {
                                onDownloadAttachment(row.url, row.filename)
                            },
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            row.filename,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Download and open")
                        }
                    }
                }
            }
    }
}
