package com.example.beeing

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================
// STREAK PHONE ALERTS  (gift, saver used, saver faded — once per event)
// ============================================================
//
// Day verdicts land at midnight, but yesterday's last two hours stay ratable until
// 2am (and a watch may sync them later still), so a daily alarm at 02:01 replays the
// engine, compares with the ids it already notified and posts each new event exactly
// once. An alert a later rating disproved is withdrawn (withdrawStaleAlerts).
// Hourly-reminder settings (pause) never apply here; the only
// setting that does is silent hours, which post the alert on the silent channel.

private val ALERT_TYPES = setOf(StreakEventType.COMEBACK_GIFT, StreakEventType.SAVER_USED, StreakEventType.SAVER_FADED)
private const val GIFT_FADE_MS = 2 * 24 * 3_600_000L + 2 * 3_600_000L // day 8 and 9, with DST slack

/** Events worth a phone alert: after the baseline, not yet notified, oldest first. */
fun alertsToPost(events: List<StreakEvent>, notifiedIds: Set<String>, baselineMs: Long): List<StreakEvent> =
    events.filter { it.type in ALERT_TYPES && it.timestamp >= baselineMs && it.id !in notifiedIds }
        .sortedBy { it.timestamp }

/** Alerts already shown whose event the replay no longer has (a late hour completed the day). */
fun alertsToWithdraw(events: List<StreakEvent>, notifiedIds: Set<String>): Set<String> {
    val alertPrefixes = ALERT_TYPES.map { "${it.name}:" }
    val live = events.mapTo(HashSet()) { it.id }
    return notifiedIds.filterTo(HashSet()) { id -> alertPrefixes.any { id.startsWith(it) } && id !in live }
}

private fun alertNotificationId(eventId: String) = 1000 + (eventId.hashCode() and 0xFFFF)

/**
 * Cancels shown alerts that stopped being true and forgets them. Runs after every
 * rating save and delete, and before the 02:01 post. It never posts: posting before
 * 2am would announce a day that can still change.
 */
fun withdrawStaleAlerts(context: Context, events: List<StreakEvent> = computeStreakEvents(
    loadRatings(context), loadReclaimSpends(context), loadModeEvents(context), System.currentTimeMillis()
)) {
    val notified = loadNotifiedIds(context)
    val stale = alertsToWithdraw(events, notified)
    if (stale.isEmpty()) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    stale.forEach { manager.cancel(alertNotificationId(it)) }
    context.getSharedPreferences("b", 0).edit().putString(NOTIFIED_KEY, (notified - stale).joinToString(";")).apply()
}

/** A fade within two days after a comeback gift is one of the gifted savers going. */
fun isGiftedFade(events: List<StreakEvent>, e: StreakEvent): Boolean =
    e.type == StreakEventType.SAVER_FADED &&
        events.any { it.type == StreakEventType.COMEBACK_GIFT && it.timestamp < e.timestamp && e.timestamp - it.timestamp <= GIFT_FADE_MS }

/** (title, body) for an alert. */
fun alertCopy(e: StreakEvent, giftedFade: Boolean): Pair<String, String> = when (e.type) {
    StreakEventType.COMEBACK_GIFT -> e.warningTitle() to e.warningDetail("")
    StreakEventType.SAVER_USED -> "A streak saver kept your ${e.streak}-day streak alive" to "${e.savers} left."
    else -> e.warningTitle() to when {
        giftedFade && e.savers == 0 -> "Your last saver faded."
        giftedFade -> "One of your gifted savers faded. Rate today to keep the last one."
        else -> e.warningDetail(SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(e.dayStartMs)))
    }
}

private const val NOTIFIED_KEY = "alert_notified_ids"
private const val BASELINE_KEY = "alert_baseline_ms"

private fun loadNotifiedIds(ctx: Context): Set<String> =
    (ctx.getSharedPreferences("b", 0).getString(NOTIFIED_KEY, "") ?: "").split(";").filter { it.isNotBlank() }.toSet()

private fun alertBaseline(ctx: Context): Long {
    val prefs = ctx.getSharedPreferences("b", 0)
    if (!prefs.contains(BASELINE_KEY)) prefs.edit().putLong(BASELINE_KEY, System.currentTimeMillis()).apply()
    return prefs.getLong(BASELINE_KEY, 0L)
}

/** After an import the whole history re-derives; nothing in it may ring the phone. */
fun resetAlertBaseline(ctx: Context) {
    ctx.getSharedPreferences("b", 0).edit().putLong(BASELINE_KEY, System.currentTimeMillis()).apply()
}

/** Daily exact alarm at 02:01, once yesterday's last ratable hour has closed. */
fun scheduleStreakAlerts(context: Context) {
    // Pin the baseline to the first launch: events before it never alert, and the
    // first replay (02:01) must not treat the night before as "old".
    alertBaseline(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return
    val pending = PendingIntent.getBroadcast(
        context, 2, Intent(context, StreakAlertReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 2); set(Calendar.MINUTE, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (!after(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
    }
    alarmManager.cancel(pending)
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
}

class StreakAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            postPendingAlerts(context)
        } finally {
            scheduleStreakAlerts(context)
        }
    }

    private fun postPendingAlerts(context: Context) {
        val now = System.currentTimeMillis()
        val events = computeStreakEvents(
            loadRatings(context), loadReclaimSpends(context), loadModeEvents(context), now
        )
        withdrawStaleAlerts(context, events)
        val notified = loadNotifiedIds(context)
        val toPost = alertsToPost(events, notified, alertBaseline(context))
        if (toPost.isEmpty()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Silent hours are the only setting that reaches an alert; pause is about
        // hourly reminders alone.
        val channel = if (otherDelivery(loadNotificationPrefs(context), now) == Delivery.SILENT)
            CHANNEL_STREAK_ALERTS_SILENT else CHANNEL_STREAK_ALERTS
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("OPEN_TAB", 0)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        for (e in toPost) {
            val (title, body) = alertCopy(e, isGiftedFade(events, e))
            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            manager.notify(alertNotificationId(e.id), n)
        }
        // Remember them only after posting, and keep the set bounded.
        val merged = (notified + toPost.map { it.id }).toList().takeLast(200)
        context.getSharedPreferences("b", 0).edit().putString(NOTIFIED_KEY, merged.joinToString(";")).apply()
    }
}
