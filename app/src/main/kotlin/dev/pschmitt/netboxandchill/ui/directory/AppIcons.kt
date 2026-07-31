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

/** Best-effort icon per NetBox app namespace - falls back to a generic icon for anything unknown
 * (custom plugins, apps NetBox adds after this list was written, etc). */
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
        BY_APP_KEY[appKey] ?: if (appKey.startsWith("plugins/")) Icons.Default.Extension else Icons.Outlined.Category

    val Devices: ImageVector = Icons.Default.Hub

    /** Mirrors [dev.pschmitt.netboxandchill.data.repository.DirectoryRepository]'s `appKey` shape
     * (`"plugins/<plugin>"` for plugin models, else the plain app segment) so a raw `endpointPath`
     * (e.g. `"api/dcim/racks/"`) resolves to the same icon [forAppKey] would pick from the
     * sidebar's own discovered `NetBoxModelEntity.appKey` - used anywhere a screen only has the
     * endpoint path on hand (generic list rows, global search results). */
    fun appKeyFromEndpointPath(endpointPath: String): String {
        val segments = endpointPath.trim('/').split('/')
        return if (segments.size >= 4 && segments[1] == "plugins") "plugins/${segments[2]}"
        else segments.getOrElse(1) { "" }
    }
}
