package dev.pschmitt.netboxandchill.ui.directory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity

private const val DEVICES_PATH = "api/dcim/devices/"

@Composable
fun Sidebar(
    onDeviceListClick: () -> Unit,
    onModelClick: (NetBoxModelEntity) -> Unit,
    viewModel: DirectoryViewModel = hiltViewModel(),
) {
    val modelsByApp by viewModel.modelsByApp.collectAsStateWithLifecycle()
    val pinnedModels by viewModel.pinnedModels.collectAsStateWithLifecycle()
    val pinnedPaths by viewModel.settingsRepository.pinnedModelPaths.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val filteredModelsByApp =
        if (searchQuery.isBlank()) modelsByApp
        else
            modelsByApp
                .mapValues { (_, models) -> models.filter { it.modelLabel.contains(searchQuery, ignoreCase = true) } }
                .filterValues { it.isNotEmpty() }

    ModalDrawerSheet {
        LazyColumn(Modifier.fillMaxHeight()) {
            item {
                Text(
                    "NetBox and Chill",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search sections") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            if (searchQuery.isBlank()) {
                item {
                    NavigationDrawerItem(
                        label = { Text("Devices") },
                        icon = { Icon(AppIcons.Devices, contentDescription = null) },
                        selected = false,
                        onClick = onDeviceListClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                items(
                    pinnedModels.filter { it.endpointPath != DEVICES_PATH },
                    key = { "pinned-${it.endpointPath}" },
                ) { model ->
                    NavigationDrawerItem(
                        label = { Text(model.modelLabel) },
                        icon = { Icon(AppIcons.forAppKey(model.appKey), contentDescription = null) },
                        selected = false,
                        onClick = { onModelClick(model) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }
            filteredModelsByApp.forEach { (appLabel, models) ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    ) {
                        Icon(
                            AppIcons.forAppKey(models.first().appKey),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            appLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(models, key = { it.endpointPath }) { model ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    ) {
                        NavigationDrawerItem(
                            label = { Text(model.modelLabel) },
                            selected = false,
                            onClick = { onModelClick(model) },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        val isPinned = model.endpointPath in pinnedPaths
                        IconButton(onClick = { viewModel.togglePinned(model.endpointPath) }) {
                            Icon(
                                if (isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isPinned) "Unpin" else "Pin",
                                tint =
                                    if (isPinned) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
