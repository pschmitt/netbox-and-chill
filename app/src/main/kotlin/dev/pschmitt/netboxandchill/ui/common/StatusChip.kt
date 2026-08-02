package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(label: String?, value: String?) {
    if (label == null) return
    val color =
        when (value?.lowercase()) {
            "active" -> MaterialTheme.colorScheme.primaryContainer
            "inventory" ->
                if (isSystemInDarkTheme()) Color(0xFF7A1E52) else Color(0xFFFFC7E2)
            "offline",
            "decommissioning",
            "failed" -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    Box(
        modifier =
            Modifier.height(32.dp)
                .background(color, RoundedCornerShape(50))
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                statusIcon(value),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun statusIcon(value: String?) =
    when (value?.lowercase()) {
        "active" -> Icons.Default.CheckCircle
        "inventory" -> Icons.Default.Inventory2
        "offline" -> Icons.Default.CloudOff
        "decommissioning" -> Icons.Default.Archive
        "failed" -> Icons.Default.Error
        "planned" -> Icons.Default.Schedule
        "staged", "maintenance" -> Icons.Default.Build
        else -> Icons.Default.Info
    }
