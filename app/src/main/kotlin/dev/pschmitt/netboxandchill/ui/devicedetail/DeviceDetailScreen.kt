package dev.pschmitt.netboxandchill.ui.devicedetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.ui.common.StatusChip
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: Int,
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Device #$deviceId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    device?.url?.takeIf { it.isNotBlank() }?.let { url ->
                        IconButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = device
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isRefreshing) "Loading…" else "Not cached yet - connect and refresh",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(label = current.statusLabel, value = current.statusValue)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                detailField("Site", current.siteName)
                detailField("Rack", current.rackName)
                detailField("Position", current.position?.toString())
                detailField("Role", current.roleName)
                detailField("Manufacturer", current.manufacturerName)
                detailField("Model", current.deviceTypeModel)
                detailField("Serial", current.serial)
                detailField("Asset tag", current.assetTag)
                detailField("Primary IP", current.primaryIp)
                detailField("Comments", current.comments)
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Last synced ${DateFormat.getDateTimeInstance().format(Date(current.syncedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.detailField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    item {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
