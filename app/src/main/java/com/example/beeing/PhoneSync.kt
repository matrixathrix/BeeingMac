package com.example.beeing

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// ============================================================
// PHONE SIDE OF THE WATCH SYNC
// ============================================================
//
// publishState() sends the watch everything it needs as one PhoneState item
// (Wire.kt). It runs after every change that matters — a rating saved or deleted,
// tags, mode, silent hours / pause, an incoming watch rating — and from the hourly
// alarm. Requests coalesce on one background thread, so an import of a thousand rows
// publishes a handful of times, not a thousand. Without Play services or a watch it
// quietly does nothing.

private const val PROCESSED_KEY = "watch_processed_ids"
private const val INSTALLED_KEY = "watch_installed"
private const val CHARGING_KEY = "watch_charging"
private const val PROCESSED_MAX = 200

private val publisher = Executors.newSingleThreadExecutor()
private val publishPending = AtomicBoolean(false)

fun publishState(context: Context) {
    val app = context.applicationContext
    if (!publishPending.compareAndSet(false, true)) return
    publisher.execute {
        publishPending.set(false)
        try {
            val bytes = encodeState(buildPhoneState(app, System.currentTimeMillis()))
            Wearable.getDataClient(app).putDataItem(PutDataRequest.create(PATH_STATE).setData(bytes).setUrgent())
        } catch (e: Exception) {
            Log.w("PhoneSync", "publish skipped", e)
        }
    }
}

private fun midnightDaysAgo(nowMs: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = nowMs
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -days)
}.timeInMillis

/** The checkpoint sits at the end of the day before yesterday, which is always settled. */
fun buildPhoneState(context: Context, nowMs: Long): PhoneState {
    val ratings = loadRatings(context)
    val modes = loadModeEvents(context)
    val tags = loadTags(context)
    val yesterday = midnightDaysAgo(nowMs, 1)
    return PhoneState(
        syncedAtMs = nowMs,
        tags = tags,
        topTags = topTags(ratings, tags, nowMs),
        modeEvents = modes,
        prefs = loadNotificationPrefs(context),
        carry = carryAt(ratings, modes, midnightDaysAgo(nowMs, 2)),
        recentRatings = ratings.filter { it.timestamp >= yesterday },
        mergedWatchIds = loadProcessedWatchIds(context)
    )
}

/** Watch ratings the phone has taken in or turned down; the watch stops resending them. */
fun loadProcessedWatchIds(context: Context): List<Long> =
    (context.getSharedPreferences("b", 0).getString(PROCESSED_KEY, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }

fun markWatchIdProcessed(context: Context, id: Long) {
    val next = (loadProcessedWatchIds(context) - id + id).takeLast(PROCESSED_MAX)
    context.getSharedPreferences("b", 0).edit().putString(PROCESSED_KEY, next.joinToString(",")).apply()
}

/** Whether Beeing is installed on a paired watch; refreshed on app start by [refreshWatchInstalled]. */
fun watchInstalled(context: Context): Boolean = context.getSharedPreferences("b", 0).getBoolean(INSTALLED_KEY, false)

fun refreshWatchInstalled(context: Context) {
    val app = context.applicationContext
    try {
        Wearable.getCapabilityClient(app).getCapability(CAPABILITY_WATCH, CapabilityClient.FILTER_ALL)
            .addOnSuccessListener { info ->
                app.getSharedPreferences("b", 0).edit().putBoolean(INSTALLED_KEY, info.nodes.isNotEmpty()).apply()
            }
    } catch (e: Exception) {
        Log.w("PhoneSync", "capability check skipped", e)
    }
}

/** Blocking (call off the main thread): is the watch app connected right now? Errors count as no. */
fun watchReachable(context: Context): Boolean = try {
    Tasks.await(
        Wearable.getCapabilityClient(context).getCapability(CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE),
        3, TimeUnit.SECONDS
    ).nodes.isNotEmpty()
} catch (e: Exception) {
    false
}

fun watchCharging(context: Context): Boolean = context.getSharedPreferences("b", 0).getBoolean(CHARGING_KEY, false)

fun setWatchCharging(context: Context, charging: Boolean) =
    context.getSharedPreferences("b", 0).edit().putBoolean(CHARGING_KEY, charging).apply()
