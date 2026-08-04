package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.db.NetBoxObjectEntity
import dev.pschmitt.nyetbox.ui.common.NyetboxCard
import dev.pschmitt.nyetbox.ui.common.NyetboxListItem
import dev.pschmitt.nyetbox.ui.common.RemoteThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RelatedItemsBottomSheet(
    target: CountTarget,
    objects: List<NetBoxObjectEntity>,
    previewUrls: Map<Int, String>,
    isRefreshing: Boolean,
    onObjectClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                target.listLabel,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when {
                isRefreshing && objects.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                objects.isEmpty() ->
                    Text(
                        "No related items cached yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(24.dp),
                    )
                else ->
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        items(objects, key = { it.id }) { objectEntity ->
                            NyetboxCard(modifier = Modifier.padding(vertical = 4.dp)) {
                                NyetboxListItem(
                                    leadingContent = {
                                        RemoteThumbnail(
                                            imageUrl = previewUrls[objectEntity.id],
                                            contentDescription = objectEntity.display,
                                            modifier = Modifier.size(56.dp),
                                        )
                                    },
                                    headlineContent = { Text(objectEntity.display) },
                                    supportingContent =
                                        objectEntity.secondaryLine?.let { line -> { Text(line) } },
                                    modifier = Modifier.clickable { onObjectClick(objectEntity.id) },
                                )
                            }
                        }
                    }
            }
        }
    }
}
