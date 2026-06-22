package com.example.beeing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================
// STREAK ENGINE  (pure logic — fully derived from ratings)
// ============================================================

const val STREAK_HOURS_REQUIRED = 8   // distinct rated hours for a day to count
const val HOURS_PER_SAVER = 10        // banked extra hours per streak saver
const val MAX_SAVERS = 3              // max savers a user can hold

data class StreakState(
    val currentStreak: Int,      // consecutive qualifying days ending today/yesterday
    val todayHours: Int,         // distinct hours rated today (may exceed 8)
    val todayQualified: Boolean, // todayHours >= STREAK_HOURS_REQUIRED
    val savers: Int,             // 0..MAX_SAVERS
    val bankProgress: Int,       // hours banked toward the NEXT saver (0..HOURS_PER_SAVER-1)
    val extraToday: Int          // hours today beyond the required 8 (UI flavour)
)

private fun Calendar.dayKey(): Long = get(Calendar.YEAR) * 1000L + get(Calendar.DAY_OF_YEAR)

/**
 * Replays the full history day-by-day so savers are spent deterministically:
 *  - each calendar day with >=8 distinct rated hours counts toward the streak
 *  - extra hours (beyond 8) accumulate; every 10 banked grants a saver (cap 3)
 *  - a past day with <8 hours silently consumes a saver to hold the streak,
 *    or resets the streak (and the bank) if none are available
 *  - today is treated as "in progress": <8 neither counts nor breaks
 */
