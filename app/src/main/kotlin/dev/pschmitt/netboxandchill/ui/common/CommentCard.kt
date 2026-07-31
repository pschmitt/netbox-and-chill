package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown

/** A NetBox "comments" field (Markdown), styled like a social post/comment card rather than a
 * plain inline text row - its own tonal background sets it apart from the surrounding key/value
 * fields. */
@Composable
fun CommentCard(content: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Markdown(content = content, modifier = Modifier.padding(16.dp))
    }
}
