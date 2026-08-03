package dev.pschmitt.netboxandchill.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import dev.pschmitt.netboxandchill.data.topology.TopologyPosition
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

data class PrintSettings(
    val defaultPrinterName: String? = null,
    val defaultPrinterAddress: String? = null,
    val invertColors: Boolean = true,
    val verticalText: Boolean = false,
    val longLabel: Boolean = false,
    val copies: Int = 1,
    val qrSize: Int = 64,
) {
    fun normalized(): PrintSettings =
        copy(
            defaultPrinterName = defaultPrinterName?.takeIf { it.isNotBlank() },
            defaultPrinterAddress = defaultPrinterAddress?.takeIf { it.isNotBlank() },
            copies = copies.coerceIn(1, 9),
            qrSize = qrSize.takeIf { it == 48 || it == 56 || it == 64 } ?: 64,
        )
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

data class GestureTarget(val endpointPath: String, val label: String, val id: Int? = null)

enum class GestureAction(val storageKey: String, val label: String) {
    Off("off", "Off"),
    GlobalSearch("global_search", "Global search"),
    Scanner("scanner", "QR scanner"),
    Settings("settings", "Settings"),
    Add("add", "Add item"),
    AddSpecific("add_specific", "Add specific item type"),
    Sync("sync", "Sync now"),
    OfflineOn("offline_on", "Turn offline mode on"),
    OfflineOff("offline_off", "Turn offline mode off"),
    DeviceList("device_list", "Device list"),
    ListSpecific("list_specific", "Specific item list"),
    DetailSpecific("detail_specific", "Specific item detail");

    companion object {
        fun fromStorage(value: String?, fallback: GestureAction = GlobalSearch): GestureAction =
            entries.firstOrNull { it.storageKey == value } ?: fallback
    }
}

enum class GestureShortcut(val storageKey: String, val label: String) {
    TwoFingerDown("two_finger_down", "Two-finger swipe down"),
    TwoFingerLeft("two_finger_left", "Two-finger swipe left"),
    TwoFingerRight("two_finger_right", "Two-finger swipe right"),
    ThreeFingerUp("three_finger_up", "Three-finger swipe up"),
    ThreeFingerDown("three_finger_down", "Three-finger swipe down"),
    ThreeFingerLeft("three_finger_left", "Three-finger swipe left"),
    ThreeFingerRight("three_finger_right", "Three-finger swipe right");
}

enum class ScannerLens(val storageKey: String, val label: String) {
    Back("back", "Back camera"),
    Front("front", "Front camera");

    companion object {
        fun fromStorage(value: String?): ScannerLens =
            entries.firstOrNull { it.storageKey == value } ?: Back
    }
}

enum class ScannerRearLens(val storageKey: String, val label: String) {
    Automatic("automatic", "Automatic (main rear lens)"),
    UltraWide("ultra_wide", "Ultra-wide (0.6×)"),
    Wide("wide", "Wide (1×)"),
    Telephoto("telephoto", "Telephoto (2×)");

    companion object {
        fun fromStorage(value: String?): ScannerRearLens =
            entries.firstOrNull { it.storageKey == value } ?: Automatic
    }
}

enum class ThemeMode(val storageKey: String, val label: String) {
    FollowSystem("system", "Follow system"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == value } ?: FollowSystem
    }
}

enum class ThemeAccent(val storageKey: String, val label: String) {
    System("system", "System default"),
    Teal("teal", "Teal"),
    Blue("blue", "Blue"),
    Purple("purple", "Purple"),
    Orange("orange", "Orange"),
    Pink("pink", "Pink"),
    Green("green", "Green");

    companion object {
        fun fromStorage(value: String?): ThemeAccent =
            entries.firstOrNull { it.storageKey == value } ?: System
    }
}

/**
 * Base URL and API token, backed by [EncryptedSharedPreferences] (Android Keystore-tied, hence
 * `allowBackup=false` in the manifest - a restored backup couldn't decrypt these anyway).
 */
