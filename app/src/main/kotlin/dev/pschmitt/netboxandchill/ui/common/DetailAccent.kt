package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.pschmitt.netboxandchill.data.repository.ThemeAccent

/** Stable, subtle accents make adjacent NetBox object types easier to distinguish at a glance. */
fun ColorScheme.detailAccentFor(endpointPath: String, override: ThemeAccent? = null): Color =
    visualColorForEndpointPath(endpointPath, override, this)

/** Deterministic per-object-type colors used by search badges and detail screens. */
fun visualColorForEndpointPath(
    endpointPath: String,
    override: ThemeAccent? = null,
    scheme: ColorScheme? = null,
): Color {
    val fallback =
        when {
            endpointPath == "api/dcim/devices/" -> 0xFF1565C0
            endpointPath == "api/dcim/device-types/" -> 0xFF7B1FA2
            endpointPath == "api/dcim/racks/" -> 0xFFEF6C00
            endpointPath == "api/dcim/sites/" -> 0xFF2E7D32
            endpointPath == "api/ipam/ip-addresses/" -> 0xFF00838F
            endpointPath.startsWith("api/dcim/") -> 0xFF5E35B1
            endpointPath.startsWith("api/ipam/") -> 0xFF00838F
            endpointPath.startsWith("api/virtualization/") -> 0xFF6A1B9A
            endpointPath.startsWith("api/tenancy/") -> 0xFFAD1457
            else ->
                listOf(0xFF455A64, 0xFF00695C, 0xFF8D6E63, 0xFF3949AB)[
                    Math.floorMod(endpointPath.hashCode(), 4)
                ]
        }
    if (override == null || override == ThemeAccent.System) return Color(fallback)
    return when (override) {
        ThemeAccent.Teal -> scheme?.primary ?: Color(0xFF008577)
        ThemeAccent.Blue -> Color(0xFF1565C0)
        ThemeAccent.Purple -> Color(0xFF7B1FA2)
        ThemeAccent.Orange -> Color(0xFFEF6C00)
        ThemeAccent.Pink -> Color(0xFFC2185B)
        ThemeAccent.Green -> Color(0xFF2E7D32)
        ThemeAccent.System -> Color(fallback)
    }
}
