package dev.pschmitt.nyetbox.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** The universal destinations always reachable regardless of which sidebar item you're on. */
enum class BottomTab {
    Dashboard,
    Search,
    Scan,
    Add,
    Settings,
}

@Composable
fun NetBoxBottomBar(
    selected: BottomTab?,
    onDashboardClick: () -> Unit,
    onSearchClick: () -> Unit,
    onScanClick: () -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    if (LocalUseNavigationRail.current) {
        NavigationRail {
            NavigationRailItem(
                selected = selected == BottomTab.Dashboard,
                onClick = onDashboardClick,
                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                label = { Text("Home") },
            )
            NavigationRailItem(
                selected = selected == BottomTab.Search,
                onClick = onSearchClick,
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search") },
            )
            NavigationRailItem(
                selected = selected == BottomTab.Scan,
                onClick = onScanClick,
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                label = { Text("Scan") },
            )
            NavigationRailItem(
                selected = selected == BottomTab.Add,
                onClick = onAddClick,
                icon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                label = { Text("Add") },
            )
            NavigationRailItem(
                selected = selected == BottomTab.Settings,
                onClick = onSettingsClick,
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
            )
        }
    } else {
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
                selected = selected == BottomTab.Add,
                onClick = onAddClick,
                icon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                label = { Text("ADD") },
            )
        }
    }
}
