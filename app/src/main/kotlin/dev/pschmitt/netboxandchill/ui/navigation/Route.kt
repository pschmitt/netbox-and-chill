package dev.pschmitt.netboxandchill.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Onboarding : Route

    @Serializable data object DeviceList : Route

    @Serializable data class DeviceDetail(val deviceId: Int) : Route

    @Serializable data object Scanner : Route

    @Serializable data object Settings : Route
}
