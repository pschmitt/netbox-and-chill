package dev.pschmitt.netboxandchill.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PagedResponseDto<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList(),
)

@Serializable data class LabeledValueDto(val value: String? = null, val label: String? = null)

@Serializable
data class NestedRefDto(
    val id: Int,
    val url: String? = null,
    val display: String? = null,
    val name: String? = null,
)

@Serializable data class ManufacturerRefDto(val id: Int, val name: String? = null)

@Serializable
data class DeviceTypeRefDto(
    val id: Int,
    val model: String? = null,
    val manufacturer: ManufacturerRefDto? = null,
)

@Serializable
data class IpAddressRefDto(
    val id: Int,
    val address: String? = null,
    val display: String? = null,
    @SerialName("dns_name") val dnsName: String? = null,
)

@Serializable
data class DeviceDto(
    val id: Int,
    val url: String? = null,
    val display: String? = null,
    val name: String? = null,
    val status: LabeledValueDto? = null,
    val site: NestedRefDto? = null,
    val rack: NestedRefDto? = null,
    val position: Double? = null,
    // NetBox renamed "device_role" to "role" in 4.0 - accept either so this works against older
    // and newer NetBox instances alike.
    val role: NestedRefDto? = null,
    @SerialName("device_role") val deviceRole: NestedRefDto? = null,
    @SerialName("device_type") val deviceType: DeviceTypeRefDto? = null,
    val serial: String? = null,
    @SerialName("asset_tag") val assetTag: String? = null,
    @SerialName("primary_ip") val primaryIp: IpAddressRefDto? = null,
    val comments: String? = null,
    @SerialName("custom_fields") val customFields: JsonObject? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
) {
    val effectiveRole: NestedRefDto?
        get() = role ?: deviceRole
}
