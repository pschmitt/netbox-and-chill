package dev.pschmitt.netboxandchill.ui.common

/** Mutually exclusive states for the label-print operation. */
internal sealed interface PrintOperationState {
    data object Idle : PrintOperationState
    data object Printing : PrintOperationState
    data class Failed(val message: String) : PrintOperationState
}

internal val PrintOperationState.isPrinting: Boolean
    get() = this is PrintOperationState.Printing

internal val PrintOperationState.message: String?
    get() = (this as? PrintOperationState.Failed)?.message
