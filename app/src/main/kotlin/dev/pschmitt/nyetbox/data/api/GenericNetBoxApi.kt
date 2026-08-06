package dev.pschmitt.nyetbox.data.api

import dev.pschmitt.nyetbox.data.api.dto.PagedResponseDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Schema-free access used to discover NetBox's own object model (see
 * [dev.pschmitt.nyetbox.data.repository.DirectoryRepository]) and to list/fetch objects of any type
 * generically (see [dev.pschmitt.nyetbox.data.repository.GenericObjectRepository]). All calls take
 * the endpoint path/URL as returned by NetBox itself rather than a fixed `@GET("...")` path, since
 * the whole point is not knowing the object types up front.
 */
interface GenericNetBoxApi {
    /** Returns the authenticated user for the API token making the request (NetBox 4.5+). */
    @GET("api/authentication-check/") suspend fun getAuthenticationCheck(): JsonObject

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

    @POST suspend fun createObject(@Url url: String, @Body body: JsonObject): JsonObject

    @HTTP(method = "DELETE", hasBody = false) suspend fun deleteObject(@Url url: String)

    /**
     * DRF's `OPTIONS` response for a ChoiceField enumerates its valid values - used to resolve the
     * `assigned_object_type` (`app_label.model`) NetBox expects when filtering journal entries,
     * without hardcoding a model name -> content-type mapping (see [JournalEntryRepository]).
     */
    @HTTP(method = "OPTIONS", path = "api/extras/journal-entries/", hasBody = false)
    suspend fun getJournalEntryOptions(): JsonObject

    /**
     * NetBox's cable-trace endpoints (e.g. `api/dcim/interfaces/<id>/trace/`) return a bare JSON
     * array of `[nearEnds, cable, farEnds]` segments rather than the usual paginated-list or
     * single-object envelope, so neither [listObjects] nor [getObject] fits - see
     * [dev.pschmitt.nyetbox.data.repository.CableTraceRepository].
     */
    @GET suspend fun getJsonArray(@Url url: String): JsonArray

    /**
     * NetBox's rack-elevation and cable-trace endpoints also render an `image/svg+xml` diagram when
     * called with `?render=svg` - a raw, un-typed body `ResponseBody` is Retrofit's built-in escape
     * hatch for a response that isn't JSON, so it bypasses the kotlinx.serialization converter this
     * interface's other methods go through. See
     * [dev.pschmitt.nyetbox.data.repository.SvgDiagramRepository].
     */
    @GET suspend fun getSvg(@Url url: String): ResponseBody
}
