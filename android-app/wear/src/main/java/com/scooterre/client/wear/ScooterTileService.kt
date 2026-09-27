package com.scooterre.client.wear

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.DeviceParametersBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.material.Text
import androidx.wear.tiles.material.Typography
import androidx.wear.tiles.material.layouts.PrimaryLayout
import androidx.wear.protolayout.ResourceBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"

/**
 * A Tile ("Widget" - swipe left/right from the watch face, like the Fitness/Google Fit tile) that
 * shows the phone's last relayed scooter status. Deliberately reads ONLY [WatchState.status] - the
 * passive relay the phone pushes while IT is connected - never [WearConnection] (the watch's own
 * direct connection): a Tile must answer instantly and cheaply whenever the system asks, and, more
 * importantly, the watch must never connect to a scooter on its own initiative (see
 * WearConnection's doc comment for why) - a Tile querying the direct-connection state would risk
 * that same box being ticked by something other than an explicit user tap. No live BLE anything
 * happens here, ever.
 *
 * No periodic refresh of its own: StatusListenerService pushes a fresh Tile (via
 * `TileService.getUpdater(context).requestUpdate(...)`) every time a new relay status arrives, so
 * this only ever needs to answer with whatever is already sitting in [WatchState.status].
 */
class ScooterTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val status = WatchState.status.value
        val statusText = when {
            status == null -> "Keine Daten vom Handy"
            status.standby -> "Ruhezustand"
            else -> {
                val line = listOfNotNull(
                    status.battery.takeIf { it.isNotEmpty() }?.let { "Akku $it" },
                    status.rest.takeIf { it.isNotEmpty() }?.let { "Rest $it" },
                ).joinToString(" · ")
                line.ifEmpty { "Handy nicht mit Scooter verbunden" }
            }
        }

        val openApp = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.scooterre.client.wear.MainActivity")
                    .build(),
            )
            .build()
        val clickable = ModifiersBuilders.Clickable.Builder().setId("open_app").setOnClick(openApp).build()

        // The platform normally always supplies this, but its own type is nullable - a fallback
        // avoids a crash on the rare device/OS combination that omits it, at the cost of using a
        // guessed screen size for that one request only (no different from the request ever asking
        // for it wrong in the first place).
        val deviceParams = requestParams.deviceParameters
            ?: DeviceParametersBuilders.DeviceParameters.Builder().setScreenWidthDp(192).setScreenHeightDp(192).build()
        val layout = PrimaryLayout.Builder(deviceParams)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "Scooter Link").setTypography(Typography.TYPOGRAPHY_CAPTION1).build(),
            )
            .setContent(
                Text.Builder(this, statusText)
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setMaxLines(2)
                    .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
                    .build(),
            )
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout).build())
                    .build(),
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(timeline)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
}
