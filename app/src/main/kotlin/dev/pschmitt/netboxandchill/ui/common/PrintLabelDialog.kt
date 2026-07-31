package dev.pschmitt.netboxandchill.ui.common

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.pschmitt.netboxandchill.printing.BrotherLabelRenderer
import dev.pschmitt.netboxandchill.printing.BrotherPrinter
import dev.pschmitt.netboxandchill.printing.PairedPrinter
import kotlinx.coroutines.launch

data class PrintLabelRequest(val objectUrl: String, val labelText: String)

@Composable
fun PrintLabelDialog(request: PrintLabelRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(hasBluetoothPermission(context)) }
    var printers by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }
    var selected by remember { mutableStateOf<PairedPrinter?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var invertColors by remember { mutableStateOf(true) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasPermission = hasBluetoothPermission(context)
        }

    fun reloadPrinters() {
        if (!hasPermission) return
        val adapter = bluetoothAdapter(context)
        printers = adapter?.let { BrotherPrinter.pairedPrinters(it.bondedDevices) }.orEmpty()
        selected = selected?.takeIf { current -> printers.any { it.address == current.address } } ?: printers.firstOrNull()
    }

    LaunchedEffect(hasPermission) { reloadPrinters() }

    AlertDialog(
        onDismissRequest = { if (!isPrinting) onDismiss() },
        icon = { Icon(Icons.Default.Print, contentDescription = null) },
        title = { Text("Print device label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasPermission) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Text("Bluetooth permission is needed to find paired Brother printers.")
                    OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(bluetoothPermissions())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Allow Bluetooth")
                    }
                } else if (bluetoothAdapter(context)?.isEnabled != true) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null)
                    Text("Bluetooth is turned off.")
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Turn on Bluetooth")
                    }
                } else if (printers.isEmpty()) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null)
                    Text("No paired Brother P-touch printer was found. Pair it in Android settings first.")
                    TextButton(onClick = ::reloadPrinters) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh paired devices")
                    }
                } else {
                    Text("Choose a paired printer", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(printers, key = { it.address }) { printer ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected?.address == printer.address,
                                    onClick = { selected = printer },
                                )
                                Column {
                                    Text(printer.name)
                                    Text(printer.address, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text(
                        "The label contains the cached device URL as a QR code and its asset tag.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Invert print colors")
                            Text(
                                "Disable for printlabel --black-style output",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = invertColors, onCheckedChange = { invertColors = it })
                    }
                }
                if (isPrinting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text("Printing…")
                    }
                }
                resultMessage?.let { message ->
                    Text(
                        message,
                        color = if (message == "Printed") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPrinting) { Text("Close") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val printer = selected ?: return@Button
                    isPrinting = true
                    resultMessage = null
                    scope.launch {
                        val result =
                            runCatching {
                                BrotherLabelRenderer.render(
                                    request.objectUrl,
                                    request.labelText,
                                    invert = invertColors,
                                )
                            }.fold(
                                onSuccess = { label -> BrotherPrinter.print(printer, label) },
                                onFailure = { Result.failure(it) },
                            )
                        isPrinting = false
                        resultMessage = result.fold({ "Printed" }, { it.message ?: "Printing failed" })
                    }
                },
                enabled = selected != null && !isPrinting && hasPermission,
            ) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Print")
            }
        },
    )
}

private fun bluetoothPermissions(): Array<String> =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        emptyArray()
    }

private fun hasBluetoothPermission(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

private fun bluetoothAdapter(context: Context): BluetoothAdapter? =
    context.getSystemService(BluetoothManager::class.java)?.adapter
