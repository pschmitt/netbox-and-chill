package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** NetBox component models that can be attached to a device. */
data class DeviceComponentKind(
    val label: String,
    val endpointPath: String,
    val icon: ImageVector,
)

val deviceComponentKinds =
    listOf(
        DeviceComponentKind("Interface", "api/dcim/interfaces/", Icons.Default.Lan),
        DeviceComponentKind("Front port", "api/dcim/front-ports/", Icons.Default.Cable),
        DeviceComponentKind("Rear port", "api/dcim/rear-ports/", Icons.Default.Cable),
        DeviceComponentKind("Console port", "api/dcim/console-ports/", Icons.Default.Cable),
        DeviceComponentKind("Power port", "api/dcim/power-ports/", Icons.Default.Power),
        DeviceComponentKind("Power outlet", "api/dcim/power-outlets/", Icons.Default.Power),
        DeviceComponentKind("Module bay", "api/dcim/module-bays/", Icons.Default.Memory),
        DeviceComponentKind("Device bay", "api/dcim/device-bays/", Icons.Default.Storage),
        DeviceComponentKind("Inventory item", "api/dcim/inventory-items/", Icons.Default.Inventory2),
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddComponentScreen(
    onBack: () -> Unit,
    onComponentClick: (DeviceComponentKind) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add component") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(
                    "Choose the component type to add to this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            items(deviceComponentKinds, key = DeviceComponentKind::endpointPath) { component ->
                ListItem(
                    leadingContent = { Icon(component.icon, contentDescription = null) },
                    headlineContent = { Text(component.label) },
                    supportingContent = { Text("Create a ${component.label.lowercase()} on this device") },
                    modifier = Modifier.clickable { onComponentClick(component) },
                )
            }
        }
    }
}
