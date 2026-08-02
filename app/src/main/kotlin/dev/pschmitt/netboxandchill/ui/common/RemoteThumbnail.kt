package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

/**
 * Square thumbnail for a NetBox-hosted image (device-type stock photo, image attachment). Falls
 * back to a generic device icon when [imageUrl] is null/blank (not yet synced, or none set).
 */
@Composable
fun RemoteThumbnail(
    imageUrl: String?,
    contentDescription: String?,
    localFile: File? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (imageUrl.isNullOrBlank() && localFile == null) {
        Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Hub,
                contentDescription = contentDescription,
                tint = fallbackTint,
            )
        }
    } else {
        AsyncImage(
            model = localFile ?: imageUrl,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = contentScale,
        )
    }
}
