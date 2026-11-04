package com.example.beeing.wear

import android.content.Context
import androidx.wear.tiles.TileService
import com.example.beeing.WatchView
import com.example.beeing.watchView

/**
 * The one redraw hook for every glanceable surface (the Tile and the complications).
 * Called on exactly four events — a save here, a phone state arriving, the hourly
 * alarm, boot — and never on a timer.
 */
object WatchSurfaces {
    fun refresh(ctx: Context) {
        TileService.getUpdater(ctx).requestUpdate(BeeingTileService::class.java)
    }

    fun view(ctx: Context, nowMs: Long = System.currentTimeMillis()): WatchView =
        watchView(WatchStore.state(ctx), WatchStore.local(ctx), nowMs)
}