// AndroidX Security Crypto currently deprecates this API without providing a replacement for
// the same encrypted SharedPreferences migration path. Keep it until the library offers one;
// the suppression makes this intentional compatibility boundary visible to the compiler.
@Suppress("DEPRECATION", "UseKtx")
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
    private val _syncAttachmentsToDisk =
        MutableStateFlow(prefs.getBoolean(KEY_SYNC_ATTACHMENTS, false))
    val syncAttachmentsToDisk: StateFlow<Boolean> = _syncAttachmentsToDisk.asStateFlow()

    // Preserve the existing connected-network behavior by default; users can opt into the safer
    // Wi-Fi-only policy when a full cache (especially attachments) should never use mobile data.
    private val _syncOnlyOnWifi = MutableStateFlow(prefs.getBoolean(KEY_SYNC_ONLY_ON_WIFI, false))
    val syncOnlyOnWifi: StateFlow<Boolean> = _syncOnlyOnWifi.asStateFlow()

    private val _syncWhileRoaming = MutableStateFlow(prefs.getBoolean(KEY_SYNC_WHILE_ROAMING, true))
    val syncWhileRoaming: StateFlow<Boolean> = _syncWhileRoaming.asStateFlow()

    // Preserve the existing behavior for existing installs; users can opt out when startup
    // refreshes are undesirable on metered or otherwise constrained devices.
    private val _syncOnAppLaunch = MutableStateFlow(prefs.getBoolean(KEY_SYNC_ON_APP_LAUNCH, true))
    val syncOnAppLaunch: StateFlow<Boolean> = _syncOnAppLaunch.asStateFlow()

    private val _changeNotificationsEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_CHANGE_NOTIFICATIONS_ENABLED, false))
    val changeNotificationsEnabled: StateFlow<Boolean> = _changeNotificationsEnabled.asStateFlow()

    private val _changeNotificationFilters =
        MutableStateFlow(loadChangeNotificationFilters())
    val changeNotificationFilters: StateFlow<Set<String>> =
        _changeNotificationFilters.asStateFlow()

    /** Highest object-change id seen during a changelog refresh, used to avoid historical spam. */
    var changeNotificationCursor: Int
        get() = prefs.getInt(KEY_CHANGE_NOTIFICATION_CURSOR, 0)
        private set(value) {
            prefs.edit().putInt(KEY_CHANGE_NOTIFICATION_CURSOR, value).apply()
        }

    private val _gestureActions = MutableStateFlow(loadGestureActions())
    val gestureActions: StateFlow<Map<GestureShortcut, GestureAction>> = _gestureActions.asStateFlow()

    private val _gestureTargets = MutableStateFlow(loadGestureTargets())
    val gestureTargets: StateFlow<Map<GestureShortcut, GestureTarget>> = _gestureTargets.asStateFlow()

    private val _scannerLens = MutableStateFlow(loadScannerLens())
    val scannerLens: StateFlow<ScannerLens> = _scannerLens.asStateFlow()

    private val _scannerRearLens = MutableStateFlow(loadScannerRearLens())
    val scannerRearLens: StateFlow<ScannerRearLens> = _scannerRearLens.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _themeAccent = MutableStateFlow(loadThemeAccent())
    val themeAccent: StateFlow<ThemeAccent> = _themeAccent.asStateFlow()

    private val _objectTypeAccents = MutableStateFlow(loadObjectTypeAccents())
    val objectTypeAccents: StateFlow<Map<String, ThemeAccent>> =
        _objectTypeAccents.asStateFlow()

    private val _printSettings = MutableStateFlow(loadPrintSettings())
    val printSettings: StateFlow<PrintSettings> = _printSettings.asStateFlow()

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

    private val _hiddenSidebarApps = MutableStateFlow(loadHiddenSidebarApps())
    val hiddenSidebarApps: StateFlow<Set<String>> = _hiddenSidebarApps.asStateFlow()

    private val _dashboardSectionOrder = MutableStateFlow(loadOrder(KEY_DASHBOARD_SECTION_ORDER))
    val dashboardSectionOrder: StateFlow<List<String>> = _dashboardSectionOrder.asStateFlow()

    private val _hiddenDashboardSections = MutableStateFlow(loadHiddenDashboardSections())
    val hiddenDashboardSections: StateFlow<Set<String>> =
        _hiddenDashboardSections.asStateFlow()

    private val _showTopologyDeviceTypeImages =
        MutableStateFlow(prefs.getBoolean(KEY_SHOW_TOPOLOGY_DEVICE_TYPE_IMAGES, true))
    val showTopologyDeviceTypeImages: StateFlow<Boolean> =
        _showTopologyDeviceTypeImages.asStateFlow()

    private val _topologyNodePositions = MutableStateFlow(loadTopologyNodePositions())
    val topologyNodePositions: StateFlow<Map<String, TopologyPosition>> =
        _topologyNodePositions.asStateFlow()

    fun setSyncAttachmentsToDisk(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ATTACHMENTS, enabled).apply()
        _syncAttachmentsToDisk.value = enabled
    }

    fun setSyncOnlyOnWifi(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ONLY_ON_WIFI, enabled).apply()
        _syncOnlyOnWifi.value = enabled
    }

    fun setSyncWhileRoaming(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_WHILE_ROAMING, enabled).apply()
        _syncWhileRoaming.value = enabled
    }

    fun setSyncOnAppLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ON_APP_LAUNCH, enabled).apply()
        _syncOnAppLaunch.value = enabled
    }

    fun setChangeNotificationsEnabled(enabled: Boolean) {
        var filters = _changeNotificationFilters.value
        if (enabled && filters.isEmpty()) {
            filters = setOf(ChangeNotificationFilter.All.storageKey)
            persistChangeNotificationFilters(filters)
        }
        prefs.edit().putBoolean(KEY_CHANGE_NOTIFICATIONS_ENABLED, enabled).apply()
        _changeNotificationsEnabled.value = enabled
    }

    fun setChangeNotificationFilter(filter: ChangeNotificationFilter, enabled: Boolean) {
        val current = _changeNotificationFilters.value
        val updated =
            when {
                filter == ChangeNotificationFilter.All && enabled ->
                    setOf(ChangeNotificationFilter.All.storageKey)
                filter == ChangeNotificationFilter.All -> current - filter.storageKey
                enabled -> (current - ChangeNotificationFilter.All.storageKey) + filter.storageKey
                else -> current - filter.storageKey
            }
        persistChangeNotificationFilters(updated)
    }

    fun recordChangeNotificationCursor(id: Int) {
        if (id > changeNotificationCursor) changeNotificationCursor = id
    }

    fun setGestureAction(action: GestureAction) {
        setGestureAction(GestureShortcut.TwoFingerDown, action)
    }

    fun setGestureAction(shortcut: GestureShortcut, action: GestureAction) {
        prefs.edit().putString(gesturePreferenceKey(shortcut), action.storageKey).apply()
        _gestureActions.value = _gestureActions.value + (shortcut to action)
    }

    fun setGestureTarget(shortcut: GestureShortcut, target: GestureTarget) {
        prefs
            .edit()
            .putString(gestureTargetKey(shortcut), encodeGestureTarget(target))
            .apply()
        _gestureTargets.value = _gestureTargets.value + (shortcut to target)
    }

    fun clearGestureTarget(shortcut: GestureShortcut) {
        prefs.edit().remove(gestureTargetKey(shortcut)).apply()
        _gestureTargets.value = _gestureTargets.value - shortcut
    }

    fun setScannerLens(lens: ScannerLens) {
        prefs.edit().putString(KEY_SCANNER_LENS, lens.storageKey).apply()
        _scannerLens.value = lens
    }

    fun setScannerRearLens(lens: ScannerRearLens) {
        prefs.edit().putString(KEY_SCANNER_REAR_LENS, lens.storageKey).apply()
        _scannerRearLens.value = lens
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.storageKey).apply()
        _themeMode.value = mode
    }

    fun setThemeAccent(accent: ThemeAccent) {
        prefs.edit().putString(KEY_THEME_ACCENT, accent.storageKey).apply()
        _themeAccent.value = accent
    }

    fun setObjectTypeAccent(endpointPath: String, accent: ThemeAccent?) {
        val normalizedPath = endpointPath.trim('/')
        val key = objectTypeAccentKey(normalizedPath)
        val updated = _objectTypeAccents.value.toMutableMap()
        if (accent == null || accent == ThemeAccent.System) {
            prefs.edit().remove(key).apply()
            updated.remove(normalizedPath)
        } else {
            prefs.edit().putString(key, accent.storageKey).apply()
            updated[normalizedPath] = accent
        }
        _objectTypeAccents.value = updated
    }

    fun updatePrintSettings(settings: PrintSettings) {
        val normalized = settings.normalized()
        prefs
            .edit()
            .putString(KEY_DEFAULT_PRINTER_NAME, normalized.defaultPrinterName)
            .putString(KEY_DEFAULT_PRINTER_ADDRESS, normalized.defaultPrinterAddress)
            .putBoolean(KEY_PRINT_INVERT_COLORS, normalized.invertColors)
            .putBoolean(KEY_PRINT_VERTICAL_TEXT, normalized.verticalText)
            .putBoolean(KEY_PRINT_LONG_LABEL, normalized.longLabel)
            .putInt(KEY_PRINT_COPIES, normalized.copies)
            .putInt(KEY_PRINT_QR_SIZE, normalized.qrSize)
            .apply()
        _printSettings.value = normalized
    }

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
        _offlineMode.value = enabled
        if (enabled) clearSyncIssue()
    }

    fun recordSyncIssue(error: Throwable) {
        recordSyncIssue(
            error.message?.takeIf { it.isNotBlank() }
                ?: error::class.simpleName?.takeIf { it.isNotBlank() }
                ?: "Sync failed"
        )
    }

    fun recordSyncIssue(message: String) {
        val issueMessage = summarizeSyncIssueMessage(message)
        val issue =
            SyncIssue(issueMessage.take(MAX_SYNC_MESSAGE_LENGTH), System.currentTimeMillis())
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
        prefs
            .edit()
            .putString(KEY_SIDEBAR_APP_ORDER, normalized.joinToString(ORDER_SEPARATOR))
            .apply()
        _sidebarAppOrder.value = normalized
    }

    fun setSidebarModelOrder(appKey: String, order: List<String>) {
        val normalized = order.distinct().filter(String::isNotBlank)
        val updated = _sidebarModelOrders.value.toMutableMap()
        updated[appKey] = normalized
        prefs.edit().putString(KEY_SIDEBAR_MODEL_ORDERS, encodeModelOrders(updated)).apply()
        _sidebarModelOrders.value = updated
    }

    fun setSidebarAppHidden(appKey: String, hidden: Boolean) {
        val updated = updateStringSet(_hiddenSidebarApps.value, appKey, hidden)
        prefs.edit().putStringSet(KEY_HIDDEN_SIDEBAR_APPS, updated).apply()
        _hiddenSidebarApps.value = updated
    }

    fun setDashboardSectionOrder(order: List<String>) {
        val normalized = order.distinct().filter(String::isNotBlank)
        prefs
            .edit()
            .putString(KEY_DASHBOARD_SECTION_ORDER, normalized.joinToString(ORDER_SEPARATOR))
            .apply()
        _dashboardSectionOrder.value = normalized
    }

    fun setDashboardSectionHidden(sectionKey: String, hidden: Boolean) {
        val updated = updateStringSet(_hiddenDashboardSections.value, sectionKey, hidden)
        prefs.edit().putStringSet(KEY_HIDDEN_DASHBOARD_SECTIONS, updated).apply()
        _hiddenDashboardSections.value = updated
    }

    fun setShowTopologyDeviceTypeImages(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TOPOLOGY_DEVICE_TYPE_IMAGES, enabled).apply()
        _showTopologyDeviceTypeImages.value = enabled
    }

    fun setTopologyNodePosition(nodeId: String, position: TopologyPosition) {
        if (nodeId.isBlank() || !position.x.isFinite() || !position.y.isFinite()) return
        val updated = _topologyNodePositions.value + (nodeId to position)
        prefs.edit().putString(KEY_TOPOLOGY_NODE_POSITIONS, encodeTopologyNodePositions(updated)).apply()
        _topologyNodePositions.value = updated
    }

    fun togglePinned(endpointPath: String) {
        val current = _pinnedModelPaths.value
        val updated =
            when {
                endpointPath in current -> current - endpointPath
                current.size >= MAX_PINNED_MODEL_PATHS -> current
                else -> current + endpointPath
            }
        if (updated == current) return
        prefs.edit().putStringSet(KEY_PINNED_MODELS, updated).apply()
        _pinnedModelPaths.value = updated
    }

    private fun loadPinnedModelPaths(): Set<String> =
        (prefs.getStringSet(KEY_PINNED_MODELS, null) ?: setOf(DEFAULT_PINNED_MODEL_PATH))
            .take(MAX_PINNED_MODEL_PATHS)
            .toSet()

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
        _printSettings.value = PrintSettings()
        _themeMode.value = ThemeMode.FollowSystem
        _themeAccent.value = ThemeAccent.System
        _objectTypeAccents.value = emptyMap()
        _gestureActions.value = defaultGestureActions()
        _gestureTargets.value = emptyMap()
        _hiddenFieldKeys.value = emptySet()
        _hiddenSidebarApps.value = emptySet()
        _sidebarAppOrder.value = emptyList()
        _sidebarModelOrders.value = emptyMap()
        _dashboardSectionOrder.value = emptyList()
        _hiddenDashboardSections.value = DEFAULT_HIDDEN_DASHBOARD_SECTIONS
        _showTopologyDeviceTypeImages.value = true
        _topologyNodePositions.value = emptyMap()
        _changeNotificationsEnabled.value = false
        _changeNotificationFilters.value = setOf(ChangeNotificationFilter.All.storageKey)
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
        return if (message != null && occurredAt > 0L) {
            SyncIssue(summarizeSyncIssueMessage(message), occurredAt)
        } else null
    }

    private fun loadLastSuccessfulSyncAt(): Long? =
        prefs.getLong(KEY_LAST_SUCCESSFUL_SYNC, 0L).takeIf { it > 0L }

    private fun loadThemeMode(): ThemeMode =
        ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, ThemeMode.FollowSystem.storageKey))

    private fun loadThemeAccent(): ThemeAccent =
        ThemeAccent.fromStorage(prefs.getString(KEY_THEME_ACCENT, ThemeAccent.System.storageKey))

    private fun loadObjectTypeAccents(): Map<String, ThemeAccent> =
        prefs.all
            .mapNotNull { (key, value) ->
                if (!key.startsWith(KEY_OBJECT_TYPE_ACCENT_PREFIX) || value !is String) {
                    return@mapNotNull null
                }
                val endpointPath = key.removePrefix(KEY_OBJECT_TYPE_ACCENT_PREFIX)
                ThemeAccent.fromStorage(value)
                    .takeIf { it != ThemeAccent.System }
                    ?.let { endpointPath to it }
            }
            .toMap()

    private fun loadGestureActions(): Map<GestureShortcut, GestureAction> =
        GestureShortcut.entries.associateWith { shortcut ->
            val default =
                if (shortcut == GestureShortcut.TwoFingerDown) {
                    GestureAction.GlobalSearch
                } else {
                    GestureAction.Off
                }
            GestureAction.fromStorage(
                prefs.getString(gesturePreferenceKey(shortcut), default.storageKey),
                default,
            )
        }

    private fun defaultGestureActions(): Map<GestureShortcut, GestureAction> =
        GestureShortcut.entries.associateWith { shortcut ->
            if (shortcut == GestureShortcut.TwoFingerDown) GestureAction.GlobalSearch
            else GestureAction.Off
        }

    private fun loadGestureTargets(): Map<GestureShortcut, GestureTarget> =
        GestureShortcut.entries.mapNotNull { shortcut ->
            prefs.getString(gestureTargetKey(shortcut), null)?.let(::decodeGestureTarget)?.let {
                shortcut to it
            }
        }.toMap()

    private fun gesturePreferenceKey(shortcut: GestureShortcut): String =
        if (shortcut == GestureShortcut.TwoFingerDown) {
            KEY_GESTURE_ACTION
        } else {
            "gesture_action_${shortcut.storageKey}"
        }

    private fun gestureTargetKey(shortcut: GestureShortcut): String =
        "gesture_target_${shortcut.storageKey}"

    private fun encodeGestureTarget(target: GestureTarget): String =
        buildString {
            append(target.endpointPath)
            append(TARGET_SEPARATOR)
            append(target.label)
            target.id?.let {
                append(TARGET_SEPARATOR)
                append(it)
            }
        }

    private fun decodeGestureTarget(value: String): GestureTarget? {
        val parts = value.split(TARGET_SEPARATOR, limit = 3)
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return GestureTarget(parts[0], parts[1], parts.getOrNull(2)?.toIntOrNull())
    }

    private fun loadScannerLens(): ScannerLens =
        ScannerLens.fromStorage(prefs.getString(KEY_SCANNER_LENS, ScannerLens.Back.storageKey))

    private fun loadScannerRearLens(): ScannerRearLens =
        ScannerRearLens.fromStorage(
            prefs.getString(KEY_SCANNER_REAR_LENS, ScannerRearLens.Automatic.storageKey)
        )

    private fun loadPrintSettings(): PrintSettings =
        PrintSettings(
                defaultPrinterName = prefs.getString(KEY_DEFAULT_PRINTER_NAME, null),
                defaultPrinterAddress = prefs.getString(KEY_DEFAULT_PRINTER_ADDRESS, null),
                invertColors = prefs.getBoolean(KEY_PRINT_INVERT_COLORS, true),
                verticalText = prefs.getBoolean(KEY_PRINT_VERTICAL_TEXT, false),
                longLabel = prefs.getBoolean(KEY_PRINT_LONG_LABEL, false),
                copies = prefs.getInt(KEY_PRINT_COPIES, 1),
                qrSize = prefs.getInt(KEY_PRINT_QR_SIZE, 64),
            )
            .normalized()

    private fun loadChangeNotificationFilters(): Set<String> {
        val stored = prefs.getStringSet(KEY_CHANGE_NOTIFICATION_FILTERS, null)
        if (stored == null) return setOf(ChangeNotificationFilter.All.storageKey)
        return stored.mapNotNull { ChangeNotificationFilter.fromStorage(it)?.storageKey }.toSet()
    }

    private fun persistChangeNotificationFilters(filters: Set<String>) {
        prefs.edit().putStringSet(KEY_CHANGE_NOTIFICATION_FILTERS, filters).apply()
        _changeNotificationFilters.value = filters
    }

    private fun loadHiddenFieldKeys(): Set<String> =
        prefs
            .getStringSet(KEY_HIDDEN_FIELDS, null)
            .orEmpty()
            .mapNotNull(::normalizeHiddenFieldPreferenceKey)
            .toSet()

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

    private fun loadHiddenSidebarApps(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_SIDEBAR_APPS, null).orEmpty().toSet()

    private fun loadHiddenDashboardSections(): Set<String> =
        prefs
            .getStringSet(KEY_HIDDEN_DASHBOARD_SECTIONS, null)
            ?.toSet()
            ?: DEFAULT_HIDDEN_DASHBOARD_SECTIONS

    private fun loadTopologyNodePositions(): Map<String, TopologyPosition> =
        prefs
            .getString(KEY_TOPOLOGY_NODE_POSITIONS, null)
            .orEmpty()
            .split(ITEM_SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(TARGET_SEPARATOR, limit = 3)
                val x = parts.getOrNull(1)?.toFloatOrNull()
                val y = parts.getOrNull(2)?.toFloatOrNull()
                if (parts.size == 3 && parts[0].isNotBlank() && x?.isFinite() == true && y?.isFinite() == true) {
                    parts[0] to TopologyPosition(x, y)
                } else null
            }
            .toMap()

    private fun updateStringSet(current: Set<String>, key: String, enabled: Boolean): Set<String> =
        if (key.isBlank()) current else if (enabled) current + key else current - key

    private fun encodeModelOrders(orders: Map<String, List<String>>): String =
        orders.entries.joinToString(MODEL_ENTRY_SEPARATOR) { (appKey, modelKeys) ->
            appKey + ORDER_SEPARATOR + modelKeys.joinToString(ITEM_SEPARATOR)
        }

    private fun encodeTopologyNodePositions(positions: Map<String, TopologyPosition>): String =
        positions.entries.joinToString(ITEM_SEPARATOR) { (id, position) ->
            id + TARGET_SEPARATOR + position.x + TARGET_SEPARATOR + position.y
        }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_PINNED_MODELS = "pinned_model_paths"
        const val DEFAULT_PINNED_MODEL_PATH = NetBoxRef.DEVICES_ENDPOINT_PATH
        const val MAX_PINNED_MODEL_PATHS = 5
        const val KEY_SYNC_ATTACHMENTS = "sync_attachments_to_disk"
        const val KEY_SYNC_ONLY_ON_WIFI = "sync_only_on_wifi"
        const val KEY_SYNC_WHILE_ROAMING = "sync_while_roaming"
        const val KEY_SYNC_ON_APP_LAUNCH = "sync_on_app_launch"
        const val KEY_CHANGE_NOTIFICATIONS_ENABLED = "change_notifications_enabled"
        const val KEY_CHANGE_NOTIFICATION_FILTERS = "change_notification_filters"
        const val KEY_CHANGE_NOTIFICATION_CURSOR = "change_notification_cursor"
        const val KEY_GESTURE_ACTION = "two_finger_swipe_action"
        const val TARGET_SEPARATOR = "\u001F"
        const val KEY_SCANNER_LENS = "scanner_default_lens"
        const val KEY_SCANNER_REAR_LENS = "scanner_default_rear_lens"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_ACCENT = "theme_accent"
        const val KEY_OBJECT_TYPE_ACCENT_PREFIX = "object_type_accent:"
        const val KEY_DEFAULT_PRINTER_NAME = "default_printer_name"
        const val KEY_DEFAULT_PRINTER_ADDRESS = "default_printer_address"
        const val KEY_PRINT_INVERT_COLORS = "print_invert_colors"
        const val KEY_PRINT_VERTICAL_TEXT = "print_vertical_text"
        const val KEY_PRINT_LONG_LABEL = "print_long_label"
        const val KEY_PRINT_COPIES = "print_copies"
        const val KEY_PRINT_QR_SIZE = "print_qr_size"
        const val KEY_OFFLINE_MODE = "offline_mode"
        const val KEY_SYNC_ISSUE_MESSAGE = "sync_issue_message"
        const val KEY_SYNC_ISSUE_TIME = "sync_issue_time"
        const val KEY_LAST_SUCCESSFUL_SYNC = "last_successful_sync"
        const val MAX_SYNC_MESSAGE_LENGTH = 1000
        const val KEY_HIDDEN_FIELDS = "hidden_field_keys"
        const val KEY_SIDEBAR_APP_ORDER = "sidebar_app_order"
        const val KEY_SIDEBAR_MODEL_ORDERS = "sidebar_model_orders"
        const val KEY_HIDDEN_SIDEBAR_APPS = "hidden_sidebar_apps"
        const val KEY_DASHBOARD_SECTION_ORDER = "dashboard_section_order"
        const val KEY_HIDDEN_DASHBOARD_SECTIONS = "hidden_dashboard_sections"
        const val KEY_SHOW_TOPOLOGY_DEVICE_TYPE_IMAGES = "show_topology_device_type_images"
        const val KEY_TOPOLOGY_NODE_POSITIONS = "topology_node_positions"
        val DEFAULT_HIDDEN_DASHBOARD_SECTIONS = setOf("news")
        const val ORDER_SEPARATOR = "\u001F"
        const val ITEM_SEPARATOR = "\u001E"
        const val MODEL_ENTRY_SEPARATOR = "\u001D"
    }

    private fun objectTypeAccentKey(endpointPath: String): String =
        KEY_OBJECT_TYPE_ACCENT_PREFIX + endpointPath
}
