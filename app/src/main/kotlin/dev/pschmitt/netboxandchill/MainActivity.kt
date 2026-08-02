package dev.pschmitt.netboxandchill

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.GestureAction
import dev.pschmitt.netboxandchill.data.repository.GestureShortcut
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.crash.CrashReportStore
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.scanner.NetBoxUrlParser
import dev.pschmitt.netboxandchill.sync.SyncNotifier
import dev.pschmitt.netboxandchill.ui.directory.Sidebar
import dev.pschmitt.netboxandchill.ui.common.CrashReportDialog
import dev.pschmitt.netboxandchill.ui.gestures.SwipeDirection
import dev.pschmitt.netboxandchill.ui.gestures.multiFingerSwipe
import dev.pschmitt.netboxandchill.ui.navigation.NetBoxNavHost
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import dev.pschmitt.netboxandchill.ui.navigation.Route
import dev.pschmitt.netboxandchill.ui.settings.SettingsCategory
import dev.pschmitt.netboxandchill.ui.theme.NetBoxAndChillTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var syncNotifier: SyncNotifier
    @Inject lateinit var syncScheduler: SyncScheduler

    private var pendingTarget by mutableStateOf<NetBoxTarget?>(null)
    private var pendingSetup by mutableStateOf<NetBoxTarget.Setup?>(null)
    private var pendingReconciliationSummary by mutableStateOf<String?>(null)
    private var pendingCrashReport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingCrashReport = CrashReportStore(applicationContext).takePending()
        pendingTarget = extractTarget(intent)
        pendingReconciliationSummary =
            intent.getStringExtra(SyncNotifier.EXTRA_RECONCILIATION_SUMMARY)

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle()
            val themeAccent by settingsRepository.themeAccent.collectAsStateWithLifecycle()
            NetBoxAndChillTheme(themeMode = themeMode, accent = themeAccent) {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()
                val gestureActions by
                    settingsRepository.gestureActions.collectAsStateWithLifecycle()
                val gestureTargets by
                    settingsRepository.gestureTargets.collectAsStateWithLifecycle()
                val startDestination =
                    if (settingsRepository.isConfigured) Route.Dashboard else Route.Onboarding

                // Background sync failure notifications (NBC-23) need POST_NOTIFICATIONS on API
                // 33+ - requested once at startup, same shape as ScannerScreen's CAMERA request.
                // Denial just means SyncNotifier silently skips posting later; nothing here
                // blocks on the result.
                val notificationPermissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) {}
                LaunchedEffect(Unit) {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                }

                // Handles both cold start (pendingTarget set above, before setContent) and a warm
                // relaunch of this singleTask activity via onNewIntent below.
                LaunchedEffect(pendingTarget) {
                    val target = pendingTarget ?: return@LaunchedEffect
                    when (target) {
                        is NetBoxTarget.Setup -> {
                            pendingSetup = target
                            if (settingsRepository.isConfigured) {
                                navController.navigate(Route.Onboarding) { launchSingleTop = true }
                            }
                        }
                        is NetBoxTarget.Device,
                        is NetBoxTarget.Object -> {
                            if (settingsRepository.isConfigured) {
                                val destination =
                                    when (target) {
                                        is NetBoxTarget.Device -> Route.DeviceDetail(target.id)
                                        is NetBoxTarget.Object ->
                                            Route.Generic(target.endpointPath, target.id)
                                        is NetBoxTarget.Setup -> error("unreachable")
                                    }
                                navController.navigate(destination)
                            }
                        }
                        is NetBoxTarget.DeviceAssetTag -> {
                            if (settingsRepository.isConfigured) {
                                deviceRepository.findByAssetTag(target.assetTag)?.let { device ->
                                    navController.navigate(Route.DeviceDetail(device.id))
                                }
                            }
                        }
                    }
                    pendingTarget = null
                }

                LaunchedEffect(pendingReconciliationSummary) {
                    val summary = pendingReconciliationSummary ?: return@LaunchedEffect
                    if (settingsRepository.isConfigured) {
                        navController.navigate(Route.SyncSummary(summary))
                    }
                    pendingReconciliationSummary = null
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        Sidebar(
                            onDeviceListClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Route.DeviceList) { launchSingleTop = true }
                            },
                            onModelClick = { model ->
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(
                                    Route.GenericList(model.endpointPath, model.modelLabel)
                                ) {
                                    launchSingleTop = true
                                }
                            },
                            onTopologyClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Route.Topology) { launchSingleTop = true }
                            },
                            onSettingsClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Route.Settings)
                            },
                            onAboutClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Route.SettingsCategory(SettingsCategory.About))
                            },
                        )
                    },
                ) {
                    val performGestureAction: (GestureShortcut, GestureAction) -> Unit =
                        { shortcut, action ->
                        when (action) {
                            GestureAction.GlobalSearch ->
                                navController.navigate(Route.GlobalSearch) {
                                    launchSingleTop = true
                                }
                            GestureAction.Scanner -> navController.navigate(Route.Scanner())
                            GestureAction.Settings -> navController.navigate(Route.Settings)
                            GestureAction.Add -> navController.navigate(Route.Add)
                            GestureAction.AddSpecific ->
                                gestureTargets[shortcut]?.let { target ->
                                    navController.navigate(
                                        Route.GenericCreate(target.endpointPath, target.label)
                                    )
                                }
                                    ?: navController.navigate(Route.Add)
                            GestureAction.Sync -> syncScheduler.syncNow()
                            GestureAction.OfflineOn -> settingsRepository.setOfflineMode(true)
                            GestureAction.OfflineOff -> settingsRepository.setOfflineMode(false)
                            GestureAction.DeviceList -> navController.navigate(Route.DeviceList)
                            GestureAction.ListSpecific ->
                                gestureTargets[shortcut]?.let { target ->
                                    navController.navigate(
                                        Route.GenericList(target.endpointPath, target.label)
                                    )
                                }
                            GestureAction.DetailSpecific ->
                                gestureTargets[shortcut]?.let { target ->
                                    val id = target.id ?: return@let
                                    val destination =
                                        if (target.endpointPath == NetBoxRef.DEVICES_ENDPOINT_PATH && id > 0) {
                                            Route.DeviceDetail(id)
                                        } else {
                                            Route.Generic(target.endpointPath, id, target.label)
                                        }
                                    navController.navigate(destination)
                                }
                            GestureAction.Off -> Unit
                        }
                    }
                    val gestureModifier =
                        if (!settingsRepository.isConfigured) {
                            Modifier
                        } else {
                            Modifier
                                .multiFingerSwipe(2, SwipeDirection.Down) {
                                    performGestureAction(
                                        GestureShortcut.TwoFingerDown,
                                        gestureActions[GestureShortcut.TwoFingerDown]
                                            ?: GestureAction.Off
                                    )
                                }
                                .multiFingerSwipe(2, SwipeDirection.Left) {
                                    performGestureAction(
                                        GestureShortcut.TwoFingerLeft,
                                        gestureActions[GestureShortcut.TwoFingerLeft]
                                            ?: GestureAction.Off
                                    )
                                }
                                .multiFingerSwipe(2, SwipeDirection.Right) {
                                    performGestureAction(
                                        GestureShortcut.TwoFingerRight,
                                        gestureActions[GestureShortcut.TwoFingerRight]
                                            ?: GestureAction.Off
                                    )
                                }
                                .multiFingerSwipe(3, SwipeDirection.Up) {
                                    performGestureAction(
                                        GestureShortcut.ThreeFingerUp,
                                        gestureActions[GestureShortcut.ThreeFingerUp]
                                            ?: GestureAction.Off
                                    )
                                }
                                .multiFingerSwipe(3, SwipeDirection.Down) {
                                    performGestureAction(
                                        GestureShortcut.ThreeFingerDown,
                                        gestureActions[GestureShortcut.ThreeFingerDown]
                                            ?: GestureAction.Off
                                    )
                                }
                                .multiFingerSwipe(3, SwipeDirection.Left) {
                                    performGestureAction(
                                        GestureShortcut.ThreeFingerLeft,
                                        gestureActions[GestureShortcut.ThreeFingerLeft]
                                            ?: GestureAction.Off
                                    )
                                }
                                .multiFingerSwipe(3, SwipeDirection.Right) {
                                    performGestureAction(
                                        GestureShortcut.ThreeFingerRight,
                                        gestureActions[GestureShortcut.ThreeFingerRight]
                                            ?: GestureAction.Off
                                    )
                                }
                        }
                    Box(Modifier.fillMaxSize().then(gestureModifier)) {
                        NetBoxNavHost(
                            navController = navController,
                            startDestination = startDestination,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            setup = pendingSetup,
                            onSetupImport = { setup ->
                                pendingSetup = setup
                            },
                            onSetupConsumed = { pendingSetup = null },
                        )
                    }
                }
                pendingCrashReport?.let { report ->
                    CrashReportDialog(
                        report = report,
                        onCopy = { copyCrashReport(report) },
                        onRestart = ::restartApplication,
                        onDismiss = { pendingCrashReport = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTarget = extractTarget(intent)
        pendingReconciliationSummary =
            intent.getStringExtra(SyncNotifier.EXTRA_RECONCILIATION_SUMMARY)
    }

    override fun onStart() {
        super.onStart()
        syncNotifier.onAppForeground()
    }

    override fun onStop() {
        syncNotifier.onAppBackground()
        super.onStop()
    }

    private fun extractTarget(intent: Intent?): NetBoxTarget? {
        val text =
            when (intent?.action) {
                Intent.ACTION_VIEW -> intent.dataString
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            }
        return text?.let { NetBoxUrlParser.parse(it) }
    }

    private fun copyCrashReport(report: String) {
        getSystemService<ClipboardManager>()?.setPrimaryClip(
            ClipData.newPlainText("Crash report", report)
        )
        Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show()
    }

    private fun restartApplication() {
        pendingCrashReport = null
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(launchIntent)
        }
        finishAffinity()
    }
}
