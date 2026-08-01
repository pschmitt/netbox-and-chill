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

data class SyncIssue(val message: String, val occurredAt: Long)

/** Stable object key used by field preferences, e.g. `device`. */
fun hiddenFieldObjectKey(endpointPath: String): String =
    endpointPath.trim('/').substringAfterLast('/').lowercase().let(::singularizeModel)

/** Stable user-facing key for a field preference, e.g. `device/model`. */
fun hiddenFieldPreferenceKey(endpointPath: String, fieldName: String): String {
    val model = hiddenFieldObjectKey(endpointPath)
    val field = fieldName.trim().lowercase().replace(Regex("\\s+"), "_")
    return "$model/$field"
}

private fun singularizeModel(model: String): String =
    when {
        model.endsWith("ies") -> model.removeSuffix("ies") + "y"
        model.endsWith("sses") -> model.removeSuffix("es")
        model.endsWith("xes") -> model.removeSuffix("es")
        model.endsWith("s") -> model.removeSuffix("s")
        else -> model
    }

fun normalizeHiddenFieldPreferenceKey(value: String): String? {
    val normalized =
        value
            .trim()
            .lowercase()
            .replace(Regex("\\s*/\\s*"), "/")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_./-]"), "")
    val parts = normalized.split('/', limit = 2)
    return normalized.takeIf { parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank() }
}

enum class GestureAction(val storageKey: String, val label: String) {
    Off("off", "Off"),
    GlobalSearch("global_search", "Global search"),
    Scanner("scanner", "QR scanner");

    companion object {
        fun fromStorage(value: String?): GestureAction =
            values().firstOrNull { it.storageKey == value } ?: GlobalSearch
    }
}

enum class ScannerLens(val storageKey: String, val label: String) {
    Back("back", "Back camera"),
    Front("front", "Front camera");

    companion object {
        fun fromStorage(value: String?): ScannerLens =
            entries.firstOrNull { it.storageKey == value } ?: Back
    }
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

    // Endpoint paths (e.g. "api/dcim/racks/") pinned to the top of the sidebar - user-configurable
    // via the star toggle on each model row. Defaults to just Devices, this app's original focus.
    private val _pinnedModelPaths = MutableStateFlow(loadPinnedModelPaths())
    val pinnedModelPaths: StateFlow<Set<String>> = _pinnedModelPaths.asStateFlow()

    // Off by default: downloading every cached object's attachments is a meaningful amount of
    // storage/bandwidth the user should opt into, not something that happens the first time they
    // sync.
    private val _syncAttachmentsToDisk = MutableStateFlow(prefs.getBoolean(KEY_SYNC_ATTACHMENTS, false))
    val syncAttachmentsToDisk: StateFlow<Boolean> = _syncAttachmentsToDisk.asStateFlow()

    private val _gestureAction = MutableStateFlow(loadGestureAction())
    val gestureAction: StateFlow<GestureAction> = _gestureAction.asStateFlow()

    private val _scannerLens = MutableStateFlow(loadScannerLens())
    val scannerLens: StateFlow<ScannerLens> = _scannerLens.asStateFlow()

