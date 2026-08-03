package dev.pschmitt.nyetbox.ui.common

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Plain ACTION_VIEW (not a forced chooser) so common types open directly in whatever app is already
 * the user's default, while types Android can't resolve a default for - or has no associated app at
 * all - naturally fall through to the system's own "Open with" dialog. That's the standard Android
 * resolution behavior for ACTION_VIEW; no extra chooser wrapping needed.
 */
fun fileViewIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mimeType =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
