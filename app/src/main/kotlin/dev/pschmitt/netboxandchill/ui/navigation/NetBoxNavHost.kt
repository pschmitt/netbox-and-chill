package dev.pschmitt.netboxandchill.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.ui.conflicts.EditConflictsScreen
import dev.pschmitt.netboxandchill.ui.dashboard.DashboardScreen
import dev.pschmitt.netboxandchill.ui.dashboard.ObjectChangeDiffScreen
import dev.pschmitt.netboxandchill.ui.devicedetail.DeviceDetailScreen
import dev.pschmitt.netboxandchill.ui.devices.DeviceListScreen
import dev.pschmitt.netboxandchill.ui.generic.AddItemScreen
import dev.pschmitt.netboxandchill.ui.generic.GenericCreateScreen
import dev.pschmitt.netboxandchill.ui.generic.GenericDetailScreen
import dev.pschmitt.netboxandchill.ui.generic.GenericListScreen
import dev.pschmitt.netboxandchill.ui.generic.LINKED_CREATE_RESULT_KEY
import dev.pschmitt.netboxandchill.ui.generic.LinkedCreateResult
import dev.pschmitt.netboxandchill.ui.generic.encodeForSavedState
import dev.pschmitt.netboxandchill.ui.onboarding.OnboardingScreen
import dev.pschmitt.netboxandchill.ui.pending.PendingChangesScreen
import dev.pschmitt.netboxandchill.ui.scanner.ScannerScreen
import dev.pschmitt.netboxandchill.ui.search.GlobalSearchScreen
import dev.pschmitt.netboxandchill.ui.settings.SettingsCategoryScreen
import dev.pschmitt.netboxandchill.ui.settings.SettingsScreen
import dev.pschmitt.netboxandchill.ui.sync.SyncSummaryScreen
import dev.pschmitt.netboxandchill.ui.topology.TopologyScreen

// The typed Device list/cache (NBC-1) is richer (thumbnails, status chips, already-synced) than
// the generic object cache for the same endpoint, which may be empty until separately visited -
// used to special-case the dashboard's "Devices" stat tile onto the existing typed screen instead
// of the generic list route the other stat tiles use (see NBC-9's TODO.md entry).
private const val DEVICES_ENDPOINT_PATH = "api/dcim/devices/"
private const val DEVICE_TYPES_ENDPOINT_PATH = "api/dcim/device-types/"

