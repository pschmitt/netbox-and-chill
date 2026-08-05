package dev.pschmitt.nyetbox.ui.search

import dev.pschmitt.nyetbox.ui.common.objectTypeLabel

internal fun searchObjectTypeLabel(modelLabel: String?, endpointPath: String): String {
    return objectTypeLabel(modelLabel, endpointPath)
}
