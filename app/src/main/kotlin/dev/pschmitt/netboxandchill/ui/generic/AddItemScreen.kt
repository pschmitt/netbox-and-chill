package dev.pschmitt.netboxandchill.ui.generic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.ui.common.BottomTab
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import dev.pschmitt.netboxandchill.ui.common.NetBoxResponsiveScaffold
import dev.pschmitt.netboxandchill.ui.directory.AppIcons
import dev.pschmitt.netboxandchill.ui.directory.DirectoryViewModel

/** Lets the user choose any discovered NetBox model before opening the generic create form. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddItemScreen(
    onBack: () -> Unit,
    onModelClick: (NetBoxModelEntity) -> Unit,
    onDashboardClick: () -> Unit,
    onSearchClick: () -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DirectoryViewModel = hiltViewModel(),
) {
    val modelsByApp by viewModel.modelsByApp.collectAsStateWithLifecycle()
    val pinnedPaths by viewModel.settingsRepository.pinnedModelPaths.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val models =
        buildList {
                add(
                    NetBoxModelEntity(
                        appKey = "dcim",
                        appLabel = "DCIM",
                        modelKey = "devices",
                        modelLabel = "Devices",
                        endpointPath = "api/dcim/devices/",
                    )
                )
                add(
                    NetBoxModelEntity(
                        appKey = "dcim",
                        appLabel = "DCIM",
                        modelKey = "device-types",
                        modelLabel = "Device types",
                        endpointPath = "api/dcim/device-types/",
                    )
                )
                addAll(
                    modelsByApp
                        .toList()
                        .sortedBy { (appKey, models) -> models.firstOrNull()?.appLabel ?: appKey }
                        .flatMap { (_, appModels) ->
                            appModels.sortedBy { it.modelLabel.lowercase() }
                        }
                )
            }
            .distinctBy { it.endpointPath }
    val normalizedQuery = query.trim().lowercase()
    val filteredModels = models.filter { model ->
        normalizedQuery.isBlank() ||
            model.modelLabel.lowercase().contains(normalizedQuery) ||
            model.modelKey.lowercase().contains(normalizedQuery) ||
            model.appLabel.lowercase().contains(normalizedQuery)
    }
    val defaultPinnedEndpoints = listOf("api/dcim/devices/", "api/dcim/device-types/")
    val pinnedEndpoints = buildList {
        addAll(defaultPinnedEndpoints)
        addAll(
            filteredModels
                .filter {
                    it.endpointPath in pinnedPaths && it.endpointPath !in defaultPinnedEndpoints
                }
                .sortedWith(
                    compareBy<NetBoxModelEntity> { it.appLabel.lowercase() }
                        .thenBy { it.modelLabel.lowercase() }
                )
                .map { it.endpointPath }
        )
    }
    val pinnedModels = pinnedEndpoints.mapNotNull { endpoint ->
        filteredModels.firstOrNull { it.endpointPath == endpoint }
    }
    val otherModels =
        filteredModels
            .filterNot { it.endpointPath in pinnedEndpoints }
            .sortedWith(
                compareBy<NetBoxModelEntity> { it.appLabel.lowercase() }
                    .thenBy { it.modelLabel.lowercase() }
            )

    NetBoxResponsiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            NetBoxBottomBar(
                selected = BottomTab.Add,
                onDashboardClick = onDashboardClick,
                onSearchClick = onSearchClick,
                onScanClick = onScanClick,
                onAddClick = {},
                onSettingsClick = onSettingsClick,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                singleLine = true,
                label = { Text("Search item types") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear item type search")
                        }
                    }
                },
            )
            if (models.isEmpty()) {
                Text(
                    "No NetBox object types are cached yet. Connect and sync first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                )
            } else if (filteredModels.isEmpty()) {
                Text(
                    "No matching item types.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                    if (pinnedModels.isNotEmpty()) {
                        item {
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Default.PushPin, contentDescription = null)
                                },
                                headlineContent = { Text("Pinned") },
                            )
                        }
                        items(pinnedModels, key = { "pinned:${it.endpointPath}" }) { model ->
                            AddModelRow(
                                model = model,
                                onModelClick = onModelClick,
                                onTogglePin = { viewModel.togglePinned(model.endpointPath) },
                            )
                        }
                    }
                    if (otherModels.isNotEmpty()) {
                        item {
                            ListItem(
                                leadingContent = {
                                    Icon(AppIcons.forAppKey("core"), contentDescription = null)
                                },
                                headlineContent = {
                                    Text(
                                        if (pinnedModels.isEmpty()) "Item types"
                                        else "All item types"
                                    )
                                },
                            )
                        }
                        items(otherModels, key = { it.endpointPath }) { model ->
                            AddModelRow(
                                model = model,
                                onModelClick = onModelClick,
                                onTogglePin = { viewModel.togglePinned(model.endpointPath) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AddModelRow(
    model: NetBoxModelEntity,
    onModelClick: (NetBoxModelEntity) -> Unit,
    onTogglePin: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(AppIcons.forAppKey(model.appKey), contentDescription = null) },
        headlineContent = { Text(model.modelLabel) },
        supportingContent = { Text(model.appLabel) },
        trailingContent = {
            Icon(Icons.Default.AddCircle, contentDescription = "Add ${model.modelLabel}")
        },
        modifier =
            Modifier.combinedClickable(
                onClick = { onModelClick(model) },
                onLongClick = onTogglePin,
            ),
    )
}
