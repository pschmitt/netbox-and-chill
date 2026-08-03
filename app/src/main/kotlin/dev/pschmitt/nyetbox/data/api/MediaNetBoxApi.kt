package dev.pschmitt.nyetbox.data.api

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Url

/** Multipart-only API kept separate so generic JSON test fakes remain small and stable. */
interface MediaNetBoxApi {
    @Multipart
    @POST
    suspend fun uploadFile(
        @Url url: String,
        @Part file: MultipartBody.Part,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody> = emptyMap(),
    ): JsonObject

    @Multipart
    @PATCH
    suspend fun patchFile(
        @Url url: String,
        @Part file: MultipartBody.Part,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody> = emptyMap(),
    ): JsonObject
}
