package com.example.beeing

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.unit.lerp
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
const val GIFTED_SAVERS = 1           // day-one gift so the first stumble doesn't reset
const val RECLAIM_COST = 5            // flowers to rate one expired hour from today
const val FLOWER_CAP = 20             // max flowers the bank can hold
const val RECLAIM_TAG = "💧reclaimed" // marks hours rated via a reclaim

// ============================================================
// RECLAIM SPENDS  (persisted — not derivable from ratings)
// ============================================================

private fun loadMillisList(context: android.content.Context, key: String): List<Long> =
    (context.getSharedPreferences("b", 0).getString(key, "") ?: "")
        .split(",").mapNotNull { it.toLongOrNull() }

fun loadReclaimSpends(context: android.content.Context) = loadMillisList(context, "reclaim_spends")

fun recordReclaimSpend(context: android.content.Context) {
    val prefs = context.getSharedPreferences("b", 0)
    val cur = prefs.getString("reclaim_spends", "") ?: ""
    val next = if (cur.isBlank()) "${System.currentTimeMillis()}" else "$cur,${System.currentTimeMillis()}"
    prefs.edit().putString("reclaim_spends", next).apply()
}

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
 *  - extra hours (beyond 8) accumulate as flowers (bank capped at FLOWER_CAP);
 *    every 10 banked auto-forges a saver while below the saver cap
 *  - reclaimed hours count toward the 8 but never earn flowers
 *  - each reclaim deducts RECLAIM_COST flowers on its day
 *  - a past day with <8 hours silently consumes a saver to hold the streak,
 *    or resets the streak (and the bank) if none are available
 *  - today is treated as "in progress": <8 neither counts nor breaks
 *  - everyone starts with one gifted saver
 */
