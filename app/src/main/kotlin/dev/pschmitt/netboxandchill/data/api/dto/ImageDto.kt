package dev.pschmitt.netboxandchill.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceTypeDto(
    val id: Int,
    val model: String? = null,
    @SerialName("front_image") val frontImage: String? = null,
    @SerialName("rear_image") val rearImage: String? = null,
)

@Serializable
data class ImageAttachmentDto(
    val id: Int,
    val name: String? = null,
    val display: String? = null,
    val image: String? = null,
    val description: String? = null,
    @SerialName("image_height") val imageHeight: Int? = null,
    @SerialName("image_width") val imageWidth: Int? = null,
    val created: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    @SerialName("object_type") val objectType: String? = null,
    @SerialName("object_id") val objectId: Int? = null,
)
