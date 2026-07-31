package dev.pschmitt.netboxandchill.scanner

/** What a scanned/opened NetBox URL (or a bare numeric id, which is assumed to be a device) resolves to. */
sealed interface NetBoxTarget {
    data class Device(val id: Int) : NetBoxTarget

    data class Object(val endpointPath: String, val id: Int) : NetBoxTarget
}

object NetBoxUrlParser {
    // Matches any NetBox object detail path, e.g. /dcim/devices/393/, /ipam/prefixes/12,
    // /plugins/netbox-documents/documents/7/ (an extra segment before the id is fine - "find"
    // just needs *a* two-or-more-segment/id tail to match, and NetBox web paths are always
    // <app>/<model>/<id>/, optionally with more segments in front for a reverse-proxy subpath).
    private val OBJECT_PATH_FINDER = Regex("""/([a-z0-9_-]+)/([a-z0-9_-]+)/(\d+)/?""")

    /** Extracts a NetBox device/object target from a scanned/opened URL, or a bare numeric id. */
    fun parse(text: String): NetBoxTarget? {
        val trimmed = text.trim()
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
}
