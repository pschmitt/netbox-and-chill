package dev.pschmitt.netboxandchill.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetBoxCredentials(val baseUrl: String, val token: String) {
    val isValid: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()
}

/**
 * Base URL and API token, backed by [EncryptedSharedPreferences] (Android Keystore-tied, hence
 * `allowBackup=false` in the manifest - a restored backup couldn't decrypt these anyway).
 */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs =
        EncryptedSharedPreferences.create(
            context,
            "netbox_settings",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private val _credentials = MutableStateFlow(loadCredentials())
    val credentials: StateFlow<NetBoxCredentials> = _credentials.asStateFlow()

    val isConfigured: Boolean
        get() = _credentials.value.isValid

    fun save(baseUrl: String, token: String) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val trimmedToken = token.trim()
        prefs
            .edit()
            .putString(KEY_BASE_URL, normalizedBaseUrl)
            .putString(KEY_TOKEN, trimmedToken)
            .apply()
        _credentials.value = NetBoxCredentials(normalizedBaseUrl, trimmedToken)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _credentials.value = NetBoxCredentials("", "")
    }

    private fun loadCredentials() =
        NetBoxCredentials(
            baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
            token = prefs.getString(KEY_TOKEN, "") ?: "",
        )

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
    }
}
