package dev.pschmitt.netboxandchill.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pschmitt.netboxandchill.di.DownloadClient
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a NetBox attachment (document, image, ...) to the app's cache dir, ready to be opened
 * via FileProvider. Uses [DownloadClient] (auth only, no base-URL rewriting - the media URL NetBox
 * returned is already complete/correct) - NetBox media URLs commonly require the API token too, not
 * just the REST API itself (confirmed against a real instance: unauthenticated media requests 302
 * to the login page).
 */
@Singleton
class FileDownloadRepository
@Inject
constructor(
    @DownloadClient private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {

    data class PersistentStats(val fileCount: Int, val bytes: Long)

    fun persistentStats(): PersistentStats {
        val files =
            File(context.filesDir, "offline-attachments").listFiles().orEmpty().filter { it.isFile }
        return PersistentStats(files.size, files.sumOf { it.length() })
    }

    /**
     * Returns a previously synced durable copy, if one exists for this exact media URL.
     *
     * Some callers use a generated logical name (for example, `device-type-72-front`) while the
     * sync worker uses that same name to derive the stored extension from the downloaded media.
     * Resolve the URL hash independently of the requested display name so those callers still
     * find `hash.png`/`hash.avif` in the offline cache.
     */
    fun persistentFile(url: String, filename: String): File? {
        val exact = persistentPath(url, filename)
        if (exact.isFile) return exact

        val hash = persistentHash(url)
        return exact.parentFile
            ?.listFiles()
            ?.firstOrNull { it.isFile && it.name.substringBeforeLast('.', it.name) == hash }
    }

    /** Downloads an attachment into filesDir so Android's cache eviction cannot remove it. */
    suspend fun downloadToPersistent(url: String, filename: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = persistentPath(url, filename)
                if (target.isFile && target.length() > 0L) return@runCatching target
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "${target.name}.part")
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                    response.body.byteStream().use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                check(temp.renameTo(target)) { "Couldn't finalize downloaded attachment" }
                target
            }
        }

    /** Stores a generated or API-exported artifact alongside durable attachments. */
    suspend fun writeToPersistent(url: String, filename: String, content: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = persistentPath(url, filename)
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "${target.name}.part")
                temp.writeText(content)
                if (target.exists() && !target.delete()) {
                    error("Couldn't replace persistent cache file")
                }
                check(temp.renameTo(target)) { "Couldn't finalize persistent cache file" }
                target
            }
        }

    suspend fun downloadToCache(url: String, filename: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                    val downloadsDir = File(context.cacheDir, "downloads").apply { mkdirs() }
                    val safeFilename = filename.substringAfterLast('/').substringAfterLast('\\')
                    val outFile = File(downloadsDir, safeFilename.ifBlank { "attachment" })
                    response.body.byteStream().use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outFile
                }
            }
        }

    private fun persistentPath(url: String, filename: String): File {
        val hash = persistentHash(url)
        val extension =
            filename
                .substringAfterLast('.', "")
                .takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) }
                ?.let { ".${it.lowercase()}" } ?: ""
        return File(File(context.filesDir, "offline-attachments"), "$hash$extension")
    }

    private fun persistentHash(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
