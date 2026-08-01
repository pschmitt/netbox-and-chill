package dev.pschmitt.netboxandchill.ui.settings

import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.BuildConfig
import dev.pschmitt.netboxandchill.data.repository.GestureAction
import dev.pschmitt.netboxandchill.data.repository.normalizeHiddenFieldPreferenceKey
import dev.pschmitt.netboxandchill.data.repository.ScannerLens
import dev.pschmitt.netboxandchill.qrsetup.QrBitmap
import dev.pschmitt.netboxandchill.qrsetup.QrConfigCodec
import dev.pschmitt.netboxandchill.qrsetup.QrConfigEnvelope
import dev.pschmitt.netboxandchill.ui.common.SyncIssueCard

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KiB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
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
    val gestureAction by viewModel.settingsRepository.gestureAction.collectAsStateWithLifecycle()
    val scannerLens by viewModel.settingsRepository.scannerLens.collectAsStateWithLifecycle()
    val offlineMode by viewModel.settingsRepository.offlineMode.collectAsStateWithLifecycle()
    val hiddenFieldKeys by viewModel.settingsRepository.hiddenFieldKeys.collectAsStateWithLifecycle()
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
    var gestureMenuExpanded by remember { mutableStateOf(false) }
    var scannerLensMenuExpanded by remember { mutableStateOf(false) }
    var hiddenFieldsDialogVisible by remember { mutableStateOf(false) }
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

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader(
                title = "Connection",
                subtitle = "The NetBox server and credentials used by this app",
            )
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
                            },
                        ) {
                            Icon(
                                if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (tokenVisible) "Hide API token" else "Show API token",
                            )
                        }
                        IconButton(
                            onClick = {
                                authenticateForToken {
                                    context.getSystemService<ClipboardManager>()?.setPrimaryClip(
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
                        enabled = credentials.baseUrl.isNotBlank() && credentials.token.isNotBlank(),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Show connection setup QR code")
                    }
                },
            )
            SettingsSectionHeader(
                title = "Sync",
                subtitle = "Refresh cached NetBox data and control offline storage",
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
                    Switch(checked = syncAttachmentsToDisk, onCheckedChange = viewModel::setSyncAttachmentsToDisk)
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.CloudOff, contentDescription = null) },
                headlineContent = { Text("Offline mode") },
                supportingContent = {
                    Text("Use cached data only and pause network sync")
                },
                trailingContent = {
                    Switch(checked = offlineMode, onCheckedChange = viewModel::setOfflineMode)
                },
            )
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = viewModel::syncNow,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isSyncing) "Syncing…" else "Sync now")
                }
            }
            SettingsSectionHeader(
                title = "Display",
                subtitle = "Choose which fields are shown by default on item pages",
            )
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
                leadingContent = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                headlineContent = { Text("Two-finger swipe down") },
                supportingContent = { Text(gestureAction.label) },
                trailingContent = {
                    Box {
                        IconButton(onClick = { gestureMenuExpanded = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Configure swipe action")
                        }
                        DropdownMenu(
                            expanded = gestureMenuExpanded,
                            onDismissRequest = { gestureMenuExpanded = false },
                        ) {
                            GestureAction.values().forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = {
                                        Icon(
                                            when (action) {
                                                GestureAction.Off -> Icons.Default.Block
                                                GestureAction.GlobalSearch -> Icons.Default.Search
                                                GestureAction.Scanner -> Icons.Default.QrCodeScanner
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        viewModel.setGestureAction(action)
                                        gestureMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
            SettingsSectionHeader(
                title = "Scanner and gestures",
                subtitle = "Set the camera and shortcut behavior for quick navigation",
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.Cameraswitch, contentDescription = null) },
                headlineContent = { Text("Scanner default camera") },
                supportingContent = {
                    Text("${scannerLens.label}; falls back when this camera is unavailable")
                },
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
                                        viewModel.setScannerLens(lens)
                                        scannerLensMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
            SettingsSectionHeader(
                title = "Actions",
                subtitle = "Disconnect this NetBox instance",
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
            SettingsSectionHeader(
                title = "About",
                subtitle = "Application and build information",
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                headlineContent = { Text("NetBox and Chill") },
                supportingContent = { Text("Version " + BuildConfig.VERSION_NAME + " · GPLv3") },
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
                headlineContent = { Text("Build") },
                // Deliberately not concatenated with any other literal: Kotlin/R8 constant-folds
                // string-template concatenations of compile-time constants into a single merged
                // dex string entry, which would bury the raw commit hash where release.yaml's
                // `grep -Fx` revision-verification check (an exact standalone-line match) can't
                // find it. Kept as a lone reference so it stays its own dex string constant.
                supportingContent = { Text(BuildConfig.GIT_REVISION) },
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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

/** Edit the configured NetBox base URL (NBC-39). Save triggers
 * [SettingsViewModel.updateBaseUrl], which validates reachability before committing and reverts on
 * failure - this dialog doesn't wait around for that, it dismisses immediately and any failure
 * surfaces via the screen's existing Snackbar, same as every other async action here. */
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
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isUpdating) { Text("Cancel") } },
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
