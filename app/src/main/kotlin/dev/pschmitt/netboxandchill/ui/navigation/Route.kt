package dev.pschmitt.netboxandchill.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Onboarding : Route

    @Serializable data object DeviceList : Route

    @Serializable data class DeviceDetail(val deviceId: Int) : Route

    @Serializable data object Scanner : Route

    @Serializable data object Settings : Route

    /** Generated list/detail screens for any NetBox object type - see NBC-6/DirectoryRepository. */
    @Serializable data class GenericList(val endpointPath: String, val label: String) : Route

    @Serializable data class Generic(val endpointPath: String, val id: Int) : Route
}
