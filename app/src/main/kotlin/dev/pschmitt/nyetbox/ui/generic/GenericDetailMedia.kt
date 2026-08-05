package dev.pschmitt.nyetbox.ui.generic

import dev.pschmitt.nyetbox.ui.common.ImageViewerItem
import dev.pschmitt.nyetbox.ui.common.MediaUploadKind

internal fun deviceTypePhotoItems(
    rows: List<FieldRow.Image>,
    title: String?,
): List<ImageViewerItem> {
    val itemTitle = title?.takeIf { it.isNotBlank() } ?: "Device type"
    return rows
        .filter { deviceTypePhotoUploadKind(it.label) != null }
        .map { row ->
            ImageViewerItem(
                url = row.url,
                title = "${row.label} of $itemTitle",
                metadata = listOf("View" to row.label),
                canEdit = true,
            )
        }
}

internal fun deviceTypePhotoUploadKind(label: String): MediaUploadKind? =
    when {
        label.contains("front", ignoreCase = true) -> MediaUploadKind.DeviceTypeFront
        label.contains("rear", ignoreCase = true) -> MediaUploadKind.DeviceTypeRear
        else -> null
    }
