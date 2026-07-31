package dev.pschmitt.netboxandchill.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val credentials by viewModel.settingsRepository.credentials.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val cachedDeviceCount by viewModel.cachedDeviceCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("NetBox instance") },
                supportingContent = { Text(credentials.baseUrl) },
            )
            ListItem(
                headlineContent = { Text("Cached devices") },
                supportingContent = { Text("$cachedDeviceCount devices synced locally") },
            )
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = viewModel::syncNow,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSyncing) "Syncing…" else "Sync now")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.logOut()
                        onLoggedOut()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disconnect")
                }
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("NetBox and Chill") },
                supportingContent = {
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_REVISION}) · GPLv3")
                },
            )
        }
    }
}
