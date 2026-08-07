package dev.pschmitt.nyetbox.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.nyetbox.data.repository.GestureAction
import dev.pschmitt.nyetbox.data.repository.NavBarItem
import dev.pschmitt.nyetbox.matchesCurrentRoute
import dev.pschmitt.nyetbox.routeForGesture
import dev.pschmitt.nyetbox.ui.navigation.Route

/** The icon for a [GestureAction] - shared with the gesture-shortcut picker in Settings. */
internal fun iconForGestureAction(action: GestureAction): ImageVector =
    when (action) {
        GestureAction.Off -> Icons.Default.Block
        GestureAction.Dashboard -> Icons.Default.Dashboard
        GestureAction.GlobalSearch -> Icons.Default.Search
        GestureAction.Scanner -> Icons.Default.QrCodeScanner
        GestureAction.Settings -> Icons.Default.Info
        GestureAction.Add,
        GestureAction.AddSpecific -> Icons.Default.Add
        GestureAction.Sync -> Icons.Default.Sync
        GestureAction.OfflineOn,
        GestureAction.OfflineOff -> Icons.Default.CloudOff
        GestureAction.SwitchServer -> Icons.Default.SwapHoriz
        GestureAction.DeviceList,
        GestureAction.ListSpecific,
        GestureAction.DetailSpecific -> Icons.Default.Storage
    }

/** The bottom bar's short on-screen label for a slot - falls back to the target's own label. */
private fun shortLabelFor(item: NavBarItem): String =
    when (item.action) {
        GestureAction.Dashboard -> "Home"
        GestureAction.GlobalSearch -> "Search"
        GestureAction.Scanner -> "Scan"
        GestureAction.Add -> "Add"
        GestureAction.Settings -> "Settings"
        else -> item.target?.label ?: item.action.label
    }

@Composable
fun NetBoxBottomBar(onNavigate: (Route) -> Unit) {
    val viewModel: NavBarViewModel = hiltViewModel()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val currentRoute = LocalCurrentRoute.current
    val slots = items.mapNotNull { item ->
        routeForGesture(item.action, item.target)?.let { route -> item to route }
    }
    if (LocalUseNavigationRail.current) {
        NavigationRail {
            slots.forEach { (item, route) ->
                NavigationRailItem(
                    selected = matchesCurrentRoute(currentRoute, route),
                    onClick = { onNavigate(route) },
                    icon = { Icon(iconForGestureAction(item.action), contentDescription = null) },
                    label = { Text(shortLabelFor(item)) },
                )
            }
        }
    } else {
        NavigationBar {
            slots.forEach { (item, route) ->
                NavigationBarItem(
                    selected = matchesCurrentRoute(currentRoute, route),
                    onClick = { onNavigate(route) },
                    icon = { Icon(iconForGestureAction(item.action), contentDescription = null) },
                    label = { Text(shortLabelFor(item)) },
                )
            }
        }
    }
}