fun computeStreakState(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long> = emptyList()
): StreakState {
    if (ratings.isEmpty()) return StreakState(0, 0, false, GIFTED_SAVERS, 0, 0)

    val hoursByDay = HashMap<Long, MutableSet<Int>>()
    val reclaimedByDay = HashMap<Long, MutableSet<Int>>()
    var earliest = Long.MAX_VALUE
    val tmp = Calendar.getInstance()
    for (e in ratings) {
        tmp.timeInMillis = e.timestamp
        val key = tmp.dayKey()
        val hour = tmp.get(Calendar.HOUR_OF_DAY)
        hoursByDay.getOrPut(key) { mutableSetOf() }.add(hour)
        if (RECLAIM_TAG in e.tags) reclaimedByDay.getOrPut(key) { mutableSetOf() }.add(hour)
        if (e.timestamp < earliest) earliest = e.timestamp
    }
    val spendsByDay = HashMap<Long, Int>()
    for (p in reclaimSpends) {
        tmp.timeInMillis = p
        spendsByDay[tmp.dayKey()] = (spendsByDay[tmp.dayKey()] ?: 0) + 1
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
    var savers = GIFTED_SAVERS
    var bank = 0

    while (true) {
        val key = cursor.dayKey()
        val hours = hoursByDay[key]?.size ?: 0

        if (hours >= STREAK_HOURS_REQUIRED) {
            streak += 1
            val reclaimed = reclaimedByDay[key]?.size ?: 0
            bank += (hours - STREAK_HOURS_REQUIRED - reclaimed).coerceAtLeast(0)
            while (bank >= HOURS_PER_SAVER && savers < MAX_SAVERS) {
                bank -= HOURS_PER_SAVER
                savers += 1
            }
            if (bank > FLOWER_CAP) bank = FLOWER_CAP
        } else if (key != todayKey) {
            // a real missed day in the past
            if (savers > 0) savers -= 1 // auto-consume, streak holds
            else { streak = 0; bank = 0 }
        }
        // today with <8 hours falls through: in progress, no effect

        // reclaims spend from the bank on the day they happen
        bank -= RECLAIM_COST * (spendsByDay[key] ?: 0)
        if (bank < 0) bank = 0

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
// DAY OUTCOMES  (calendar view: 💐 qualified / 🛡️ saved / 🥀 missed)
// ============================================================

enum class DayOutcome { QUALIFIED, SAVED, MISSED }

/**
 * Per-day outcome for every day from the first rating through yesterday,
 * replaying the exact same rules as computeStreakState so the calendar can
 * never disagree with the meter. Today appears only once it has qualified.
 * Key = year * 1000 + dayOfYear.
 */
fun computeDayOutcomes(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long> = emptyList()
): Map<Long, DayOutcome> {
    if (ratings.isEmpty()) return emptyMap()

    val hoursByDay = HashMap<Long, MutableSet<Int>>()
    val reclaimedByDay = HashMap<Long, MutableSet<Int>>()
    var earliest = Long.MAX_VALUE
    val tmp = Calendar.getInstance()
    for (e in ratings) {
        tmp.timeInMillis = e.timestamp
        val key = tmp.dayKey()
        val hour = tmp.get(Calendar.HOUR_OF_DAY)
        hoursByDay.getOrPut(key) { mutableSetOf() }.add(hour)
        if (RECLAIM_TAG in e.tags) reclaimedByDay.getOrPut(key) { mutableSetOf() }.add(hour)
        if (e.timestamp < earliest) earliest = e.timestamp
    }
    val spendsByDay = HashMap<Long, Int>()
    for (p in reclaimSpends) {
        tmp.timeInMillis = p
        spendsByDay[tmp.dayKey()] = (spendsByDay[tmp.dayKey()] ?: 0) + 1
    }

    val todayKey = Calendar.getInstance().dayKey()
    val cursor = Calendar.getInstance().apply {
        timeInMillis = earliest
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    val outcomes = HashMap<Long, DayOutcome>()
    var savers = GIFTED_SAVERS
    var bank = 0

    while (true) {
        val key = cursor.dayKey()
        val hours = hoursByDay[key]?.size ?: 0

        if (hours >= STREAK_HOURS_REQUIRED) {
            outcomes[key] = DayOutcome.QUALIFIED
            val reclaimed = reclaimedByDay[key]?.size ?: 0
            bank += (hours - STREAK_HOURS_REQUIRED - reclaimed).coerceAtLeast(0)
            while (bank >= HOURS_PER_SAVER && savers < MAX_SAVERS) {
                bank -= HOURS_PER_SAVER
                savers += 1
            }
            if (bank > FLOWER_CAP) bank = FLOWER_CAP
        } else if (key != todayKey) {
            if (savers > 0) {
                savers -= 1
                outcomes[key] = DayOutcome.SAVED
            } else {
                bank = 0
                outcomes[key] = DayOutcome.MISSED
            }
        }

        bank -= RECLAIM_COST * (spendsByDay[key] ?: 0)
        if (bank < 0) bank = 0

        if (key == todayKey) break
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }
    return outcomes
}

// ============================================================
// STREAK METER  (8-segment ring; grows full-width when the dial rests)
// ============================================================

@Composable
fun StreakMeter(
    state: StreakState,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val filled = state.todayHours.coerceAtMost(STREAK_HOURS_REQUIRED)
    val animatedFill by animateFloatAsState(
        targetValue = filled.toFloat(),
        animationSpec = tween(700),
        label = "streakFill"
    )
    // 0 = compact (ring left, text right); 1 = ring fills the card, text below
    val expandFraction by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "streakExpand"
    )

    val accent = MaterialTheme.colorScheme.primary
    val done = Color(0xFF66BB6A)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = if (state.todayQualified) done else accent

    val statusText = if (state.todayQualified) "Today secured ✓"
    else "${state.todayHours} / $STREAK_HOURS_REQUIRED hours today"
    val promiseText = if (state.todayQualified) "See your month on the Streaks tab"
    else "Fill the ring → day ${state.currentStreak + 1}"

    val card = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Card(modifier = modifier.then(card), shape = RoundedCornerShape(20.dp)) {
        BoxWithConstraints(Modifier.padding(16.dp)) {
            val ringSize = lerp(116.dp, maxWidth.coerceAtMost(260.dp), expandFraction)

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (expandFraction > 0.5f) Arrangement.Center
                    else Arrangement.spacedBy(20.dp)
                ) {
                    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = lerp(16.dp, 32.dp, expandFraction).toPx()
                            val gapDeg = 6f
                            val slotDeg = 360f / STREAK_HOURS_REQUIRED
                            val segSweep = slotDeg - gapDeg
                            val inset = stroke / 2
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            val topLeft = Offset(inset, inset)
                            val a0 = -90f + gapDeg / 2

                            // Filled hours merge into ONE arc: flat (butt) start at the
                            // top marks where filling began; drawn first so the track's
                            // round cap can bite a concave notch at the leading end only.
                            val whole = animatedFill.toInt().coerceIn(0, STREAK_HOURS_REQUIRED)
                            val frac = animatedFill - whole
                            val fillSweep = when {
                                frac > 0f -> whole * slotDeg + segSweep * frac
                                whole > 0 -> (whole - 1) * slotDeg + segSweep
                                else -> 0f
                            }
                            if (fillSweep > 0f) {
                                drawArc(
                                    color = ringColor,
                                    startAngle = a0, sweepAngle = fillSweep, useCenter = false,
                                    topLeft = topLeft, size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                )
                            }

                            // Unfilled hours stay as separate segments with round caps;
                            // the first one starts at the fill's leading edge and its
                            // round cap carves the concave notch into the fill.
                            for (i in whole until STREAK_HOURS_REQUIRED) {
                                val segStart = a0 + i * slotDeg
                                val from = if (i == whole && frac > 0f) segStart + segSweep * frac else segStart
                                val sweep = segStart + segSweep - from
                                if (sweep > 0f) {
                                    drawArc(
                                        color = track,
                                        startAngle = from, sweepAngle = sweep, useCenter = false,
                                        topLeft = topLeft, size = arcSize,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${state.currentStreak}",
                                fontSize = androidx.compose.ui.unit.lerp(34.sp, 56.sp, expandFraction),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "day streak",
                                fontSize = androidx.compose.ui.unit.lerp(11.sp, 14.sp, expandFraction),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Compact state: status to the right of the ring
                    if (expandFraction < 1f) {
                        Column(
                            Modifier
                                .weight(1f)
                                .alpha(1f - expandFraction),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                statusText,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.todayQualified) done else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                            Text(
                                promiseText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expanded state: the same status slides in below the ring
                if (expandFraction > 0f) {
                    Column(
                        Modifier
                            .padding(top = lerp(0.dp, 12.dp, expandFraction))
                            .alpha(expandFraction),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            statusText,
                            fontWeight = FontWeight.Bold,
                            color = if (state.todayQualified) done else MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp
                        )
                        Text(
                            promiseText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// STREAK LOG  (exhaustive, timestamped event history)
// ============================================================

enum class StreakEventType { STARTED, EXTENDED, SAVER_EARNED, SAVER_USED, RESET, BANKED, RECLAIMED }

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
    StreakEventType.RECLAIMED -> "💧"
}

/**
 * Replays history at hour granularity to emit an exhaustive, timestamped log of
 * streak milestones. Returned newest-first.
 */
fun computeStreakLog(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long> = emptyList()
): List<StreakEvent> {
    if (ratings.isEmpty()) return emptyList()

    // For each day: distinct hours mapped to the earliest timestamp they were rated.
    val byDay = HashMap<Long, HashMap<Int, Long>>()
    val reclaimedByDay = HashMap<Long, MutableSet<Int>>()
    var earliest = Long.MAX_VALUE
    val tmp = Calendar.getInstance()
    for (e in ratings) {
        tmp.timeInMillis = e.timestamp
        val key = tmp.get(Calendar.YEAR) * 1000L + tmp.get(Calendar.DAY_OF_YEAR)
        val hour = tmp.get(Calendar.HOUR_OF_DAY)
        val map = byDay.getOrPut(key) { HashMap() }
        val existing = map[hour]
        if (existing == null || e.timestamp < existing) map[hour] = e.timestamp
        if (RECLAIM_TAG in e.tags) reclaimedByDay.getOrPut(key) { mutableSetOf() }.add(hour)
        if (e.timestamp < earliest) earliest = e.timestamp
    }
    val spendsByDay = HashMap<Long, MutableList<Long>>()
    for (p in reclaimSpends) {
        tmp.timeInMillis = p
        val key = tmp.get(Calendar.YEAR) * 1000L + tmp.get(Calendar.DAY_OF_YEAR)
        spendsByDay.getOrPut(key) { mutableListOf() }.add(p)
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
    var savers = GIFTED_SAVERS
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
            // extra hours -> bank flowers one by one (reclaimed hours never earn)
            val reclaimed = reclaimedByDay[key]?.size ?: 0
            val eligible = (n - STREAK_HOURS_REQUIRED - reclaimed).coerceAtLeast(0)
            for (i in (n - eligible) until n) {
                if (bank < FLOWER_CAP) bank += 1
                if (bank >= HOURS_PER_SAVER && savers < MAX_SAVERS) {
                    bank -= HOURS_PER_SAVER; savers += 1
                    events.add(StreakEvent(orderedTs[i], StreakEventType.SAVER_EARNED, "Streak saver earned 🛡️", "Collected 10 🌸 flowers · savers now $savers"))
                }
            }
            // end-of-day balance — only when extra hours were actually banked
            if (eligible > 0) {
                events.add(
                    StreakEvent(
                        endOfDay, StreakEventType.BANKED, "End of day",
                        "$dayLabel: +$eligible 🌸 flowers · balance ${bank} 🌸 · $savers saver${if (savers == 1) "" else "s"}"
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

        // reclaims spend from the bank on the day they happen
        spendsByDay[key]?.forEach { ts ->
            bank = (bank - RECLAIM_COST).coerceAtLeast(0)
            events.add(StreakEvent(ts, StreakEventType.RECLAIMED, "Hour reclaimed 💧", "Rated a missed hour · −$RECLAIM_COST 🌸 · balance $bank 🌸"))
        }

        if (key == todayKey) break
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }

    return events.sortedByDescending { it.timestamp }
}

@Composable
fun StreakLogContent(ratings: List<RatingEntry>, refreshKey: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val events = remember(ratings, refreshKey) {
        computeStreakLog(ratings, loadReclaimSpends(context))
    }
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
