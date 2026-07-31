package dev.pschmitt.netboxandchill

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.scanner.NetBoxUrlParser
import dev.pschmitt.netboxandchill.ui.common.SyncStatusIndicator
import dev.pschmitt.netboxandchill.ui.directory.Sidebar
import dev.pschmitt.netboxandchill.ui.navigation.NetBoxNavHost
import dev.pschmitt.netboxandchill.ui.navigation.Route
import dev.pschmitt.netboxandchill.ui.theme.NetBoxAndChillTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private var pendingTarget by mutableStateOf<NetBoxTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingTarget = extractTarget(intent)

        setContent {
            NetBoxAndChillTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()
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
                    if (settingsRepository.isConfigured) {
                        val destination =
                            when (target) {
                                is NetBoxTarget.Device -> Route.DeviceDetail(target.id)
                                is NetBoxTarget.Object ->
                                    Route.Generic(target.endpointPath, target.id)
                            }
                        navController.navigate(destination)
                    }
                    pendingTarget = null
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
                            onSettingsClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Route.Settings)
                            },
                        )
                    },
                ) {
                    // SyncStatusIndicator is layered above the nav host (not inside any one
                    // screen's own Scaffold) so it reflects SyncWorker's WorkManager state
                    // regardless of which screen is currently showing - see NBC-23.
                    Box(Modifier.fillMaxSize()) {
                        NetBoxNavHost(
                            navController = navController,
                            startDestination = startDestination,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        )
                        SyncStatusIndicator(
                            modifier =
                                Modifier.align(Alignment.TopCenter)
                                    .windowInsetsPadding(WindowInsets.statusBars)
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTarget = extractTarget(intent)
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
}
