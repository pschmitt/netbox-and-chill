package dev.pschmitt.netboxandchill.ui.generic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import dev.pschmitt.netboxandchill.ui.directory.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericListScreen(
    onObjectClick: (Int) -> Unit,
    onDashboardClick: () -> Unit,
    onDevicesClick: () -> Unit,
    onScanClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: GenericListViewModel = hiltViewModel(),
) {
    val objects by viewModel.objects.collectAsStateWithLifecycle()
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

    Scaffold(
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
                onDevicesClick = onDevicesClick,
                onScanClick = onScanClick,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
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
                    val rowIcon = AppIcons.forAppKey(NetBoxRef.appKeyFromEndpointPath(viewModel.route.endpointPath))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(objects, key = { it.id }) { obj ->
                            ObjectRow(obj = obj, icon = rowIcon, onClick = { onObjectClick(obj.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObjectRow(obj: NetBoxObjectEntity, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(obj.display) },
        supportingContent = obj.secondaryLine?.let { line -> { Text(line) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
