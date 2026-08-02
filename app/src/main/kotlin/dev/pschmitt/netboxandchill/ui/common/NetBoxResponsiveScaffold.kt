package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** True while a screen is being laid out with the tablet navigation rail. */
internal val LocalUseNavigationRail = compositionLocalOf { false }

/**
 * The app's responsive shell: a bottom navigation bar on phones and the same navigation as a
 * left-side rail on wider tablet-sized windows.
 */
@Composable
fun NetBoxResponsiveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    fullScreenOnRail: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        CompositionLocalProvider(LocalUseNavigationRail provides useNavigationRail) {
            Scaffold(
                topBar = topBar,
                snackbarHost = snackbarHost,
                bottomBar = { if (!useNavigationRail) bottomBar() },
            ) { padding ->
                Row(Modifier.fillMaxSize()) {
                    if (useNavigationRail && !fullScreenOnRail) {
                        Box(Modifier.fillMaxHeight()) { bottomBar() }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { content(padding) }
                }
            }
        }
    }
}
