package dev.pschmitt.netboxandchill.data.schema

internal data class DocumentTypePresentation(
    val key: String,
    val label: String,
)

internal fun documentTypePresentation(raw: String?): DocumentTypePresentation? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val words = readableDocumentTypeWords(value)
    if (words.isEmpty()) return null

    val key = words.joinToString("").lowercase()
    val label =
        DOCUMENT_TYPE_LABELS[key]
            ?: words.mapIndexed { index, word ->
                if (index == 0) word.replaceFirstChar { it.uppercase() } else word
            }.joinToString(" ")
    return DocumentTypePresentation(key = key, label = label)
}

private fun readableDocumentTypeWords(value: String): List<String> =
    value
        .replace(CAMEL_CASE_BOUNDARY, " ")
        .replace(Regex("[_-]+"), " ")
        .split(Regex("\\s+"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::lowercase)

private val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")

private val DOCUMENT_TYPE_LABELS =
    mapOf(
        "manual" to "Manual",
        "purchaseorder" to "Purchase order",
        "floorplan" to "Floor plan",
        "other" to "Other",
    )
