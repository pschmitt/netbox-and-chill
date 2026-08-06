package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.pschmitt.nyetbox.ui.gestures.LocalActivePointerCount

/**
 * Same shape as Material3's `PullToRefreshBox`, plus [enabled] - `PullToRefreshBox` itself doesn't
 * expose that, but the underlying `Modifier.pullToRefresh` does, which is what lets the gesture be
 * suppressed while [LocalActivePointerCount] is 2+ (see that file for why).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppressiblePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    enabled: Boolean = LocalActivePointerCount.current <= 1,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.pullToRefresh(
            isRefreshing = isRefreshing,
            state = state,
            enabled = enabled,
            onRefresh = onRefresh,
        )
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            modifier = Modifier.align(Alignment.TopCenter),
            isRefreshing = isRefreshing,
            state = state,
        )
    }
}
