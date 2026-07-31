package dev.pschmitt.netboxandchill.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.BuildConfig

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
    val syncAttachmentsToDisk by
        viewModel.settingsRepository.syncAttachmentsToDisk.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditServerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var tokenVisible by remember { mutableStateOf(false) }
    var pendingTokenAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var tokenAuthError by remember { mutableStateOf<String?>(null) }
    var tokenCopied by remember { mutableStateOf(false) }
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
        Column(Modifier.padding(padding).fillMaxWidth()) {
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
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                headlineContent = { Text("Cached devices") },
                supportingContent = { Text("$cachedDeviceCount devices synced locally") },
            )
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
            HorizontalDivider()
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
                Spacer(Modifier.height(8.dp))
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
            HorizontalDivider()
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
