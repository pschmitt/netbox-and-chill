package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.db.RackElevationEntity
import dev.pschmitt.nyetbox.data.repository.RackFace
import dev.pschmitt.nyetbox.ui.common.RemoteThumbnail

@Composable
internal fun RackElevationOverview(
    front: List<RackElevationEntity>,
    rear: List<RackElevationEntity>,
    previews: Map<Int, RackDevicePreview>,
    highlightDeviceId: Int? = null,
    onDeviceClick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Rack elevation", style = MaterialTheme.typography.titleLarge)
        }
        if (front.isEmpty() && rear.isEmpty()) {
            Text(
                "No elevation data cached yet - refresh while online",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RackFaceOverview(RackFace.FRONT, front, previews, highlightDeviceId, onDeviceClick)
            RackFaceOverview(RackFace.REAR, rear, previews, highlightDeviceId, onDeviceClick)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RackFaceOverview(
    face: RackFace,
    slots: List<RackElevationEntity>,
    previews: Map<Int, RackDevicePreview>,
    highlightDeviceId: Int?,
    onDeviceClick: (Int) -> Unit,
) {
    Column {
        Text(face.label, style = MaterialTheme.typography.titleMedium)
        if (slots.isEmpty()) {
            Text(
                "No ${face.label.lowercase()} elevation cached",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp, horizontal = 6.dp)) {
                mergeRackSlots(slots).forEach { block ->
                    val firstSlot = block.slots.first()
                    val lastSlot = block.slots.last()
                    val deviceId = block.deviceId
                    val highlighted = deviceId != null && deviceId == highlightDeviceId
                    val preview = deviceId?.let(previews::get)
                    val imageUrl =
                        if (face == RackFace.FRONT) preview?.frontUrl ?: preview?.rearUrl
                        else preview?.rearUrl ?: preview?.frontUrl
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(28.dp * block.slots.size).fillMaxWidth(),
                    ) {
                        Text(
                            if (firstSlot.slotName == lastSlot.slotName) firstSlot.slotName
                            else "${firstSlot.slotName}–${lastSlot.slotName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp),
                            maxLines = 1,
                        )
                        Surface(
                            color =
                                if (deviceId != null) rackDeviceColor(deviceId)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .clickable(enabled = deviceId != null) {
                                        deviceId?.let(onDeviceClick)
                                    }
                                    .then(
                                        if (highlighted) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp),
                                            )
                                        } else Modifier
                                    ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .fillMaxHeight()
                                        .padding(horizontal = 6.dp),
                            ) {
                                if (deviceId != null) {
                                    RemoteThumbnail(
                                        imageUrl = imageUrl,
                                        contentDescription = firstSlot.deviceDisplay,
                                        modifier = Modifier.size(44.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        firstSlot.deviceDisplay ?: "Device #$deviceId",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF263238),
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RackElevationBlock(
    val deviceId: Int?,
    val slots: List<RackElevationEntity>,
)

private fun mergeRackSlots(slots: List<RackElevationEntity>): List<RackElevationBlock> {
    val blocks = mutableListOf<RackElevationBlock>()
    slots.forEach { slot ->
        val current = blocks.lastOrNull()
        if (current != null && current.deviceId == slot.deviceId) {
            blocks[blocks.lastIndex] = current.copy(slots = current.slots + slot)
        } else {
            blocks += RackElevationBlock(slot.deviceId, listOf(slot))
        }
    }
    return blocks
}

private fun rackDeviceColor(deviceId: Int): Color {
    val palette =
        listOf(
            Color(0xFFDDEBFF),
            Color(0xFFE3F4E7),
            Color(0xFFFFE5D0),
            Color(0xFFEDE0FF),
            Color(0xFFFFF0B3),
            Color(0xFFD9F4F0),
            Color(0xFFFFDDE4),
            Color(0xFFE4E8F0),
        )
    return palette[Math.floorMod(deviceId, palette.size)]
}
