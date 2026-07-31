package dev.pschmitt.netboxandchill.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Onboarding : Route

    /** Home/dashboard screen (NBC-9) - the default post-login landing destination and a bottom-nav
     * tab, see NetBoxNavHost/MainActivity. */
    @Serializable data object Dashboard : Route

    @Serializable data object DeviceList : Route

    @Serializable data class DeviceDetail(val deviceId: Int) : Route

    @Serializable data object Scanner : Route

    @Serializable data object Settings : Route

    /** Cross-model search (NBC-13) - distinct from the sidebar's own section-name filter (NBC-6). */
    @Serializable data object GlobalSearch : Route

    /** Generated list/detail screens for any NetBox object type - see NBC-6/DirectoryRepository. */
    @Serializable
    data class GenericList(
        val endpointPath: String,
        val label: String,
        val filterKey: String? = null,
        val filterValue: Int? = null,
    ) : Route

    @Serializable data class Generic(val endpointPath: String, val id: Int) : Route

    /** Field-by-field before/after view for one dashboard changelog entry (NBC-42) - distinct
     * from [Generic], which only ever shows the object's *current* state. */
    @Serializable data class ObjectChangeDiff(val changeId: Int) : Route
}
