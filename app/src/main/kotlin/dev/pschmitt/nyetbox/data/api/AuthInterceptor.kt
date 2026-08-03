package dev.pschmitt.nyetbox.data.api

import dev.pschmitt.nyetbox.data.repository.SettingsRepository
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
            else {
                // NetBox 4.5+ v2 tokens carry a non-secret key and use Bearer auth. Keep the
                // legacy 40-character Token form for older instances and existing connections.
                val scheme = if (token.startsWith("nbt_")) "Bearer" else "Token"
                request.newBuilder().header("Authorization", "$scheme $token").build()
            }
        return chain.proceed(authorized)
    }
}
