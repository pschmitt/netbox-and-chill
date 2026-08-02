package dev.pschmitt.netboxandchill.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.ui.common.AssetTagBadge
import dev.pschmitt.netboxandchill.ui.common.MissingAssetTagBadge
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import dev.pschmitt.netboxandchill.ui.common.NetBoxResponsiveScaffold
import dev.pschmitt.netboxandchill.ui.common.RemoteThumbnail
import dev.pschmitt.netboxandchill.ui.common.StatusChip
import dev.pschmitt.netboxandchill.ui.common.detailAccentFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onDeviceClick: (Int) -> Unit,
    onCreateClick: () -> Unit,
    onDashboardClick: () -> Unit,
    onScanClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    viewModel: DeviceListViewModel = hiltViewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val deviceTypeImages by viewModel.deviceTypeImages.collectAsStateWithLifecycle()
    val objectTypeAccent by viewModel.objectTypeAccent.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
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
                title = { Text("Devices") },
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
                    label = { Text("Search devices") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                if (devices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (isRefreshing) "Loading devices…"
                            else "No devices cached yet - pull to sync",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val rowColor =
                        MaterialTheme.colorScheme.detailAccentFor(
                            "api/dcim/devices/",
                            objectTypeAccent,
                        )
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(
                            items = devices,
                            key = { it.id },
                            contentType = { "device-row" },
                        ) { device ->
                            DeviceRow(
                                device = device,
                                frontImageUrl =
                                    deviceTypeImages[device.deviceTypeId]?.frontImageUrl,
                                fallbackTint = rowColor,
                                localImageFile = viewModel::localImageFile,
                                onClick = { onDeviceClick(device.id) },
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
                Icon(Icons.Default.Add, contentDescription = "Create device")
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceEntity,
    frontImageUrl: String?,
    localImageFile: (String, String) -> java.io.File?,
    fallbackTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val localFile =
        remember(frontImageUrl, device.deviceTypeId) {
            frontImageUrl?.let { localImageFile(it, "device-type-${device.deviceTypeId}-front") }
        }

    ListItem(
        leadingContent = {
            RemoteThumbnail(
                imageUrl = frontImageUrl,
                contentDescription = device.deviceTypeModel,
                localFile = localFile,
                modifier = Modifier.size(72.dp),
                fallbackTint = fallbackTint,
            )
        },
        headlineContent = { Text(device.name) },
        supportingContent = {
            val subtitle =
                listOfNotNull(device.siteName, device.deviceTypeModel).joinToString(" · ")
            val assetTag = device.assetTag?.takeIf(String::isNotBlank)
            if (subtitle.isNotBlank() || assetTag != null || device.assetTag.isNullOrBlank()) {
                Column {
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (assetTag != null) AssetTagBadge(assetTag) else MissingAssetTagBadge()
                }
            }
        },
        trailingContent = { StatusChip(label = device.statusLabel, value = device.statusValue) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
