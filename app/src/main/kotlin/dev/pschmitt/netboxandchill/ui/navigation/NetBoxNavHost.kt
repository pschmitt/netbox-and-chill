package dev.pschmitt.netboxandchill.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.pschmitt.netboxandchill.ui.dashboard.DashboardScreen
import dev.pschmitt.netboxandchill.ui.dashboard.ObjectChangeDiffScreen
import dev.pschmitt.netboxandchill.ui.conflicts.EditConflictsScreen
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
private const val DEVICE_TYPES_ENDPOINT_PATH = "api/dcim/device-types/"

@Composable
fun NetBoxNavHost(
    navController: NavHostController,
    startDestination: Route,
    onOpenDrawer: () -> Unit,
    setup: NetBoxTarget.Setup?,
    onSetupImport: (NetBoxTarget.Setup) -> Unit,
    onSetupConsumed: () -> Unit,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                initialSetup = setup,
                onScanSetupClick = { navController.navigate(Route.Scanner(fromOnboarding = true)) },
                onDone = {
                    onSetupConsumed()
                    navController.navigate(Route.Dashboard) {
                        popUpTo(Route.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Route.Dashboard> {
            DashboardScreen(
                onOpenDrawer = onOpenDrawer,
                onScanClick = { navController.navigate(Route.Scanner()) },
                onSearchClick = { navController.navigate(Route.GlobalSearch) { launchSingleTop = true } },
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
                onChangeDiffClick = { changeId -> navController.navigate(Route.ObjectChangeDiff(changeId)) },
                onConflictsClick = { navController.navigate(Route.EditConflicts) },
            )
        }
        composable<Route.ObjectChangeDiff> {
            ObjectChangeDiffScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.DeviceList> {
            DeviceListScreen(
                onDeviceClick = { id -> navController.navigate(Route.DeviceDetail(id)) },
                onDashboardClick = { navController.navigate(Route.Dashboard) { launchSingleTop = true } },
                onScanClick = { navController.navigate(Route.Scanner()) },
                onOpenDrawer = onOpenDrawer,
                onSearchClick = { navController.navigate(Route.GlobalSearch) },
            )
        }
        composable<Route.DeviceDetail> { backStackEntry ->
            val route: Route.DeviceDetail = backStackEntry.toRoute()
            DeviceDetailScreen(
                deviceId = route.deviceId,
                onBack = { navController.popBackStack() },
                onEditClick = {
                    navController.navigate(Route.Generic(DEVICES_ENDPOINT_PATH, route.deviceId))
                },
                onDeviceTypeClick = { id -> navController.navigate(Route.Generic(DEVICE_TYPES_ENDPOINT_PATH, id)) },
                onReferenceClick = { endpointPath, id -> navController.navigate(Route.Generic(endpointPath, id)) },
            )
        }
        composable<Route.GenericList> { backStackEntry ->
            val route: Route.GenericList = backStackEntry.toRoute()
            GenericListScreen(
                onObjectClick = { id -> navController.navigate(Route.Generic(route.endpointPath, id)) },
                onDashboardClick = { navController.navigate(Route.Dashboard) { launchSingleTop = true } },
                onScanClick = { navController.navigate(Route.Scanner()) },
                onOpenDrawer = onOpenDrawer,
                onSearchClick = { navController.navigate(Route.GlobalSearch) },
            )
        }
        composable<Route.GlobalSearch> {
            GlobalSearchScreen(
                onResultClick = { endpointPath, id -> navController.navigate(Route.Generic(endpointPath, id)) },
                onBack = { navController.popBackStack() },
                onDashboardClick = { navController.navigate(Route.Dashboard) { launchSingleTop = true } },
                onScanClick = { navController.navigate(Route.Scanner()) },
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
        composable<Route.Scanner> { backStackEntry ->
            val route: Route.Scanner = backStackEntry.toRoute()
            ScannerScreen(
                onTargetFound = { target ->
                    val destination =
                        when (target) {
                            is NetBoxTarget.Device -> Route.DeviceDetail(target.id)
                            is NetBoxTarget.Object -> Route.Generic(target.endpointPath, target.id)
                            is NetBoxTarget.Setup -> {
                                onSetupImport(target)
                                navController.navigate(Route.Onboarding) {
                                    popUpTo(Route.Scanner()) { inclusive = true }
                                    launchSingleTop = true
                                }
                                return@ScannerScreen
                            }
                        }
                    navController.navigate(destination) { popUpTo(Route.Scanner()) { inclusive = true } }
                },
                onBack = { navController.popBackStack() },
                onDashboardClick = { navController.navigate(Route.Dashboard) { launchSingleTop = true } },
                onSearchClick = { navController.navigate(Route.GlobalSearch) { launchSingleTop = true } },
                showBottomBar = !route.fromOnboarding,
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
        composable<Route.EditConflicts> {
            EditConflictsScreen(onBack = { navController.popBackStack() })
        }
    }
}
