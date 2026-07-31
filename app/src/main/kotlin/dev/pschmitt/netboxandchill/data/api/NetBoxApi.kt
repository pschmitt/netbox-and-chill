package dev.pschmitt.netboxandchill.data.api

import dev.pschmitt.netboxandchill.data.api.dto.DeviceDto
import dev.pschmitt.netboxandchill.data.api.dto.DeviceTypeDto
import dev.pschmitt.netboxandchill.data.api.dto.ImageAttachmentDto
import dev.pschmitt.netboxandchill.data.api.dto.PagedResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface NetBoxApi {
    @GET("api/dcim/devices/")
    suspend fun listDevices(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("q") search: String? = null,
    ): PagedResponseDto<DeviceDto>

    @GET("api/dcim/devices/{id}/") suspend fun getDevice(@Path("id") id: Int): DeviceDto

    @GET("api/dcim/device-types/{id}/")
    suspend fun getDeviceType(@Path("id") id: Int): DeviceTypeDto

    @GET("api/extras/image-attachments/")
    suspend fun listImageAttachments(
        @Query("object_type") objectType: String,
        @Query("object_id") objectId: Int,
    ): PagedResponseDto<ImageAttachmentDto>
}
