package dev.pschmitt.nyetbox.data.repository

import dev.pschmitt.nyetbox.data.api.GenericNetBoxApi
import dev.pschmitt.nyetbox.data.db.CustomFieldDao
import dev.pschmitt.nyetbox.data.db.CustomFieldEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class CustomFieldDefinition(
    val name: String,
    val type: String,
    val label: String?,
    val group: String?,
    val weight: Int,
    val objectTypes: List<String> = emptyList(),
    val choiceSetUrl: String? = null,
)

/** Cache-first access to NetBox's per-instance custom-field type definitions. */
@Singleton
class CustomFieldRepository
@Inject
constructor(private val api: GenericNetBoxApi, private val dao: CustomFieldDao) {

    suspend fun choicesFor(definition: CustomFieldDefinition): List<CreateChoice> {
        val url = definition.choiceSetUrl ?: return emptyList()
        val response = runCatching { api.getObject(url) }.getOrNull() ?: return emptyList()
        val choices = (response["choices"] ?: response["values"]) as? JsonArray
        if (choices != null) {
            return choices.mapNotNull { element ->
                val choice = element as? JsonObject ?: return@mapNotNull null
                val value =
                    (choice["value"] as? JsonPrimitive)?.contentOrNull
                        ?: (choice["name"] as? JsonPrimitive)?.contentOrNull
                        ?: return@mapNotNull null
                val label =
                    (choice["label"] as? JsonPrimitive)?.contentOrNull
                        ?: (choice["display"] as? JsonPrimitive)?.contentOrNull
                        ?: value
                CreateChoice(value, label)
            }
        }
        return listOf("base_choices", "extra_choices")
            .flatMap { key ->
                (response[key] as? JsonArray).orEmpty().mapNotNull { pair ->
                    val values = pair as? JsonArray ?: return@mapNotNull null
                    val value =
                        (values.getOrNull(0) as? JsonPrimitive)?.contentOrNull
                            ?: return@mapNotNull null
                    val label = (values.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: value
                    CreateChoice(value, label)
                }
            }
            .distinctBy { it.value }
    }

    fun observeMarkdownNames(): Flow<Set<String>> = dao.observeMarkdownNames().map { it.toSet() }

    fun observeDefinitions(): Flow<List<CustomFieldDefinition>> =
        dao.observeAll().map { fields ->
            fields.map { field ->
                CustomFieldDefinition(
                    name = field.name,
                    type = field.type,
                    label = field.label,
                    group = field.groupName,
                    weight = field.weight,
                    objectTypes =
                        field.objectTypes
                            .orEmpty()
                            .split(OBJECT_TYPE_SEPARATOR)
                            .filter(String::isNotBlank),
                    choiceSetUrl = field.choiceSetUrl,
                )
            }
        }

    /**
     * Updates the definition cache immediately after a generic create/edit operation. The next full
     * sync remains authoritative, but dependent item forms must see an online or queued definition
     * change without waiting for that sync.
     */
    suspend fun cacheDefinition(objectJson: JsonObject) {
        objectJson.toEntity()?.let { dao.upsertAll(listOf(it)) }
    }

    /** Removes a definition from the dependent-form cache after a generic delete operation. */
    suspend fun removeDefinition(name: String) {
        dao.delete(name)
    }

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
        val name =
            (this["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return null
        val type =
            when (val value = this["type"]) {
                is JsonPrimitive -> value.contentOrNull
                is JsonObject ->
                    (value["value"] as? JsonPrimitive)?.contentOrNull
                        ?: (value["label"] as? JsonPrimitive)?.contentOrNull
                else -> null
            } ?: return null
        val label = (this["label"] as? JsonPrimitive)?.contentOrNull
        val group = ((this["group"] ?: this["group_name"]) as? JsonPrimitive)?.contentOrNull
        val weight = (this["weight"] as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE
        val objectTypes =
            (this["object_types"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.joinToString(OBJECT_TYPE_SEPARATOR)
        val choiceSetUrl =
            ((this["choice_set"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull
        return CustomFieldEntity(
            name = name,
            type = type,
            label = label,
            groupName = group,
            weight = weight,
            objectTypes = objectTypes,
            choiceSetUrl = choiceSetUrl,
            syncedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val OBJECT_TYPE_SEPARATOR = "\u001F"
    }
}
