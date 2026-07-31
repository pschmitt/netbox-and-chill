package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** The universal destinations always reachable regardless of which sidebar item you're on -
 * Settings lives in the sidebar footer instead (see Sidebar.kt), not here. */
enum class BottomTab {
    Dashboard,
    Devices,
    Scan,
}

@Composable
fun NetBoxBottomBar(
    selected: BottomTab?,
    onDashboardClick: () -> Unit,
    onDevicesClick: () -> Unit,
    onScanClick: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == BottomTab.Dashboard,
            onClick = onDashboardClick,
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = selected == BottomTab.Devices,
            onClick = onDevicesClick,
            icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
            label = { Text("Devices") },
        )
        NavigationBarItem(
            selected = selected == BottomTab.Scan,
            onClick = onScanClick,
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
            label = { Text("Scan") },
        )
    }
}
