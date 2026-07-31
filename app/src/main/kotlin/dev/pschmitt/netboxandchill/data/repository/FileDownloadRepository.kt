package dev.pschmitt.netboxandchill.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pschmitt.netboxandchill.di.DownloadClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a NetBox attachment (document, image, ...) to the app's cache dir, ready to be opened
 * via FileProvider. Uses [DownloadClient] (auth only, no base-URL rewriting - the media URL
 * NetBox returned is already complete/correct) - NetBox media URLs commonly require the API
 * token too, not just the REST API itself (confirmed against a real instance: unauthenticated
 * media requests 302 to the login page).
 */
@Singleton
class FileDownloadRepository
@Inject
constructor(
    @DownloadClient private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {

    suspend fun downloadToCache(url: String, filename: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                    val downloadsDir = File(context.cacheDir, "downloads").apply { mkdirs() }
                    val outFile = File(downloadsDir, filename.ifBlank { "attachment" })
                    response.body.byteStream().use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outFile
                }
            }
        }
}
