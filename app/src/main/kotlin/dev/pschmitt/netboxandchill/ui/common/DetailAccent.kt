package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Stable, subtle accents make adjacent NetBox object types easier to distinguish at a glance. */
fun ColorScheme.detailAccentFor(endpointPath: String): Color =
    when {
        endpointPath == "api/dcim/devices/" -> primary
        endpointPath == "api/dcim/device-types/" -> secondary
        endpointPath.startsWith("api/dcim/") -> tertiary
        endpointPath.startsWith("api/ipam/") -> primary
        endpointPath.startsWith("api/virtualization/") -> secondary
        endpointPath.startsWith("api/tenancy/") -> tertiary
        else -> primary
    }
