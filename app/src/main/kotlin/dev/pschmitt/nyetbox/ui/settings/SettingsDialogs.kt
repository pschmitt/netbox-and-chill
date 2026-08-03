package dev.pschmitt.nyetbox.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.db.NetBoxModelEntity
import dev.pschmitt.nyetbox.data.repository.ChangeNotificationFilter
import dev.pschmitt.nyetbox.data.repository.ThemeAccent
import dev.pschmitt.nyetbox.data.repository.normalizeHiddenFieldPreferenceKey
import dev.pschmitt.nyetbox.ui.common.visualColorForEndpointPath
import dev.pschmitt.nyetbox.ui.directory.AppIcons

internal fun selectedChangeNotificationSummary(filters: Set<String>): String {
    val selected =
        if (ChangeNotificationFilter.All.storageKey in filters) {
            listOf(ChangeNotificationFilter.All.label)
        } else {
            ChangeNotificationFilter.entries
                .filter { it.storageKey in filters }
                .map { it.label }
        }
    return if (selected.isEmpty()) {
        "No change types selected"
    } else {
        "Notify about " + selected.joinToString(", ")
    }
}

@Composable
internal fun ChangeNotificationsDialog(
    filters: Set<String>,
    onFilterChanged: (ChangeNotificationFilter, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NetBox change notifications") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Choose which new changes should appear as a silent notification when the app is in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                ChangeNotificationFilter.entries.forEach { filter ->
                    val checked =
                        if (ChangeNotificationFilter.All.storageKey in filters) {
                            filter == ChangeNotificationFilter.All
                        } else {
                            filter.storageKey in filters
                        }
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                onFilterChanged(filter, !checked)
                            },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onFilterChanged(filter, it) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(filter.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
internal fun HiddenFieldsDialog(
    keys: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newKey by remember { mutableStateOf("") }
    val normalizedKey = normalizeHiddenFieldPreferenceKey(newKey)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden fields") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Use object/field keys such as device/model. Long-press a field to add it here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                keys.sorted().forEach { key ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(key, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(key) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Remove $key")
                        }
                    }
                }
                if (keys.isEmpty()) {
                    Text(
                        "No fields are hidden by default.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("Object/field key") },
                    placeholder = { Text("device/model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        normalizedKey?.let {
                            onAdd(it)
                            newKey = ""
                        }
                    },
                    enabled = normalizedKey != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hide field by default")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * Edit the configured NetBox base URL (NBC-39). Save triggers [SettingsViewModel.updateBaseUrl],
 * which validates reachability before committing and reverts on failure - this dialog doesn't wait
 * around for that, it dismisses immediately and any failure surfaces via the screen's existing
 * Snackbar, same as every other async action here.
 */
@Composable
internal fun EditServerDialog(
    currentBaseUrl: String,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(currentBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change NetBox server") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("NetBox URL") },
                singleLine = true,
                enabled = !isUpdating,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = !isUpdating && text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUpdating) { Text("Cancel") }
        },
    )
}

@Composable
internal fun SetupQrDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share NetBox setup") },
        text = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "NetBox setup QR code",
                    modifier = Modifier.size(280.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "This QR code contains the NetBox server URL and API token. Scan it from the login screen on a trusted device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun ObjectTypeColorsDialog(
    models: List<NetBoxModelEntity>,
    accents: Map<String, ThemeAccent>,
    onAccentChanged: (String, ThemeAccent?) -> Unit,
    onDismiss: () -> Unit,
) {
    val distinctModels = models.distinctBy { it.endpointPath }.sortedBy { it.modelLabel.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Object type colors") },
        text = {
            if (distinctModels.isEmpty()) {
                Text("Object types will appear here after the directory has been synced.")
            } else {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Choose a color for each cached object type. Automatic uses the built-in palette.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    distinctModels.forEach { model ->
                        val key = model.endpointPath.trim('/')
                        var menuExpanded by remember(key) { mutableStateOf(false) }
                        val selected = accents[key]
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Surface(
                                color =
                                    visualColorForEndpointPath(
                                            model.endpointPath,
                                            selected,
                                            MaterialTheme.colorScheme,
                                        )
                                        .copy(alpha = 0.18f),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Icon(
                                    AppIcons.forEndpointPath(model.endpointPath),
                                    contentDescription = null,
                                    tint =
                                        visualColorForEndpointPath(
                                            model.endpointPath,
                                            selected,
                                            MaterialTheme.colorScheme,
                                        ),
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(model.modelLabel, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    selected?.label ?: ThemeAccent.System.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Choose ${model.modelLabel} color")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(ThemeAccent.System.label) },
                                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                        onClick = {
                                            onAccentChanged(model.endpointPath, null)
                                            menuExpanded = false
                                        },
                                    )
                                    ThemeAccent.entries
                                        .filter { it != ThemeAccent.System }
                                        .forEach { accent ->
                                            DropdownMenuItem(
                                                text = { Text(accent.label) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Palette, contentDescription = null)
                                                },
                                                onClick = {
                                                    onAccentChanged(model.endpointPath, accent)
                                                    menuExpanded = false
                                                },
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
