package dev.pschmitt.netboxandchill

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class NetBoxAndChillApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var imageLoader: ImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        syncScheduler.schedulePeriodic()
    }

    // Wires AsyncImage/rememberAsyncImagePainter app-wide to the Hilt-provided ImageLoader (the
    // one backed by the authenticated OkHttp client), instead of Coil's unauthenticated default.
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
