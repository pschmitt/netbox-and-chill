package dev.pschmitt.netboxandchill.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.BuildConfig
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.repository.ChangeNotificationFilter
import dev.pschmitt.netboxandchill.data.repository.GestureAction
import dev.pschmitt.netboxandchill.data.repository.GestureShortcut
import dev.pschmitt.netboxandchill.data.repository.GestureTarget
import dev.pschmitt.netboxandchill.data.repository.ScannerLens
import dev.pschmitt.netboxandchill.data.repository.ScannerRearLens
import dev.pschmitt.netboxandchill.data.repository.PrintSettings
import dev.pschmitt.netboxandchill.data.repository.normalizeHiddenFieldPreferenceKey
import dev.pschmitt.netboxandchill.qrsetup.QrBitmap
import dev.pschmitt.netboxandchill.qrsetup.QrConfigCodec
import dev.pschmitt.netboxandchill.qrsetup.QrConfigEnvelope
import dev.pschmitt.netboxandchill.ui.common.SyncIssueCard
import dev.pschmitt.netboxandchill.printing.BrotherPrinter
import dev.pschmitt.netboxandchill.printing.BrotherLabelRenderer
import dev.pschmitt.netboxandchill.printing.PairedPrinter
import dev.pschmitt.netboxandchill.ui.common.PrintSettingsViewModel

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KiB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

