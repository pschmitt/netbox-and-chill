package dev.pschmitt.netboxandchill.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.BookmarkEntity
import dev.pschmitt.netboxandchill.data.db.DashboardStatEntity
import dev.pschmitt.netboxandchill.data.db.ObjectChangeEntity
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import dev.pschmitt.netboxandchill.ui.common.BottomTab
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import dev.pschmitt.netboxandchill.ui.common.SyncIssueCard
import dev.pschmitt.netboxandchill.ui.directory.AppIcons

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
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val changelog by viewModel.changelog.collectAsStateWithLifecycle()
    val conflictCount by viewModel.conflictCount.collectAsStateWithLifecycle()
    val offlineMode by viewModel.offlineMode.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val syncIssue by viewModel.syncIssue.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                    }
                },
            )
        },
        bottomBar = {
            NetBoxBottomBar(
                selected = BottomTab.Dashboard,
                onDashboardClick = {},
                onScanClick = onScanClick,
                onSearchClick = onSearchClick,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val bookmarkTargets =
                bookmarks.mapNotNull { bookmark ->
                    val path = bookmark.targetEndpointPath
                    val id = bookmark.targetId
                    if (path != null && id != null) bookmark.id to (path to id) else null
                }.toMap()
            val changeTargets =
                changelog.mapNotNull { change ->
                    val path = change.targetEndpointPath
                    val id = change.targetId
                    if (path != null && id != null) change.id to (path to id) else null
                }.toMap()

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                syncIssue?.let { issue ->
                    item {
                        SyncIssueCard(issue, onRetry = viewModel::retrySync)
                        Spacer(Modifier.height(16.dp))
                    }
                }
                if (offlineMode) {
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Offline mode", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Showing cached data; network sync is paused",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    Text("Review local and server values", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                item { SectionHeader(Icons.Default.BarChart, "Stats") }
                item {
                    if (stats.isEmpty()) {
                        EmptyHint(isRefreshing, "No stats cached yet - pull to sync")
                    } else {
                        StatsRow(stats, onStatClick)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
                item { GlobalSearchCard(onSearchClick) }
                item { Spacer(Modifier.height(24.dp)) }

                item { SectionHeader(Icons.Default.Bookmark, "Bookmarks") }
                if (bookmarks.isEmpty()) {
                    item { EmptyHint(isRefreshing, "No bookmarks yet") }
                } else {
                    items(bookmarks, key = { "bookmark-${it.id}" }) { bookmark ->
                        BookmarkRow(bookmark) {
                            bookmarkTargets[bookmark.id]?.let { (path, id) -> onNavigateToReference(path, id) }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }

                item { SectionHeader(Icons.Default.History, "Recent changes") }
                if (changelog.isEmpty()) {
                    item { EmptyHint(isRefreshing, "No changes cached yet - pull to sync") }
                } else {
                    items(changelog, key = { "change-${it.id}" }) { change ->
                        ChangeRow(
                            change = change,
                            onClick = {
                                changeTargets[change.id]?.let { (path, id) -> onNavigateToReference(path, id) }
                            },
                            onDiffClick = { onChangeDiffClick(change.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(stats: List<DashboardStatEntity>, onStatClick: (String, String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(stats, key = { it.endpointPath }) { stat ->
            StatTile(stat, onClick = { onStatClick(stat.endpointPath, stat.label) })
        }
    }
}

@Composable
private fun StatTile(stat: DashboardStatEntity, onClick: () -> Unit) {
    // Fixed height too, not just width - the label ("Device Types" vs. "Racks") wraps to a
    // different number of lines depending on its length, which otherwise leaves the cards in a
    // row at different heights.
    ElevatedCard(onClick = onClick, modifier = Modifier.size(110.dp, 136.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.forAppKey(NetBoxRef.appKeyFromEndpointPath(stat.endpointPath)), contentDescription = null)
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
private fun GlobalSearchCard(onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            headlineContent = { Text("Search NetBox") },
            supportingContent = { Text("Find devices, IPs, sites, racks, and more") },
        )
    }
}

@Composable
private fun BookmarkRow(bookmark: BookmarkEntity, onClick: () -> Unit) {
    val hasTarget = bookmark.targetEndpointPath != null && bookmark.targetId != null
    val icon =
        bookmark.targetEndpointPath?.let { AppIcons.forAppKey(NetBoxRef.appKeyFromEndpointPath(it)) }
            ?: Icons.Default.Bookmark
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(bookmark.display) },
        supportingContent = { Text(formatTimestamp(bookmark.created)) },
        modifier = Modifier.clickable(enabled = hasTarget, onClick = onClick),
    )
}

@Composable
private fun ChangeRow(change: ObjectChangeEntity, onClick: () -> Unit, onDiffClick: () -> Unit) {
    val hasTarget = change.targetEndpointPath != null && change.targetId != null
    val (icon, tint) =
        when (change.actionValue) {
            "create" -> Icons.Default.AddCircle to MaterialTheme.colorScheme.primary
            "update" -> Icons.Default.Edit to MaterialTheme.colorScheme.onSurfaceVariant
            "delete" -> Icons.Default.Delete to MaterialTheme.colorScheme.error
            else -> Icons.Default.History to MaterialTheme.colorScheme.onSurfaceVariant
        }
    ListItem(
        leadingContent = { Icon(icon, contentDescription = change.actionLabel, tint = tint) },
        headlineContent = { Text(change.objectRepr) },
        supportingContent = {
            Text("${change.actionLabel} by ${change.userDisplay} · ${formatTimestamp(change.time)}")
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
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyHint(isRefreshing: Boolean, idleText: String) {
    Text(
        if (isRefreshing) "Loading…" else idleText,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** "2026-07-25T16:33:05.946712Z" -> "2026-07-25 16:33" - a first-pass, good-enough human format;
 * no timezone conversion, matches how timestamps are shown elsewhere in the app (e.g. Journal
 * entries) - just raw-ish ISO trimmed to the minute. */
private fun formatTimestamp(iso: String): String = iso.take(16).replace('T', ' ')
