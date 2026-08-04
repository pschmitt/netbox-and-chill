package dev.pschmitt.nyetbox.data.repository

import dev.pschmitt.nyetbox.sync.SyncNotifier
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** Turns the cached changelog refresh into optional, background-only user notifications. */
@Singleton
class ChangeNotificationRepository
@Inject
constructor(
    private val settingsRepository: SettingsRepository,
    private val syncNotifier: SyncNotifier,
) {
    fun process(rawChanges: List<JsonObject>) {
        val events = rawChanges.mapNotNull { it.toChangeNotificationEvent() }
        val newestId = events.maxOfOrNull { it.id }
        val cursor = settingsRepository.changeNotificationCursor
        // The first refresh is a baseline. Users enabling the option later should only receive
        // notifications for changes that happen after the app has observed the current server.
        val freshEvents = if (cursor == 0) emptyList() else events.filter { it.id > cursor }
        newestId?.let(settingsRepository::recordChangeNotificationCursor)
        if (!settingsRepository.changeNotificationsEnabled.value || freshEvents.isEmpty()) return

        val filters =
            settingsRepository.changeNotificationFilters.value
                .mapNotNull {
                    ChangeNotificationFilter.fromStorage(it)
                }
                .toSet()
        val matching = matchingChangeNotificationEvents(freshEvents, filters)
        runCatching { syncNotifier.notifyNetBoxChanges(matching) }
            .onFailure { Timber.w(it, "Couldn't post NetBox change notification") }
    }
}
