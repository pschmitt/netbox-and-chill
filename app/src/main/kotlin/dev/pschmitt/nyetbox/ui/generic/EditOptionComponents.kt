package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.repository.choiceSearchMatches
import dev.pschmitt.nyetbox.ui.common.RemoteThumbnail

/** Search control shared by single- and multi-value linked-field pickers. */
@Composable
internal fun EditOptionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { androidx.compose.material3.Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

internal fun filterEditOptions(options: List<EditOption>, query: String): List<EditOption> {
    if (query.isBlank()) return options
    return options.filter {
        choiceSearchMatches(it.label, it.value, it.searchFields, query).isNotEmpty()
    }
}

@Composable
internal fun EditOptionPreview(option: EditOption) {
    val hasImages = !option.frontImageUrl.isNullOrBlank() || !option.rearImageUrl.isNullOrBlank()
    if (!hasImages) {
        Icon(Icons.Default.Link, contentDescription = null)
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(76.dp),
    ) {
        option.frontImageUrl?.let {
            RemoteThumbnail(
                imageUrl = it,
                contentDescription = "Front image",
                modifier = Modifier.size(34.dp),
            )
        }
        option.rearImageUrl?.let {
            RemoteThumbnail(
                imageUrl = it,
                contentDescription = "Rear image",
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
