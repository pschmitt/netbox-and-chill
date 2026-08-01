package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(label: String?, value: String?) {
    if (label == null) return
    val color =
        when (value) {
            "active" -> MaterialTheme.colorScheme.primaryContainer
            "offline",
            "decommissioning",
            "failed" -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    Text(
        text = label,
        modifier =
            Modifier.background(color, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
    )
}
