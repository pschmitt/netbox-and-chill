package dev.pschmitt.nyetbox

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dev.pschmitt.nyetbox.crash.CrashReportInstaller
import dev.pschmitt.nyetbox.data.backup.BackupScheduler
import dev.pschmitt.nyetbox.sync.SyncNotifier
import dev.pschmitt.nyetbox.sync.SyncScheduler
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class NyetboxApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var syncNotifier: SyncNotifier
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var imageLoader: ImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        CrashReportInstaller.install(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Channel creation is idempotent/cheap - safe to call unconditionally on every launch
        // rather than tracking whether it's already been done.
        syncNotifier.createChannel()
        syncScheduler.schedulePeriodic()
        syncScheduler.scheduleStartup()
        backupScheduler.schedule()
    }

    // Wires AsyncImage/rememberAsyncImagePainter app-wide to the Hilt-provided ImageLoader (the
    // one backed by the authenticated OkHttp client), instead of Coil's unauthenticated default.
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