    private val _offlineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_MODE, false))
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    private val _syncIssue = MutableStateFlow(loadSyncIssue())
    val syncIssue: StateFlow<SyncIssue?> = _syncIssue.asStateFlow()

    private val _lastSuccessfulSyncAt = MutableStateFlow(loadLastSuccessfulSyncAt())
    val lastSuccessfulSyncAt: StateFlow<Long?> = _lastSuccessfulSyncAt.asStateFlow()

    private val _hiddenFieldKeys = MutableStateFlow(loadHiddenFieldKeys())
    val hiddenFieldKeys: StateFlow<Set<String>> = _hiddenFieldKeys.asStateFlow()

    private val _sidebarAppOrder = MutableStateFlow(loadOrder(KEY_SIDEBAR_APP_ORDER))
    val sidebarAppOrder: StateFlow<List<String>> = _sidebarAppOrder.asStateFlow()

    private val _sidebarModelOrders = MutableStateFlow(loadModelOrders())
    val sidebarModelOrders: StateFlow<Map<String, List<String>>> = _sidebarModelOrders.asStateFlow()

    fun setSyncAttachmentsToDisk(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ATTACHMENTS, enabled).apply()
        _syncAttachmentsToDisk.value = enabled
    }

    fun setGestureAction(action: GestureAction) {
        prefs.edit().putString(KEY_GESTURE_ACTION, action.storageKey).apply()
        _gestureAction.value = action
    }

    fun setScannerLens(lens: ScannerLens) {
        prefs.edit().putString(KEY_SCANNER_LENS, lens.storageKey).apply()
        _scannerLens.value = lens
    }

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
        _offlineMode.value = enabled
    }

    fun recordSyncIssue(error: Throwable) {
        recordSyncIssue(
            error.message?.takeIf { it.isNotBlank() }
                ?: error::class.simpleName?.takeIf { it.isNotBlank() }
                ?: "Sync failed"
        )
    }

    fun recordSyncIssue(message: String) {
        val issueMessage = message.takeIf { it.isNotBlank() } ?: "Sync failed"
        val issue = SyncIssue(issueMessage.take(MAX_SYNC_MESSAGE_LENGTH), System.currentTimeMillis())
        prefs
            .edit()
            .putString(KEY_SYNC_ISSUE_MESSAGE, issue.message)
            .putLong(KEY_SYNC_ISSUE_TIME, issue.occurredAt)
            .apply()
        _syncIssue.value = issue
    }

    fun clearSyncIssue() {
        prefs.edit().remove(KEY_SYNC_ISSUE_MESSAGE).remove(KEY_SYNC_ISSUE_TIME).apply()
        _syncIssue.value = null
    }

    fun recordSuccessfulSync() {
        val timestamp = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SUCCESSFUL_SYNC, timestamp).apply()
        _lastSuccessfulSyncAt.value = timestamp
    }

    fun addHiddenField(key: String) {
        val normalized = normalizeHiddenFieldPreferenceKey(key) ?: return
        val updated = _hiddenFieldKeys.value + normalized
        prefs.edit().putStringSet(KEY_HIDDEN_FIELDS, updated).apply()
        _hiddenFieldKeys.value = updated
    }

    fun removeHiddenField(key: String) {
        val updated = _hiddenFieldKeys.value - key
        prefs.edit().putStringSet(KEY_HIDDEN_FIELDS, updated).apply()
        _hiddenFieldKeys.value = updated
    }

    fun setSidebarAppOrder(order: List<String>) {
        val normalized = order.distinct().filter(String::isNotBlank)
        prefs.edit().putString(KEY_SIDEBAR_APP_ORDER, normalized.joinToString(ORDER_SEPARATOR)).apply()
        _sidebarAppOrder.value = normalized
    }

    fun setSidebarModelOrder(appKey: String, order: List<String>) {
        val normalized = order.distinct().filter(String::isNotBlank)
        val updated = _sidebarModelOrders.value.toMutableMap()
        updated[appKey] = normalized
        prefs.edit().putString(KEY_SIDEBAR_MODEL_ORDERS, encodeModelOrders(updated)).apply()
        _sidebarModelOrders.value = updated
    }

    fun togglePinned(endpointPath: String) {
        val current = _pinnedModelPaths.value
        val updated = if (endpointPath in current) current - endpointPath else current + endpointPath
        prefs.edit().putStringSet(KEY_PINNED_MODELS, updated).apply()
        _pinnedModelPaths.value = updated
    }

    private fun loadPinnedModelPaths(): Set<String> =
        prefs.getStringSet(KEY_PINNED_MODELS, null) ?: setOf(DEFAULT_PINNED_MODEL_PATH)

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
        _offlineMode.value = false
        _hiddenFieldKeys.value = emptySet()
        clearSyncIssue()
    }

    private fun loadCredentials() =
        NetBoxCredentials(
            baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
            token = prefs.getString(KEY_TOKEN, "") ?: "",
        )

    private fun loadSyncIssue(): SyncIssue? {
        val message = prefs.getString(KEY_SYNC_ISSUE_MESSAGE, null)?.takeIf { it.isNotBlank() }
        val occurredAt = prefs.getLong(KEY_SYNC_ISSUE_TIME, 0L)
        return if (message != null && occurredAt > 0L) SyncIssue(message, occurredAt) else null
    }

    private fun loadLastSuccessfulSyncAt(): Long? =
        prefs.getLong(KEY_LAST_SUCCESSFUL_SYNC, 0L).takeIf { it > 0L }

    private fun loadGestureAction(): GestureAction =
        GestureAction.fromStorage(prefs.getString(KEY_GESTURE_ACTION, GestureAction.GlobalSearch.storageKey))

    private fun loadScannerLens(): ScannerLens =
        ScannerLens.fromStorage(prefs.getString(KEY_SCANNER_LENS, ScannerLens.Back.storageKey))

    private fun loadHiddenFieldKeys(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_FIELDS, null).orEmpty().mapNotNull(::normalizeHiddenFieldPreferenceKey).toSet()

    private fun loadOrder(key: String): List<String> =
        prefs.getString(key, null).orEmpty().split(ORDER_SEPARATOR).filter(String::isNotBlank)

    private fun loadModelOrders(): Map<String, List<String>> =
        prefs
            .getString(KEY_SIDEBAR_MODEL_ORDERS, null)
            .orEmpty()
            .split(MODEL_ENTRY_SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(ORDER_SEPARATOR, limit = 2)
                if (parts.size != 2 || parts[0].isBlank()) return@mapNotNull null
                parts[0] to parts[1].split(ITEM_SEPARATOR).filter(String::isNotBlank)
            }
            .toMap()

    private fun encodeModelOrders(orders: Map<String, List<String>>): String =
        orders.entries.joinToString(MODEL_ENTRY_SEPARATOR) { (appKey, modelKeys) ->
            appKey + ORDER_SEPARATOR + modelKeys.joinToString(ITEM_SEPARATOR)
        }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_PINNED_MODELS = "pinned_model_paths"
        const val DEFAULT_PINNED_MODEL_PATH = "api/dcim/devices/"
        const val KEY_SYNC_ATTACHMENTS = "sync_attachments_to_disk"
        const val KEY_GESTURE_ACTION = "two_finger_swipe_action"
        const val KEY_SCANNER_LENS = "scanner_default_lens"
        const val KEY_OFFLINE_MODE = "offline_mode"
        const val KEY_SYNC_ISSUE_MESSAGE = "sync_issue_message"
        const val KEY_SYNC_ISSUE_TIME = "sync_issue_time"
        const val KEY_LAST_SUCCESSFUL_SYNC = "last_successful_sync"
        const val MAX_SYNC_MESSAGE_LENGTH = 1000
        const val KEY_HIDDEN_FIELDS = "hidden_field_keys"
        const val KEY_SIDEBAR_APP_ORDER = "sidebar_app_order"
        const val KEY_SIDEBAR_MODEL_ORDERS = "sidebar_model_orders"
        const val ORDER_SEPARATOR = "\u001F"
        const val ITEM_SEPARATOR = "\u001E"
        const val MODEL_ENTRY_SEPARATOR = "\u001D"
    }
}
