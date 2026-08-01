package dev.pschmitt.netboxandchill.data.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

/** Raw export endpoint exposed by the optional netbox-topology-views plugin. */
interface TopologyApi {
    @GET suspend fun export(@Url url: String): ResponseBody
}
