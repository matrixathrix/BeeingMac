package com.example.beeing.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.beeing.Origin
import com.example.beeing.PATH_CHARGING
import com.example.beeing.PATH_RATING_PREFIX
import com.example.beeing.PATH_STATE
import com.example.beeing.RatingEntry
import com.example.beeing.decodeState
import com.example.beeing.encodeRating
import com.example.beeing.hourLabelFor
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

// The watch side of the sync (see :shared Wire.kt). Every save goes into the Data
// Layer at once; the Data Layer holds it until the phone is reachable.

/** Saves a rating made on the watch: locally first, then queued for the phone. */
fun saveFromWatch(ctx: Context, hourStartMs: Long, score: Int, tags: List<String>, nowMs: Long): RatingEntry {
    val e = RatingEntry(
        id = nowMs, score = score, timestamp = hourStartMs, hourLabel = hourLabelFor(hourStartMs),
        tags = tags, modifiedAt = nowMs, origin = Origin.WATCH
    )
    WatchStore.saveLocal(ctx, e)
    try {
        Wearable.getDataClient(ctx).putDataItem(
            PutDataRequest.create(PATH_RATING_PREFIX + hourStartMs).setData(encodeRating(e)).setUrgent()
        )
    } catch (ex: Exception) {
        Log.w("WatchSync", "queue skipped", ex)
    }
    WatchSurfaces.refresh(ctx)
    return e
}

/** The phone's state arrives: store it, forget the saves it has processed, redraw. */
class WatchSync : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != PATH_STATE) continue
            val state = decodeState(event.dataItem.data ?: continue) ?: continue
            WatchStore.saveState(this, state)
            val gone = WatchStore.pruneProcessed(this, state.mergedWatchIds, System.currentTimeMillis())
            val data = Wearable.getDataClient(this)
            gone.forEach { e ->
                // No host = every node's copy of that path.
                data.deleteDataItems(Uri.Builder().scheme("wear").path(PATH_RATING_PREFIX + e.timestamp).build())
            }
            WatchSurfaces.refresh(this)
        }
    }
}

/** Tells the phone when the watch goes on or off its charger (an event, no sensor). */
class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val charging = intent.action == Intent.ACTION_POWER_CONNECTED
        try {
            Wearable.getDataClient(context).putDataItem(
                PutDataRequest.create(PATH_CHARGING).setData(byteArrayOf(if (charging) 1 else 0)).setUrgent()
            )
        } catch (ex: Exception) {
            Log.w("WatchSync", "charging flag skipped", ex)
        }
    }
}
