package dev.pschmitt.netboxandchill.ui.generic

/**
 * Route-focused editing is a one-shot action. A review dialog is a separate transient state and
 * must prevent the retained route argument from opening the editor again while it is visible.
 */
internal fun shouldLaunchRouteFocusedEditor(
    routeFocusHandled: Boolean,
    focusFieldKey: String?,
    focusedEditFieldKey: String?,
    hasPendingEdits: Boolean,
): Boolean =
    focusFieldKey != null &&
        !routeFocusHandled &&
        focusedEditFieldKey == null &&
        !hasPendingEdits
