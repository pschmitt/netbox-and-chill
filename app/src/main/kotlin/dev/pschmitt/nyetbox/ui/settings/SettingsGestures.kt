package dev.pschmitt.nyetbox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.db.NetBoxModelEntity
import dev.pschmitt.nyetbox.data.db.NetBoxObjectEntity
import dev.pschmitt.nyetbox.data.repository.GestureAction
import dev.pschmitt.nyetbox.data.repository.GestureShortcut
import dev.pschmitt.nyetbox.data.repository.GestureTarget

@Composable
internal fun GestureShortcutRow(
    shortcut: GestureShortcut,
    action: GestureAction,
    target: GestureTarget?,
    models: List<dev.pschmitt.nyetbox.data.db.NetBoxModelEntity>,
    objects: List<NetBoxObjectEntity>,
    onActionSelected: (GestureAction) -> Unit,
    onTargetSelected: (dev.pschmitt.nyetbox.data.db.NetBoxModelEntity) -> Unit,
    onDetailTargetSelected: (NetBoxObjectEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var targetPickerVisible by remember { mutableStateOf(false) }
    var targetQuery by remember { mutableStateOf("") }
    var detailModel by remember {
        mutableStateOf<dev.pschmitt.nyetbox.data.db.NetBoxModelEntity?>(null)
    }
    val actionLabel =
        target?.let { configured -> "${action.label}: ${configured.label}" } ?: action.label
    SettingsListItem(
        modifier = Modifier.clickable { expanded = true },
        leadingContent = {
            Icon(
                when {
                    shortcut.label.contains("down", ignoreCase = true) ->
                        Icons.Default.KeyboardArrowDown
                    shortcut.label.contains("up", ignoreCase = true) ->
                        Icons.Default.KeyboardArrowUp
                    shortcut.label.contains("left", ignoreCase = true) ->
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    shortcut.label.contains("right", ignoreCase = true) ->
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    else -> Icons.Default.TouchApp
                },
                contentDescription = null,
            )
        },
        headlineContent = { Text(shortcut.label) },
        supportingContent = { Text(actionLabel) },
        trailingContent = {
            Box {
                Icon(Icons.Default.ExpandMore, contentDescription = null)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    GestureAction.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.label) },
                            leadingIcon = {
                                Icon(
                                    when (candidate) {
                                        GestureAction.Off -> Icons.Default.Block
                                        GestureAction.GlobalSearch -> Icons.Default.Search
                                        GestureAction.Scanner -> Icons.Default.QrCodeScanner
                                        GestureAction.Settings -> Icons.Default.Info
                                        GestureAction.Add,
                                        GestureAction.AddSpecific -> Icons.Default.Add
                                        GestureAction.Sync -> Icons.Default.Sync
                                        GestureAction.OfflineOn,
                                        GestureAction.OfflineOff -> Icons.Default.CloudOff
                                        GestureAction.SwitchServer -> Icons.Default.SwapHoriz
                                        GestureAction.DeviceList,
                                        GestureAction.ListSpecific,
                                        GestureAction.DetailSpecific -> Icons.Default.Storage
                                    },
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onActionSelected(candidate)
                                expanded = false
                                if (
                                    candidate == GestureAction.AddSpecific ||
                                        candidate == GestureAction.ListSpecific ||
                                        candidate == GestureAction.DetailSpecific
                                ) {
                                    targetQuery = ""
                                    detailModel = null
                                    targetPickerVisible = true
                                }
                            },
                        )
                    }
                }
            }
        },
    )
    if (targetPickerVisible) {
        val filteredModels = models.filter { model ->
            targetQuery.isBlank() ||
                model.modelLabel.contains(targetQuery, ignoreCase = true) ||
                model.appLabel.contains(targetQuery, ignoreCase = true)
        }
        val filteredObjects =
            detailModel
                ?.let { selectedModel ->
                    objects
                        .asSequence()
                        .filter { it.endpointPath == selectedModel.endpointPath }
                        .filter { obj ->
                            targetQuery.isBlank() ||
                                obj.display.contains(targetQuery, ignoreCase = true) ||
                                obj.secondaryLine
                                    .orEmpty()
                                    .contains(targetQuery, ignoreCase = true) ||
                                obj.json.contains(targetQuery, ignoreCase = true)
                        }
                        .toList()
                }
                .orEmpty()
        AlertDialog(
            onDismissRequest = {
                targetPickerVisible = false
                detailModel = null
            },
            title = {
                Text(
                    if (action == GestureAction.DetailSpecific && detailModel != null) {
                        "Choose cached ${detailModel!!.modelLabel.lowercase()}"
                    } else {
                        "Choose item type"
                    }
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = targetQuery,
                        onValueChange = { targetQuery = it },
                        label = {
                            Text(
                                if (action == GestureAction.DetailSpecific && detailModel != null) {
                                    "Search cached items"
                                } else {
                                    "Search item types"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (action == GestureAction.DetailSpecific && detailModel != null) {
                        if (filteredObjects.isEmpty()) {
                            Text(
                                "No matching cached items",
                                modifier = Modifier.padding(top = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        filteredObjects.forEach { obj ->
                            SettingsListItem(
                                modifier =
                                    Modifier.clickable {
                                        onDetailTargetSelected(obj)
                                        targetPickerVisible = false
                                        detailModel = null
                                    },
                                leadingContent = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                },
                                headlineContent = { Text(obj.display) },
                                supportingContent = { obj.secondaryLine?.let { Text(it) } },
                            )
                        }
                    } else {
                        filteredModels.forEach { model ->
                            SettingsListItem(
                                modifier =
                                    Modifier.clickable {
                                        if (action == GestureAction.DetailSpecific) {
                                            detailModel = model
                                            targetQuery = ""
                                        } else {
                                            onTargetSelected(model)
                                            targetPickerVisible = false
                                        }
                                    },
                                leadingContent = {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                },
                                headlineContent = { Text(model.modelLabel) },
                                supportingContent = { Text(model.appLabel) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        targetPickerVisible = false
                        detailModel = null
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
