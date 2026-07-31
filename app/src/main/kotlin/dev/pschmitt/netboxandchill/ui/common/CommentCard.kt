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
import com.mikepenz.markdown.m3.markdownTypography

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
        // NetBox comments often carry leading/trailing blank lines - the Markdown renderer treats
        // those as real empty paragraphs, padding the card out further than the 16dp below adds
        // on its own.
        Markdown(
            content = content.trim(),
            modifier = Modifier.padding(16.dp),
            typography =
                markdownTypography(
                    text = MaterialTheme.typography.bodyMedium,
                    paragraph = MaterialTheme.typography.bodyMedium,
                    ordered = MaterialTheme.typography.bodyMedium,
                    bullet = MaterialTheme.typography.bodyMedium,
                    list = MaterialTheme.typography.bodyMedium,
                ),
        )
    }
}
