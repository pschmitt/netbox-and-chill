package dev.pschmitt.netboxandchill

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.scanner.DeviceUrlParser
import dev.pschmitt.netboxandchill.ui.navigation.NetBoxNavHost
import dev.pschmitt.netboxandchill.ui.navigation.Route
import dev.pschmitt.netboxandchill.ui.theme.NetBoxAndChillTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private var pendingDeviceId by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingDeviceId = extractDeviceId(intent)

        setContent {
            NetBoxAndChillTheme {
                val navController = rememberNavController()
                val startDestination =
                    if (settingsRepository.isConfigured) Route.DeviceList else Route.Onboarding

                // Handles both cold start (pendingDeviceId set above, before setContent) and a
                // warm relaunch of this singleTask activity via onNewIntent below.
                LaunchedEffect(pendingDeviceId) {
                    val id = pendingDeviceId ?: return@LaunchedEffect
                    if (settingsRepository.isConfigured) {
                        navController.navigate(Route.DeviceDetail(id))
                    }
                    pendingDeviceId = null
                }

                NetBoxNavHost(navController = navController, startDestination = startDestination)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeviceId = extractDeviceId(intent)
    }

    private fun extractDeviceId(intent: Intent?): Int? {
        val text =
            when (intent?.action) {
                Intent.ACTION_VIEW -> intent.dataString
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            }
        return text?.let { DeviceUrlParser.parseDeviceId(it) }
    }
}
