package dev.pschmitt.nyetbox.ui.search

import dev.pschmitt.nyetbox.data.schema.Humanize

internal fun searchObjectTypeLabel(modelLabel: String?, endpointPath: String): String {
    modelLabel?.takeIf(String::isNotBlank)?.let {
        return singularizeLabel(it)
    }
    val modelKey = endpointPath.trim('/').split('/').lastOrNull().orEmpty()
    return modelKey.takeIf(String::isNotBlank)?.let(Humanize::label)?.let(::singularizeLabel)
        ?: "Object"
}

private fun singularizeLabel(label: String): String {
    val words = label.split(' ').filter(String::isNotBlank)
    if (words.isEmpty()) return label
    val last = words.last()
    val lower = last.lowercase()
    val singular =
        when {
            lower.endsWith("ies") -> last.dropLast(3) + "y"
            lower.endsWith("sses") ||
                lower.endsWith("xes") ||
                lower.endsWith("ches") ||
                lower.endsWith("shes") -> last.dropLast(2)
            lower.endsWith("ses") -> last.dropLast(2)
            lower.endsWith("s") && !lower.endsWith("ss") -> last.dropLast(1)
            else -> last
        }
    return (words.dropLast(1) + singular).joinToString(" ")
}
