package dev.pschmitt.nyetbox.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.pschmitt.nyetbox.BuildConfig
import dev.pschmitt.nyetbox.data.db.NetBoxModelEntity
import dev.pschmitt.nyetbox.data.db.NetBoxObjectEntity
import dev.pschmitt.nyetbox.data.repository.*
import dev.pschmitt.nyetbox.ui.common.SyncIssueCard

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KiB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

private val TWO_FINGER_SHORTCUTS =
    setOf(
        GestureShortcut.TwoFingerDown,
        GestureShortcut.TwoFingerLeft,
        GestureShortcut.TwoFingerRight,
    )

private val THREE_FINGER_SHORTCUTS =
    setOf(
        GestureShortcut.ThreeFingerUp,
        GestureShortcut.ThreeFingerDown,
        GestureShortcut.ThreeFingerLeft,
        GestureShortcut.ThreeFingerRight,
    )

internal data class SettingsCategoryState(
    val credentials: NetBoxCredentials,
    val currentUser: NetBoxUserIdentity?,
    val isLoadingCurrentUser: Boolean,
    val connectionTest: ConnectionTestState,
    val tokenVisible: Boolean,
    val isSyncing: Boolean,
    val syncIssue: SyncIssue?,
    val cachedDeviceCount: Int,
    val cachedObjectCount: Int,
    val cachedImageCount: Int,
    val persistentCacheBytes: Long,
    val persistentCacheFiles: Int,
    val syncAttachmentsToDisk: Boolean,
    val syncOnlyOnWifi: Boolean,
    val syncWhileRoaming: Boolean,
    val syncOnAppLaunch: Boolean,
    val changeNotificationsEnabled: Boolean,
    val changeNotificationFilters: Set<String>,
    val gestureActions: Map<GestureShortcut, GestureAction>,
    val gestureTargets: Map<GestureShortcut, GestureTarget>,
    val gestureModels: List<NetBoxModelEntity>,
    val gestureObjects: List<NetBoxObjectEntity>,
    val scannerLens: ScannerLens,
    val scannerRearLens: ScannerRearLens,
    val printSettings: PrintSettings,
    val hiddenFieldKeys: Set<String>,
    val pinnedModelPaths: Set<String>,
    val themeMode: ThemeMode,
    val themeAccent: ThemeAccent,
    val objectTypeAccents: Map<String, ThemeAccent>,
    val showTopologyDeviceTypeImages: Boolean,
)

internal data class SettingsCategoryActions(
    val onEditServer: () -> Unit,
    val onTestConnection: () -> Unit,
    val onDisconnect: () -> Unit,
    val onShowToken: () -> Unit,
    val onHideToken: () -> Unit,
    val onCopyToken: () -> Unit,
    val onShareSetup: () -> Unit,
    val onSync: () -> Unit,
    val onSetSyncAttachmentsToDisk: (Boolean) -> Unit,
    val onSetSyncOnlyOnWifi: (Boolean) -> Unit,
    val onSetSyncWhileRoaming: (Boolean) -> Unit,
    val onSetSyncOnAppLaunch: (Boolean) -> Unit,
    val onSetThemeMode: (ThemeMode) -> Unit,
    val onSetThemeAccent: (ThemeAccent) -> Unit,
    val onShowObjectTypeColors: () -> Unit,
    val onShowHiddenFields: () -> Unit,
    val onSetScannerLens: (ScannerLens) -> Unit,
    val onSetScannerRearLens: (ScannerRearLens) -> Unit,
    val onUpdatePrintSettings: ((PrintSettings) -> PrintSettings) -> Unit,
    val onSetDefaultPrinter: (String, String) -> Unit,
    val onClearDefaultPrinter: () -> Unit,
    val onSetShowTopologyDeviceTypeImages: (Boolean) -> Unit,
    val onSetChangeNotificationsEnabled: (Boolean) -> Unit,
    val onShowChangeNotifications: () -> Unit,
    val onSetGestureAction: (GestureShortcut, GestureAction) -> Unit,
    val onSetGestureTarget: (GestureShortcut, NetBoxModelEntity) -> Unit,
    val onSetGestureDetailTarget: (GestureShortcut, NetBoxObjectEntity) -> Unit,
)

