package com.example.beeing

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
// PERIOD MODEL  (Day / Week / Month / Year, 7-period windows)
// ============================================================

enum class StatPeriod(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class PeriodBucket(
    val label: String,
    val startMillis: Long,
    val endMillisExclusive: Long
)

private fun normalizeToPeriodStart(cal: Calendar, period: StatPeriod) {
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    when (period) {
        StatPeriod.DAY -> {}
        StatPeriod.WEEK -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        StatPeriod.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        StatPeriod.YEAR -> { cal.set(Calendar.MONTH, 0); cal.set(Calendar.DAY_OF_MONTH, 1) }
    }
}

private fun stepBack(cal: Calendar, period: StatPeriod) = when (period) {
    StatPeriod.DAY -> cal.add(Calendar.DATE, -1)
    StatPeriod.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -1)
    StatPeriod.MONTH -> cal.add(Calendar.MONTH, -1)
    StatPeriod.YEAR -> cal.add(Calendar.YEAR, -1)
}

private fun stepForward(cal: Calendar, period: StatPeriod) = when (period) {
    StatPeriod.DAY -> cal.add(Calendar.DATE, 1)
    StatPeriod.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
    StatPeriod.MONTH -> cal.add(Calendar.MONTH, 1)
    StatPeriod.YEAR -> cal.add(Calendar.YEAR, 1)
}

/**
 * Returns [window] buckets oldest -> newest. pageOffset 0 = most recent window
 * ending with the current period; pageOffset 1 = the previous window, etc.
 */
fun buildPeriodBuckets(period: StatPeriod, pageOffset: Int, window: Int = 7): List<PeriodBucket> {
    val dayFmt = SimpleDateFormat("d MMM", Locale.getDefault())
    val monthFmt = SimpleDateFormat("MMM yy", Locale.getDefault())
    val yearFmt = SimpleDateFormat("yyyy", Locale.getDefault())

    val cal = Calendar.getInstance()
    normalizeToPeriodStart(cal, period)
    repeat(pageOffset * window) { stepBack(cal, period) }

    val buckets = ArrayList<PeriodBucket>(window)
    for (i in 0 until window) {
        val start = cal.timeInMillis
        val end = (cal.clone() as Calendar).apply { stepForward(this, period) }.timeInMillis
        val label = when (period) {
            StatPeriod.DAY -> dayFmt.format(cal.time)
            StatPeriod.WEEK -> "W${cal.get(Calendar.WEEK_OF_YEAR)}"
            StatPeriod.MONTH -> monthFmt.format(cal.time)
            StatPeriod.YEAR -> yearFmt.format(cal.time)
        }
        buckets.add(PeriodBucket(label, start, end))
        stepBack(cal, period)
    }
    return buckets.reversed()
}

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

// ============================================================
// INSIGHTS SECTION  (Past tab) — features 2, 3, 5
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsContent(
    ratings: List<RatingEntry>,
    refreshKey: Int = 0
) {
    var period by remember { mutableStateOf(StatPeriod.DAY) }
    var pageOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(period) { pageOffset = 0 }

    val bucket = remember(period, pageOffset) { buildPeriodBuckets(period, pageOffset, window = 1).first() }
    val windowEntries = remember(ratings, bucket, refreshKey) {
        ratings.filter { it.timestamp in bucket.startMillis until bucket.endMillisExclusive }
    }

    val periodLabel = if (pageOffset == 0) when (period) {
        StatPeriod.DAY -> "Today"
        StatPeriod.WEEK -> "This week"
        StatPeriod.MONTH -> "This month"
        StatPeriod.YEAR -> "This year"
    } else bucket.label

    Column(Modifier.fillMaxWidth()) {
        // --- Granularity selector (one unit at a time) ---
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatPeriod.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = period == p,
                    onClick = { period = p },
                    shape = SegmentedButtonDefaults.itemShape(i, StatPeriod.entries.size)
                ) { Text(p.label, fontSize = 13.sp) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { pageOffset++ }) {
                Icon(Icons.Default.KeyboardArrowLeft, "Previous")
            }
            Text(
                periodLabel,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { if (pageOffset > 0) pageOffset-- }, enabled = pageOffset > 0) {
                Icon(
                    Icons.Default.KeyboardArrowRight, "Next",
                    tint = if (pageOffset > 0) LocalContentColor.current else Color.Gray
                )
            }
        }

        if (windowEntries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No ratings in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            TagCorrelationCard(windowEntries)
        }
    }
}

/**
 * Uniform, collapsible section wrapper used across the Past tab.
 * Pass a blank title to render a header bar with just the chevron.
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title.isNotBlank()) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotation)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

// --- Feature 2: tag-score correlation ---
@Composable
private fun TagCorrelationCard(windowEntries: List<RatingEntry>) {
    val tagStats = remember(windowEntries) {
        windowEntries.flatMap { e -> e.tags.map { it to e.score } }
            .groupBy({ it.first }, { it.second })
            .map { (tag, scores) -> Triple(tag, scores.average(), scores.size) }
            .sortedByDescending { it.second }
    }
    if (tagStats.isEmpty()) {
        Text("No tagged hours in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        return
    }

    Text("How tags score", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    tagStats.forEach { (tag, avg, count) ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tag, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(String.format("%.1f", avg), color = getScoreColor(avg), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("($count)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { (avg / 10f).toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = getScoreColor(avg),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

