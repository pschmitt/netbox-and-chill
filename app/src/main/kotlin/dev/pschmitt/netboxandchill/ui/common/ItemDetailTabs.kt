package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A tab in an item detail page. */
data class ItemDetailTab(
    val label: String,
    val icon: ImageVector,
    val count: Int? = null,
)

/**
 * Shared detail-page tab control.
 *
 * All tabs, including Overview, use the regular Material 3 scrollable tab treatment. This keeps
 * the interaction and visual treatment identical on device and generic item pages while still
 * making long tab lists usable on narrow screens.
 */
@Composable
fun ItemDetailTabs(
    tabs: List<ItemDetailTab>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex),
        modifier = modifier.fillMaxWidth(),
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                    if (tab.count != null && tab.count > 0) {
                        BadgedBox(badge = { Badge { Text(tab.count.toString()) } }) {
                            Icon(tab.icon, contentDescription = null)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null)
                    }
                },
                text = { Text(tab.label, maxLines = 1) },
            )
        }
    }
}

fun overviewDetailTab() = ItemDetailTab("Overview", Icons.Default.Info)
