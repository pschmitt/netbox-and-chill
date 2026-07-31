package dev.pschmitt.netboxandchill.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.pschmitt.netboxandchill.ui.devicedetail.DeviceDetailScreen
import dev.pschmitt.netboxandchill.ui.devices.DeviceListScreen
import dev.pschmitt.netboxandchill.ui.onboarding.OnboardingScreen
import dev.pschmitt.netboxandchill.ui.scanner.ScannerScreen
import dev.pschmitt.netboxandchill.ui.settings.SettingsScreen

@Composable
fun NetBoxNavHost(navController: NavHostController, startDestination: Route) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Route.DeviceList) {
                        popUpTo(Route.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Route.DeviceList> {
            DeviceListScreen(
                onDeviceClick = { id -> navController.navigate(Route.DeviceDetail(id)) },
                onScanClick = { navController.navigate(Route.Scanner) },
                onSettingsClick = { navController.navigate(Route.Settings) },
            )
        }
        composable<Route.DeviceDetail> { backStackEntry ->
            val route: Route.DeviceDetail = backStackEntry.toRoute()
            DeviceDetailScreen(deviceId = route.deviceId, onBack = { navController.popBackStack() })
        }
        composable<Route.Scanner> {
            ScannerScreen(
                onDeviceFound = { id ->
                    navController.navigate(Route.DeviceDetail(id)) {
                        popUpTo(Route.Scanner) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Route.Onboarding) { popUpTo(0) { inclusive = true } }
                },
            )
        }
    }
}
