package dev.pschmitt.netboxandchill.data.api

import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor @Inject constructor(private val settingsRepository: SettingsRepository) :
    Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = settingsRepository.credentials.value.token
        val request = chain.request()
        val authorized =
            if (token.isBlank()) request
            else request.newBuilder().header("Authorization", "Token $token").build()
        return chain.proceed(authorized)
    }
}
