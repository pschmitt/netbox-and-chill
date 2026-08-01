package dev.pschmitt.netboxandchill.data.api

import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/** Stops absolute media/document requests from bypassing the app's cached-only mode. */
class OfflineModeInterceptor
@Inject
constructor(private val settingsRepository: SettingsRepository) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (settingsRepository.offlineMode.value) {
            throw IOException("Offline mode is enabled")
        }
        return chain.proceed(chain.request())
    }
}
