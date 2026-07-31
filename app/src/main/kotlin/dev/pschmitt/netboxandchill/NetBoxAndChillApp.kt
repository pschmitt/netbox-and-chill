package dev.pschmitt.netboxandchill

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class NetBoxAndChillApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        syncScheduler.schedulePeriodic()
    }
}
