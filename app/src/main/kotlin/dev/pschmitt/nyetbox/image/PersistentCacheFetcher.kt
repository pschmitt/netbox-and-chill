package dev.pschmitt.nyetbox.image

import android.net.Uri
import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.request.Options
import dev.pschmitt.nyetbox.data.repository.FileDownloadRepository

/**
 * Resolves NetBox media URLs against the app's offline attachment cache before falling through to
 * the network fetcher, inside Coil's own async pipeline instead of on the UI thread.
 *
 * Previously, screens looked up the cached file themselves (a filesystem stat, or a full directory
 * scan on a cache miss) synchronously inside `remember` blocks in list-row composables - meaning
 * every new row scrolling into a LazyColumn blocked the frame on disk I/O. Registering this factory
 * ahead of [coil3.network.okhttp.OkHttpNetworkFetcherFactory] lets callers just pass the remote URL
 * and have local-vs-remote resolution happen off the main thread, the same way an unmodified
 * AsyncImage/Coil setup already works.
 */
class PersistentCacheFetcher {
    class Factory(private val fileDownloadRepository: FileDownloadRepository) :
        Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "http" && data.scheme != "https") return null
            val file = fileDownloadRepository.persistentFile(data.toString()) ?: return null
            return LocalFileFetcher(file, options)
        }
    }
}
