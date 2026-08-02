package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

private const val COLLAPSIBLE_COMMENT_MAX_LINES = 12
private const val COLLAPSIBLE_COMMENT_MIN_CHARACTERS = 800
private val CollapsedCommentHeight = 180.dp

/**
 * A NetBox "comments" field (Markdown), styled like a social post/comment card rather than a plain
 * inline text row - its own tonal background sets it apart from the surrounding key/value fields.
 */
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

internal fun isLongComment(content: String): Boolean =
    content.lineSequence().count() > COLLAPSIBLE_COMMENT_MAX_LINES ||
        content.length > COLLAPSIBLE_COMMENT_MIN_CHARACTERS

/** Markdown card that keeps very long item comments compact until the user expands them. */
@Composable
fun CollapsibleCommentCard(content: String, modifier: Modifier = Modifier) {
    val collapsible = isLongComment(content)
    var expanded by remember(content) { mutableStateOf(false) }
    Column(modifier) {
        if (collapsible && !expanded) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(CollapsedCommentHeight)
                        .clip(RoundedCornerShape(16.dp))
            ) {
                CommentCard(content = content, modifier = Modifier.fillMaxWidth())
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    )
                                )
                            )
                )
            }
        } else {
            CommentCard(content = content, modifier = Modifier.fillMaxWidth())
        }
        if (collapsible) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
                Text(if (expanded) "Collapse" else "Show more")
            }
        }
    }
}
