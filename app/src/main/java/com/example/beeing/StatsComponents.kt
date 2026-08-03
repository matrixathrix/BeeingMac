package com.example.beeing

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.*

// ============================================================
// WEEKLY REPORT  (feature 4) — reuses the manifest-registered
// NotificationReceiver via ACTION_WEEKLY_REPORT.
// ============================================================

const val ACTION_WEEKLY_REPORT = "ACTION_WEEKLY_REPORT"
private const val WEEKLY_REPORT_REQUEST = 200
private const val WEEKLY_REPORT_NOTIF_ID = 2

fun buildWeeklySummaryText(context: Context): String {
    val ratings = loadRatings(context)
    val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
    val week = ratings.filter { it.timestamp >= weekAgo }
    if (week.isEmpty()) return "No ratings logged this week. A fresh start awaits! 🐝"

    val avg = week.map { it.score }.average()
    val topTag = week.flatMap { it.tags }
        .groupingBy { it }.eachCount()
        .maxByOrNull { it.value }?.key
    val streak = computeStreakState(ratings, loadReclaimSpends(context))

    val parts = mutableListOf<String>()
    parts.add("Avg score ${String.format("%.1f", avg)} over ${week.size} hours")
    if (topTag != null) parts.add("most logged: $topTag")
    parts.add("streak ⬢${streak.currentStreak}")
    return parts.joinToString(" · ")
}

fun showWeeklyReportNotification(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pending = PendingIntent.getActivity(
        context, 0, openIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = NotificationCompat.Builder(context, "weekly_report")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("🐝 Your week in review")
        .setContentText(buildWeeklySummaryText(context))
        .setStyle(NotificationCompat.BigTextStyle().bigText(buildWeeklySummaryText(context)))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    manager.notify(WEEKLY_REPORT_NOTIF_ID, notification)
}

fun scheduleWeeklyReport(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
        !alarmManager.canScheduleExactAlarms()
    ) return

    val intent = Intent(context, NotificationReceiver::class.java).apply { action = ACTION_WEEKLY_REPORT }
    val pendingIntent = PendingIntent.getBroadcast(
        context, WEEKLY_REPORT_REQUEST, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    // Next Sunday 19:00
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 19); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    alarmManager.cancel(pendingIntent)
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
}

fun cancelWeeklyReport(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, NotificationReceiver::class.java).apply { action = ACTION_WEEKLY_REPORT }
    val pendingIntent = PendingIntent.getBroadcast(
        context, WEEKLY_REPORT_REQUEST, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    alarmManager.cancel(pendingIntent)
}
