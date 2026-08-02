package dev.pschmitt.netboxandchill.ui.generic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import dev.pschmitt.netboxandchill.data.schema.assetTagStateFromRawJson
import dev.pschmitt.netboxandchill.ui.common.AssetTagBadge
import dev.pschmitt.netboxandchill.ui.common.MissingAssetTagBadge
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import dev.pschmitt.netboxandchill.ui.common.NetBoxResponsiveScaffold
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.detailAccentFor
import dev.pschmitt.netboxandchill.ui.directory.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericListScreen(
    onObjectClick: (Int) -> Unit,
    onCreateClick: () -> Unit,
    onDashboardClick: () -> Unit,
    onScanClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    viewModel: GenericListViewModel = hiltViewModel(),
) {
    val objects by viewModel.objects.collectAsStateWithLifecycle()
    val deviceTypeImages by viewModel.deviceTypeImages.collectAsStateWithLifecycle()
    val objectTypeAccent by viewModel.objectTypeAccent.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text(viewModel.route.label) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search all NetBox objects")
                    }
                },
            )
        },
        bottomBar = {
            NetBoxBottomBar(
                // None of the fixed bottom-nav tabs represents "browsing this particular model" -
                // that's what the sidebar/drawer is for.
                selected = null,
                onDashboardClick = onDashboardClick,
                onSearchClick = onSearchClick,
                onScanClick = onScanClick,
                onAddClick = onAddClick,
                onSettingsClick = onSettingsClick,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            PullToRefreshBox(
                // Keep the gesture active, but don't duplicate the global sync progress indicator.
                isRefreshing = false,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search ${viewModel.route.label.lowercase()}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                if (objects.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (isRefreshing) "Loading…" else "Nothing cached yet - pull to sync",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val rowIcon =
                        AppIcons.forAppKey(
                            NetBoxRef.appKeyFromEndpointPath(viewModel.route.endpointPath)
                        )
                    val rowColor =
                        MaterialTheme.colorScheme.detailAccentFor(
                            viewModel.route.endpointPath,
                            objectTypeAccent,
                        )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(objects, key = { it.id }) { obj ->
                            ObjectRow(
                                obj = obj,
                                icon = rowIcon,
                                iconTint = rowColor,
                                frontImageUrl = deviceTypeImages[obj.id]?.frontImageUrl,
                                localImageFile = viewModel::localImageFile,
                                onClick = { onObjectClick(obj.id) },
                            )
                        }
                    }
                }
                }
            }
            FloatingActionButton(
                onClick = onCreateClick,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create ${viewModel.route.label}")
            }
        }
    }
}

@Composable
private fun ObjectRow(
    obj: NetBoxObjectEntity,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    frontImageUrl: String?,
    localImageFile: (String, String) -> java.io.File?,
    onClick: () -> Unit,
) {
    val assetTag = remember(obj.json) { assetTagStateFromRawJson(obj.json) }
    val localFile =
        remember(frontImageUrl, obj.id) {
            frontImageUrl?.let { localImageFile(it, "device-type-${obj.id}-front") }
        }

    ListItem(
        leadingContent = {
            if (frontImageUrl.isNullOrBlank()) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
            } else {
                RemoteThumbnail(
                    imageUrl = frontImageUrl,
                    contentDescription = obj.display,
                    localFile = localFile,
                    modifier = Modifier.size(72.dp),
                )
            }
        },
        headlineContent = { Text(obj.display) },
        supportingContent = {
            val subtitle = obj.secondaryLine?.takeIf(String::isNotBlank)
            if (subtitle != null || assetTag.hasField) {
                Column {
                    subtitle?.let { Text(it) }
                    if (assetTag.value != null) AssetTagBadge(assetTag.value)
                    else if (assetTag.hasField) MissingAssetTagBadge()
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
