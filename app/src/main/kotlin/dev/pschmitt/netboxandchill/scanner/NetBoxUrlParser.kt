package dev.pschmitt.netboxandchill.scanner

import dev.pschmitt.netboxandchill.qrsetup.QrConfigCodec

/** What a scanned/opened NetBox URL (or a bare numeric id, which is assumed to be a device) resolves to. */
sealed interface NetBoxTarget {
    data class Device(val id: Int) : NetBoxTarget

    data class Object(val endpointPath: String, val id: Int) : NetBoxTarget

    data class Setup(val baseUrl: String, val token: String) : NetBoxTarget
}

object NetBoxUrlParser {
    // Matches any NetBox object detail path, e.g. /dcim/devices/393/, /ipam/prefixes/12,
    // /plugins/netbox-documents/documents/7/ (an extra segment before the id is fine - "find"
    // just needs *a* two-or-more-segment/id tail to match, and NetBox web paths are always
    // <app>/<model>/<id>/, optionally with more segments in front for a reverse-proxy subpath).
    private val OBJECT_PATH_FINDER = Regex("""/([a-z0-9_-]+)/([a-z0-9_-]+)/(\d+)/?""")
    private val ASSET_TAG_PATTERN = Regex("""#?[A-Za-z0-9][A-Za-z0-9._/-]{0,127}""")

    /** Extracts a NetBox device/object target from a scanned/opened URL, or a bare numeric id. */
    fun parse(text: String): NetBoxTarget? {
        val trimmed = text.trim()
        if (QrConfigCodec.looksLikeQrConfigUri(trimmed)) {
            return runCatching {
                    QrConfigCodec.decodePayload(trimmed).let { NetBoxTarget.Setup(it.baseUrl, it.token) }
                }
                .getOrNull()
        }
        trimmed.toIntOrNull()?.let {
            return NetBoxTarget.Device(it)
        }

        val match = OBJECT_PATH_FINDER.findAll(trimmed).lastOrNull() ?: return null
        val (app, model, idText) = match.destructured
        val id = idText.toIntOrNull() ?: return null
        return if (app == "dcim" && model == "devices") {
            NetBoxTarget.Device(id)
        } else {
            NetBoxTarget.Object(endpointPath = "api/$app/$model/", id = id)
        }
    }

    /** Returns a barcode-friendly asset-tag candidate without changing URL/ID parsing semantics. */
    fun parseAssetTag(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.toIntOrNull() != null || parse(trimmed) != null) return null
        return trimmed.takeIf { ASSET_TAG_PATTERN.matches(it) }
    }
}
