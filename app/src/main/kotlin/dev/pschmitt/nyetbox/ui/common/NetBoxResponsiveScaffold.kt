package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.ui.navigation.Route

/** True while a screen is being laid out with the tablet navigation rail. */
internal val LocalUseNavigationRail = compositionLocalOf { false }

/**
 * The [Route] currently on top of the back stack, provided once per `composable<Route.X>` block
 * in `NetBoxNavHost.kt` - lets [NetBoxBottomBar] highlight the matching slot without threading a
 * new parameter through every screen that hosts it.
 */
internal val LocalCurrentRoute = compositionLocalOf<Route?> { null }

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
                        // `content(padding)` applies `padding` itself below, but this sibling box
                        // shares the same unpadded Row, so without its own top inset the rail
                        // extends up underneath the TopAppBar - occluding and misplacing its
                        // topmost item (invisible and unclickable, though still present and
                        // "clickable" in the semantics tree at its true, occluded bounds).
                        Box(Modifier.fillMaxHeight().padding(top = padding.calculateTopPadding())) {
                            bottomBar()
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { content(padding) }
                }
            }
        }
    }
}
