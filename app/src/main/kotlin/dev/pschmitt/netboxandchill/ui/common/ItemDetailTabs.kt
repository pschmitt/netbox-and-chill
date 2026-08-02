package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A tab in an item detail page; index zero is reserved for Overview. */
data class ItemDetailTab(
    val label: String,
    val icon: ImageVector,
    val count: Int? = null,
)

/**
 * Shared detail-page tab control.
 *
 * Overview stays fixed at the leading edge while the related tabs scroll horizontally. This keeps
 * Overview available on narrow phones without forcing the remaining tabs into a second/vertical
 * layout, and gives device and generic item pages the same interaction and visual treatment.
 */
@Composable
fun ItemDetailTabs(
    tabs: List<ItemDetailTab>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailTab(
                tab = tabs.first(),
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.width(104.dp),
            )
            if (tabs.size > 1) {
                Spacer(Modifier.width(4.dp))
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.drop(1).forEachIndexed { index, tab ->
                        DetailTab(
                            tab = tab,
                            selected = selectedTab == index + 1,
                            onClick = { onTabSelected(index + 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTab(
    tab: ItemDetailTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Tab(
        selected = selected,
        onClick = onClick,
        modifier =
            modifier
                .clip(shape)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(tab.icon, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(
                text = tab.count?.let { "${tab.label} ($it)" } ?: tab.label,
                maxLines = 1,
                color =
                    if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

fun overviewDetailTab() = ItemDetailTab("Overview", Icons.Default.Info)
