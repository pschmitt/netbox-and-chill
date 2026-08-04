package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.ui.common.ImageViewerItem
import dev.pschmitt.nyetbox.ui.common.NyetboxCard
import dev.pschmitt.nyetbox.ui.common.RemoteThumbnail
import dev.pschmitt.nyetbox.ui.common.StatusChip
import dev.pschmitt.nyetbox.ui.directory.AppIcons

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GenericDetailIdentityCard(
    id: Int,
    endpointPath: String,
    title: String? = null,
    preview: ImageViewerItem? = null,
    onPreviewClick: (() -> Unit)? = null,
    onPreviewLongPress: (() -> Unit)? = null,
    statusField: FieldRow.PlainText?,
    detailAccent: Color,
    onStatusLongPress: () -> Unit,
) {
    NyetboxCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val previewModifier =
                Modifier.size(64.dp)
                    .then(
                        if (onPreviewClick != null || onPreviewLongPress != null) {
                            Modifier.combinedClickable(
                                onClick = { onPreviewClick?.invoke() },
                                onLongClick = { onPreviewLongPress?.invoke() },
                            )
                        } else Modifier
                    )
            if (preview != null) {
                RemoteThumbnail(
                    imageUrl = preview.url,
                    contentDescription = preview.title,
                    localFile = preview.localFile,
                    modifier = previewModifier,
                    contentScale = ContentScale.Fit,
                    fallbackTint = detailAccent,
                )
            } else {
                Surface(
                    color = detailAccent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = previewModifier,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            AppIcons.forEndpointPath(endpointPath),
                            contentDescription = null,
                            tint = detailAccent,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
            Column(Modifier.padding(start = 12.dp).padding(end = 8.dp).weight(1f)) {
                title
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                        )
                    }
                Text(
                    "ID #$id",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                statusField?.let { status ->
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onStatusLongPress,
                            )
                    ) {
                        StatusChip(
                            label = status.value,
                            value = status.value.lowercase(),
                        )
                    }
                }
            }
        }
    }
}
