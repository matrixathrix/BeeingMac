package com.example.beeing.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.beeing.WatchView
import com.example.beeing.hourRangeLabel
import com.google.common.util.concurrent.ListenableFuture

// The Tile: today's ring, streak and savers, and what waits. It never refreshes on a
// timer (freshness 0); WatchSurfaces.refresh asks for a redraw after a save, a sync,
// the hourly alarm or a restart.

private const val RES_VERSION = "1"
private const val HONEY = 0xFFF5A623.toInt()
private const val INK = 0xFFF3ECF6.toInt()
private const val INK_MUTED = 0xFFB9A9CC.toInt()
private const val GLASS = 0x33F3ECF6
private const val RED = 0xFFFF8A80.toInt()
private const val ON_HONEY = 0xFF2E0F33.toInt()

class BeeingTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { c ->
            val layout = tileLayout(WatchSurfaces.view(this), packageName)
            c.set(
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(RES_VERSION)
                    .setFreshnessIntervalMillis(0)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                    .build()
            )
            "beeing-tile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { c ->
            c.set(
                ResourceBuilders.Resources.Builder().setVersion(RES_VERSION)
                    .addIdToImageMapping("fire", image(R.drawable.ic_sym_fire))
                    .addIdToImageMapping("leaf", image(R.drawable.ic_sym_beginner))
                    .addIdToImageMapping("saver", image(R.drawable.ic_sym_saver))
                    .addIdToImageMapping("sync", image(R.drawable.ic_sym_reset))
                    .build()
            )
            "beeing-tile-res"
        }
}

private fun image(resId: Int) = ResourceBuilders.ImageResource.Builder()
    .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(resId).build())
    .build()

private fun text(s: String, size: Float, color: Int, bold: Boolean = false): LayoutElement =
    LayoutElementBuilders.Text.Builder().setText(s).setMaxLines(2)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                .build()
        ).build()

private fun icon(id: String, color: Int): LayoutElement =
    LayoutElementBuilders.Image.Builder().setResourceId(id).setWidth(dp(14f)).setHeight(dp(14f))
        .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(color)).build())
        .build()

private fun gap(w: Float): LayoutElement = LayoutElementBuilders.Spacer.Builder().setWidth(dp(w)).setHeight(dp(w)).build()

/** Opens the app: the launcher goes straight to rating when an hour waits. */
private fun openApp(pkg: String) = ModifiersBuilders.Clickable.Builder().setId("open")
    .setOnClick(
        ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder().setPackageName(pkg).setClassName("com.example.beeing.wear.MainActivity").build()
        ).build()
    ).build()

private fun ring(fraction: Float): LayoutElement = LayoutElementBuilders.Arc.Builder()
    .setAnchorAngle(degrees(0f))
    .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
    .addContent(
        LayoutElementBuilders.ArcLine.Builder().setLength(degrees(360f * fraction.coerceIn(0f, 1f)))
            .setThickness(dp(6f)).setColor(argb(HONEY)).build()
    ).build()

private fun track(): LayoutElement = LayoutElementBuilders.Arc.Builder()
    .addContent(LayoutElementBuilders.ArcLine.Builder().setLength(degrees(360f)).setThickness(dp(6f)).setColor(argb(GLASS)).build())
    .build()

internal fun tileLayout(v: WatchView, pkg: String): LayoutElement {
    val column = LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
    if (!v.setUp) {
        column.addContent(text("Open Beeing on your phone to set up", 13f, INK))
    } else {
        column.addContent(text("${v.todayHours}/${v.goal}", 26f, INK, bold = true))
        val streakInk = if (v.atRisk) RED else INK
        val streakText = when { v.paused -> " Paused ${v.streak}"; v.atRisk -> " ${v.streak}!"; else -> " ${v.streak}" }
        val line = LayoutElementBuilders.Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(icon(if (v.paused || v.beginner) "leaf" else "fire", if (v.atRisk) RED else HONEY))
            .addContent(text(streakText, 13f, streakInk, bold = true))
            .addContent(gap(8f))
            .addContent(icon("saver", HONEY))
            .addContent(text(" ${v.savers}/5", 13f, INK, bold = true))
        if (!v.confirmed) line.addContent(gap(4f)).addContent(icon("sync", INK_MUTED))
        column.addContent(line.build()).addContent(gap(6f))
        if (v.waiting.isNotEmpty()) {
            column.addContent(text("${hourRangeLabel(v.waiting.first())} is waiting", 12f, INK_MUTED))
                .addContent(gap(4f))
                .addContent(
                    LayoutElementBuilders.Box.Builder().setWidth(dp(84f)).setHeight(dp(30f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder().setColor(argb(HONEY))
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(15f)).build()).build()
                                ).setClickable(openApp(pkg)).build()
                        )
                        .addContent(text("Rate", 13f, ON_HONEY, bold = true)).build()
                )
        } else {
            column.addContent(text("All caught up", 12f, INK_MUTED))
        }
    }
    return LayoutElementBuilders.Box.Builder().setWidth(expand()).setHeight(expand())
        .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp(pkg)).build())
        .addContent(track())
        .addContent(ring(if (v.setUp) v.todayHours / v.goal.toFloat() else 0f))
        .addContent(column.build())
        .build()
}
