package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.PendingEditDao
import dev.pschmitt.netboxandchill.data.db.PendingEditEntity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber

sealed interface EditSubmission {
    data object Updated : EditSubmission

    data object Queued : EditSubmission

    data object ConflictDetected : EditSubmission
}

class StaleConflictException : Exception("The server changed again; review the conflict once more")

/** Durable edit outbox and three-way conflict store for generic NetBox objects (NBC-32). */
@Singleton
class PendingEditRepository
@Inject
constructor(
    private val api: GenericNetBoxApi,
    private val pendingEditDao: PendingEditDao,
    private val genericObjectRepository: GenericObjectRepository,
    private val json: Json,
) {
    fun observeConflicts(): Flow<List<PendingEditEntity>> = pendingEditDao.observeConflicts()

    fun observeConflictCount(): Flow<Int> = pendingEditDao.observeConflictCount()

    /** Checks the server version before PATCHing, or persists the edit when the network is down. */
    suspend fun submitEdit(
        endpointPath: String,
        id: Int,
        baseJson: String,
        patch: JsonObject,
    ): Result<EditSubmission> {
        val existing = pendingEditDao.get(endpointPath, id)
        val effectiveBase = existing?.baseJson ?: baseJson
        val effectiveLocal = merge(decode(existing?.localJson ?: baseJson), patch)
        val effectivePatch = merge(decode(existing?.patchJson ?: "{}"), patch)
        val edit =
            PendingEditEntity(
                endpointPath = endpointPath,
                id = id,
                baseJson = effectiveBase,
                localJson = encode(effectiveLocal),
                patchJson = encode(effectivePatch),
                state = PendingEditEntity.QUEUED,
                serverJson = null,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )

        return try {
            val server = api.getObject("$endpointPath$id/")
            if (hasChanged(decode(effectiveBase), server)) {
                pendingEditDao.upsert(edit.copy(state = PendingEditEntity.CONFLICT, serverJson = encode(server)))
                genericObjectRepository.cacheLocalObject(endpointPath, effectiveLocal)
                Result.success(EditSubmission.ConflictDetected)
            } else {
                val updated = api.patchObject("$endpointPath$id/", effectivePatch)
                genericObjectRepository.cacheLocalObject(endpointPath, updated)
                pendingEditDao.delete(endpointPath, id)
                Result.success(EditSubmission.Updated)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            pendingEditDao.upsert(edit)
            genericObjectRepository.cacheLocalObject(endpointPath, effectiveLocal)
            Result.success(EditSubmission.Queued)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** Retries queued edits before the normal cache sync can overwrite their local view. */
    suspend fun syncPending() {
        for (edit in pendingEditDao.getQueued()) {
            try {
                val server = api.getObject("${edit.endpointPath}${edit.id}/")
                if (hasChanged(decode(edit.baseJson), server)) {
                    pendingEditDao.upsert(edit.copy(state = PendingEditEntity.CONFLICT, serverJson = encode(server)))
                    continue
                }
                val updated = api.patchObject("${edit.endpointPath}${edit.id}/", decode(edit.patchJson))
                genericObjectRepository.cacheLocalObject(edit.endpointPath, updated)
                pendingEditDao.delete(edit.endpointPath, edit.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                // Leave the edit queued for the next scheduled/manual sync.
                return
            } catch (error: Exception) {
                Timber.w(error, "Pending edit sync failed for %s/%d", edit.endpointPath, edit.id)
            }
        }
    }

    /** Applies the selected local fields, after confirming the conflict's server snapshot is current. */
    suspend fun resolveConflict(edit: PendingEditEntity, keepLocalKeys: Set<String>): Result<Unit> {
        val savedServer = edit.serverJson ?: return Result.failure(IllegalStateException("Conflict has no server snapshot"))
        return try {
            val currentServer = api.getObject("${edit.endpointPath}${edit.id}/")
            if (hasChanged(decode(savedServer), currentServer)) {
                pendingEditDao.upsert(edit.copy(serverJson = encode(currentServer)))
                return Result.failure(StaleConflictException())
            }
            val local = decode(edit.localJson)
            val patch =
                JsonObject(
                    keepLocalKeys.mapNotNull { key ->
                        local[key]?.let { key to it }
                    }.toMap()
                )
            if (patch.isNotEmpty()) {
                val updated = api.patchObject("${edit.endpointPath}${edit.id}/", patch)
                genericObjectRepository.cacheLocalObject(edit.endpointPath, updated)
            } else {
                genericObjectRepository.cacheLocalObject(edit.endpointPath, currentServer)
            }
            pendingEditDao.delete(edit.endpointPath, edit.id)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun decode(raw: String): JsonObject =
        json.decodeFromString(JsonObject.serializer(), raw)

    private fun encode(value: JsonObject): String =
        json.encodeToString(JsonObject.serializer(), value)

    private fun merge(base: JsonObject, patch: JsonObject): JsonObject =
        JsonObject(buildMap {
            putAll(base)
            putAll(patch)
        })

    private fun hasChanged(base: JsonObject, server: JsonObject): Boolean {
        val baseVersion = version(base)
        val serverVersion = version(server)
        return if (baseVersion != null && serverVersion != null) {
            baseVersion != serverVersion
        } else {
            base != server
        }
    }

    private fun version(value: JsonObject): String? =
        (value["last_updated"] as? JsonPrimitive)?.contentOrNull
}
