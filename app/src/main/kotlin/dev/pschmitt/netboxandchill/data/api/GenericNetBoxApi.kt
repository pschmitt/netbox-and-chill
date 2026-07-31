package dev.pschmitt.netboxandchill.data.api

import dev.pschmitt.netboxandchill.data.api.dto.PagedResponseDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Schema-free access used to discover NetBox's own object model (see
 * [dev.pschmitt.netboxandchill.data.repository.DirectoryRepository]) and to list/fetch objects of
 * any type generically (see [dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository]).
 * All calls take the endpoint path/URL as returned by NetBox itself rather than a fixed
 * `@GET("...")` path, since the whole point is not knowing the object types up front.
 */
interface GenericNetBoxApi {
    /**
     * The API root (`GET /api/`) and every app root (`GET /api/<app>/`) are both a flat
     * `{modelOrAppName: "url"}` map - DRF's default router root view shape.
     */
    @GET("api/") suspend fun getApiRoot(): Map<String, String>

    @GET suspend fun getUrlMap(@Url url: String): Map<String, String>

    @GET
    suspend fun listObjects(
        @Url url: String,
        @QueryMap query: Map<String, String>,
    ): PagedResponseDto<JsonObject>

    @GET suspend fun getObject(@Url url: String): JsonObject

    @HTTP(method = "OPTIONS", hasBody = false)
    suspend fun getObjectOptions(@Url url: String): JsonObject

    @PATCH suspend fun patchObject(@Url url: String, @Body body: JsonObject): JsonObject

    /**
     * DRF's `OPTIONS` response for a ChoiceField enumerates its valid values - used to resolve the
     * `assigned_object_type` (`app_label.model`) NetBox expects when filtering journal entries,
     * without hardcoding a model name -> content-type mapping (see [JournalEntryRepository]).
     */
    @HTTP(method = "OPTIONS", path = "api/extras/journal-entries/", hasBody = false)
    suspend fun getJournalEntryOptions(): JsonObject
}