private fun NavHostController.navigateToObject(endpointPath: String, id: Int) {
    // Offline-created devices have a negative local cache ID until the POST is reconciled. The
    // typed device screen only reads the server-backed DeviceDao, so keep those local objects on
    // the generic cache-first detail screen instead of showing an empty typed page.
    if (endpointPath == DEVICES_ENDPOINT_PATH && id > 0) {
        navigate(Route.DeviceDetail(id))
    } else {
        navigate(Route.Generic(endpointPath, id))
    }
}

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
                },
            )
        }
        composable<Route.Dashboard> {
            DashboardScreen(
                onOpenDrawer = onOpenDrawer,
                onScanClick = { navController.navigate(Route.Scanner()) },
                onSearchClick = {
                    navController.navigate(Route.GlobalSearch) { launchSingleTop = true }
                },
                onSettingsClick = {
                    navController.navigate(Route.Settings) { launchSingleTop = true }
                },
                onAddClick = { navController.navigate(Route.Add) { launchSingleTop = true } },
                onNavigateToReference = { endpointPath, id ->
                    navController.navigateToObject(endpointPath, id)
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
                onChangeDiffClick = { changeId ->
                    navController.navigate(Route.ObjectChangeDiff(changeId))
                },
                onConflictsClick = { navController.navigate(Route.EditConflicts) },
                onPendingChangesClick = { navController.navigate(Route.PendingChanges) },
            )
        }
        composable<Route.ObjectChangeDiff> {
            ObjectChangeDiffScreen(
                onBack = { navController.popBackStack() },
                onOpenChangedObject = { endpointPath, id ->
                    navController.navigateToObject(endpointPath, id)
                },
            )
        }
        composable<Route.PendingChanges> {
            PendingChangesScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.SyncSummary> { backStackEntry ->
            val route: Route.SyncSummary = backStackEntry.toRoute()
            SyncSummaryScreen(summary = route.summary, onBack = { navController.popBackStack() })
        }
        composable<Route.DeviceList> {
            DeviceListScreen(
                onDeviceClick = { id -> navController.navigate(Route.DeviceDetail(id)) },
                onCreateClick = {
                    navController.navigate(Route.GenericCreate(DEVICES_ENDPOINT_PATH, "device"))
                },
                onDashboardClick = {
                    navController.navigate(Route.Dashboard) { launchSingleTop = true }
                },
                onScanClick = { navController.navigate(Route.Scanner()) },
                onOpenDrawer = onOpenDrawer,
                onSearchClick = { navController.navigate(Route.GlobalSearch) },
                onSettingsClick = {
                    navController.navigate(Route.Settings) { launchSingleTop = true }
                },
                onAddClick = { navController.navigate(Route.Add) { launchSingleTop = true } },
            )
        }
        composable<Route.Topology> {
            TopologyScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.DeviceDetail> { backStackEntry ->
            val route: Route.DeviceDetail = backStackEntry.toRoute()
            DeviceDetailScreen(
                deviceId = route.deviceId,
                onBack = { navController.popBackStack() },
                onEditClick = {
                    navController.navigate(
                        Route.Generic(
                            endpointPath = DEVICES_ENDPOINT_PATH,
                            id = route.deviceId,
                            startInEdit = true,
                        )
                    )
                },
                onEditFieldClick = { fieldKey ->
                    navController.navigate(
                        Route.Generic(
                            endpointPath = DEVICES_ENDPOINT_PATH,
                            id = route.deviceId,
                            focusFieldKey = fieldKey,
                        )
                    )
                },
                onDeviceTypeClick = { id, breadcrumb ->
                    navController.navigate(
                        Route.Generic(DEVICE_TYPES_ENDPOINT_PATH, id, breadcrumb)
                    )
                },
                onReferenceClick = { endpointPath, id, breadcrumb ->
                    navController.navigate(Route.Generic(endpointPath, id, breadcrumb))
                },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable<Route.GenericList> { backStackEntry ->
            val route: Route.GenericList = backStackEntry.toRoute()
            GenericListScreen(
                onObjectClick = { id -> navController.navigateToObject(route.endpointPath, id) },
                onCreateClick = {
                    navController.navigate(Route.GenericCreate(route.endpointPath, route.label))
                },
                onDashboardClick = {
                    navController.navigate(Route.Dashboard) { launchSingleTop = true }
                },
                onScanClick = { navController.navigate(Route.Scanner()) },
                onOpenDrawer = onOpenDrawer,
                onSearchClick = { navController.navigate(Route.GlobalSearch) },
                onSettingsClick = {
                    navController.navigate(Route.Settings) { launchSingleTop = true }
                },
                onAddClick = { navController.navigate(Route.Add) { launchSingleTop = true } },
            )
        }
        composable<Route.GlobalSearch> {
            GlobalSearchScreen(
                onResultClick = { endpointPath, id ->
                    navController.navigateToObject(endpointPath, id)
                },
                onBack = { navController.popBackStack() },
                onDashboardClick = {
                    navController.navigate(Route.Dashboard) { launchSingleTop = true }
                },
                onScanClick = { navController.navigate(Route.Scanner()) },
                onSettingsClick = {
                    navController.navigate(Route.Settings) { launchSingleTop = true }
                },
                onAddClick = { navController.navigate(Route.Add) { launchSingleTop = true } },
            )
        }
        composable<Route.Generic> {
            GenericDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReference = { endpointPath, id, breadcrumb ->
                    navController.navigate(Route.Generic(endpointPath, id, breadcrumb))
                },
                onCreateLinkedItem = { fieldKey, endpointPath, label, reopenFocusedEditor ->
                    navController.navigate(
                        Route.GenericCreate(
                            endpointPath = endpointPath,
                            label = label,
                            returnFieldKey = fieldKey,
                            reopenFocusedEditor = reopenFocusedEditor,
                        )
                    )
                },
            )
        }
        composable<Route.Add> {
            AddItemScreen(
                onBack = { navController.popBackStack() },
                onModelClick = { model ->
                    navController.navigate(
                        Route.GenericCreate(model.endpointPath, model.modelLabel)
                    )
                },
                onDashboardClick = {
                    navController.navigate(Route.Dashboard) { launchSingleTop = true }
                },
                onSearchClick = {
                    navController.navigate(Route.GlobalSearch) { launchSingleTop = true }
                },
                onScanClick = { navController.navigate(Route.Scanner()) },
                onSettingsClick = {
                    navController.navigate(Route.Settings) { launchSingleTop = true }
                },
            )
        }
        composable<Route.GenericCreate> { backStackEntry ->
            val route: Route.GenericCreate = backStackEntry.toRoute()
            GenericCreateScreen(
                onBack = { navController.popBackStack() },
                onCreated = { endpointPath, id, display ->
                    if (route.returnFieldKey != null) {
                        val result =
                            LinkedCreateResult(
                                fieldKey = route.returnFieldKey,
                                endpointPath = endpointPath,
                                id = id,
                                display = display ?: "#$id",
                                reopenFocusedEditor = route.reopenFocusedEditor,
                            )
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            LINKED_CREATE_RESULT_KEY,
                            result.encodeForSavedState(),
                        )
                        navController.popBackStack()
                    } else {
                        navController.popBackStack()
                        navController.navigateToObject(endpointPath, id)
                    }
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
                            is NetBoxTarget.DeviceAssetTag -> return@ScannerScreen
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
                    navController.navigate(destination) {
                        popUpTo(Route.Scanner()) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                onDashboardClick = {
                    navController.navigate(Route.Dashboard) { launchSingleTop = true }
                },
                onSearchClick = {
                    navController.navigate(Route.GlobalSearch) { launchSingleTop = true }
                },
                onSettingsClick = {
                    navController.navigate(Route.Settings) { launchSingleTop = true }
                },
                onAddClick = { navController.navigate(Route.Add) { launchSingleTop = true } },
                showBottomBar = !route.fromOnboarding,
            )
        }
        composable<Route.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onCategoryClick = { category ->
                    navController.navigate(Route.SettingsCategory(category))
                },
            )
        }
        composable<Route.SettingsCategory> { backStackEntry ->
            val route: Route.SettingsCategory = backStackEntry.toRoute()
            SettingsCategoryScreen(
                category = route.category,
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
