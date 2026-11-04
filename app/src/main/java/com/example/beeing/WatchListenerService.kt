package com.example.beeing

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Takes in what the watch sends: each rating it saved (merged by [applyIncoming]) and
 * whether it is on duty for reminders. Woken by the Data Layer only when an item changes.
 */
class WatchListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        var merged = false
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            val path = item.uri.path ?: continue
            val data = item.data ?: continue
            when {
                path.startsWith(PATH_RATING_PREFIX) -> {
                    val incoming = decodeRating(data) ?: continue
                    synchronized(STORE_LOCK) {
                        val applied = applyIncoming(
                            incoming, loadRatings(this), loadTombstones(this), loadReclaimSpends(this)
                        )
                        if (applied.accepted) {
                            saveAllRatings(this, applied.ratings)
                            saveReclaimSpends(this, applied.spends)
                        }
                        markWatchIdProcessed(this, incoming.id)
                    }
                    merged = true
                }
                path == PATH_DUTY -> setWatchOnDuty(this, data.firstOrNull() == 1.toByte())
            }
        }
        if (merged) {
            withdrawStaleAlerts(this)
            publishState(this)
        }
    }
}