private fun openExternalLink(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCategoryClick: (SettingsCategory) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val offlineMode by viewModel.settingsRepository.offlineMode.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())) {
            ListItem(
                leadingContent = { Icon(Icons.Default.CloudOff, contentDescription = null) },
                headlineContent = { Text("Offline mode") },
                supportingContent = { Text("Use cached data only and pause network sync") },
                trailingContent = {
                    Switch(checked = offlineMode, onCheckedChange = viewModel::setOfflineMode)
                },
            )
            SettingsCategory.entries.forEach { category ->
                ListItem(
                    modifier = Modifier.clickable { onCategoryClick(category) },
                    leadingContent = { Icon(category.icon, contentDescription = null) },
                    headlineContent = { Text(category.title) },
                    supportingContent = { Text(category.subtitle) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open ${category.title} settings",
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    printSettingsViewModel: PrintSettingsViewModel = hiltViewModel(),
) {
    val credentials by viewModel.settingsRepository.credentials.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isUpdatingBaseUrl by viewModel.isUpdatingBaseUrl.collectAsStateWithLifecycle()
    val cachedDeviceCount by viewModel.cachedDeviceCount.collectAsStateWithLifecycle()
    val cachedObjectCount by viewModel.cachedObjectCount.collectAsStateWithLifecycle()
    val cachedImageCount by viewModel.cachedImageCount.collectAsStateWithLifecycle()
    val persistentCacheBytes by viewModel.persistentCacheBytes.collectAsStateWithLifecycle()
    val persistentCacheFiles by viewModel.persistentCacheFiles.collectAsStateWithLifecycle()
    val syncAttachmentsToDisk by
        viewModel.settingsRepository.syncAttachmentsToDisk.collectAsStateWithLifecycle()
    val syncOnlyOnWifi by viewModel.settingsRepository.syncOnlyOnWifi.collectAsStateWithLifecycle()
    val syncWhileRoaming by
        viewModel.settingsRepository.syncWhileRoaming.collectAsStateWithLifecycle()
    val changeNotificationsEnabled by
        viewModel.settingsRepository.changeNotificationsEnabled.collectAsStateWithLifecycle()
    val changeNotificationFilters by
        viewModel.settingsRepository.changeNotificationFilters.collectAsStateWithLifecycle()
    val gestureActions by
        viewModel.settingsRepository.gestureActions.collectAsStateWithLifecycle()
    val gestureTargets by viewModel.gestureTargets.collectAsStateWithLifecycle()
    val gestureModels by viewModel.gestureModels.collectAsStateWithLifecycle()
    val gestureObjects by viewModel.gestureObjects.collectAsStateWithLifecycle()
    val scannerLens by viewModel.settingsRepository.scannerLens.collectAsStateWithLifecycle()
    val scannerRearLens by
        viewModel.settingsRepository.scannerRearLens.collectAsStateWithLifecycle()
    val printSettings by printSettingsViewModel.settings.collectAsStateWithLifecycle()
    val offlineMode by viewModel.settingsRepository.offlineMode.collectAsStateWithLifecycle()
    val hiddenFieldKeys by
        viewModel.settingsRepository.hiddenFieldKeys.collectAsStateWithLifecycle()
    val pinnedModelPaths by
        viewModel.settingsRepository.pinnedModelPaths.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val syncIssue by viewModel.settingsRepository.syncIssue.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditServerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var tokenVisible by remember { mutableStateOf(false) }
    var pendingTokenAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var tokenAuthError by remember { mutableStateOf<String?>(null) }
    var tokenCopied by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scannerLensMenuExpanded by remember { mutableStateOf(false) }
    var scannerRearLensMenuExpanded by remember { mutableStateOf(false) }
    var hiddenFieldsDialogVisible by remember { mutableStateOf(false) }
    var changeNotificationsDialogVisible by remember { mutableStateOf(false) }
    val currentPendingTokenAction by rememberUpdatedState(pendingTokenAction)

    val biometricPrompt =
        remember(activity) {
            activity?.let { host ->
                BiometricPrompt(
                    host,
                    ContextCompat.getMainExecutor(host),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            currentPendingTokenAction?.invoke()
                            pendingTokenAction = null
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            pendingTokenAction = null
                            tokenAuthError = errString.toString()
                        }
                    },
                )
            }
        }
    val authenticateForToken: (() -> Unit) -> Unit = { action ->
        val host = activity
        if (host == null) {
            tokenAuthError = "Device authentication is unavailable"
        } else {
            val authenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            when (BiometricManager.from(host).canAuthenticate(authenticators)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    tokenAuthError = null
                    pendingTokenAction = action
                    biometricPrompt?.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Authenticate to access API token")
                            .setSubtitle("Confirm your fingerprint or device PIN")
                            .setAllowedAuthenticators(authenticators)
                            .build()
                    )
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    tokenAuthError = "Set up a fingerprint or device PIN to access the API token"
                else -> tokenAuthError = "Device authentication is unavailable"
            }
        }
    }

    LaunchedEffect(credentials) {
        // A server switch or disconnect must never leave a previously-authorized token visible or
        // allow a pending authentication callback to act on credentials that are no longer shown.
        tokenVisible = false
        pendingTokenAction = null
        qrBitmap = null
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    LaunchedEffect(tokenAuthError) {
        tokenAuthError?.let {
            snackbarHostState.showSnackbar(it)
            tokenAuthError = null
        }
    }

    LaunchedEffect(tokenCopied) {
        if (tokenCopied) {
            snackbarHostState.showSnackbar("API token copied")
            tokenCopied = false
        }
    }

    if (showEditServerDialog) {
        EditServerDialog(
            currentBaseUrl = credentials.baseUrl,
            isUpdating = isUpdatingBaseUrl,
            onDismiss = { showEditServerDialog = false },
            onSave = { newBaseUrl ->
                viewModel.updateBaseUrl(newBaseUrl)
                showEditServerDialog = false
            },
        )
    }

    qrBitmap?.let { bitmap ->
        SetupQrDialog(bitmap = bitmap, onDismiss = { qrBitmap = null })
    }

    if (hiddenFieldsDialogVisible) {
        HiddenFieldsDialog(
            keys = hiddenFieldKeys,
            onAdd = viewModel::addHiddenField,
            onRemove = viewModel::removeHiddenField,
            onDismiss = { hiddenFieldsDialogVisible = false },
        )
    }

    if (changeNotificationsDialogVisible) {
        ChangeNotificationsDialog(
            filters = changeNotificationFilters,
            onFilterChanged = viewModel::setChangeNotificationFilter,
            onDismiss = { changeNotificationsDialogVisible = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(category.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (category) {
                SettingsCategory.Connection -> {
            ListItem(
                leadingContent = { Icon(Icons.Default.Dns, contentDescription = null) },
                headlineContent = { Text("NetBox instance") },
                supportingContent = { Text(credentials.baseUrl) },
                trailingContent = {
                    IconButton(onClick = { showEditServerDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Change NetBox server")
                    }
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
                headlineContent = { Text("API token") },
                supportingContent = {
                    Text(if (tokenVisible) credentials.token else "••••••••••••")
                },
                trailingContent = {
                    Row {
                        IconButton(
                            onClick = {
                                if (tokenVisible) {
                                    tokenVisible = false
                                } else {
                                    authenticateForToken { tokenVisible = true }
                                }
                            }
                        ) {
                            Icon(
                                if (tokenVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription =
                                    if (tokenVisible) "Hide API token" else "Show API token",
                            )
                        }
                        IconButton(
                            onClick = {
                                authenticateForToken {
                                    context
                                        .getSystemService<ClipboardManager>()
                                        ?.setPrimaryClip(
                                            ClipData.newPlainText("API token", credentials.token)
                                        )
                                    tokenCopied = true
                                }
                            },
                            enabled = credentials.token.isNotBlank(),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy API token")
                        }
                    }
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                headlineContent = { Text("Share connection setup") },
                supportingContent = {
                    Text("Show a QR code with this server URL and API token for another app")
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            authenticateForToken {
                                val payload =
                                    QrConfigCodec.encodePayload(
                                        QrConfigEnvelope(
                                            createdAt = System.currentTimeMillis(),
                                            baseUrl = credentials.baseUrl,
                                            token = credentials.token,
                                        )
                                    )
                                qrBitmap = QrBitmap.encode(payload)
                            }
                        },
                        enabled =
                            credentials.baseUrl.isNotBlank() && credentials.token.isNotBlank(),
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Show connection setup QR code",
                        )
                    }
                },
            )
            Column(Modifier.padding(16.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.logOut()
                        onLoggedOut()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Disconnect")
                }
            }
                }
                SettingsCategory.Sync -> {
            syncIssue?.let { issue ->
                SyncIssueCard(
                    issue,
                    onRetry = viewModel::syncNow,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            ListItem(
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                headlineContent = { Text("Sync attachments to disk") },
                supportingContent = {
                    Text("Download documents and images on sync for full offline access")
                },
                trailingContent = {
                    Switch(
                        checked = syncAttachmentsToDisk,
                        onCheckedChange = viewModel::setSyncAttachmentsToDisk,
                    )
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null) },
                headlineContent = { Text("Sync only on Wi-Fi") },
                supportingContent = {
                    Text("Use an unmetered connection for background and manual sync")
                },
                trailingContent = {
                    Switch(checked = syncOnlyOnWifi, onCheckedChange = viewModel::setSyncOnlyOnWifi)
                },
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null)
                },
                headlineContent = { Text("Sync while roaming") },
                supportingContent = {
                    Text(
                        if (syncOnlyOnWifi) {
                            "No effect while Wi-Fi-only sync is enabled"
                        } else {
                            "Allow sync over a roaming mobile connection"
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = syncWhileRoaming,
                        onCheckedChange = viewModel::setSyncWhileRoaming,
                        enabled = !syncOnlyOnWifi,
                    )
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                headlineContent = { Text("Cached data") },
                supportingContent = {
                    Text(
                        "$cachedDeviceCount devices · $cachedObjectCount other objects · " +
                            "$cachedImageCount image records\n" +
                            "$persistentCacheFiles downloaded files · ${formatBytes(persistentCacheBytes)}\n" +
                            "Downloaded images and documents are kept in app storage for offline use " +
                            "and are not temporary Android cache files."
                    )
                },
            )
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = viewModel::syncNow,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isSyncing) "Syncing…" else "Sync now")
                }
            }
                }
                SettingsCategory.Display -> {
            ListItem(
                leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                headlineContent = { Text("Hidden fields") },
                supportingContent = {
                    Text(
                        if (hiddenFieldKeys.isEmpty()) {
                            "No fields hidden by default"
                        } else {
                            val countLabel = if (hiddenFieldKeys.size == 1) "field" else "fields"
                            "$countLabel hidden by default · ${hiddenFieldKeys.sorted().joinToString(", ")}"
                        }
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (hiddenFieldKeys.isNotEmpty()) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Hidden fields configured",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { hiddenFieldsDialogVisible = true }) {
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
                        if (pinnedModelPaths.isEmpty()) "No item types pinned"
                        else
                            "${pinnedModelPaths.size} pinned · Long-press an item type on Add to change this"
                    )
                },
            )
                }
                SettingsCategory.Camera -> {
            ListItem(
                leadingContent = { Icon(Icons.Default.Cameraswitch, contentDescription = null) },
                headlineContent = { Text("Scanner default camera") },
                supportingContent = {
                    Text("${scannerLens.label}; falls back when this camera is unavailable")
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { scannerLensMenuExpanded = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Configure scanner camera",
                            )
                        }
                        DropdownMenu(
                            expanded = scannerLensMenuExpanded,
                            onDismissRequest = { scannerLensMenuExpanded = false },
                        ) {
                            ScannerLens.entries.forEach { lens ->
                                DropdownMenuItem(
                                    text = { Text(lens.label) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cameraswitch, contentDescription = null)
                                    },
                                    onClick = {
                                        viewModel.setScannerLens(lens)
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
                    Text(
                        "${scannerRearLens.label}; uses the closest available lens when this " +
                            "choice is unavailable"
                    )
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { scannerRearLensMenuExpanded = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Configure default rear lens",
                            )
                        }
                        DropdownMenu(
                            expanded = scannerRearLensMenuExpanded,
                            onDismissRequest = { scannerRearLensMenuExpanded = false },
                        ) {
                            ScannerRearLens.entries.forEach { lens ->
                                DropdownMenuItem(
                                    text = { Text(lens.label) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cameraswitch, contentDescription = null)
                                    },
                                    onClick = {
                                        viewModel.setScannerRearLens(lens)
                                        scannerRearLensMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
                }
                SettingsCategory.Printing -> {
                    PrintingSettingsSection(
                        settings = printSettings,
                        onUpdate = printSettingsViewModel::update,
                        onSetDefaultPrinter = printSettingsViewModel::setDefaultPrinter,
                        onClearDefaultPrinter = printSettingsViewModel::clearDefaultPrinter,
                    )
                }
                SettingsCategory.Gestures -> {
            SettingsSubsectionHeader("Two-finger gestures")
            GestureShortcut.entries.filter { it in TWO_FINGER_SHORTCUTS }.forEach { shortcut ->
                    GestureShortcutRow(
                        shortcut = shortcut,
                        action = gestureActions[shortcut] ?: GestureAction.Off,
                        target = gestureTargets[shortcut],
                        models = gestureModels,
                        objects = gestureObjects,
                        onActionSelected = { action ->
                            viewModel.setGestureAction(shortcut, action)
                        },
                        onTargetSelected = { model -> viewModel.setGestureTarget(shortcut, model) },
                        onDetailTargetSelected = { obj ->
                            viewModel.setGestureDetailTarget(shortcut, obj)
                        },
                    )
            }
            SettingsSubsectionHeader("Three-finger gestures")
            GestureShortcut.entries.filter { it in THREE_FINGER_SHORTCUTS }.forEach { shortcut ->
                    GestureShortcutRow(
                        shortcut = shortcut,
                        action = gestureActions[shortcut] ?: GestureAction.Off,
                        target = gestureTargets[shortcut],
                        models = gestureModels,
                        objects = gestureObjects,
                        onActionSelected = { action ->
                            viewModel.setGestureAction(shortcut, action)
                        },
                        onTargetSelected = { model -> viewModel.setGestureTarget(shortcut, model) },
                        onDetailTargetSelected = { obj ->
                            viewModel.setGestureDetailTarget(shortcut, obj)
                        },
                    )
                }
                }
                SettingsCategory.Notifications -> {
            ListItem(
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                headlineContent = { Text("NetBox change notifications") },
                supportingContent = {
                    Text(
                        if (changeNotificationsEnabled) {
                            selectedChangeNotificationSummary(changeNotificationFilters)
                        } else {
                            "Disabled by default; notify only about changes you choose"
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = changeNotificationsEnabled,
                        onCheckedChange = viewModel::setChangeNotificationsEnabled,
                    )
                },
            )
            if (changeNotificationsEnabled) {
                OutlinedButton(
                    onClick = { changeNotificationsDialogVisible = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose change types")
                }
            }
                }
                SettingsCategory.About -> {
            ListItem(
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                headlineContent = { Text("NetBox and Chill") },
                supportingContent = { Text("Version " + BuildConfig.VERSION_NAME + " · GPLv3") },
            )
            var buildTapCount by remember { mutableStateOf(0) }
            ListItem(
                modifier =
                    Modifier.clickable {
                        val tapCount = buildTapCount + 1
                        buildTapCount = if (tapCount >= 7) 0 else tapCount
                        val message =
                            if (tapCount >= 7) {
                                "Developer mode enabled"
                            } else {
                                "${7 - tapCount} more taps to enable developer mode"
                            }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
                headlineContent = { Text("Build") },
                // Deliberately not concatenated with any other literal: Kotlin/R8 constant-folds
                // string-template concatenations of compile-time constants into a single merged
                // dex string entry, which would bury the raw commit hash where release.yaml's
                // `grep -Fx` revision-verification check (an exact standalone-line match) can't
                // find it. Kept as a lone reference so it stays its own dex string constant.
                supportingContent = { Text(BuildConfig.GIT_REVISION) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null) },
                headlineContent = { Text("Build date") },
                supportingContent = { Text(BuildConfig.BUILD_DATE) },
            )
            ListItem(
                modifier =
                    Modifier.clickable {
                        openExternalLink(context, "https://github.com/pschmitt/netbox-and-chill")
                    },
                leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                headlineContent = { Text("GitHub repository") },
                supportingContent = { Text("View the source code and report issues") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open GitHub repository",
                    )
                },
            )
            ListItem(
                modifier =
                    Modifier.clickable {
                        openExternalLink(context, "https://github.com/sponsors/pschmitt")
                    },
                leadingContent = { Icon(Icons.Default.Favorite, contentDescription = null) },
                headlineContent = { Text("Sponsor the project") },
                supportingContent = { Text("Support development on GitHub Sponsors") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open GitHub Sponsors",
                    )
                },
            )
                }
            }
        }
    }
}

@Composable
private fun SettingsSubsectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
private fun PrintingSettingsSection(
    settings: PrintSettings,
    onUpdate: ((PrintSettings) -> PrintSettings) -> Unit,
    onSetDefaultPrinter: (String, String) -> Unit,
    onClearDefaultPrinter: () -> Unit,
) {
    val context = LocalContext.current
    var hasBluetoothPermission by remember { mutableStateOf(canReadBluetooth(context)) }
    var pairedPrinters by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }
    var printerMenuExpanded by remember { mutableStateOf(false) }
    var qrSizeMenuExpanded by remember { mutableStateOf(false) }
    var copiesText by remember(settings.copies) { mutableStateOf(settings.copies.toString()) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasBluetoothPermission = canReadBluetooth(context)
        }

    LaunchedEffect(hasBluetoothPermission) {
        pairedPrinters =
            if (hasBluetoothPermission) {
                context
                    .getSystemService<BluetoothManager>()
                    ?.adapter
                    ?.let { BrotherPrinter.pairedPrinters(it.bondedDevices) }
                    .orEmpty()
            } else {
                emptyList()
            }
    }

    val defaultPrinterLabel =
        settings.defaultPrinterName
            ?: settings.defaultPrinterAddress
            ?: "No default printer selected"
    val previewText =
        if (settings.longLabel) {
            "Example device\nASSET-0001\nSN-EXAMPLE"
        } else {
            "ASSET-0001"
        }
    val previewBitmap =
        remember(
            settings.invertColors,
            settings.verticalText,
            settings.longLabel,
            settings.qrSize,
        ) {
            runCatching {
                    BrotherLabelRenderer.preview(
                        objectUrl = "https://netbox.example/dcim/devices/1/",
                        labelText = previewText,
                        invert = settings.invertColors,
                        vertical = settings.verticalText,
                        qrSize = settings.qrSize,
                    )
                }
                .getOrNull()
        }
    androidx.compose.runtime.DisposableEffect(previewBitmap) {
        onDispose { previewBitmap?.recycle() }
    }
    SettingsSubsectionHeader("Label designer")
    Text(
        "Preview of the current label settings using example content. It works without a printer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (previewBitmap == null) {
        Text(
            "The label preview is unavailable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Label preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(144.dp).padding(12.dp),
            )
        }
    }
    ListItem(
        modifier = Modifier.clickable { printerMenuExpanded = true },
        leadingContent = { Icon(Icons.Default.Print, contentDescription = null) },
        headlineContent = { Text("Default printer") },
        supportingContent = { Text(defaultPrinterLabel) },
        trailingContent = {
            Box {
                IconButton(onClick = { printerMenuExpanded = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Choose default printer")
                }
                DropdownMenu(
                    expanded = printerMenuExpanded,
                    onDismissRequest = { printerMenuExpanded = false },
                ) {
                    pairedPrinters.forEach { printer ->
                        DropdownMenuItem(
                            text = { Text("${printer.name} (${printer.address})") },
                            leadingIcon = {
                                Icon(Icons.Default.Bluetooth, contentDescription = null)
                            },
                            onClick = {
                                onSetDefaultPrinter(printer.name, printer.address)
                                printerMenuExpanded = false
                            },
                        )
                    }
                    if (settings.defaultPrinterAddress != null) {
                        DropdownMenuItem(
                            text = { Text("Clear default printer") },
                            leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                            onClick = {
                                onClearDefaultPrinter()
                                printerMenuExpanded = false
                            },
                        )
                    }
                }
            }
        },
    )
    if (!hasBluetoothPermission) {
        OutlinedButton(
            onClick = {
                permissionLauncher.launch(settingsBluetoothPermissions())
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Allow Bluetooth to choose a printer")
        }
    } else if (pairedPrinters.isEmpty()) {
        Text(
            "Pair a Brother P-touch printer in the print dialog before choosing it here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    ListItem(
        leadingContent = { Icon(Icons.Default.Print, contentDescription = null) },
        headlineContent = { Text("Invert print colors") },
        supportingContent = { Text("Disable if printing on black tape") },
        trailingContent = {
            Switch(
                checked = settings.invertColors,
                onCheckedChange = { value -> onUpdate { it.copy(invertColors = value) } },
            )
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Print, contentDescription = null) },
        headlineContent = { Text("Vertical label text") },
        supportingContent = { Text("Rotate text for narrow labels") },
        trailingContent = {
            Switch(
                checked = settings.verticalText,
                onCheckedChange = { value -> onUpdate { it.copy(verticalText = value) } },
            )
        },
    )
    ListItem(
        leadingContent = { Icon(Icons.Default.Print, contentDescription = null) },
        headlineContent = { Text("Long label") },
        supportingContent = { Text("Use the extended name, asset tag, and serial layout") },
        trailingContent = {
            Switch(
                checked = settings.longLabel,
                onCheckedChange = { value -> onUpdate { it.copy(longLabel = value) } },
            )
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = copiesText,
            onValueChange = { value ->
                copiesText = value.filter(Char::isDigit).take(1)
                copiesText.toIntOrNull()?.takeIf { it in 1..9 }?.let { copies ->
                    onUpdate { it.copy(copies = copies) }
                }
            },
            label = { Text("Copies") },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
            modifier = Modifier.width(120.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box {
            OutlinedButton(onClick = { qrSizeMenuExpanded = true }) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("QR ${settings.qrSize}px")
            }
            DropdownMenu(
                expanded = qrSizeMenuExpanded,
                onDismissRequest = { qrSizeMenuExpanded = false },
            ) {
                listOf(48, 56, 64).forEach { size ->
                    DropdownMenuItem(
                        text = { Text("${size}px") },
                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                        onClick = {
                            onUpdate { it.copy(qrSize = size) }
                            qrSizeMenuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun canReadBluetooth(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

private fun settingsBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
private fun GestureShortcutRow(
    shortcut: GestureShortcut,
    action: GestureAction,
    target: GestureTarget?,
    models: List<dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity>,
    objects: List<NetBoxObjectEntity>,
    onActionSelected: (GestureAction) -> Unit,
    onTargetSelected: (dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity) -> Unit,
    onDetailTargetSelected: (NetBoxObjectEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var targetPickerVisible by remember { mutableStateOf(false) }
    var targetQuery by remember { mutableStateOf("") }
    var detailModel by remember { mutableStateOf<dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity?>(null) }
    val actionLabel =
        target?.let { configured -> "${action.label}: ${configured.label}" } ?: action.label
    ListItem(
        leadingContent = { Icon(Icons.Default.TouchApp, contentDescription = null) },
        headlineContent = { Text(shortcut.label) },
        supportingContent = { Text(actionLabel) },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Configure ${shortcut.label}")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    GestureAction.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.label) },
                            leadingIcon = {
                                Icon(
                                    when (candidate) {
                                        GestureAction.Off -> Icons.Default.Block
                                        GestureAction.GlobalSearch -> Icons.Default.Search
                                        GestureAction.Scanner -> Icons.Default.QrCodeScanner
                                        GestureAction.Settings -> Icons.Default.Info
                                        GestureAction.Add,
                                        GestureAction.AddSpecific -> Icons.Default.Add
                                        GestureAction.Sync -> Icons.Default.Sync
                                        GestureAction.OfflineOn,
                                        GestureAction.OfflineOff -> Icons.Default.CloudOff
                                        GestureAction.DeviceList,
                                        GestureAction.ListSpecific,
                                        GestureAction.DetailSpecific -> Icons.Default.Storage
                                    },
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onActionSelected(candidate)
                                expanded = false
                                if (
                                    candidate == GestureAction.AddSpecific ||
                                        candidate == GestureAction.ListSpecific ||
                                        candidate == GestureAction.DetailSpecific
                                ) {
                                    targetQuery = ""
                                    detailModel = null
                                    targetPickerVisible = true
                                }
                            },
                        )
                    }
                }
            }
        },
    )
    if (targetPickerVisible) {
        val filteredModels =
            models.filter { model ->
                targetQuery.isBlank() ||
                    model.modelLabel.contains(targetQuery, ignoreCase = true) ||
                    model.appLabel.contains(targetQuery, ignoreCase = true)
            }
        val filteredObjects =
            detailModel?.let { selectedModel ->
                objects
                    .asSequence()
                    .filter { it.endpointPath == selectedModel.endpointPath }
                    .filter { obj ->
                        targetQuery.isBlank() ||
                            obj.display.contains(targetQuery, ignoreCase = true) ||
                            obj.secondaryLine.orEmpty().contains(targetQuery, ignoreCase = true) ||
                            obj.json.contains(targetQuery, ignoreCase = true)
                    }
                    .toList()
            }.orEmpty()
        AlertDialog(
            onDismissRequest = {
                targetPickerVisible = false
                detailModel = null
            },
            title = {
                Text(
                    if (action == GestureAction.DetailSpecific && detailModel != null) {
                        "Choose cached ${detailModel!!.modelLabel.lowercase()}"
                    } else {
                        "Choose item type"
                    }
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = targetQuery,
                        onValueChange = { targetQuery = it },
                        label = {
                            Text(
                                if (action == GestureAction.DetailSpecific && detailModel != null) {
                                    "Search cached items"
                                } else {
                                    "Search item types"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (action == GestureAction.DetailSpecific && detailModel != null) {
                        if (filteredObjects.isEmpty()) {
                            Text(
                                "No matching cached items",
                                modifier = Modifier.padding(top = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        filteredObjects.forEach { obj ->
                            ListItem(
                                modifier =
                                    Modifier.clickable {
                                        onDetailTargetSelected(obj)
                                        targetPickerVisible = false
                                        detailModel = null
                                    },
                                leadingContent = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                },
                                headlineContent = { Text(obj.display) },
                                supportingContent = { obj.secondaryLine?.let { Text(it) } },
                            )
                        }
                    } else {
                        filteredModels.forEach { model ->
                            ListItem(
                                modifier =
                                    Modifier.clickable {
                                        if (action == GestureAction.DetailSpecific) {
                                            detailModel = model
                                            targetQuery = ""
                                        } else {
                                            onTargetSelected(model)
                                            targetPickerVisible = false
                                        }
                                    },
                                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                                headlineContent = { Text(model.modelLabel) },
                                supportingContent = { Text(model.appLabel) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        targetPickerVisible = false
                        detailModel = null
                    }
                ) { Text("Cancel") }
            },
        )
    }
}

private fun selectedChangeNotificationSummary(filters: Set<String>): String {
    val selected =
        if (ChangeNotificationFilter.All.storageKey in filters) {
            listOf(ChangeNotificationFilter.All.label)
        } else {
            ChangeNotificationFilter.entries
                .filter { it.storageKey in filters }
                .map { it.label }
        }
    return if (selected.isEmpty()) {
        "No change types selected"
    } else {
        "Notify about " + selected.joinToString(", ")
    }
}

@Composable
private fun ChangeNotificationsDialog(
    filters: Set<String>,
    onFilterChanged: (ChangeNotificationFilter, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NetBox change notifications") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Choose which new changes should appear as a silent notification when the app is in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                ChangeNotificationFilter.entries.forEach { filter ->
                    val checked =
                        if (ChangeNotificationFilter.All.storageKey in filters) {
                            filter == ChangeNotificationFilter.All
                        } else {
                            filter.storageKey in filters
                        }
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                onFilterChanged(filter, !checked)
                            },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onFilterChanged(filter, it) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(filter.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun HiddenFieldsDialog(
    keys: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newKey by remember { mutableStateOf("") }
    val normalizedKey = normalizeHiddenFieldPreferenceKey(newKey)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden fields") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Use object/field keys such as device/model. Long-press a field to add it here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                keys.sorted().forEach { key ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(key, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(key) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Remove $key")
                        }
                    }
                }
                if (keys.isEmpty()) {
                    Text(
                        "No fields are hidden by default.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("Object/field key") },
                    placeholder = { Text("device/model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        normalizedKey?.let {
                            onAdd(it)
                            newKey = ""
                        }
                    },
                    enabled = normalizedKey != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hide field by default")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * Edit the configured NetBox base URL (NBC-39). Save triggers [SettingsViewModel.updateBaseUrl],
 * which validates reachability before committing and reverts on failure - this dialog doesn't wait
 * around for that, it dismisses immediately and any failure surfaces via the screen's existing
 * Snackbar, same as every other async action here.
 */
@Composable
private fun EditServerDialog(
    currentBaseUrl: String,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(currentBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change NetBox server") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("NetBox URL") },
                singleLine = true,
                enabled = !isUpdating,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = !isUpdating && text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUpdating) { Text("Cancel") }
        },
    )
}

@Composable
private fun SetupQrDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share NetBox setup") },
        text = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "NetBox setup QR code",
                    modifier = Modifier.size(280.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "This QR code contains the NetBox server URL and API token. Scan it from the login screen on a trusted device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
