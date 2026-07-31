package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.NetBoxModelDao
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.schema.Humanize
import dev.pschmitt.netboxandchill.sync.SyncIssueReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

/**
 * Discovers every NetBox object type (including plugin-provided ones) by walking the API's own
 * self-describing root views - `GET api/` lists app namespaces, `GET api/<app>/` lists that app's
 * models - rather than parsing the full OpenAPI schema. This is what drives the sidebar (NBC-6)
 * and the generic list/detail screens.
 */
@Singleton
class DirectoryRepository
@Inject
constructor(
    private val api: GenericNetBoxApi,
    private val dao: NetBoxModelDao,
    private val syncIssueReporter: SyncIssueReporter,
) {

    fun observeAll(): Flow<List<NetBoxModelEntity>> = dao.observeAll()

    fun observePinned(endpointPaths: Set<String>): Flow<List<NetBoxModelEntity>> =
        dao.observeByPaths(endpointPaths)

    suspend fun cachedModelCount(): Int = dao.count()

    suspend fun cachedModels(): List<NetBoxModelEntity> = dao.getAll()

    suspend fun refresh(): Result<Int> = runCatching {
        val root = api.getApiRoot()
        val models = mutableListOf<NetBoxModelEntity>()
        for ((appKey, appUrl) in root) {
            if (appKey in SKIPPED_ROOT_KEYS) continue
            runCatching { discoverApp(appKey, appUrl, models) }
                .onFailure {
                    Timber.w(it, "Failed to discover NetBox app '%s'", appKey)
                    syncIssueReporter.report("Directory discovery failed for $appKey: ${it.message}")
                }
        }
        dao.clear()
        dao.upsertAll(models)
        models.size
    }

    private suspend fun discoverApp(appKey: String, appUrl: String, into: MutableList<NetBoxModelEntity>) {
        val appModels = api.getUrlMap(relativePath(appUrl))
        if (appKey == "plugins") {
            // One extra nesting level: api/plugins/ -> {pluginName: url}, then
            // api/plugins/<plugin>/ -> {modelKey: url} - flatten so each plugin reads as its own
            // sidebar group.
            for ((pluginKey, pluginUrl) in appModels) {
                val pluginModels = runCatching { api.getUrlMap(relativePath(pluginUrl)) }.getOrNull() ?: continue
                for ((modelKey, modelUrl) in pluginModels) {
                    addCollectionModel(
                        into = into,
                        appKey = "plugins/$pluginKey",
                        appLabel = Humanize.label(pluginKey),
                        modelKey = modelKey,
                        modelUrl = modelUrl,
                    )
                }
            }
            return
        }
        val appLabel = if (appKey.length <= 5) appKey.uppercase() else Humanize.label(appKey)
        for ((modelKey, modelUrl) in appModels) {
            addCollectionModel(
                into = into,
                appKey = appKey,
                appLabel = appLabel,
                modelKey = modelKey,
                modelUrl = modelUrl,
            )
        }
    }

    private suspend fun addCollectionModel(
        into: MutableList<NetBoxModelEntity>,
        appKey: String,
        appLabel: String,
        modelKey: String,
        modelUrl: String,
    ) {
        val endpointPath = relativePath(modelUrl)
        if (!isPaginatedCollection(endpointPath)) {
            Timber.i("Skipping non-collection NetBox route %s", endpointPath)
            return
        }
        into +=
            NetBoxModelEntity(
                appKey = appKey,
                appLabel = appLabel,
                modelKey = modelKey,
                modelLabel = Humanize.label(modelKey),
                endpointPath = endpointPath,
            )
    }

    /**
     * DRF's API root includes custom actions alongside router collections. A one-row list probe is
     * deliberately used here because it validates the HTTP method, response shape, and numeric
     * object identity; OPTIONS alone cannot distinguish an XML export, an action that happens to
     * allow GET, or an operational summary collection such as background queues.
     */
    private suspend fun isPaginatedCollection(endpointPath: String): Boolean =
        runCatching {
            val page = api.listObjects(endpointPath, mapOf("limit" to "1", "offset" to "0"))
            page.results.isEmpty() || page.results.any(JsonObject::hasNumericId)
        }.getOrDefault(false)

    private fun relativePath(url: String): String =
        url.toHttpUrlOrNull()?.encodedPath?.trimStart('/') ?: url.trimStart('/')

    private companion object {
        // "status"/"schema"/docs endpoints aren't object collections - nothing to list/generate a
        // screen for.
        val SKIPPED_ROOT_KEYS = setOf("status", "schema", "graphql", "swagger", "redoc", "docs")
    }
}

private fun JsonObject.hasNumericId(): Boolean = this["id"]?.jsonPrimitive?.intOrNull != null
