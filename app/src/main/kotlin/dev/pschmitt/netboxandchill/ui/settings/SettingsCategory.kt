package dev.pschmitt.netboxandchill.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
enum class SettingsCategory(val title: String, val subtitle: String) {
    Connection("Connection", "NetBox server and credentials"),
    Sync("Sync", "Cached data and refresh policy"),
    Camera("Camera", "Scanner camera and lens preferences"),
    Printing("Printing", "Printer and label defaults"),
    Gestures("Gestures", "Gesture shortcuts"),
    Display("Display", "Fields and item types shown by default"),
    Notifications("Notifications", "NetBox change alerts"),
    About("About", "Application and build information");

    val icon: ImageVector
        get() =
            when (this) {
                Connection -> Icons.Default.Dns
                Sync -> Icons.Default.Storage
                Camera -> Icons.Default.Cameraswitch
                Printing -> Icons.Default.Print
                Gestures -> Icons.Default.TouchApp
                Display -> Icons.Default.Visibility
                Notifications -> Icons.Default.Notifications
                About -> Icons.Default.Info
            }
}
