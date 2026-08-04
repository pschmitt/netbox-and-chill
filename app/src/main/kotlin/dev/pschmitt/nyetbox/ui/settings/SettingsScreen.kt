package dev.pschmitt.nyetbox.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.nyetbox.qrsetup.QrBitmap
import dev.pschmitt.nyetbox.qrsetup.QrConfigCodec
import dev.pschmitt.nyetbox.qrsetup.QrConfigEnvelope
import dev.pschmitt.nyetbox.ui.common.PrintSettingsViewModel

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
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Preferences",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            SettingsSingleItemCard {
                SettingsToggleItem(
                    checked = offlineMode,
                    onCheckedChange = viewModel::setOfflineMode,
                    leadingContent = { Icon(Icons.Default.CloudOff, contentDescription = null) },
                    headlineContent = {
                        Text("Offline mode")
                    },
                    supportingContent = {
                        Text(
                            if (offlineMode) "Network access is paused"
                            else "Allow background network sync"
                        )
                    },
                )
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            SettingsCategory.entries.forEach { category ->
                SettingsNavigationCard(
                    title = category.title,
                    subtitle = category.subtitle,
                    icon = category.icon,
                    onClick = { onCategoryClick(category) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    openServerManager: Boolean = false,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    printSettingsViewModel: PrintSettingsViewModel = hiltViewModel(),
) {
    val credentials by viewModel.settingsRepository.credentials.collectAsStateWithLifecycle()
    val serverProfiles by viewModel.settingsRepository.serverProfiles.collectAsStateWithLifecycle()
    val activeServerId by viewModel.settingsRepository.activeServerId.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isLoadingCurrentUser by viewModel.isLoadingCurrentUser.collectAsStateWithLifecycle()
    val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
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
    val syncOnAppLaunch by
        viewModel.settingsRepository.syncOnAppLaunch.collectAsStateWithLifecycle()
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
    val hiddenFieldKeys by
        viewModel.settingsRepository.hiddenFieldKeys.collectAsStateWithLifecycle()
    val pinnedModelPaths by
        viewModel.settingsRepository.pinnedModelPaths.collectAsStateWithLifecycle()
    val themeMode by viewModel.settingsRepository.themeMode.collectAsStateWithLifecycle()
    val themeAccent by viewModel.settingsRepository.themeAccent.collectAsStateWithLifecycle()
    val objectTypeAccents by
        viewModel.settingsRepository.objectTypeAccents.collectAsStateWithLifecycle()
    val showTopologyDeviceTypeImages by
        viewModel.settingsRepository.showTopologyDeviceTypeImages.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val syncIssue by viewModel.settingsRepository.syncIssue.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditServerDialog by remember(openServerManager) { mutableStateOf(openServerManager) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var tokenVisible by remember { mutableStateOf(false) }
    var pendingTokenAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var tokenAuthError by remember { mutableStateOf<String?>(null) }
    var tokenCopied by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hiddenFieldsDialogVisible by remember { mutableStateOf(false) }
    var changeNotificationsDialogVisible by remember { mutableStateOf(false) }
    var objectTypeColorsDialogVisible by remember { mutableStateOf(false) }
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
        ServerProfilesDialog(
            profiles = serverProfiles,
            activeServerId = activeServerId,
            onSwitch = viewModel::switchServer,
            onAdd = viewModel::addServer,
            onUpdate = viewModel::updateServer,
            onRemove = { id -> viewModel.removeServer(id) { onLoggedOut() } },
            onDismiss = { showEditServerDialog = false },
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

    if (objectTypeColorsDialogVisible) {
        ObjectTypeColorsDialog(
            models = gestureModels,
            accents = objectTypeAccents,
            onAccentChanged = viewModel.settingsRepository::setObjectTypeAccent,
            onDismiss = { objectTypeColorsDialogVisible = false },
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
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            SettingsCategoryContent(
                category = category,
                state =
                    SettingsCategoryState(
                        credentials = credentials,
                        serverProfiles = serverProfiles,
                        activeServerId = activeServerId,
                        currentUser = currentUser,
                        isLoadingCurrentUser = isLoadingCurrentUser,
                        connectionTest = connectionTest,
                        tokenVisible = tokenVisible,
                        isSyncing = isSyncing,
                        syncIssue = syncIssue,
                        cachedDeviceCount = cachedDeviceCount,
                        cachedObjectCount = cachedObjectCount,
                        cachedImageCount = cachedImageCount,
                        persistentCacheBytes = persistentCacheBytes,
                        persistentCacheFiles = persistentCacheFiles,
                        syncAttachmentsToDisk = syncAttachmentsToDisk,
                        syncOnlyOnWifi = syncOnlyOnWifi,
                        syncWhileRoaming = syncWhileRoaming,
                        syncOnAppLaunch = syncOnAppLaunch,
                        changeNotificationsEnabled = changeNotificationsEnabled,
                        changeNotificationFilters = changeNotificationFilters,
                        gestureActions = gestureActions,
                        gestureTargets = gestureTargets,
                        gestureModels = gestureModels,
                        gestureObjects = gestureObjects,
                        scannerLens = scannerLens,
                        scannerRearLens = scannerRearLens,
                        printSettings = printSettings,
                        hiddenFieldKeys = hiddenFieldKeys,
                        pinnedModelPaths = pinnedModelPaths,
                        themeMode = themeMode,
                        themeAccent = themeAccent,
                        objectTypeAccents = objectTypeAccents,
                        showTopologyDeviceTypeImages = showTopologyDeviceTypeImages,
                    ),
                actions =
                    SettingsCategoryActions(
                        onEditServer = { showEditServerDialog = true },
                        onSwitchServer = viewModel::switchServer,
                        onAddServer = viewModel::addServer,
                        onUpdateServer = viewModel::updateServer,
                        onRemoveServer = { id -> viewModel.removeServer(id) { onLoggedOut() } },
                        onTestConnection = viewModel::testConnection,
                        onShowToken = { authenticateForToken { tokenVisible = true } },
                        onHideToken = { tokenVisible = false },
                        onCopyToken = {
                            authenticateForToken {
                                context
                                    .getSystemService<ClipboardManager>()
                                    ?.setPrimaryClip(
                                        ClipData.newPlainText("API token", credentials.token)
                                    )
                                tokenCopied = true
                            }
                        },
                        onShareSetup = {
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
                        onSync = viewModel::syncNow,
                        onSetSyncAttachmentsToDisk = viewModel::setSyncAttachmentsToDisk,
                        onSetSyncOnlyOnWifi = viewModel::setSyncOnlyOnWifi,
                        onSetSyncWhileRoaming = viewModel::setSyncWhileRoaming,
                        onSetSyncOnAppLaunch = viewModel::setSyncOnAppLaunch,
                        onSetThemeMode = viewModel.settingsRepository::setThemeMode,
                        onSetThemeAccent = viewModel.settingsRepository::setThemeAccent,
                        onShowObjectTypeColors = { objectTypeColorsDialogVisible = true },
                        onShowHiddenFields = { hiddenFieldsDialogVisible = true },
                        onSetScannerLens = viewModel::setScannerLens,
                        onSetScannerRearLens = viewModel::setScannerRearLens,
                        onUpdatePrintSettings = printSettingsViewModel::update,
                        onSetDefaultPrinter = printSettingsViewModel::setDefaultPrinter,
                        onClearDefaultPrinter = printSettingsViewModel::clearDefaultPrinter,
                        onSetShowTopologyDeviceTypeImages =
                            viewModel.settingsRepository::setShowTopologyDeviceTypeImages,
                        onSetChangeNotificationsEnabled =
                            viewModel::setChangeNotificationsEnabled,
                        onShowChangeNotifications = { changeNotificationsDialogVisible = true },
                        onSetGestureAction = viewModel::setGestureAction,
                        onSetGestureTarget = viewModel::setGestureTarget,
                        onSetGestureDetailTarget = viewModel::setGestureDetailTarget,
                    ),
            )
        }
    }
}
