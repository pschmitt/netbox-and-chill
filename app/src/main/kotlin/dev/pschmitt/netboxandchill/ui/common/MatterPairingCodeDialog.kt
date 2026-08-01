package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import dev.pschmitt.netboxandchill.qrsetup.QrBitmap

@Composable
fun MatterPairingCodeDialog(code: String, onDismiss: () -> Unit) {
    val bitmap = remember(code) { QrBitmap.encode(code, size = 768) }
    DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
        title = { Text("Matter pairing code") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Matter pairing QR code",
                    modifier = Modifier.size(256.dp),
                )
                Text(code)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
