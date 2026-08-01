package dev.pschmitt.netboxandchill.ui.search

import dev.pschmitt.netboxandchill.data.schema.Humanize

internal fun searchObjectTypeLabel(modelLabel: String?, endpointPath: String): String {
    modelLabel?.takeIf(String::isNotBlank)?.let { return it }
    val modelKey = endpointPath.trim('/').split('/').lastOrNull().orEmpty()
    return modelKey.takeIf(String::isNotBlank)?.let(Humanize::label) ?: "Object"
}
