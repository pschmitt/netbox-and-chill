package dev.pschmitt.nyetbox.ui.directory

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun displayNetBoxHostname(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return ""

    return trimmed.toHttpUrlOrNull()?.host
        ?: trimmed
            .substringAfter("://", trimmed)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
}
