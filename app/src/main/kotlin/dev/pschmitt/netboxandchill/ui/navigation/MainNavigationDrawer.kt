package dev.pschmitt.netboxandchill.ui.navigation

import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.ui.directory.Sidebar
import kotlinx.coroutines.launch

/** Application drawer shell kept separate from lifecycle/intent orchestration in MainActivity. */
@Composable
internal fun MainNavigationDrawer(
    drawerState: DrawerState,
    onDeviceListClick: () -> Unit,
    onModelClick: (NetBoxModelEntity) -> Unit,
    onTopologyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Sidebar(
                onDeviceListClick = { closeThen(onDeviceListClick) },
                onModelClick = { model -> closeThen { onModelClick(model) } },
                onTopologyClick = { closeThen(onTopologyClick) },
                onSettingsClick = { closeThen(onSettingsClick) },
                onAboutClick = { closeThen(onAboutClick) },
            )
        },
        content = content,
    )
}
