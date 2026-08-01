package dev.pschmitt.netboxandchill.ui.generic

/** What a single field on a generic detail screen renders as - see GenericFieldRenderer. */
sealed interface FieldRow {
    val label: String

    /** Visual section heading used to separate top-level fields from custom fields. */
    data class Section(override val label: String) : FieldRow

    /** Identifier-shaped fields may expose a trailing copy action. */
    data class PlainText(
        override val label: String,
        val value: String,
        val copyable: Boolean = false,
        val matterPairingCode: Boolean = false,
    ) : FieldRow

    /** A real Boolean value, kept semantic so detail pages can show state instead of Yes/No. */
    data class BooleanValue(override val label: String, val value: Boolean) : FieldRow

    /**
     * A reverse-relation count that can open the related model with the current object as a filter.
     */
    data class Count(override val label: String, val value: String, val target: CountTarget) :
        FieldRow

    /** NetBox's "comments" fields support Markdown - rendered, not shown as literal text. */
    data class Markdown(override val label: String, val content: String) : FieldRow

    data class CustomGroup(override val label: String) : FieldRow

    /** A reference can remain navigable while also exposing its display value for copying. */
    data class Reference(
        override val label: String,
        val target: RefTarget,
        val copyable: Boolean = false,
    ) : FieldRow

    data class ReferenceList(override val label: String, val targets: List<RefTarget>) : FieldRow

    data class ChipList(override val label: String, val values: List<String>) : FieldRow

    /**
     * A downloadable NetBox-served file (a netbox-documents document, an image, ...) - see
     * GenericFieldRenderer's media-URL detection.
     */
    data class FileAttachment(override val label: String, val url: String, val filename: String) :
        FieldRow

    /** A device-type stock photo rendered inline rather than as a download row. */
    data class Image(override val label: String, val url: String) : FieldRow

    /**
     * A plain string field whose value is itself a URL (e.g. a "vendor support URL" custom field) -
     * opens in the browser, as opposed to [Reference] which navigates in-app.
     */
    data class ExternalLink(override val label: String, val url: String) : FieldRow
}

/** A tappable link to another NetBox object's generic detail screen. */
data class RefTarget(val display: String, val endpointPath: String, val id: Int)

data class CountTarget(
    val endpointPath: String,
    val listLabel: String,
    val relationKey: String,
    val parentId: Int,
)
