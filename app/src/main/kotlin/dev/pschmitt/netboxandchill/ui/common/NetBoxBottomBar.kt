package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** The universal destinations always reachable regardless of which sidebar item you're on. */
enum class BottomTab {
    Dashboard,
    Search,
    Scan,
    Settings,
}

@Composable
fun NetBoxBottomBar(
    selected: BottomTab?,
    onDashboardClick: () -> Unit,
    onSearchClick: () -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == BottomTab.Dashboard,
            onClick = onDashboardClick,
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = selected == BottomTab.Search,
            onClick = onSearchClick,
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("SEARCH") },
        )
        NavigationBarItem(
            selected = selected == BottomTab.Scan,
            onClick = onScanClick,
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
            label = { Text("SCAN") },
        )
        NavigationBarItem(
            selected = selected == BottomTab.Settings,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("SETTINGS") },
        )
    }
}
