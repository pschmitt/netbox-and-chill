package dev.pschmitt.netboxandchill.data.repository

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ChangeNotificationEvent(
    val id: Int,
    val actionValue: String,
    val actionLabel: String,
    val objectType: String,
    val objectRepr: String,
)

/** Notification filters exposed in Settings. The raw object type is NetBox's app.model key. */
enum class ChangeNotificationFilter(
    val storageKey: String,
    val label: String,
    val actionValue: String? = null,
    val objectType: String? = null,
) {
    All("all", "All changes"),
    Created("create", "New items", actionValue = "create"),
    Updated("update", "Updated items", actionValue = "update"),
    Deleted("delete", "Deleted items", actionValue = "delete"),
    DeviceCreated("create:dcim.device", "New devices", "create", "dcim.device"),
    DeviceDeleted("delete:dcim.device", "Deleted devices", "delete", "dcim.device"),
    CableCreated("create:dcim.cable", "New cables", "create", "dcim.cable"),
    CableDeleted("delete:dcim.cable", "Deleted cables", "delete", "dcim.cable");

    fun matches(event: ChangeNotificationEvent): Boolean =
        (actionValue == null || actionValue == event.actionValue) &&
            (objectType == null || objectType == event.objectType)

    companion object {
        fun fromStorage(value: String): ChangeNotificationFilter? =
            entries.firstOrNull { it.storageKey == value }
    }
}

fun JsonObject.toChangeNotificationEvent(): ChangeNotificationEvent? {
    val id = this["id"]?.jsonPrimitive?.intOrNull ?: return null
    val action = this["action"] as? JsonObject
    val actionValue = action?.get("value")?.jsonPrimitive?.contentOrNull ?: return null
    val actionLabel = action["label"]?.jsonPrimitive?.contentOrNull ?: actionValue
    val objectType = this["changed_object_type"]?.jsonPrimitive?.contentOrNull ?: return null
    val objectRepr = this["object_repr"]?.jsonPrimitive?.contentOrNull ?: "#$id"
    return ChangeNotificationEvent(id, actionValue, actionLabel, objectType, objectRepr)
}

fun matchingChangeNotificationEvents(
    events: List<ChangeNotificationEvent>,
    filters: Set<ChangeNotificationFilter>,
): List<ChangeNotificationEvent> = events.filter { event -> filters.any { it.matches(event) } }
