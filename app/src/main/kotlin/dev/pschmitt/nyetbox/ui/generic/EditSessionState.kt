package dev.pschmitt.nyetbox.ui.generic

/**
 * Presentation state for the generic detail editor.
 *
 * A focused field editor uses the same save path as the full form but deliberately keeps
 * [isEditing] false so the full-form dialog is not reopened underneath it. Keeping the base
 * snapshot, draft, and save flag together prevents those transient states from drifting apart.
 */
internal data class EditSessionState(
    val baseJson: String? = null,
    val draftValues: Map<String, String> = emptyMap(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
) {
    fun beginFullForm(baseJson: String?, values: Map<String, String>): EditSessionState =
        copy(baseJson = baseJson, draftValues = values, isEditing = true, isSaving = false)

    fun beginFocusedField(baseJson: String?): EditSessionState =
        copy(baseJson = baseJson, draftValues = emptyMap(), isEditing = false, isSaving = false)

    fun updateDraft(key: String, value: String): EditSessionState =
        copy(draftValues = draftValues + (key to value))

    fun saving(): EditSessionState = copy(isSaving = true)

    companion object {
        val Idle = EditSessionState()
    }
}
