package dev.pschmitt.netboxandchill.ui.generic

/** What a single field on a generic detail screen renders as - see GenericFieldRenderer. */
sealed interface FieldRow {
    val label: String

    data class PlainText(override val label: String, val value: String) : FieldRow

    /** NetBox's "comments" fields support Markdown - rendered, not shown as literal text. */
    data class Markdown(override val label: String, val content: String) : FieldRow

    data class Reference(override val label: String, val target: RefTarget) : FieldRow

    data class ReferenceList(override val label: String, val targets: List<RefTarget>) : FieldRow

    data class ChipList(override val label: String, val values: List<String>) : FieldRow
}

/** A tappable link to another NetBox object's generic detail screen. */
data class RefTarget(val display: String, val endpointPath: String, val id: Int)
