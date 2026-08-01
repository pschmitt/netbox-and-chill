package dev.pschmitt.netboxandchill.ui.directory

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Category
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Best-effort icon per NetBox app namespace - falls back to a generic icon for anything unknown
 * (custom plugins, apps NetBox adds after this list was written, etc).
 */
object AppIcons {
    private val BY_APP_KEY: Map<String, ImageVector> =
        mapOf(
            "dcim" to Icons.Default.Dns,
            "ipam" to Icons.Default.Lan,
            "circuits" to Icons.Default.Cable,
            "tenancy" to Icons.Default.Group,
            "virtualization" to Icons.Default.Storage,
            "wireless" to Icons.Default.Wifi,
            "vpn" to Icons.Default.VpnLock,
            "extras" to Icons.Default.Layers,
        )

    fun forAppKey(appKey: String): ImageVector =
        BY_APP_KEY[appKey]
            ?: if (appKey.startsWith("plugins/")) Icons.Default.Extension
            else Icons.Outlined.Category

    val Devices: ImageVector = Icons.Default.Hub
}
