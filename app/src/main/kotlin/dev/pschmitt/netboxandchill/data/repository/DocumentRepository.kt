package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.schema.documentTypePresentation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class CachedDocument(
    val id: Int,
    val name: String,
    val filename: String,
    val documentUrl: String?,
    val externalUrl: String?,
    val documentType: String?,
    val comments: String?,
)

/** Cache-first access to records from the optional NetBox Documents plugin. */
@Singleton
class DocumentRepository
@Inject
constructor(
    private val directoryRepository: DirectoryRepository,
    private val genericObjectRepository: GenericObjectRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFor(endpointPath: String, objectId: Int): Flow<List<CachedDocument>> =
        directoryRepository
            .observeAll()
            .map { models -> models.firstOrNull(::isDocumentModel)?.endpointPath }
            .distinctUntilChanged()
            .flatMapLatest { documentEndpointPath ->
                documentEndpointPath?.let {
                    genericObjectRepository.observeObjects(it, "")
                } ?: flowOf(emptyList())
            }
            .map { objects ->
                objects
                    .mapNotNull { entity ->
                        parseDocument(entity)?.takeIf {
                            it.assignedObjectMatches(endpointPath, objectId)
                        }
                    }
                    .sortedWith(compareByDescending<CachedDocumentWithTarget> { it.created }.thenBy { it.document.name })
                    .map { it.document }
            }

    private fun isDocumentModel(model: NetBoxModelEntity): Boolean =
        model.appKey.contains("document", ignoreCase = true) &&
            model.modelKey.equals("documents", ignoreCase = true)

    private fun parseDocument(entity: NetBoxObjectEntity): CachedDocumentWithTarget? {
        val objectJson = runCatching {
            json.decodeFromString(JsonObject.serializer(), entity.json)
        }.getOrNull() ?: return null
        val assignedObject = objectJson["assigned_object"] as? JsonObject
        val assignedUrl = assignedObject?.get("url")?.jsonPrimitive?.contentOrNull
        val assignedId =
            assignedObject?.get("id")?.jsonPrimitive?.intOrNull
                ?: objectJson["assigned_object_id"]?.jsonPrimitive?.intOrNull
                ?: return null
        val documentUrl = objectJson["document"]?.jsonPrimitive?.contentOrNull
        val externalUrl = objectJson["external_url"]?.jsonPrimitive?.contentOrNull
        val filename =
            objectJson["filename"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: documentUrl?.substringAfterLast('/')?.substringBefore('?')
                ?: entity.display
        val name =
            objectJson["name"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: entity.display
        val documentTypeCandidates =
            when (val rawType = objectJson["document_type"]) {
                is JsonObject ->
                    listOfNotNull(
                        rawType["value"]?.jsonPrimitive?.contentOrNull,
                        rawType["label"]?.jsonPrimitive?.contentOrNull,
                        rawType["display"]?.jsonPrimitive?.contentOrNull,
                    )
                else -> listOfNotNull(rawType?.jsonPrimitive?.contentOrNull)
            }
        val documentType =
            documentTypeCandidates.firstNotNullOfOrNull { candidate ->
                candidate.takeIf { it.any(Char::isLetter) }?.let { type ->
                    documentTypePresentation(type)?.label ?: type
                }
            }
        return CachedDocumentWithTarget(
            document =
                CachedDocument(
                    id = entity.id,
                    name = name,
                    filename = filename,
                    documentUrl = documentUrl?.takeIf(String::isNotBlank),
                    externalUrl = externalUrl?.takeIf(String::isNotBlank),
                    documentType = documentType,
                    comments = objectJson["comments"]?.jsonPrimitive?.contentOrNull,
                ),
            assignedId = assignedId,
            assignedUrl = assignedUrl,
            created = objectJson["created"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}

private data class CachedDocumentWithTarget(
    val document: CachedDocument,
    val assignedId: Int,
    val assignedUrl: String?,
    val created: String,
) {
    fun assignedObjectMatches(endpointPath: String, objectId: Int): Boolean {
        if (assignedId != objectId) return false
        val assignedUrl = assignedUrl ?: return true
        val route = endpointPath.removePrefix("api/").trim('/').trimEnd('/')
        val expectedSuffix = "/$route/$objectId"
        return assignedUrl.substringBefore('?').trimEnd('/').endsWith(expectedSuffix)
    }
}