fun computeStreakState(ratings: List<RatingEntry>): StreakState {
    if (ratings.isEmpty()) return StreakState(0, 0, false, 0, 0, 0)

    val hoursByDay = HashMap<Long, MutableSet<Int>>()
    var earliest = Long.MAX_VALUE
    val tmp = Calendar.getInstance()
    for (e in ratings) {
        tmp.timeInMillis = e.timestamp
        hoursByDay.getOrPut(tmp.dayKey()) { mutableSetOf() }.add(tmp.get(Calendar.HOUR_OF_DAY))
        if (e.timestamp < earliest) earliest = e.timestamp
    }

    val today = Calendar.getInstance()
    val todayKey = today.dayKey()
    val todayHours = hoursByDay[todayKey]?.size ?: 0

    val cursor = Calendar.getInstance().apply {
        timeInMillis = earliest
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    var streak = 0
    var savers = 0
    var bank = 0

    while (true) {
        val key = cursor.dayKey()
        val hours = hoursByDay[key]?.size ?: 0

        if (hours >= STREAK_HOURS_REQUIRED) {
            streak += 1
            if (savers < MAX_SAVERS) {
                bank += hours - STREAK_HOURS_REQUIRED
                while (bank >= HOURS_PER_SAVER && savers < MAX_SAVERS) {
                    bank -= HOURS_PER_SAVER
                    savers += 1
                }
                if (savers >= MAX_SAVERS) bank = 0 // surplus discarded at cap
            }
        } else if (key != todayKey) {
            // a real missed day in the past
            if (savers > 0) savers -= 1 // auto-consume, streak holds
            else { streak = 0; bank = 0 }
        }
        // today with <8 hours falls through: in progress, no effect

        if (key == todayKey) break
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }

    return StreakState(
        currentStreak = streak,
        todayHours = todayHours,
        todayQualified = todayHours >= STREAK_HOURS_REQUIRED,
        savers = savers,
        bankProgress = bank,
        extraToday = maxOf(0, todayHours - STREAK_HOURS_REQUIRED)
    )
}

// ============================================================
// STREAK METER  (modern 8-segment ring + saver pips + bank bar)
// ============================================================

@Composable
fun StreakMeter(
    state: StreakState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val filled = state.todayHours.coerceAtMost(STREAK_HOURS_REQUIRED)
    val animatedFill by animateFloatAsState(
        targetValue = filled.toFloat(),
        animationSpec = tween(700),
        label = "streakFill"
    )

    val accent = MaterialTheme.colorScheme.primary
    val done = Color(0xFF66BB6A)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = if (state.todayQualified) done else accent

    val card = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Card(modifier = modifier.then(card), shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 8-segment ring with the streak count in the middle
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val gapDeg = 6f
                    val segSweep = 360f / STREAK_HOURS_REQUIRED - gapDeg
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)

                    for (i in 0 until STREAK_HOURS_REQUIRED) {
                        val start = -90f + i * (360f / STREAK_HOURS_REQUIRED) + gapDeg / 2
                        // track
                        drawArc(
                            color = track,
                            startAngle = start, sweepAngle = segSweep, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        // fill (supports a partial last segment as it animates)
                        val frac = (animatedFill - i).coerceIn(0f, 1f)
                        if (frac > 0f) {
                            drawArc(
                                color = ringColor,
                                startAngle = start, sweepAngle = segSweep * frac, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${state.currentStreak}",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "day streak",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right column: today's progress, saver pips, bank bar
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.todayQualified) "Today secured ✓"
                    else "${state.todayHours} / $STREAK_HOURS_REQUIRED hours today",
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.todayQualified) done else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )

                // Saver shield pips
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0 until MAX_SAVERS) {
                        Text(
                            "🛡️",
                            fontSize = 18.sp,
                            modifier = Modifier.alpha(if (i < state.savers) 1f else 0.25f)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${state.savers} saver${if (state.savers == 1) "" else "s"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bank progress toward next saver (hidden at cap)
                if (state.savers < MAX_SAVERS) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LinearProgressIndicator(
                            progress = { state.bankProgress / HOURS_PER_SAVER.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accent,
                            trackColor = track
                        )
                        Text(
                            "${state.bankProgress}/$HOURS_PER_SAVER 🌸 flowers to next saver",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Savers maxed 🛡️🛡️🛡️",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================
// STREAK LOG  (exhaustive, timestamped event history)
// ============================================================

enum class StreakEventType { STARTED, EXTENDED, SAVER_EARNED, SAVER_USED, RESET, BANKED }

data class StreakEvent(
    val timestamp: Long,
    val type: StreakEventType,
    val title: String,
    val detail: String
)

private fun StreakEventType.emoji(): String = when (this) {
    StreakEventType.STARTED -> "🌱"
    StreakEventType.EXTENDED -> "🔥"
    StreakEventType.SAVER_EARNED -> "🛡️"
    StreakEventType.SAVER_USED -> "🛟"
    StreakEventType.RESET -> "💔"
    StreakEventType.BANKED -> "🌸"
}

/**
 * Replays history at hour granularity to emit an exhaustive, timestamped log of
 * streak milestones. Returned newest-first.
 */
fun computeStreakLog(ratings: List<RatingEntry>): List<StreakEvent> {
    if (ratings.isEmpty()) return emptyList()

    // For each day: distinct hours mapped to the earliest timestamp they were rated.
    data class DayInfo(val dayStart: Long, val hourTimestamps: List<Long>)
    val byDay = HashMap<Long, HashMap<Int, Long>>()
    var earliest = Long.MAX_VALUE
    val tmp = Calendar.getInstance()
    for (e in ratings) {
        tmp.timeInMillis = e.timestamp
        val key = tmp.get(Calendar.YEAR) * 1000L + tmp.get(Calendar.DAY_OF_YEAR)
        val hour = tmp.get(Calendar.HOUR_OF_DAY)
        val map = byDay.getOrPut(key) { HashMap() }
        val existing = map[hour]
        if (existing == null || e.timestamp < existing) map[hour] = e.timestamp
        if (e.timestamp < earliest) earliest = e.timestamp
    }

    val today = Calendar.getInstance()
    val todayKey = today.get(Calendar.YEAR) * 1000L + today.get(Calendar.DAY_OF_YEAR)

    val cursor = Calendar.getInstance().apply {
        timeInMillis = earliest
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())

    val events = ArrayList<StreakEvent>()
    var streak = 0
    var savers = 0
    var bank = 0

    while (true) {
        val key = cursor.get(Calendar.YEAR) * 1000L + cursor.get(Calendar.DAY_OF_YEAR)
        val dayStart = cursor.timeInMillis
        val endOfDay = dayStart + 23L * 3600000L + 59L * 60000L
        val dayLabel = dateFmt.format(cursor.time)

        val orderedTs = byDay[key]?.values?.sorted() ?: emptyList()
        val n = orderedTs.size

        if (n >= STREAK_HOURS_REQUIRED) {
            // 8th distinct hour -> start/extend
            val qualifyTs = orderedTs[STREAK_HOURS_REQUIRED - 1]
            if (streak == 0) {
                streak = 1
                events.add(StreakEvent(qualifyTs, StreakEventType.STARTED, "Streak started", "Day 1 — reached 8 hours on $dayLabel"))
            } else {
                streak += 1
                events.add(StreakEvent(qualifyTs, StreakEventType.EXTENDED, "Streak extended", "Day $streak — reached 8 hours on $dayLabel"))
            }
            // extra hours -> bank flowers one by one
            var bankedToday = 0
            for (i in STREAK_HOURS_REQUIRED until n) {
                if (savers >= MAX_SAVERS) break
                bank += 1; bankedToday += 1
                if (bank >= HOURS_PER_SAVER) {
                    bank = 0; savers += 1
                    events.add(StreakEvent(orderedTs[i], StreakEventType.SAVER_EARNED, "Streak saver earned 🛡️", "Collected 10 🌸 flowers · savers now $savers"))
                    if (savers >= MAX_SAVERS) bank = 0
                }
            }
            // end-of-day balance — only when extra hours were actually banked
            if (bankedToday > 0) {
                events.add(
                    StreakEvent(
                        endOfDay, StreakEventType.BANKED, "End of day",
                        "$dayLabel: +$bankedToday 🌸 flowers · balance ${bank}/$HOURS_PER_SAVER · $savers saver${if (savers == 1) "" else "s"}"
                    )
                )
            }
        } else if (key != todayKey) {
            // missed past day
            if (savers > 0) {
                savers -= 1
                events.add(StreakEvent(endOfDay, StreakEventType.SAVER_USED, "Streak saver used 🛟", "$dayLabel had under 8 hours — streak saved · $savers saver${if (savers == 1) "" else "s"} left"))
            } else if (streak > 0) {
                streak = 0; bank = 0
                events.add(StreakEvent(endOfDay, StreakEventType.RESET, "Streak reset 💔", "$dayLabel had under 8 hours and no savers left"))
            }
        }
        // today with <8 hours: in progress, no event

        if (key == todayKey) break
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }

    return events.sortedByDescending { it.timestamp }
}

@Composable
fun StreakLogContent(ratings: List<RatingEntry>, refreshKey: Int = 0) {
    val events = remember(ratings, refreshKey) { computeStreakLog(ratings) }
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    if (events.isEmpty()) {
        Text("No streak events yet — rate 8 hours in a day to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column {
            events.take(60).forEach { ev ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(ev.type.emoji(), fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ev.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(ev.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            fmt.format(java.util.Date(ev.timestamp)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
            }
            if (events.size > 60) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Showing latest 60 of ${events.size} events",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
