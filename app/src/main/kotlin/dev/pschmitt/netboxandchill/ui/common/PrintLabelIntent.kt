package dev.pschmitt.netboxandchill.ui.common

import android.content.Intent

/**
 * NBC-10: the user's existing [printlabel](https://github.com/pschmitt/printlabel) project is a
 * local shell/Python CLI that talks directly over Bluetooth to a paired Brother P-Touch Cube
 * label printer (see `ptcbp.py`/`labelmaker.py` in that repo) - it has no daemon, server, or HTTP
 * surface an Android app could call into directly, so a real in-app "print" integration isn't
 * feasible in this pass (see the NBC-10 entry in TODO.md for the full investigation).
 *
 * Its `--netbox QUERY` mode already does everything needed once it runs on the user's machine:
 * given a NetBox device id (and optionally `--netbox-url`), it resolves the device via the `nbx`
 * CLI and prints its QR/asset-tag label. So the only useful thing this app can do is hand the user
 * a ready-to-run invocation for their own printlabel setup, via the same chooser-wrapped
 * `ACTION_SEND` share pattern as [shareIntent].
 */
fun printLabelCommand(deviceId: Int, netboxBaseUrl: String?): String = buildString {
    append("printlabel --netbox ")
    append(deviceId)
    if (!netboxBaseUrl.isNullOrBlank()) {
        append(" --netbox-url ")
        append(netboxBaseUrl)
    }
}

/** Share sheet for a ready-to-run `printlabel --netbox <id>` command - see [printLabelCommand]. */
fun printLabelShareIntent(deviceId: Int, netboxBaseUrl: String?, deviceName: String? = null): Intent {
    val subject = if (deviceName.isNullOrBlank()) "Print label" else "Print label - $deviceName"
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, printLabelCommand(deviceId, netboxBaseUrl))
        }
    return Intent.createChooser(send, "Print label via printlabel")
}
