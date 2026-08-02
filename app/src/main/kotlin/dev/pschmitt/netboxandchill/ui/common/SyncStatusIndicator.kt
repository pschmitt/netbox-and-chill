package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * App-wide "background sync in progress" indicator (NBC-23) - a thin indeterminate progress bar
 * that appears/disappears based on [SyncStatusRepository.isSyncing], independent of which screen
 * happens to be on-screen. Meant to be hosted once, above the navigation host (see `MainActivity`),
 * rather than duplicated into every screen's own `Scaffold` - unlike the existing per-screen
 * `PullToRefreshBox` spinners, this stays correct even while looking at a screen that has nothing
 * to do with the sync that's currently running.
 */
@Composable
fun SyncStatusIndicator(
    modifier: Modifier = Modifier,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = isSyncing, modifier = modifier) {
        LinearProgressIndicator(
            modifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = "Syncing with NetBox"
                    liveRegion = LiveRegionMode.Polite
                }
        )
    }
}
