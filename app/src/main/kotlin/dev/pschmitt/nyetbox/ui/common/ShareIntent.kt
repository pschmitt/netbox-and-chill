package dev.pschmitt.nyetbox.ui.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** A chooser-wrapped ACTION_SEND for sharing an object's web URL from a detail screen. */
fun shareIntent(context: Context, url: String): Intent {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
    return Intent.createChooser(send, "Share link").apply {
        putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(context.packageName, "dev.pschmitt.nyetbox.MainActivity")),
        )
    }
}
