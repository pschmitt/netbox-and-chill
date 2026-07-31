package dev.pschmitt.netboxandchill.ui.generic

enum class EditFieldKind {
    STRING,
    NUMBER,
    BOOLEAN,
}

/**
 * A field that can be edited and PATCHed back, e.g. via NBC-5's edit mode on the generic detail
 * screen. Only plain primitive (string/number/boolean) fields are editable this way for now -
 * references (site, rack, ...) and choice fields need a picker, not a text field, and aren't
 * covered yet.
 */
data class EditableField(val key: String, val label: String, val kind: EditFieldKind, val value: String)
