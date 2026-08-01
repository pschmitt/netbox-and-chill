package dev.pschmitt.netboxandchill.ui.generic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.netboxandchill.ui.common.CommentCard

/** A small Markdown editor with formatting shortcuts and a live rendered preview. */
@Composable
fun MarkdownEditor(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MarkdownShortcut("Bold", Icons.Default.FormatBold, "**bold**", value, onValueChange)
            MarkdownShortcut("Italic", Icons.Default.FormatItalic, "*italic*", value, onValueChange)
            MarkdownShortcut(
                "List",
                Icons.Default.FormatListBulleted,
                "\n- item",
                value,
                onValueChange,
            )
            MarkdownShortcut(
                "Link",
                Icons.Default.Link,
                "[label](https://example.com)",
                value,
                onValueChange,
            )
            MarkdownShortcut("Code", Icons.Default.Code, "`code`", value, onValueChange)
        }
        if (value.isNotBlank()) {
            HorizontalDivider()
            Text(
                "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            CommentCard(content = value)
        }
    }
}

@Composable
private fun MarkdownShortcut(
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    insertion: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    IconButton(
        onClick = { onValueChange(value + if (value.isBlank()) insertion else "\n$insertion") }
    ) {
        Icon(icon, contentDescription = "Insert $description")
    }
}