@Composable
internal fun SettingsCategoryContent(
    category: SettingsCategory,
    state: SettingsCategoryState,
    actions: SettingsCategoryActions,
) {
    when (category) {
        SettingsCategory.Connection -> ConnectionSettingsContent(state, actions)
        SettingsCategory.Sync -> SyncSettingsContent(state, actions)
        SettingsCategory.Display -> DisplaySettingsContent(state, actions)
        SettingsCategory.Camera -> CameraSettingsContent(state, actions)
        SettingsCategory.Printing ->
            PrintingSettingsSection(
                settings = state.printSettings,
                onUpdate = actions.onUpdatePrintSettings,
                onSetDefaultPrinter = actions.onSetDefaultPrinter,
                onClearDefaultPrinter = actions.onClearDefaultPrinter,
            )
        SettingsCategory.Gestures -> GestureSettingsContent(state, actions)
        SettingsCategory.Notifications -> NotificationSettingsContent(state, actions)
        SettingsCategory.About -> AboutSettingsContent()
    }
}

@Composable
private fun ConnectionSettingsContent(
    state: SettingsCategoryState,
    actions: SettingsCategoryActions,
) {
    val context = LocalContext.current
    ListItem(
        leadingContent = { Icon(Icons.Default.Dns, contentDescription = null) },
        headlineContent = { Text("NetBox instance") },
        supportingContent = { Text(state.credentials.baseUrl) },
        trailingContent = {
            IconButton(onClick = actions.onEditServer) {
                Icon(Icons.Default.Edit, contentDescription = "Change NetBox server")
            }
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
        headlineContent = { Text("Signed in as") },
        supportingContent = {
            Text(
                when {
                    state.currentUser != null ->
                        buildString {
                            append(state.currentUser.summary)
                            state.currentUser.email?.let { append(" · ").append(it) }
                        }
                    state.isLoadingCurrentUser -> "Checking NetBox credentials…"
                    else -> "Not available from this API token"
                }
            )
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
        headlineContent = { Text("API token") },
        supportingContent = {
            Text(
                when {
                    state.credentials.token.isBlank() -> "Not configured"
                    state.tokenVisible -> state.credentials.token
                    else -> "••••••••••••"
                }
            )
        },
        trailingContent = {
            Row {
                IconButton(
                    onClick =
                        if (state.credentials.token.isBlank()) actions.onShowToken
                        else if (state.tokenVisible) actions.onHideToken
                        else actions.onShowToken,
                ) {
                    Icon(
                        if (state.tokenVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription =
                            if (state.tokenVisible) "Hide API token" else "Show API token",
                    )
                }
                IconButton(
                    onClick = actions.onCopyToken,
                    enabled = state.credentials.token.isNotBlank(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy API token")
                }
            }
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
        headlineContent = { Text("Share connection setup") },
        supportingContent = { Text("Show a QR code with this server URL and API token") },
        trailingContent = {
            IconButton(
                onClick = actions.onShareSetup,
                enabled = state.credentials.isValid,
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Show setup QR code")
            }
        },
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(
            onClick = actions.onTestConnection,
            enabled =
                state.credentials.isValid && state.connectionTest !is ConnectionTestState.Testing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (state.connectionTest is ConnectionTestState.Testing) Icons.Default.Sync
                else Icons.Default.NetworkCheck,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.connectionTest is ConnectionTestState.Testing) {
                    "Testing connection…"
                } else {
                    "Test connection"
                }
            )
        }
        when (val result = state.connectionTest) {
            is ConnectionTestState.Success ->
                Text(
                    result.message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            is ConnectionTestState.Failure ->
                Text(
                    result.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            ConnectionTestState.Idle, ConnectionTestState.Testing -> Unit
        }
    }
    Column(Modifier.padding(16.dp)) {
        OutlinedButton(onClick = actions.onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Disconnect")
        }
    }
}

@Composable
private fun SyncSettingsContent(
    state: SettingsCategoryState,
    actions: SettingsCategoryActions,
) {
    state.syncIssue?.let { issue ->
        SyncIssueCard(
            issue,
            onRetry = actions.onSync,
            isSyncing = state.isSyncing,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    ListItem(
        leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
        headlineContent = { Text("Sync attachments to disk") },
        supportingContent = { Text("Download documents and images on sync for full offline access") },
        trailingContent = {
            Switch(
                checked = state.syncAttachmentsToDisk,
                onCheckedChange = actions.onSetSyncAttachmentsToDisk,
            )
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null) },
        headlineContent = { Text("Sync only on Wi-Fi") },
        supportingContent = { Text("Use an unmetered connection for background and manual sync") },
        trailingContent = {
            Switch(checked = state.syncOnlyOnWifi, onCheckedChange = actions.onSetSyncOnlyOnWifi)
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.SignalCellularAlt, contentDescription = null) },
        headlineContent = { Text("Sync while roaming") },
        supportingContent = {
            Text(
                if (state.syncOnlyOnWifi) {
                    "No effect while Wi-Fi-only sync is enabled"
                } else {
                    "Allow sync over a roaming mobile connection"
                }
            )
        },
        trailingContent = {
            Switch(
                checked = state.syncWhileRoaming,
                onCheckedChange = actions.onSetSyncWhileRoaming,
                enabled = !state.syncOnlyOnWifi,
            )
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
        headlineContent = { Text("Sync on app launch") },
        supportingContent = { Text("Refresh NetBox in the background when the app starts") },
        trailingContent = {
            Switch(
                checked = state.syncOnAppLaunch,
                onCheckedChange = actions.onSetSyncOnAppLaunch,
            )
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
        headlineContent = { Text("Cached data") },
        supportingContent = {
            Text(
                "${state.cachedDeviceCount} devices · ${state.cachedObjectCount} other objects · " +
                    "${state.cachedImageCount} image records\n" +
                    "${state.persistentCacheFiles} downloaded files · ${formatBytes(state.persistentCacheBytes)}\n" +
                    "Downloaded images and documents are kept in app storage for offline use and are not temporary Android cache files."
            )
        },
    )
    Column(Modifier.padding(16.dp)) {
        Button(
            onClick = actions.onSync,
            enabled = !state.isSyncing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.isSyncing) "Syncing…" else "Sync now")
        }
    }
}

@Composable
private fun DisplaySettingsContent(
    state: SettingsCategoryState,
    actions: SettingsCategoryActions,
) {
    var themeModeMenuExpanded by remember { mutableStateOf(false) }
    var themeAccentMenuExpanded by remember { mutableStateOf(false) }
    SettingsSubsectionHeader("Theme")
    ListItem(
        leadingContent = {
            Icon(
                when (state.themeMode) {
                    ThemeMode.FollowSystem -> Icons.Default.BrightnessAuto
                    ThemeMode.Light -> Icons.Default.LightMode
                    ThemeMode.Dark -> Icons.Default.DarkMode
                },
                contentDescription = null,
            )
        },
        headlineContent = { Text("Color scheme") },
        supportingContent = { Text(state.themeMode.label) },
        trailingContent = {
            Box {
                IconButton(onClick = { themeModeMenuExpanded = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Choose color scheme")
                }
                DropdownMenu(
                    expanded = themeModeMenuExpanded,
                    onDismissRequest = { themeModeMenuExpanded = false },
                ) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                            onClick = {
                                actions.onSetThemeMode(mode)
                                themeModeMenuExpanded = false
                            },
                        )
                    }
                }
            }
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
        headlineContent = { Text("Accent color") },
        supportingContent = { Text(state.themeAccent.label) },
        trailingContent = {
            Box {
                IconButton(onClick = { themeAccentMenuExpanded = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Choose accent color")
                }
                DropdownMenu(
                    expanded = themeAccentMenuExpanded,
                    onDismissRequest = { themeAccentMenuExpanded = false },
                ) {
                    ThemeAccent.entries.forEach { accent ->
                        DropdownMenuItem(
                            text = { Text(accent.label) },
                            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                            onClick = {
                                actions.onSetThemeAccent(accent)
                                themeAccentMenuExpanded = false
                            },
                        )
                    }
                }
            }
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
        headlineContent = { Text("Object type colors") },
        supportingContent = {
            Text(
                if (state.objectTypeAccents.isEmpty()) "Automatic colors"
                else "${state.objectTypeAccents.size} customized object types"
            )
        },
        trailingContent = {
            IconButton(onClick = actions.onShowObjectTypeColors) {
                Icon(Icons.Default.Edit, contentDescription = "Customize object type colors")
            }
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
        headlineContent = { Text("Hidden fields") },
        supportingContent = {
            Text(
                if (state.hiddenFieldKeys.isEmpty()) {
                    "No fields hidden by default"
                } else {
                    val countLabel = if (state.hiddenFieldKeys.size == 1) "field" else "fields"
                    "$countLabel hidden by default · ${state.hiddenFieldKeys.sorted().joinToString(", ")}"
                }
            )
        },
        trailingContent = {
            Row {
                if (state.hiddenFieldKeys.isNotEmpty()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Hidden fields configured", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = actions.onShowHiddenFields) {
                    Icon(Icons.Default.Edit, contentDescription = "Configure hidden fields")
                }
            }
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.PushPin, contentDescription = null) },
        headlineContent = { Text("Pinned item types") },
        supportingContent = {
            Text(
                if (state.pinnedModelPaths.isEmpty()) "No item types pinned"
                else "${state.pinnedModelPaths.size} pinned · Long-press an item type on Add to change this"
            )
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Hub, contentDescription = null) },
        headlineContent = { Text("Topology device images") },
        supportingContent = {
            Text("Use cached device-type front images for matching topology nodes")
        },
        trailingContent = {
            Switch(
                checked = state.showTopologyDeviceTypeImages,
                onCheckedChange = actions.onSetShowTopologyDeviceTypeImages,
            )
        },
    )
}

@Composable
private fun CameraSettingsContent(
    state: SettingsCategoryState,
    actions: SettingsCategoryActions,
) {
    var scannerLensMenuExpanded by remember { mutableStateOf(false) }
    var scannerRearLensMenuExpanded by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { Icon(Icons.Default.Cameraswitch, contentDescription = null) },
        headlineContent = { Text("Scanner default camera") },
        supportingContent = { Text("${state.scannerLens.label}; falls back when unavailable") },
        trailingContent = {
            Box {
                IconButton(onClick = { scannerLensMenuExpanded = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Configure scanner camera")
                }
                DropdownMenu(
                    expanded = scannerLensMenuExpanded,
                    onDismissRequest = { scannerLensMenuExpanded = false },
                ) {
                    ScannerLens.entries.forEach { lens ->
                        DropdownMenuItem(
                            text = { Text(lens.label) },
                            leadingIcon = { Icon(Icons.Default.Cameraswitch, contentDescription = null) },
                            onClick = {
                                actions.onSetScannerLens(lens)
                                scannerLensMenuExpanded = false
                            },
                        )
                    }
                }
            }
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Cameraswitch, contentDescription = null) },
        headlineContent = { Text("Default rear lens") },
        supportingContent = {
            Text("${state.scannerRearLens.label}; uses the closest available lens when unavailable")
        },
        trailingContent = {
            Box {
                IconButton(onClick = { scannerRearLensMenuExpanded = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Configure default rear lens")
                }
                DropdownMenu(
                    expanded = scannerRearLensMenuExpanded,
                    onDismissRequest = { scannerRearLensMenuExpanded = false },
                ) {
                    ScannerRearLens.entries.forEach { lens ->
                        DropdownMenuItem(
                            text = { Text(lens.label) },
                            leadingIcon = { Icon(Icons.Default.Cameraswitch, contentDescription = null) },
                            onClick = {
                                actions.onSetScannerRearLens(lens)
                                scannerRearLensMenuExpanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun GestureSettingsContent(
    state: SettingsCategoryState,
    actions: SettingsCategoryActions,
) {
    SettingsSubsectionHeader("Two-finger gestures")
    GestureShortcut.entries.filter { it in TWO_FINGER_SHORTCUTS }.forEach { shortcut ->
        GestureShortcutRow(
            shortcut = shortcut,
            action = state.gestureActions[shortcut] ?: GestureAction.Off,
            target = state.gestureTargets[shortcut],
            models = state.gestureModels,
            objects = state.gestureObjects,
            onActionSelected = { action -> actions.onSetGestureAction(shortcut, action) },
            onTargetSelected = { model -> actions.onSetGestureTarget(shortcut, model) },
            onDetailTargetSelected = { obj -> actions.onSetGestureDetailTarget(shortcut, obj) },
        )
    }
    SettingsSubsectionHeader("Three-finger gestures")
    GestureShortcut.entries.filter { it in THREE_FINGER_SHORTCUTS }.forEach { shortcut ->
        GestureShortcutRow(
            shortcut = shortcut,
            action = state.gestureActions[shortcut] ?: GestureAction.Off,
            target = state.gestureTargets[shortcut],
            models = state.gestureModels,
            objects = state.gestureObjects,
            onActionSelected = { action -> actions.onSetGestureAction(shortcut, action) },
            onTargetSelected = { model -> actions.onSetGestureTarget(shortcut, model) },
            onDetailTargetSelected = { obj -> actions.onSetGestureDetailTarget(shortcut, obj) },
        )
    }
}

@Composable
private fun NotificationSettingsContent(
    state: SettingsCategoryState,
    actions: SettingsCategoryActions,
) {
    ListItem(
        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
        headlineContent = { Text("NetBox change notifications") },
        supportingContent = {
            Text(
                if (state.changeNotificationsEnabled) {
                    selectedChangeNotificationSummary(state.changeNotificationFilters)
                } else {
                    "Disabled by default; notify only about changes you choose"
                }
            )
        },
        trailingContent = {
            Switch(
                checked = state.changeNotificationsEnabled,
                onCheckedChange = actions.onSetChangeNotificationsEnabled,
            )
        },
    )
    if (state.changeNotificationsEnabled) {
        OutlinedButton(
            onClick = actions.onShowChangeNotifications,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose change types")
        }
    }
}

@Composable
private fun AboutSettingsContent() {
    val context = LocalContext.current
    ListItem(
        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
        headlineContent = { Text("Nyetbox") },
        supportingContent = { Text("Version " + BuildConfig.VERSION_NAME + " · GPLv3") },
    )
    var buildTapCount by remember { mutableIntStateOf(0) }
    ListItem(
        modifier =
            Modifier.clickable {
                val tapCount = buildTapCount + 1
                buildTapCount = if (tapCount >= 7) 0 else tapCount
                Toast.makeText(
                        context,
                        if (tapCount >= 7) "Developer mode enabled"
                        else "${7 - tapCount} more taps to enable developer mode",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            },
        leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
        headlineContent = { Text("Build") },
        supportingContent = { Text(BuildConfig.GIT_REVISION) },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null) },
        headlineContent = { Text("Build date") },
        supportingContent = { Text(BuildConfig.BUILD_DATE) },
    )
    ExternalLinkRow(
        context = context,
        url = "https://github.com/pschmitt/nyetbox",
        icon = Icons.Default.Code,
        title = "GitHub repository",
        subtitle = "View the source code and report issues",
    )
    ExternalLinkRow(
        context = context,
        url = "https://github.com/sponsors/pschmitt",
        icon = Icons.Default.Favorite,
        title = "Sponsor the project",
        subtitle = "Support development on GitHub Sponsors",
    )
    ExternalLinkRow(
        context = context,
        url = "https://github.com/pschmitt/nyetbox/blob/main/PRIVACY.md",
        icon = Icons.Default.PrivacyTip,
        title = "Privacy policy",
        subtitle = "How Nyetbox handles data and network access",
    )
}

@Composable
private fun ExternalLinkRow(
    context: Context,
    url: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    ListItem(
        modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open $title")
        },
    )
}
