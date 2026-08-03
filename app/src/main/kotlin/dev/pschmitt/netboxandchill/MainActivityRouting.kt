package dev.pschmitt.netboxandchill

import android.content.Intent
import dev.pschmitt.netboxandchill.data.repository.GestureAction
import dev.pschmitt.netboxandchill.data.repository.GestureTarget
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.scanner.NetBoxUrlParser
import dev.pschmitt.netboxandchill.ui.navigation.Route

/** Extracts a NetBox deep-link/setup payload from the intent shapes Android can deliver. */
internal fun extractNetBoxTarget(intent: Intent?): NetBoxTarget? {
    return extractNetBoxTargetText(
        action = intent?.action,
        dataString = intent?.dataString,
        sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT),
    )
}

internal fun extractNetBoxTargetText(
    action: String?,
    dataString: String?,
    sharedText: String?,
): NetBoxTarget? {
    val text =
        when (action) {
            Intent.ACTION_VIEW -> dataString
            Intent.ACTION_SEND -> sharedText
            else -> null
        }
    return text?.let(NetBoxUrlParser::parse)
}

/** Maps a parsed target to a route when the target needs no repository lookup. */
internal fun routeForTarget(target: NetBoxTarget): Route? =
    when (target) {
        is NetBoxTarget.Setup -> Route.Onboarding
        is NetBoxTarget.Device -> Route.DeviceDetail(target.id)
        is NetBoxTarget.Object -> Route.Generic(target.endpointPath, target.id)
        is NetBoxTarget.DeviceAssetTag -> null
    }

/** Pure route selection for configured gesture actions; side effects remain in MainActivity. */
internal fun routeForGesture(action: GestureAction, target: GestureTarget?): Route? =
    when (action) {
        GestureAction.GlobalSearch -> Route.GlobalSearch
        GestureAction.Scanner -> Route.Scanner()
        GestureAction.Settings -> Route.Settings
        GestureAction.Add -> Route.Add
        GestureAction.AddSpecific ->
            target?.let { Route.GenericCreate(it.endpointPath, it.label) } ?: Route.Add
        GestureAction.DeviceList -> Route.DeviceList
        GestureAction.ListSpecific ->
            target?.let { Route.GenericList(it.endpointPath, it.label) }
        GestureAction.DetailSpecific ->
            target?.id?.let { id ->
                if (target.endpointPath == NetBoxRef.DEVICES_ENDPOINT_PATH && id > 0) {
                    Route.DeviceDetail(id)
                } else {
                    Route.Generic(target.endpointPath, id, target.label)
                }
            }
        GestureAction.Off,
        GestureAction.Sync,
        GestureAction.OfflineOn,
        GestureAction.OfflineOff -> null
    }
