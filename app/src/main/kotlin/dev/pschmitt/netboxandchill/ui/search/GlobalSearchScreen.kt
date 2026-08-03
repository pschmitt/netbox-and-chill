package dev.pschmitt.netboxandchill.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.repository.parseGlobalSearchQuery
import dev.pschmitt.netboxandchill.data.repository.SearchHit
import dev.pschmitt.netboxandchill.data.repository.GlobalSearchRepository
import dev.pschmitt.netboxandchill.ui.common.BottomTab
import dev.pschmitt.netboxandchill.ui.common.AssetTagBadge
import dev.pschmitt.netboxandchill.ui.common.MissingAssetTagBadge
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import dev.pschmitt.netboxandchill.ui.common.NetBoxResponsiveScaffold
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.SearchQueryVisualTransformation
import dev.pschmitt.netboxandchill.ui.common.visualColorForEndpointPath
import dev.pschmitt.netboxandchill.ui.directory.AppIcons

/**
 * Cross-model search (NBC-13) - reachable from a search icon on the Devices/generic list top bars,
 * distinct from the sidebar's own search field (NBC-6/14), which only filters the list of
 * section/category names, not object data. Debounced in [GlobalSearchViewModel]; results come
 * straight from the offline cache, so they render even with no connectivity - [isRefreshing] is
 * just a best-effort background network pass, never a gate on showing what's already cached (see
 * [GlobalSearchRepository]/[GlobalSearchViewModel] for why).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onResultClick: (endpointPath: String, id: Int, display: String) -> Unit,
    onBack: () -> Unit,
    onDashboardClick: () -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    selectionPrompt: String? = null,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
    val typeSuggestions by viewModel.typeSuggestions.collectAsStateWithLifecycle()
    val recentResults by viewModel.recentResults.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val modelsByEndpointPath by viewModel.modelsByEndpointPath.collectAsStateWithLifecycle()
    val devicesById by viewModel.devicesById.collectAsStateWithLifecycle()
    val deviceTypesById by viewModel.deviceTypesById.collectAsStateWithLifecycle()
    val objectTypeAccents by viewModel.objectTypeAccents.collectAsStateWithLifecycle()
    val recentKeys = remember(recentResults) { recentResults.mapTo(HashSet()) { searchHitKey(it) } }
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    NetBoxResponsiveScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NetBoxBottomBar(
                selected = BottomTab.Search,
                onDashboardClick = onDashboardClick,
                onSearchClick = {},
                onScanClick = onScanClick,
                onAddClick = onAddClick,
                onSettingsClick = onSettingsClick,
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = {
                            Text(selectionPrompt ?: "Search all NetBox objects")
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        visualTransformation =
                            SearchQueryVisualTransformation(MaterialTheme.colorScheme.primary),
                        modifier =
                            Modifier.fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("e2e-global-search"),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Purely a "refresh in flight" hint, never hides already-cached results below - search
            // must keep working with no connectivity, so cached hits always take priority.
            if (isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when {
                typeFilter == null && query.isBlank() && recentResults.isNotEmpty() ->
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                },
                                headlineContent = { Text("Recently visited") },
                                supportingContent = {
                                    Text("Your latest devices and NetBox pages")
                                },
                            )
                        }
                        items(recentResults, key = { "recent-${it.endpointPath}-${it.id}" }) { hit
                            ->
                            val model = modelsByEndpointPath[hit.endpointPath]
                            val thumbnail =
                                viewModel.thumbnailFor(hit, devicesById, deviceTypesById)
                            SearchResultRow(
                                hit = hit,
                                modelLabel = model?.modelLabel,
                                icon = AppIcons.forEndpointPath(hit.endpointPath),
                                typeColor =
                                    visualColorForEndpointPath(
                                        hit.endpointPath,
                                        objectTypeAccents[hit.endpointPath.trim('/')],
                                        MaterialTheme.colorScheme,
                                    ),
                                thumbnail = thumbnail,
                                assetTag =
                                    hit.assetTag
                                        ?: if (hit.endpointPath == GlobalSearchRepository.DEVICES_ENDPOINT_PATH) {
                                            devicesById[hit.id]?.assetTag
                                        } else {
                                            null
                                        },
                                hasAssetTagField =
                                    hit.hasAssetTagField ||
                                        (hit.endpointPath ==
                                            GlobalSearchRepository.DEVICES_ENDPOINT_PATH &&
                                            devicesById[hit.id] != null),
                                localImageFile = viewModel::localImageFile,
                                isRecent = true,
                                onClick = {
                                    onResultClick(hit.endpointPath, hit.id, hit.display)
                                },
                            )
                        }
                    }
                typeFilter == null && query.isBlank() ->
                    SearchEmptyState(
                        title = "Search your NetBox",
                        message = "Find devices, sites, racks, IPs, circuits, and more",
                    )
                typeSuggestions.isNotEmpty() || results.isNotEmpty() ->
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        typeFilter?.let { model ->
                            item(key = "active-type-filter") {
                                ActiveTypeFilter(
                                    model = model,
                                    accent =
                                        visualColorForEndpointPath(
                                            model.endpointPath,
                                            objectTypeAccents[model.endpointPath.trim('/')],
                                            MaterialTheme.colorScheme,
                                        ),
                                    onClear = viewModel::clearTypeFilter,
                                )
                            }
                        }
                        if (typeSuggestions.isNotEmpty()) {
                            item(key = "type-filter-heading") {
                                ListItem(
                                    headlineContent = { Text("Filter by object type") },
                                    supportingContent = {
                                        Text("Choose a type to search only that NetBox collection")
                                    },
                                )
                            }
                            items(
                                typeSuggestions,
                                key = { "type-suggestion-${it.endpointPath}" },
                            ) { model ->
                                TypeSuggestionRow(
                                    model = model,
                                    onClick = { viewModel.selectType(model) },
                                )
                            }
                        }
                        if (results.isNotEmpty() && typeSuggestions.isNotEmpty()) {
                            item(key = "search-results-heading") {
                                ListItem(headlineContent = { Text("Matches") })
                            }
                        }
                        items(results, key = { "${it.endpointPath}-${it.id}" }) { hit ->
                            val model = modelsByEndpointPath[hit.endpointPath]
                            val thumbnail =
                                viewModel.thumbnailFor(hit, devicesById, deviceTypesById)
                            SearchResultRow(
                                hit = hit,
                                modelLabel = model?.modelLabel,
                                icon = AppIcons.forEndpointPath(hit.endpointPath),
                                typeColor =
                                    visualColorForEndpointPath(
                                        hit.endpointPath,
                                        objectTypeAccents[hit.endpointPath.trim('/')],
                                        MaterialTheme.colorScheme,
                                    ),
                                thumbnail = thumbnail,
                                assetTag =
                                    hit.assetTag
                                        ?: if (hit.endpointPath == GlobalSearchRepository.DEVICES_ENDPOINT_PATH) {
                                            devicesById[hit.id]?.assetTag
                                        } else {
                                            null
                                        },
                                hasAssetTagField =
                                    hit.hasAssetTagField ||
                                        (hit.endpointPath ==
                                            GlobalSearchRepository.DEVICES_ENDPOINT_PATH &&
                                            devicesById[hit.id] != null),
                                localImageFile = viewModel::localImageFile,
                                isRecent = searchHitKey(hit) in recentKeys,
                                onClick = {
                                    onResultClick(hit.endpointPath, hit.id, hit.display)
                                },
                            )
                        }
                    }
                isRefreshing -> CenteredHint("Searching…")
                else ->
                    SearchEmptyState(
                        title = if (typeFilter == null) "No matches yet" else "No cached matches",
                        message =
                            if (typeFilter == null) {
                                "Try a device name, asset tag, IP address, or model"
                            } else {
                                "Try another query or clear the object-type filter"
                            },
                    )
            }
        }
    }
}

@Composable
private fun ActiveTypeFilter(
    model: NetBoxModelEntity,
    accent: Color,
    onClear: () -> Unit,
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.forEndpointPath(model.endpointPath),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FilterAlt,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Active filter",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                }
                Text(
                    model.modelLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Only ${model.modelLabel.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "Clear object type filter")
            }
        }
    }
}

@Composable
private fun TypeSuggestionRow(model: NetBoxModelEntity, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(AppIcons.forEndpointPath(model.endpointPath), contentDescription = null)
        },
        headlineContent = { Text(model.modelLabel) },
        supportingContent = { Text("Search only " + model.modelLabel.lowercase()) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchEmptyState(title: String, message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    hit: SearchHit,
    modelLabel: String?,
    icon: ImageVector,
    typeColor: Color,
    thumbnail: SearchThumbnail?,
    assetTag: String?,
    hasAssetTagField: Boolean,
    localImageFile: (SearchThumbnail) -> java.io.File?,
    isRecent: Boolean = false,
    onClick: () -> Unit,
) {
    val localFile = remember(thumbnail) { thumbnail?.let(localImageFile) }

    ListItem(
        leadingContent = {
            if (thumbnail == null) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = typeColor)
                }
            } else {
                RemoteThumbnail(
                    imageUrl = thumbnail.url,
                    contentDescription = hit.display,
                    localFile = localFile,
                    modifier = Modifier.size(56.dp),
                )
            }
        },
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    hit.display,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                ObjectTypeBadge(
                    label = searchObjectTypeLabel(modelLabel, hit.endpointPath),
                    icon = icon,
                    color = typeColor,
                )
            }
        },
        supportingContent = {
            val secondaryLine = hit.secondaryLine?.takeIf(String::isNotBlank)
            val visibleAssetTag = assetTag?.takeIf(String::isNotBlank)
            val matchHint = hit.matchHint?.takeIf { it != secondaryLine }
            if (isRecent || secondaryLine != null || visibleAssetTag != null || hasAssetTagField || matchHint != null) {
                Column {
                    if (isRecent) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text("Recently visited", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    secondaryLine?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (visibleAssetTag != null) AssetTagBadge(visibleAssetTag)
                    else if (hasAssetTagField) MissingAssetTagBadge()
                    matchHint?.let {
                        Text(
                            "Matched $it",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun searchHitKey(hit: SearchHit): String = "${hit.endpointPath.trimEnd('/')}:${hit.id}"

@Composable
private fun ObjectTypeBadge(label: String, icon: ImageVector, color: Color) {
    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
