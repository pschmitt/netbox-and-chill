package dev.pschmitt.netboxandchill.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.BookmarkEntity
import dev.pschmitt.netboxandchill.data.db.DashboardStatEntity
import dev.pschmitt.netboxandchill.data.db.ObjectChangeEntity
import dev.pschmitt.netboxandchill.data.db.NewsItemEntity
import dev.pschmitt.netboxandchill.ui.common.BottomTab
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import dev.pschmitt.netboxandchill.ui.common.NetBoxResponsiveScaffold
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.detailAccentFor
import dev.pschmitt.netboxandchill.ui.common.SectionReorderState
import dev.pschmitt.netboxandchill.ui.common.SyncIssueCard
import dev.pschmitt.netboxandchill.ui.common.formatNetBoxDateTime
import dev.pschmitt.netboxandchill.ui.common.rememberReorderWiggle
import dev.pschmitt.netboxandchill.ui.common.rememberSectionReorderState
import dev.pschmitt.netboxandchill.ui.common.sectionDragOffset
import dev.pschmitt.netboxandchill.ui.common.sectionReorderGesture
import dev.pschmitt.netboxandchill.ui.directory.AppIcons

internal fun shouldShowSyncIssue(offlineMode: Boolean): Boolean = !offlineMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToReference: (endpointPath: String, id: Int) -> Unit,
    onStatClick: (endpointPath: String, label: String) -> Unit,
    onChangeDiffClick: (changeId: Int) -> Unit,
    onConflictsClick: () -> Unit,
    onPendingChangesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val changelog by viewModel.changelog.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()
    val devicesById by viewModel.devicesById.collectAsStateWithLifecycle()
    val deviceTypesById by viewModel.deviceTypesById.collectAsStateWithLifecycle()
    val conflictCount by viewModel.conflictCount.collectAsStateWithLifecycle()
    val pendingChangeCount by viewModel.pendingChangeCount.collectAsStateWithLifecycle()
    val offlineMode by viewModel.offlineMode.collectAsStateWithLifecycle()
    val lastSuccessfulSyncAt by viewModel.lastSuccessfulSyncAt.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val syncIssue by viewModel.syncIssue.collectAsStateWithLifecycle()
    val dashboardSavedOrder by viewModel.dashboardSectionOrder.collectAsStateWithLifecycle()
    val hiddenDashboardSections by viewModel.hiddenDashboardSections.collectAsStateWithLifecycle()
    val objectTypeAccents by viewModel.objectTypeAccents.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dashboardSections =
        orderedDashboardSections(
            savedOrder = dashboardSavedOrder,
            hidden = hiddenDashboardSections,
        )
    val dashboardOrder = dashboardSections.map { it.key }
    val dashboardListState = rememberLazyListState()
    val dashboardReorderState = rememberSectionReorderState()
    var dashboardReorderMode by remember { mutableStateOf(false) }
    var showDashboardVisibilityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    NetBoxResponsiveScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                    }
                },
                actions = {
                    if (dashboardReorderMode) {
                        IconButton(onClick = { showDashboardVisibilityDialog = true }) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Show or hide dashboard sections",
                            )
                        }
                        IconButton(onClick = { dashboardReorderMode = false }) {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = "Finish organizing dashboard",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NetBoxBottomBar(
                selected = BottomTab.Dashboard,
                onDashboardClick = {},
                onSearchClick = onSearchClick,
                onScanClick = onScanClick,
                onAddClick = onAddClick,
                onSettingsClick = onSettingsClick,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            // Sync has a global progress bar and Android notification; avoid the large circular
            // indicator moving over the dashboard while that background work is running.
            isRefreshing = false,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val bookmarkTargets =
                bookmarks
                    .mapNotNull { bookmark ->
                        val path = bookmark.targetEndpointPath
                        val id = bookmark.targetId
                        if (path != null && id != null) bookmark.id to (path to id) else null
                    }
                    .toMap()
            val changeTargets =
                changelog
                    .mapNotNull { change ->
                        val path = change.targetEndpointPath
                        val id = change.targetId
                        if (path != null && id != null) change.id to (path to id) else null
                    }
                    .toMap()

            LazyColumn(
                state = dashboardListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                if (shouldShowSyncIssue(offlineMode)) {
                    syncIssue?.let { issue ->
                        item {
                            SyncIssueCard(issue, onRetry = viewModel::retrySync)
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                if (offlineMode) {
                    item {
                        ElevatedCard(
                            onClick = onPendingChangesClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Offline mode",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "Showing cached data; network sync is paused",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        lastSuccessfulSyncAt?.let { timestamp ->
                                            "Last sync: ${formatNetBoxDateTime(java.time.Instant.ofEpochMilli(timestamp).toString())}"
                                        } ?: "Last sync: not completed yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        if (pendingChangeCount == 0) {
                                            "No pending local changes"
                                        } else {
                                            "$pendingChangeCount pending change${if (pendingChangeCount == 1) "" else "s"} · Tap to review"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
                if (conflictCount > 0) {
                    item {
                        ElevatedCard(
                            onClick = onConflictsClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "$conflictCount edit conflict${if (conflictCount == 1) "" else "s"}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "Review local and server values",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                items(dashboardSections, key = { "dashboard-section-${it.key}" }) { section ->
                    DashboardSectionContainer(
                        section = section,
                        reorderMode = dashboardReorderMode,
                        order = dashboardOrder,
                        listState = dashboardListState,
                        reorderState = dashboardReorderState,
                        onEnterReorder = { dashboardReorderMode = true },
                        onHide = {
                            viewModel.setDashboardSectionHidden(section.key, true)
                            dashboardReorderMode = true
                        },
                        onOrderChanged = viewModel::setDashboardSectionOrder,
                    ) {
                        when (section) {
                            DashboardSection.Stats -> {
                                if (stats.isEmpty()) {
                                    EmptyHint(isRefreshing, "No stats cached yet - pull to sync")
                                } else {
                                    StatsRow(stats, objectTypeAccents, onStatClick)
                                }
                            }
                            DashboardSection.Search ->
                                GlobalSearchCard(
                                    onClick = onSearchClick,
                                    reorderMode = dashboardReorderMode,
                                    onLongPress = { dashboardReorderMode = true },
                                    onHide = {
                                        viewModel.setDashboardSectionHidden(section.key, true)
                                        dashboardReorderMode = true
                                    },
                                )
                            DashboardSection.News -> {
                                if (news.isEmpty()) {
                                    EmptyHint(isRefreshing, "No news cached yet - pull to sync")
                                } else {
                                    news.forEach { newsItem ->
                                        NewsRow(newsItem) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(newsItem.link))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            DashboardSection.Bookmarks -> {
                                if (bookmarks.isEmpty()) {
                                    EmptyHint(isRefreshing, "No bookmarks yet")
                                } else {
                                    bookmarks.forEach { bookmark ->
                                        val thumbnail =
                                            bookmark.targetEndpointPath?.let { path ->
                                                bookmark.targetId?.let { id ->
                                                    viewModel.thumbnailFor(
                                                        path,
                                                        id,
                                                        devicesById,
                                                        deviceTypesById,
                                                    )
                                                }
                                            }
                                        BookmarkRow(
                                            bookmark = bookmark,
                                            thumbnail = thumbnail,
                                            typeColor = bookmark.targetEndpointPath?.let { path ->
                                                MaterialTheme.colorScheme.detailAccentFor(
                                                    path,
                                                    objectTypeAccents[path.trim('/')],
                                                )
                                            } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                            localImageFile = viewModel::localImageFile,
                                        ) {
                                            bookmarkTargets[bookmark.id]?.let { (path, id) ->
                                                onNavigateToReference(path, id)
                                            }
                                        }
                                    }
                                }
                            }
                            DashboardSection.RecentChanges -> {
                                if (changelog.isEmpty()) {
                                    EmptyHint(isRefreshing, "No changes cached yet - pull to sync")
                                } else {
                                    changelog.forEach { change ->
                                        val thumbnail =
                                            change.targetEndpointPath?.let { path ->
                                                change.targetId?.let { id ->
                                                    viewModel.thumbnailFor(
                                                        path,
                                                        id,
                                                        devicesById,
                                                        deviceTypesById,
                                                    )
                                                }
                                            }
                                        ChangeRow(
                                            change = change,
                                            thumbnail = thumbnail,
                                            typeColor = change.targetEndpointPath?.let { path ->
                                                MaterialTheme.colorScheme.detailAccentFor(
                                                    path,
                                                    objectTypeAccents[path.trim('/')],
                                                )
                                            } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                            localImageFile = viewModel::localImageFile,
                                            onClick = {
                                                changeTargets[change.id]?.let { (path, id) ->
                                                    onNavigateToReference(path, id)
                                                }
                                            },
                                            onDiffClick = { onChangeDiffClick(change.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDashboardVisibilityDialog) {
        DashboardVisibilityDialog(
            hidden = hiddenDashboardSections,
            onToggle = { key, hidden -> viewModel.setDashboardSectionHidden(key, hidden) },
            onDismiss = { showDashboardVisibilityDialog = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardSectionContainer(
    section: DashboardSection,
    reorderMode: Boolean,
    order: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    reorderState: SectionReorderState,
    onEnterReorder: () -> Unit,
    onHide: () -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .sectionDragOffset("dashboard-section-${section.key}", reorderState)
                .sectionReorderGesture(
                    enabled = reorderMode,
                    key = "dashboard-section-${section.key}",
                    order = order.map { "dashboard-section-$it" },
                    listState = listState,
                    state = reorderState,
                    onOrderChanged = { changed ->
                        onOrderChanged(changed.map { it.removePrefix("dashboard-section-") })
                    },
                ),
    ) {
        if (section != DashboardSection.Search) {
            DashboardSectionHeader(
                section = section,
                reorderMode = reorderMode,
                onLongPress = onEnterReorder,
                onHide = onHide,
            )
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardSectionHeader(
    section: DashboardSection,
    reorderMode: Boolean,
    onLongPress: () -> Unit,
    onHide: () -> Unit,
) {
    val wiggle = rememberReorderWiggle(reorderMode)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .graphicsLayer { rotationZ = wiggle }
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(bottom = 8.dp),
    ) {
        Icon(
            when (section) {
                DashboardSection.Stats -> Icons.Default.BarChart
                DashboardSection.News -> Icons.Default.Newspaper
                DashboardSection.Bookmarks -> Icons.Default.Bookmark
                DashboardSection.RecentChanges -> Icons.Default.History
                DashboardSection.Search -> Icons.Default.Search
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            section.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (reorderMode) {
            IconButton(onClick = onHide) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Hide ${section.title}")
            }
        }
    }
}

@Composable
private fun DashboardVisibilityDialog(
    hidden: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
        title = { Text("Dashboard sections") },
        text = {
            Column {
                DashboardSection.entries.forEach { section ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = section.key !in hidden,
                            onCheckedChange = { visible -> onToggle(section.key, !visible) },
                        )
                        Text(section.title)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun NewsRow(item: NewsItemEntity, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Default.Newspaper, contentDescription = null) },
        headlineContent = {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                item.summary?.let {
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (item.publishedAt > 0) {
                    Text(
                        formatTimestamp(
                            java.time.Instant.ofEpochMilli(item.publishedAt).toString()
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open news article")
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun StatsRow(
    stats: List<DashboardStatEntity>,
    objectTypeAccents: Map<String, dev.pschmitt.netboxandchill.data.repository.ThemeAccent>,
    onStatClick: (String, String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(stats, key = { it.endpointPath }) { stat ->
            StatTile(
                stat,
                typeColor =
                    MaterialTheme.colorScheme.detailAccentFor(
                        stat.endpointPath,
                        objectTypeAccents[stat.endpointPath.trim('/')],
                    ),
                onClick = { onStatClick(stat.endpointPath, stat.label) },
            )
        }
    }
}

@Composable
private fun StatTile(
    stat: DashboardStatEntity,
    typeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    // Fixed height too, not just width - the label ("Device Types" vs. "Racks") wraps to a
    // different number of lines depending on its length, which otherwise leaves the cards in a
    // row at different heights.
    ElevatedCard(onClick = onClick, modifier = Modifier.size(110.dp, 136.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                AppIcons.forEndpointPath(stat.endpointPath),
                contentDescription = null,
                tint = typeColor,
            )
            Spacer(Modifier.height(8.dp))
            Text(stat.count.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(
                stat.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GlobalSearchCard(
    onClick: () -> Unit,
    reorderMode: Boolean,
    onLongPress: () -> Unit,
    onHide: () -> Unit,
) {
    val wiggle = rememberReorderWiggle(reorderMode)
    ElevatedCard(
        colors =
            androidx.compose.material3.CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
        modifier =
            Modifier.fillMaxWidth()
                .testTag("e2e-search-card")
                .graphicsLayer { rotationZ = wiggle }
                .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        ListItem(
            colors =
                ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    supportingColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            leadingContent = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            headlineContent = {
                Text("Search NetBox", style = MaterialTheme.typography.titleLarge)
            },
            supportingContent = { Text("Find devices, IPs, sites, racks, and more") },
            trailingContent = {
                if (reorderMode) {
                    IconButton(onClick = onHide) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = "Hide Search NetBox",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    thumbnail: DashboardThumbnail?,
    typeColor: androidx.compose.ui.graphics.Color,
    localImageFile: (DashboardThumbnail) -> java.io.File?,
    onClick: () -> Unit,
) {
    val hasTarget = bookmark.targetEndpointPath != null && bookmark.targetId != null
    val icon =
        bookmark.targetEndpointPath?.let {
            AppIcons.forEndpointPath(it)
        } ?: Icons.Default.Bookmark
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
                    contentDescription = bookmark.display,
                    localFile = localFile,
                    modifier = Modifier.size(56.dp),
                    fallbackTint = typeColor,
                )
            }
        },
        headlineContent = { Text(bookmark.display) },
        supportingContent = { Text(formatTimestamp(bookmark.created)) },
        modifier = Modifier.clickable(enabled = hasTarget, onClick = onClick),
    )
}

@Composable
private fun ChangeRow(
    change: ObjectChangeEntity,
    thumbnail: DashboardThumbnail?,
    typeColor: androidx.compose.ui.graphics.Color,
    localImageFile: (DashboardThumbnail) -> java.io.File?,
    onClick: () -> Unit,
    onDiffClick: () -> Unit,
) {
    val hasTarget = change.targetEndpointPath != null && change.targetId != null
    val icon =
        when (change.actionValue) {
            "create" -> Icons.Default.AddCircle
            "update" -> Icons.Default.Edit
            "delete" -> Icons.Default.Delete
            else -> Icons.Default.History
        }
    val localFile = remember(thumbnail) { thumbnail?.let(localImageFile) }
    ListItem(
        leadingContent = {
            if (thumbnail == null) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = change.actionLabel, tint = typeColor)
                }
            } else {
                RemoteThumbnail(
                    imageUrl = thumbnail.url,
                    contentDescription = change.objectRepr,
                    localFile = localFile,
                    modifier = Modifier.size(56.dp),
                    fallbackTint = typeColor,
                )
            }
        },
        headlineContent = { Text(change.objectRepr) },
        supportingContent = {
            Column {
                Text("${change.actionLabel} by ${change.userDisplay}")
                Text(
                    formatTimestamp(change.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        // A separate affordance from the row tap (which navigates to the object's *current*
        // state) - the diff view shows what this specific change actually did, which the user
        // explicitly asked for as its own destination rather than folded into the object page.
        trailingContent = {
            IconButton(onClick = onDiffClick) {
                Icon(Icons.Default.Difference, contentDescription = "View change diff")
            }
        },
        modifier = Modifier.clickable(enabled = hasTarget, onClick = onClick),
    )
}

@Composable
private fun EmptyHint(isRefreshing: Boolean, idleText: String) {
    Text(
        if (isRefreshing) "Loading…" else idleText,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/**
 * "2026-07-25T16:33:05.946712Z" -> "2026-07-25 16:33" - a first-pass, good-enough human format; no
 * timezone conversion, matches how timestamps are shown elsewhere in the app (e.g. Journal
 * entries) - just raw-ish ISO trimmed to the minute.
 */
private fun formatTimestamp(iso: String): String = formatNetBoxDateTime(iso)
