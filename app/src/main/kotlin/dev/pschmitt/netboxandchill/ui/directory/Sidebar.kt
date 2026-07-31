package dev.pschmitt.netboxandchill.ui.directory

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.BuildConfig
import dev.pschmitt.netboxandchill.R
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity

private const val DEVICES_PATH = "api/dcim/devices/"

@Composable
fun Sidebar(
    onDeviceListClick: () -> Unit,
    onModelClick: (NetBoxModelEntity) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DirectoryViewModel = hiltViewModel(),
) {
    val modelsByApp by viewModel.modelsByApp.collectAsStateWithLifecycle()
    val pinnedModels by viewModel.pinnedModels.collectAsStateWithLifecycle()
    val pinnedPaths by viewModel.settingsRepository.pinnedModelPaths.collectAsStateWithLifecycle()
    val credentials by viewModel.settingsRepository.credentials.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    // Collapsed by default, like the NetBox web UI's sidebar - matches app keys ("dcim",
    // "plugins/netbox-documents", ...), not the humanized labels.
    var expandedApps by remember { mutableStateOf(emptySet<String>()) }

    val filteredModelsByApp =
        if (searchQuery.isBlank()) modelsByApp
        else
            modelsByApp
                .mapValues { (_, models) -> models.filter { it.modelLabel.contains(searchQuery, ignoreCase = true) } }
                .filterValues { it.isNotEmpty() }

    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
        Column(Modifier.fillMaxHeight()) {
            LazyColumn(Modifier.weight(1f)) {
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
                    val appKey = models.first().appKey
                    // Searching implicitly expands every matching section - no point collapsing
                    // search results the user is actively looking for.
                    val isExpanded = searchQuery.isNotBlank() || appKey in expandedApps
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        expandedApps =
                                            if (appKey in expandedApps) expandedApps - appKey
                                            else expandedApps + appKey
                                    }
                                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                        ) {
                            Icon(
                                AppIcons.forAppKey(appKey),
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
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (isExpanded) {
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
            HorizontalDivider()
            SidebarFooter(
                appVersion = BuildConfig.VERSION_NAME,
                netboxUrl = credentials.baseUrl,
                onSettingsClick = onSettingsClick,
            )
        }
    }
}

/** Static (non-scrolling) footer pinned to the bottom of the drawer. */
@Composable
private fun SidebarFooter(appVersion: String, netboxUrl: String, onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    // ic_launcher is an <adaptive-icon> (background + foreground layers) - painterResource() only
    // supports VectorDrawables and raster assets, not that wrapper format, and throws at runtime.
    // Rendering it through a Drawable -> Bitmap first works for any drawable type.
    val appIconBitmap =
        remember { ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()?.asImageBitmap() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (appIconBitmap != null) {
            Image(
                bitmap = appIconBitmap,
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Icon(
                AppIcons.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Version $appVersion", style = MaterialTheme.typography.labelMedium)
            if (netboxUrl.isNotBlank()) {
                Text(
                    netboxUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
