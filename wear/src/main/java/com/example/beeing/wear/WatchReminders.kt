package com.example.beeing.wear

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.beeing.Delivery
import com.example.beeing.hourRangeLabel
import com.example.beeing.hourlyDelivery
import java.util.Calendar

// The watch's own hourly reminder, so it works with the phone in a drawer. It obeys
// the silent hours and pause the phone last sent, and only posts when an hour waits.
// One notification id: a new hour replaces the old one, so one buzz per hour at most.

private const val CHANNEL_SOUND = "watch_hourly_v1"
private const val CHANNEL_SILENT = "watch_hourly_silent_v1"
private const val NOTIFICATION_ID = 1
private const val ACTION_HOUR = "com.example.beeing.wear.HOUR"
private const val ACTION_LATER = "com.example.beeing.wear.LATER"

/** A short double tap: Beeing's own feel, apart from Galaxy's hourly chime (an ordinary buzz). */
private val VIBRATION = longArrayOf(0, 70, 90, 70)

fun createWatchChannels(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val tone = Uri.parse("android.resource://${ctx.packageName}/${R.raw.beepingandroidbuzz}")
    val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_SOUND, "Hourly reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(tone, attrs); enableVibration(true); vibrationPattern = VIBRATION
        }
    )
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_SILENT, "Hourly reminders (silent)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null); enableVibration(false)
        }
    )
}

/** Arms the alarm for the next local hour start (exact when allowed, else as close as the system lets it). */
fun scheduleWatchHour(ctx: Context) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    val pi = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, HourReceiver::class.java).setAction(ACTION_HOUR),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val next = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    am.cancel(pi)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    } else {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }
}

/** Posts, replaces or clears the reminder to match what waits right now. */
fun updateReminder(ctx: Context, nowMs: Long = System.currentTimeMillis()) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val state = WatchStore.state(ctx)
    val waiting = WatchSurfaces.view(ctx, nowMs).waiting
    val delivery = state?.let { hourlyDelivery(it.prefs, nowMs) } ?: Delivery.NONE
    if (waiting.isEmpty() || delivery == Delivery.NONE) { nm.cancel(NOTIFICATION_ID); return }

    val rate = PendingIntent.getActivity(
        ctx, 1, RatingActivity.intent(ctx, waiting.first()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val later = PendingIntent.getBroadcast(
        ctx, 2, Intent(ctx, HourReceiver::class.java).setAction(ACTION_LATER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val title = if (waiting.size > 1) "${waiting.size} hours waiting" else "How was ${hourRangeLabel(waiting.first())}?"
    val n = NotificationCompat.Builder(ctx, if (delivery == Delivery.SOUND) CHANNEL_SOUND else CHANNEL_SILENT)
        .setSmallIcon(R.drawable.ic_sym_hourglass)
        .setContentTitle(title)
        .setContentIntent(rate)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .addAction(0, "Rate", rate)
        .addAction(0, "Later", later)
        .build()
    nm.notify(NOTIFICATION_ID, n)
}

/** After a save or a sync: take the reminder down once nothing waits (never re-posts, so never re-buzzes). */
fun clearReminderIfDone(ctx: Context) {
    if (WatchSurfaces.view(ctx).waiting.isEmpty()) ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
}

/** The hour turned, the watch restarted, or "Later" was tapped. */
class HourReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LATER) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            return
        }
        createWatchChannels(context)
        scheduleWatchHour(context)
        updateReminder(context)
        WatchSurfaces.refresh(context)
    }
}
