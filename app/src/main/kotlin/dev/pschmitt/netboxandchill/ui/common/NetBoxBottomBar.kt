package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** The three universal destinations, always reachable regardless of which sidebar item you're on. */
enum class BottomTab {
    Devices,
    Scan,
    Settings,
}

@Composable
fun NetBoxBottomBar(
    selected: BottomTab?,
    onDevicesClick: () -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    NavigationBar {
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
        NavigationBarItem(
            selected = selected == BottomTab.Settings,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
        )
    }
}
