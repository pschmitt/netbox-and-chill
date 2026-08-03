package dev.pschmitt.nyetbox.ui.common

import androidx.compose.ui.graphics.Color
import dev.pschmitt.nyetbox.data.schema.documentTypePresentation

internal data class DocumentTypeBadgeColors(
    val container: Color,
    val content: Color,
)

internal fun documentTypeBadgeColors(raw: String): DocumentTypeBadgeColors {
    val key = documentTypePresentation(raw)?.key.orEmpty()
    return KNOWN_DOCUMENT_TYPE_COLORS[key]
        ?: DOCUMENT_TYPE_COLORS[(key.hashCode() and Int.MAX_VALUE) % DOCUMENT_TYPE_COLORS.size]
}

private val DOCUMENT_TYPE_COLORS =
    listOf(
        DocumentTypeBadgeColors(container = Color(0xFF1565C0), content = Color.White),
        DocumentTypeBadgeColors(container = Color(0xFF6A1B9A), content = Color.White),
        DocumentTypeBadgeColors(container = Color(0xFF2E7D32), content = Color.White),
        DocumentTypeBadgeColors(container = Color(0xFFEF6C00), content = Color.White),
        DocumentTypeBadgeColors(container = Color(0xFF00838F), content = Color.White),
        DocumentTypeBadgeColors(container = Color(0xFFC62828), content = Color.White),
    )

private val KNOWN_DOCUMENT_TYPE_COLORS =
    mapOf(
        "manual" to DOCUMENT_TYPE_COLORS[0],
        "purchaseorder" to DOCUMENT_TYPE_COLORS[1],
        "floorplan" to DOCUMENT_TYPE_COLORS[2],
        "other" to DOCUMENT_TYPE_COLORS[3],
    )
