package dev.pschmitt.nyetbox.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.nyetbox.data.db.DeviceEntity
import dev.pschmitt.nyetbox.data.db.DeviceTypeEntity
import dev.pschmitt.nyetbox.data.db.NetBoxModelEntity
import dev.pschmitt.nyetbox.data.repository.parseGlobalSearchQuery
import dev.pschmitt.nyetbox.data.repository.SearchHit
import dev.pschmitt.nyetbox.data.repository.GlobalSearchRepository
import dev.pschmitt.nyetbox.data.repository.ThemeAccent
import dev.pschmitt.nyetbox.ui.common.BottomTab
import dev.pschmitt.nyetbox.ui.common.AssetTagBadge
import dev.pschmitt.nyetbox.ui.common.MissingAssetTagBadge
import dev.pschmitt.nyetbox.ui.common.NetBoxBottomBar
import dev.pschmitt.nyetbox.ui.common.NetBoxResponsiveScaffold
import dev.pschmitt.nyetbox.ui.common.RemoteThumbnail
import dev.pschmitt.nyetbox.ui.common.SearchHighlightedText
import dev.pschmitt.nyetbox.ui.common.SearchQueryVisualTransformation
import dev.pschmitt.nyetbox.ui.common.visualColorForEndpointPath
import dev.pschmitt.nyetbox.ui.directory.AppIcons

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
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = {
                            Text(selectionPrompt ?: "Search all NetBox objects")
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        visualTransformation =
                            SearchQueryVisualTransformation(MaterialTheme.colorScheme.primary),
                        modifier =
                            Modifier.fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("e2e-global-search"),
                        shape = RoundedCornerShape(28.dp),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
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
                    RecentSearchList(
                        recentResults = recentResults,
                        modelsByEndpointPath = modelsByEndpointPath,
                        devicesById = devicesById,
                        deviceTypesById = deviceTypesById,
                        objectTypeAccents = objectTypeAccents,
                        localImageFile = viewModel::localImageFile,
                        onResultClick = onResultClick,
                    )
                typeFilter == null && query.isBlank() ->
                    SearchEmptyState(
                        title = "Search your NetBox",
                        message = "Find devices, IP addresses, racks, circuits, and more",
                    )
                typeSuggestions.isNotEmpty() || results.isNotEmpty() || typeFilter != null ->
                    SearchResultsContent(
                        query = query,
                        typeFilter = typeFilter,
                        typeSuggestions = typeSuggestions,
                        results = results,
                        recentKeys = recentKeys,
                        modelsByEndpointPath = modelsByEndpointPath,
                        devicesById = devicesById,
                        deviceTypesById = deviceTypesById,
                        objectTypeAccents = objectTypeAccents,
                        localImageFile = viewModel::localImageFile,
                        onSelectType = viewModel::selectType,
                        onClearTypeFilter = viewModel::clearTypeFilter,
                        onResultClick = onResultClick,
                        isRefreshing = isRefreshing,
                    )
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
private fun RecentSearchList(
    recentResults: List<SearchHit>,
    modelsByEndpointPath: Map<String, NetBoxModelEntity>,
    devicesById: Map<Int, DeviceEntity>,
    deviceTypesById: Map<Int, DeviceTypeEntity>,
    objectTypeAccents: Map<String, ThemeAccent>,
    localImageFile: (SearchThumbnail) -> java.io.File?,
    onResultClick: (endpointPath: String, id: Int, display: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "recent-heading") {
            SearchSectionHeader(
                icon = Icons.Default.History,
                title = "Recently visited",
                subtitle = "Pick up where you left off",
                count = recentResults.size,
            )
        }
        items(recentResults, key = { "recent-${it.endpointPath}-${it.id}" }) { hit ->
            SearchResultRow(
                hit = hit,
                modelLabel = modelsByEndpointPath[hit.endpointPath]?.modelLabel,
                icon = AppIcons.forEndpointPath(hit.endpointPath),
                typeColor =
                    visualColorForEndpointPath(
                        hit.endpointPath,
                        objectTypeAccents[hit.endpointPath.trim('/')],
                        MaterialTheme.colorScheme,
                    ),
                thumbnail = searchThumbnailFor(hit, devicesById, deviceTypesById),
                assetTag = searchAssetTagFor(hit, devicesById),
                hasAssetTagField = searchHasAssetTagField(hit, devicesById),
                localImageFile = localImageFile,
                isRecent = true,
                onClick = { onResultClick(hit.endpointPath, hit.id, hit.display) },
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    typeFilter: NetBoxModelEntity?,
    typeSuggestions: List<NetBoxModelEntity>,
    results: List<SearchHit>,
    recentKeys: Set<String>,
    modelsByEndpointPath: Map<String, NetBoxModelEntity>,
    devicesById: Map<Int, DeviceEntity>,
    deviceTypesById: Map<Int, DeviceTypeEntity>,
    objectTypeAccents: Map<String, ThemeAccent>,
    localImageFile: (SearchThumbnail) -> java.io.File?,
    onSelectType: (NetBoxModelEntity) -> Unit,
    onClearTypeFilter: () -> Unit,
    onResultClick: (endpointPath: String, id: Int, display: String) -> Unit,
    isRefreshing: Boolean,
) {
    val highlightQuery = remember(query) { parseGlobalSearchQuery(query).networkQuery }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                    onClear = onClearTypeFilter,
                )
            }
        }
        if (typeSuggestions.isNotEmpty()) {
            item(key = "type-filter-heading") {
                SearchSectionHeader(
                    icon = Icons.Default.FilterAlt,
                    title = "Filter by type",
                    subtitle = "Search one NetBox collection",
                )
            }
            item(key = "type-suggestions") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(
                        typeSuggestions,
                        key = { "type-suggestion-${it.endpointPath}" },
                    ) { model ->
                        TypeSuggestionChip(model = model, onClick = { onSelectType(model) })
                    }
                }
            }
        }
        if (results.isNotEmpty()) {
            item(key = "search-results-heading") {
                SearchSectionHeader(
                    icon = Icons.Default.Search,
                    title = if (typeFilter == null) "Matches" else "Matches in ${typeFilter.modelLabel}",
                    subtitle = "Cached results update as you type",
                    count = results.size,
                )
            }
            items(results, key = { "${it.endpointPath}-${it.id}" }) { hit ->
                SearchResultRow(
                    hit = hit,
                    modelLabel = modelsByEndpointPath[hit.endpointPath]?.modelLabel,
                    icon = AppIcons.forEndpointPath(hit.endpointPath),
                    typeColor =
                        visualColorForEndpointPath(
                            hit.endpointPath,
                            objectTypeAccents[hit.endpointPath.trim('/')],
                            MaterialTheme.colorScheme,
                        ),
                    thumbnail = searchThumbnailFor(hit, devicesById, deviceTypesById),
                    assetTag = searchAssetTagFor(hit, devicesById),
                    hasAssetTagField = searchHasAssetTagField(hit, devicesById),
                    localImageFile = localImageFile,
                    highlightQuery = highlightQuery,
                    isRecent = searchHitKey(hit) in recentKeys,
                    onClick = { onResultClick(hit.endpointPath, hit.id, hit.display) },
                )
            }
        } else if (!isRefreshing && typeFilter != null) {
            item(key = "no-filtered-results") {
                SearchEmptyState(
                    title = "No matches",
                    message = "Try another query or remove the ${typeFilter.modelLabel.lowercase()} filter",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.FilterAlt,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Text("Scope", style = MaterialTheme.typography.labelLarge, color = accent)
        FilterChip(
            selected = true,
            onClick = onClear,
            label = {
                Text(model.modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = {
                Icon(AppIcons.forEndpointPath(model.endpointPath), contentDescription = null)
            },
            trailingIcon = {
                Icon(Icons.Default.Clear, contentDescription = "Clear object type filter")
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TypeSuggestionChip(model: NetBoxModelEntity, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(model.modelLabel) },
        leadingIcon = {
            Icon(AppIcons.forEndpointPath(model.endpointPath), contentDescription = null)
        },
        trailingIcon = {
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
        },
    )
}

@Composable
private fun SearchSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    count: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        count?.takeIf { it > 0 }?.let {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    it.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SearchEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
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
    highlightQuery: String = "",
    isRecent: Boolean = false,
    onClick: () -> Unit,
) {
    val localFile = remember(thumbnail) { thumbnail?.let(localImageFile) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (thumbnail == null) {
                Surface(
                    color = typeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = typeColor, modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                RemoteThumbnail(
                    imageUrl = thumbnail.url,
                    contentDescription = hit.display,
                    localFile = localFile,
                    modifier = Modifier.size(64.dp),
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchHighlightedText(
                        value = hit.display,
                        query = highlightQuery,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp),
                    )
                }
                hit.secondaryLine?.takeIf(String::isNotBlank)?.let {
                    SearchHighlightedText(
                        value = it,
                        query = highlightQuery,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ObjectTypeBadge(
                        label = searchObjectTypeLabel(modelLabel, hit.endpointPath),
                        icon = icon,
                        color = typeColor,
                    )
                    if (isRecent) {
                        RecentBadge()
                    }
                }
                if (assetTag?.isNotBlank() == true) {
                    AssetTagBadge(
                        assetTag,
                        modifier = Modifier.padding(top = 6.dp),
                        highlightQuery = highlightQuery,
                    )
                } else if (hasAssetTagField) {
                    MissingAssetTagBadge(Modifier.padding(top = 6.dp))
                }
                hit.matchHint?.takeIf { it != hit.secondaryLine }?.let {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.FilterAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        SearchHighlightedText(
                            value = "Matched $it",
                            query = highlightQuery,
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                "Recent",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun searchThumbnailFor(
    hit: SearchHit,
    devicesById: Map<Int, DeviceEntity>,
    deviceTypesById: Map<Int, DeviceTypeEntity>,
): SearchThumbnail? =
    when (hit.endpointPath) {
        GlobalSearchRepository.DEVICE_TYPES_ENDPOINT_PATH ->
            deviceTypesById[hit.id]?.frontImageUrl?.takeIf(String::isNotBlank)?.let { url ->
                SearchThumbnail(url, "device-type-${hit.id}-front")
            }
        GlobalSearchRepository.DEVICES_ENDPOINT_PATH ->
            devicesById[hit.id]?.deviceTypeId?.let { deviceTypeId ->
                deviceTypesById[deviceTypeId]?.frontImageUrl?.takeIf(String::isNotBlank)?.let { url ->
                    SearchThumbnail(url, "device-type-$deviceTypeId-front")
                }
            }
        else -> null
    }

private fun searchAssetTagFor(
    hit: SearchHit,
    devicesById: Map<Int, DeviceEntity>,
): String? =
    hit.assetTag
        ?: if (hit.endpointPath == GlobalSearchRepository.DEVICES_ENDPOINT_PATH) {
            devicesById[hit.id]?.assetTag
        } else {
            null
        }

private fun searchHasAssetTagField(
    hit: SearchHit,
    devicesById: Map<Int, DeviceEntity>,
): Boolean =
    hit.hasAssetTagField ||
        (hit.endpointPath == GlobalSearchRepository.DEVICES_ENDPOINT_PATH && devicesById[hit.id] != null)

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
