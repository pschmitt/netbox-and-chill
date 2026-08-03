package dev.pschmitt.nyetbox.ui.directory

import dev.pschmitt.nyetbox.data.db.NetBoxModelEntity

private val NETBOX_APP_ORDER =
    listOf("tenancy", "dcim", "virtualization", "circuits", "wireless", "ipam", "vpn", "extras")

private val NETBOX_MODEL_ORDERS =
    mapOf(
        "tenancy" to
            listOf(
                "tenants",
                "tenant-groups",
                "contacts",
                "contact-groups",
                "contact-roles",
            ),
        "dcim" to
            listOf(
                "sites",
                "locations",
                "racks",
                "rack-roles",
                "devices",
                "device-types",
                "device-roles",
                "platforms",
                "manufacturers",
                "modules",
                "module-types",
                "inventory-items",
                "interfaces",
                "console-ports",
                "console-server-ports",
                "power-ports",
                "power-outlets",
                "front-ports",
                "rear-ports",
                "device-bays",
                "virtual-chassis",
                "cables",
                "connections",
            ),
        "virtualization" to
            listOf(
                "clusters",
                "cluster-types",
                "cluster-groups",
                "virtual-machines",
                "interfaces",
                "disks",
            ),
        "circuits" to
            listOf(
                "providers",
                "provider-accounts",
                "circuit-types",
                "circuits",
                "circuit-terminations",
                "circuit-groups",
            ),
        "wireless" to
            listOf("wireless-lans", "wireless-links", "wireless-roles", "wireless-templates"),
        "ipam" to
            listOf(
                "aggregates",
                "prefixes",
                "ip-ranges",
                "ip-addresses",
                "vlans",
                "vlan-groups",
                "vrfs",
                "fhrp-groups",
                "services",
            ),
        "vpn" to listOf("tunnel-groups", "tunnels"),
        "extras" to
            listOf(
                "config-contexts",
                "config-context-schemas",
                "tags",
                "custom-fields",
                "custom-links",
                "export-templates",
                "image-attachments",
                "journal-entries",
                "events",
                "webhooks",
                "bookmarks",
            ),
    )

fun orderSidebarAppKeys(appKeys: Collection<String>, savedOrder: List<String>): List<String> {
    val customRank = savedOrder.withIndex().associate { it.value to it.index }
    val defaultRank = NETBOX_APP_ORDER.withIndex().associate { it.value to it.index }
    return appKeys
        .distinct()
        .sortedWith(
            compareBy<String> { customRank[it] == null }
                .thenBy { customRank[it] ?: defaultRank[it] ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )
}

fun orderSidebarModels(
    appKey: String,
    models: Collection<NetBoxModelEntity>,
    savedOrder: List<String>,
): List<NetBoxModelEntity> {
    val customRank = savedOrder.withIndex().associate { it.value to it.index }
    val defaultRank =
        NETBOX_MODEL_ORDERS[appKey].orEmpty().withIndex().associate { it.value to it.index }
    return models.sortedWith(
        compareBy<NetBoxModelEntity> { customRank[it.modelKey] == null }
            .thenBy { customRank[it.modelKey] ?: defaultRank[it.modelKey] ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.modelLabel }
    )
}
