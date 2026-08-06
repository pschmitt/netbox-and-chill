package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared app-bar and Scaffold shell for generic and specialized item detail screens. */
@Composable
fun ItemDetailScaffold(
    snackbarHostState: SnackbarHostState,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = topBar,
        content = content,
    )
}

/** Shared transparent detail app bar with the editing-aware back/cancel affordance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailTopBar(
    detailAccent: Color,
    onBack: () -> Unit,
    title: @Composable () -> Unit,
    isEditing: Boolean = false,
    onCancelEditing: () -> Unit = onBack,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = detailAccent,
                actionIconContentColor = detailAccent,
            ),
        title = title,
        navigationIcon = {
            IconButton(onClick = if (isEditing) onCancelEditing else onBack) {
                Icon(
                    if (isEditing) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isEditing) "Cancel" else "Back",
                )
            }
        },
        actions = actions,
    )
}

/** Shared tabs plus swipeable lazy-list body used by item detail screens. */
@Composable
fun ItemDetailTabLayout(
    tabs: List<ItemDetailTab>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabCount: Int = tabs.size,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
        ) {
            ItemDetailTabs(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        LazyColumn(
            modifier =
                Modifier.fillMaxWidth().weight(1f).itemTabSwipe(selectedTab, tabCount, onTabSelected),
            contentPadding = PaddingValues(16.dp),
            content = content,
        )
    }
}
