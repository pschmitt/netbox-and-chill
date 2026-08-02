package dev.pschmitt.netboxandchill.ui.generic

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A NetBox Journal entry (`/api/extras/journal-entries/`), reduced to what the detail screen
 * renders - see [dev.pschmitt.netboxandchill.data.repository.JournalEntryRepository].
 */
data class JournalEntryUi(
    val id: Int,
    val created: String,
    val kind: String,
    val kindLabel: String,
    val comments: String,
    /** Raw cached object used as the optimistic-edit base for the durable journal outbox. */
    val baseJson: String = "",
)

data class JournalMutationUiState(
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

fun JsonObject.toJournalEntryUi(): JournalEntryUi? {
    val id = this["id"]?.jsonPrimitive?.intOrNull ?: return null
    val created = this["created"]?.jsonPrimitive?.contentOrNull ?: ""
    val kindObj = this["kind"] as? JsonObject
    val kind = kindObj?.get("value")?.jsonPrimitive?.contentOrNull ?: "info"
    val kindLabel = kindObj?.get("label")?.jsonPrimitive?.contentOrNull ?: "Info"
    val comments = this["comments"]?.jsonPrimitive?.contentOrNull ?: ""
    return JournalEntryUi(id, created, kind, kindLabel, comments, toString())
}
