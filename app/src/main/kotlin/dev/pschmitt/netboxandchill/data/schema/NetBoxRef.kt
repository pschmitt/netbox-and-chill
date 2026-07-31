package dev.pschmitt.netboxandchill.data.schema

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Small shared helpers for turning a NetBox object's absolute detail URL / endpoint path into the
 * pieces the rest of the app needs to navigate to or icon a NetBox object generically. Originally
 * lived as private functions inside `GenericFieldRenderer`/`GenericListScreen` (NBC-6); pulled out
 * here so the dashboard's bookmark/changelog rows (NBC-9) can resolve navigation targets and icons
 * exactly the same way, instead of a third copy of the same logic.
 */
object NetBoxRef {
    /** "https://host/api/dcim/sites/3/" -> "api/dcim/sites/" (strips the trailing id segment). */
    fun endpointFromDetailUrl(detailUrl: String): String? {
        val path = detailUrl.toHttpUrlOrNull()?.encodedPath ?: return null
        val trimmed = path.trim('/')
        val lastSlash = trimmed.lastIndexOf('/')
        if (lastSlash < 0) return null
        return trimmed.substring(0, lastSlash + 1)
    }

    /**
     * Mirrors [dev.pschmitt.netboxandchill.data.repository.DirectoryRepository]'s `appKey` shape
     * (`"plugins/<plugin>"` for plugin models, else the plain app segment) so
     * [dev.pschmitt.netboxandchill.ui.directory.AppIcons] picks the same icon for a given object
     * type everywhere it's rendered (sidebar, generic list rows, dashboard rows, ...).
     */
    fun appKeyFromEndpointPath(endpointPath: String): String {
        val segments = endpointPath.trim('/').split('/')
        return if (segments.size >= 4 && segments[1] == "plugins") "plugins/${segments[2]}"
        else segments.getOrElse(1) { "" }
    }
}
