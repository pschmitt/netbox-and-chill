package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Precision
import dev.pschmitt.nyetbox.image.TransparentPaddingTransformation
import java.io.File

/**
 * Square thumbnail for a NetBox-hosted image (device-type stock photo, image attachment). Falls
 * back to a generic device icon when [imageUrl] is null/blank (not yet synced, or none set).
 */
@Composable
fun RemoteThumbnail(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    localFile: File? = null,
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
        val context = LocalPlatformContext.current
        val request =
            remember(imageUrl, localFile) {
                ImageRequest.Builder(context)
                    .data(localFile?.toUri() ?: imageUrl)
                    // These are thumbnails everywhere this composable is used. Bounding the
                    // decode is important for long device-type lists: NetBox photos can be much
                    // larger than the 64-140dp surface they occupy, and the alpha-cropping
                    // transformation otherwise has to allocate a full-size pixel buffer.
                    .size(256, 256)
                    .precision(Precision.INEXACT)
                    .transformations(TransparentPaddingTransformation())
                    .build()
            }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = contentScale,
        )
    }
}
