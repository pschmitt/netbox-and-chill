package dev.pschmitt.netboxandchill.data.schema

/** Turns NetBox's kebab-case API keys into readable labels, e.g. "device-types" -> "Device Types". */
internal object Humanize {
    private val ACRONYMS =
        setOf("ip", "vlan", "vrf", "asn", "mac", "url", "api", "vpn", "vm", "dcim", "ipam", "id")

    fun label(key: String): String =
        key.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                val lower = word.lowercase()
                val digitSuffix = lower.takeLastWhile { it.isDigit() }
                val base = lower.removeSuffix(digitSuffix)
                when {
                    lower in ACRONYMS -> lower.uppercase()
                    // "ip4"/"ip6" etc - an acronym with a trailing version/index digit.
                    digitSuffix.isNotEmpty() && base in ACRONYMS -> base.uppercase() + digitSuffix
                    else -> word.replaceFirstChar { it.uppercase() }
                }
            }
}
