package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.db.ObjectChangeEntity
import dev.pschmitt.nyetbox.ui.common.NyetboxCard
import dev.pschmitt.nyetbox.ui.common.NyetboxListItem
import dev.pschmitt.nyetbox.ui.common.formatNetBoxDateTime

@Composable
internal fun GenericDetailChangelogRow(
    change: ObjectChangeEntity,
    onClick: () -> Unit,
) {
    NyetboxCard(modifier = Modifier.padding(vertical = 4.dp)) {
        NyetboxListItem(
            leadingContent = {
                Icon(
                    changeActionIcon(change.actionValue),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            headlineContent = { Text(change.objectRepr) },
            supportingContent = {
                Text(
                    "${change.actionLabel.ifBlank { "Changed" }} by ${change.userDisplay}\n" +
                        formatNetBoxDateTime(change.time)
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "View change diff",
                )
            },
            modifier =
                Modifier.fillMaxWidth()
                    .testTag("changelog-entry-${change.id}")
                    .clickable(onClick = onClick),
        )
    }
}

private fun changeActionIcon(action: String): ImageVector =
    when (action.lowercase()) {
        "create" -> Icons.Default.AddCircle
        "delete" -> Icons.Default.Delete
        "update" -> Icons.Default.Edit
        else -> Icons.Default.History
    }
