package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.db.PendingEditEntity
import dev.pschmitt.netboxandchill.sync.SyncIssueReporter
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.HttpException
import retrofit2.Response

internal enum class FakeApiOperation {
    Get,
    Patch,
    Create,
    Delete,
}

@RunWith(Parameterized::class)
class PendingEditReconciliationMatrixTest(
    private val mutation: Mutation,
    private val failure: Failure,
) {

    enum class Mutation(val endpoint: String, val id: Int) {
        Create("api/dcim/devices/", -1),
        Edit("api/dcim/devices/", 1),
        Delete("api/dcim/racks/", 2),
    }

    enum class Failure {
        Connectivity,
        ClientError,
        NotFound,
        ServerError,
        Cancellation,
        Conflict,
    }

    @Test
    fun reconciliationKeepsOrResolvesEachMutationAccordingToFailureClass() = runTest {
        val pending = FakePendingEditDao()
        val objectDao = FakeNetBoxObjectDao()
        val api =
            FakeApi(
                server = server(if (failure == Failure.Conflict) "v2" else "v1"),
                failures = failure.apiFailure()?.let { mapOf(failure.operation() to it) }.orEmpty(),
            )
        val repository =
            PendingEditRepository(
                api,
                pending,
                GenericObjectRepository(api, objectDao, Json, SyncIssueReporter()),
                Json,
            )
        val pendingEdit = pendingEdit()
        pending.upsert(pendingEdit)

        var cancelled = false
        val result =
            try {
                repository.syncPending()
            } catch (_: CancellationException) {
                cancelled = true
                null
            }

        if (failure == Failure.Cancellation) {
            assertTrue(cancelled)
            assertNotNull(pending.get(mutation.endpoint, mutation.id))
            return@runTest
        }

        assertTrue(!cancelled)
        when {
            failure == Failure.NotFound && mutation == Mutation.Delete -> {
                assertNull(pending.get(mutation.endpoint, mutation.id))
                assertEquals(1, result!!.reconciliation.total)
            }
            failure == Failure.Conflict && mutation == Mutation.Edit -> {
                assertEquals(
                    PendingEditEntity.CONFLICT,
                    pending.get(mutation.endpoint, mutation.id)!!.state,
                )
                assertEquals(0, result!!.reconciliation.total)
                assertNull(result.retryableFailure)
            }
            else -> {
                assertNotNull(pending.get(mutation.endpoint, mutation.id))
                if (failure in setOf(Failure.Connectivity, Failure.ServerError)) {
                    assertNotNull(result!!.retryableFailure)
                } else {
                    assertNull(result!!.retryableFailure)
                }
            }
        }
    }

    private fun pendingEdit(): PendingEditEntity =
        when (mutation) {
            Mutation.Create ->
                PendingEditEntity(
                    endpointPath = mutation.endpoint,
                    id = mutation.id,
                    baseJson = "{}",
                    localJson = "{\"id\":-1,\"name\":\"local\"}",
                    patchJson = "{\"name\":\"local\"}",
                    state = PendingEditEntity.CREATE_QUEUED,
                    serverJson = null,
                    createdAt = 1L,
                )
            Mutation.Edit ->
                PendingEditEntity(
                    endpointPath = mutation.endpoint,
                    id = mutation.id,
                    baseJson = server("v1").toString(),
                    localJson = server("v1").plus("name", JsonPrimitive("local")).toString(),
                    patchJson = "{\"name\":\"local\"}",
                    state = PendingEditEntity.QUEUED,
                    serverJson = null,
                    createdAt = 1L,
                )
            Mutation.Delete ->
                PendingEditEntity(
                    endpointPath = mutation.endpoint,
                    id = mutation.id,
                    baseJson = server("v1").toString(),
                    localJson = server("v1").toString(),
                    patchJson = "{}",
                    state = PendingEditEntity.DELETE_QUEUED,
                    serverJson = null,
                    createdAt = 1L,
                )
        }

    private fun Failure.operation(): FakeApiOperation =
        when (mutation) {
            Mutation.Create -> FakeApiOperation.Create
            Mutation.Edit -> FakeApiOperation.Get
            Mutation.Delete -> FakeApiOperation.Delete
        }

    private fun Failure.apiFailure(): Throwable? =
        when (this) {
            Failure.Connectivity -> IOException("offline")
            Failure.ClientError -> httpException(400)
            Failure.NotFound -> httpException(404)
            Failure.ServerError -> httpException(500)
            Failure.Cancellation -> CancellationException("cancelled")
            Failure.Conflict -> null
        }

    private fun server(version: String): JsonObject =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(mutation.id.coerceAtLeast(1)),
                "name" to JsonPrimitive("server"),
                "display" to JsonPrimitive("server"),
                "last_updated" to JsonPrimitive(version),
            )
        )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} / {1}")
        fun cases(): List<Array<Any>> =
            buildList {
                Mutation.entries.forEach { mutation ->
                    Failure.entries.forEach { failure ->
                        if (failure != Failure.Conflict || mutation == Mutation.Edit) {
                            add(arrayOf(mutation, failure))
                        }
                    }
                }
            }

        private fun httpException(code: Int): HttpException =
            HttpException(
                Response.error<JsonObject>(
                    code,
                    "reconciliation failure".toResponseBody("text/plain".toMediaType()),
                )
            )
    }
}

private fun JsonObject.plus(key: String, value: JsonPrimitive): JsonObject =
    JsonObject(buildMap {
        putAll(this@plus)
        put(key, value)
    })
