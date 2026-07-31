package dev.pschmitt.netboxandchill.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.pschmitt.netboxandchill.ui.devicedetail.DeviceDetailScreen
import dev.pschmitt.netboxandchill.ui.devices.DeviceListScreen
import dev.pschmitt.netboxandchill.ui.generic.GenericDetailScreen
import dev.pschmitt.netboxandchill.ui.generic.GenericListScreen
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.ui.onboarding.OnboardingScreen
import dev.pschmitt.netboxandchill.ui.scanner.ScannerScreen
import dev.pschmitt.netboxandchill.ui.settings.SettingsScreen

@Composable
fun NetBoxNavHost(navController: NavHostController, startDestination: Route, onOpenDrawer: () -> Unit) {
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
                onOpenDrawer = onOpenDrawer,
            )
        }
        composable<Route.DeviceDetail> { backStackEntry ->
            val route: Route.DeviceDetail = backStackEntry.toRoute()
            DeviceDetailScreen(deviceId = route.deviceId, onBack = { navController.popBackStack() })
        }
        composable<Route.GenericList> { backStackEntry ->
            val route: Route.GenericList = backStackEntry.toRoute()
            GenericListScreen(
                onObjectClick = { id -> navController.navigate(Route.Generic(route.endpointPath, id)) },
                onDevicesClick = { navController.navigate(Route.DeviceList) { launchSingleTop = true } },
                onScanClick = { navController.navigate(Route.Scanner) },
                onSettingsClick = { navController.navigate(Route.Settings) },
                onOpenDrawer = onOpenDrawer,
            )
        }
        composable<Route.Generic> {
            GenericDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReference = { endpointPath, id ->
                    navController.navigate(Route.Generic(endpointPath, id))
                },
            )
        }
        composable<Route.Scanner> {
            ScannerScreen(
                onTargetFound = { target ->
                    val destination =
                        when (target) {
                            is NetBoxTarget.Device -> Route.DeviceDetail(target.id)
                            is NetBoxTarget.Object -> Route.Generic(target.endpointPath, target.id)
                        }
                    navController.navigate(destination) { popUpTo(Route.Scanner) { inclusive = true } }
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
