package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.CustomFieldDao
import dev.pschmitt.netboxandchill.data.db.CustomFieldEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Cache-first access to NetBox's per-instance custom-field type definitions. */
@Singleton
class CustomFieldRepository
@Inject
constructor(private val api: GenericNetBoxApi, private val dao: CustomFieldDao) {

    fun observeMarkdownNames(): Flow<Set<String>> = dao.observeMarkdownNames().map { it.toSet() }

    suspend fun refresh(): Result<Int> = runCatching {
        val fields = buildList {
            var offset = 0
            while (true) {
                val page =
                    api.listObjects(
                        "api/extras/custom-fields/",
                        mapOf("limit" to PAGE_SIZE.toString(), "offset" to offset.toString()),
                    )
                if (page.results.isEmpty()) break
                page.results.mapNotNullTo(this) { it.toEntity() }
                offset += PAGE_SIZE
                if (page.next == null) break
            }
        }
        dao.replaceAll(fields)
        fields.size
    }

    private fun JsonObject.toEntity(): CustomFieldEntity? {
        val name = (this["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val type =
            when (val value = this["type"]) {
                is JsonPrimitive -> value.contentOrNull
                is JsonObject ->
                    (value["value"] as? JsonPrimitive)?.contentOrNull
                        ?: (value["label"] as? JsonPrimitive)?.contentOrNull
                else -> null
            } ?: return null
        return CustomFieldEntity(name = name, type = type, syncedAt = System.currentTimeMillis())
    }

    private companion object {
        const val PAGE_SIZE = 200
    }
}
