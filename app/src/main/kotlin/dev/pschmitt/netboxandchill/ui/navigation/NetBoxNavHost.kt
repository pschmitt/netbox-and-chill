package dev.pschmitt.netboxandchill.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.pschmitt.netboxandchill.ui.dashboard.DashboardScreen
import dev.pschmitt.netboxandchill.ui.devicedetail.DeviceDetailScreen
import dev.pschmitt.netboxandchill.ui.devices.DeviceListScreen
import dev.pschmitt.netboxandchill.ui.generic.GenericDetailScreen
import dev.pschmitt.netboxandchill.ui.generic.GenericListScreen
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.ui.onboarding.OnboardingScreen
import dev.pschmitt.netboxandchill.ui.scanner.ScannerScreen
import dev.pschmitt.netboxandchill.ui.search.GlobalSearchScreen
import dev.pschmitt.netboxandchill.ui.settings.SettingsScreen

// The typed Device list/cache (NBC-1) is richer (thumbnails, status chips, already-synced) than
// the generic object cache for the same endpoint, which may be empty until separately visited -
// used to special-case the dashboard's "Devices" stat tile onto the existing typed screen instead
// of the generic list route the other stat tiles use (see NBC-9's TODO.md entry).
private const val DEVICES_ENDPOINT_PATH = "api/dcim/devices/"

@Composable
fun NetBoxNavHost(navController: NavHostController, startDestination: Route, onOpenDrawer: () -> Unit) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Route.Dashboard) {
                        popUpTo(Route.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Route.Dashboard> {
            DashboardScreen(
                onOpenDrawer = onOpenDrawer,
                onDevicesClick = { navController.navigate(Route.DeviceList) { launchSingleTop = true } },
                onScanClick = { navController.navigate(Route.Scanner) },
                onNavigateToReference = { endpointPath, id ->
                    navController.navigate(Route.Generic(endpointPath, id))
                },
                onStatClick = { endpointPath, label ->
                    if (endpointPath == DEVICES_ENDPOINT_PATH) {
                        navController.navigate(Route.DeviceList) { launchSingleTop = true }
                    } else {
                        navController.navigate(Route.GenericList(endpointPath, label)) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable<Route.DeviceList> {
            DeviceListScreen(
                onDeviceClick = { id -> navController.navigate(Route.DeviceDetail(id)) },
                onDashboardClick = { navController.navigate(Route.Dashboard) { launchSingleTop = true } },
                onScanClick = { navController.navigate(Route.Scanner) },
                onOpenDrawer = onOpenDrawer,
                onSearchClick = { navController.navigate(Route.GlobalSearch) },
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
                onDashboardClick = { navController.navigate(Route.Dashboard) { launchSingleTop = true } },
                onDevicesClick = { navController.navigate(Route.DeviceList) { launchSingleTop = true } },
                onScanClick = { navController.navigate(Route.Scanner) },
                onOpenDrawer = onOpenDrawer,
                onSearchClick = { navController.navigate(Route.GlobalSearch) },
            )
        }
        composable<Route.GlobalSearch> {
            GlobalSearchScreen(
                onResultClick = { endpointPath, id -> navController.navigate(Route.Generic(endpointPath, id)) },
                onBack = { navController.popBackStack() },
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
