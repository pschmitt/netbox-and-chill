package dev.pschmitt.netboxandchill.scanner

object DeviceUrlParser {
    private val DEVICE_PATH_REGEX = Regex("""/dcim/devices/(\d+)/?""")

    /**
     * Extracts a NetBox device id from a scanned/opened device URL (any host - the sticker's host
     * doesn't have to match the app's configured NetBox instance, only the path shape), or from a
     * bare numeric id.
     */
    fun parseDeviceId(text: String): Int? {
        val trimmed = text.trim()
        trimmed.toIntOrNull()?.let {
            return it
        }
        return DEVICE_PATH_REGEX.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
    }
}
