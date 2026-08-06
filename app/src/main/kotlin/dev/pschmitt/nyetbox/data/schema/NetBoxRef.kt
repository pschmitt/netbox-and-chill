package dev.pschmitt.nyetbox.data.schema

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Small shared helpers for turning a NetBox object's absolute detail URL / endpoint path into the
 * pieces the rest of the app needs to navigate to or icon a NetBox object generically. Originally
 * lived as private functions inside `GenericFieldRenderer`/`GenericListScreen` (NBC-6); pulled out
 * here so the dashboard's bookmark/changelog rows (NBC-9) can resolve navigation targets and icons
 * exactly the same way, instead of a third copy of the same logic.
 */
object NetBoxRef {
    const val DEVICES_ENDPOINT_PATH = "api/dcim/devices/"
    const val DEVICE_TYPES_ENDPOINT_PATH = "api/dcim/device-types/"
    const val INTERFACES_ENDPOINT_PATH = "api/dcim/interfaces/"
    const val IP_ADDRESSES_ENDPOINT_PATH = "api/ipam/ip-addresses/"
    const val RACKS_ENDPOINT_PATH = "api/dcim/racks/"
    const val SITES_ENDPOINT_PATH = "api/dcim/sites/"
    const val CABLES_ENDPOINT_PATH = "api/dcim/cables/"

    /** "https://host/api/dcim/sites/3/" -> "api/dcim/sites/" (strips the trailing id segment). */
    fun endpointFromDetailUrl(detailUrl: String): String? {
        val path = detailUrl.toHttpUrlOrNull()?.encodedPath ?: return null
        val trimmed = path.trim('/')
        val lastSlash = trimmed.lastIndexOf('/')
        if (lastSlash < 0) return null
        return trimmed.substring(0, lastSlash + 1)
    }

    /**
     * Mirrors [dev.pschmitt.nyetbox.data.repository.DirectoryRepository]'s `appKey` shape
     * (`"plugins/<plugin>"` for plugin models, else the plain app segment) so
     * [dev.pschmitt.nyetbox.ui.directory.AppIcons] picks the same icon for a given object type
     * everywhere it's rendered (sidebar, generic list rows, dashboard rows, ...).
     */
    fun appKeyFromEndpointPath(endpointPath: String): String {
        val segments = endpointPath.trim('/').split('/')
        return if (segments.size >= 4 && segments[1] == "plugins") "plugins/${segments[2]}"
        else segments.getOrElse(1) { "" }
    }
}

/** Metadata for the model types the app treats specially beyond generic NetBox navigation. */
data class NetBoxEndpointMetadata(
    val endpointPath: String,
    val label: String,
    val appKey: String,
    val typedDetail: Boolean = false,
    val supportsDeviceTypeImages: Boolean = false,
)

/** Shared registry for stable core model identity; plugin models continue through [NetBoxRef]. */
object NetBoxEndpointCatalog {
    val coreModels: List<NetBoxEndpointMetadata> =
        listOf(
            NetBoxEndpointMetadata(
                NetBoxRef.DEVICES_ENDPOINT_PATH,
                "Devices",
                "dcim",
                typedDetail = true,
                supportsDeviceTypeImages = true,
            ),
            NetBoxEndpointMetadata(
                NetBoxRef.DEVICE_TYPES_ENDPOINT_PATH,
                "Device Types",
                "dcim",
                supportsDeviceTypeImages = true,
            ),
            NetBoxEndpointMetadata(NetBoxRef.SITES_ENDPOINT_PATH, "Sites", "dcim"),
            NetBoxEndpointMetadata(NetBoxRef.RACKS_ENDPOINT_PATH, "Racks", "dcim"),
            NetBoxEndpointMetadata(
                NetBoxRef.IP_ADDRESSES_ENDPOINT_PATH,
                "IP Addresses",
                "ipam",
            ),
            NetBoxEndpointMetadata("api/ipam/prefixes/", "Prefixes", "ipam"),
            NetBoxEndpointMetadata("api/circuits/circuits/", "Circuits", "circuits"),
            NetBoxEndpointMetadata(
                "api/virtualization/virtual-machines/",
                "Virtual Machines",
                "virtualization",
            ),
            NetBoxEndpointMetadata("api/tenancy/tenants/", "Tenants", "tenancy"),
        )

    private val byPath = coreModels.associateBy { it.endpointPath }

    fun forPath(endpointPath: String): NetBoxEndpointMetadata? = byPath[endpointPath]
}
