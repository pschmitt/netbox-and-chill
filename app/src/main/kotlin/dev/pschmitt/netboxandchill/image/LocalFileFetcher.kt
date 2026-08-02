package dev.pschmitt.netboxandchill.image

import android.content.ContentResolver
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.io.File
import java.net.URLConnection
import okio.Path.Companion.toPath

/**
 * Reads images that were downloaded into the app's offline cache.
 *
 * Coil's built-in file fetcher is internal in Coil 3. Registering this small public fetcher keeps
 * cached media working across devices even when platform components are not discovered through
 * Coil's service loader.
 */
class LocalFileFetcher(
    private val file: File,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        SourceFetchResult(
            source = ImageSource(file.path.toPath(), options.fileSystem, diskCacheKey = file.path),
            mimeType = URLConnection.guessContentTypeFromName(file.name),
            dataSource = DataSource.DISK,
        )

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != ContentResolver.SCHEME_FILE) return null
            val path = data.path ?: return null
            return File(path).takeIf { it.isFile }?.let { LocalFileFetcher(it, options) }
        }
    }
}
