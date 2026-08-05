package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.schema.Humanize

/** Compact object-type badge shared by global search and the dashboard's cached item rows. */
@Composable
fun ObjectTypeBadge(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
        modifier = modifier.widthIn(max = 220.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The same singularized type label used by search when the directory has not loaded yet. */
fun objectTypeLabel(modelLabel: String?, endpointPath: String): String {
    modelLabel?.takeIf(String::isNotBlank)?.let { return singularizeLabel(it) }
    val modelKey = endpointPath.trim('/').split('/').lastOrNull().orEmpty()
    return modelKey.takeIf(String::isNotBlank)?.let(Humanize::label)?.let(::singularizeLabel)
        ?: "Object"
}

private fun singularizeLabel(label: String): String {
    val words = label.split(' ').filter(String::isNotBlank)
    if (words.isEmpty()) return label
    val last = words.last()
    val lower = last.lowercase()
    val singular =
        when {
            lower.endsWith("ies") -> last.dropLast(3) + "y"
            lower.endsWith("sses") ||
                lower.endsWith("xes") ||
                lower.endsWith("ches") ||
                lower.endsWith("shes") -> last.dropLast(2)
            lower.endsWith("ses") -> last.dropLast(2)
            lower.endsWith("s") && !lower.endsWith("ss") -> last.dropLast(1)
            else -> last
        }
    return (words.dropLast(1) + singular).joinToString(" ")
}
