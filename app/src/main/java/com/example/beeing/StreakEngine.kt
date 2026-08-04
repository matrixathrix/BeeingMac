package com.example.beeing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ============================================================
// STREAK ENGINE  (pure logic — fully derived from ratings)
// ============================================================

const val STREAK_HOURS_REQUIRED = 8   // distinct rated hours for a day to count
const val BEGINNER_HOURS_REQUIRED = 4 // ...and the softer day-one goal in beginner mode
const val BEGINNER_GRADUATION_DAYS = 3 // completed beginner days before the one-time nudge
const val FORGIVE_WINDOW_DAYS = 7     // a rest day needs the previous 6 days clear of another
const val RECLAIM_WINDOW_HOURS = 10   // how far back a bee can reach (clock hours, may cross midnight)
const val RECLAIM_PER_DAY = 2         // free bees a calendar day may send back
// Stored sentinel inside persisted entries — the UI shows Sym.FromMemory /
// Sym.Reclaim instead, but the stored string must never change or old reclaims
// stop being recognized. (This is why it keeps its emoji: it is data, not copy.)
const val RECLAIM_TAG = "💧reclaimed"

/** True when this entry was rated from memory via "send a bee back". */
val RatingEntry.isReclaimed: Boolean get() = RECLAIM_TAG in tags

/** Tags fit to show: the reclaim sentinel is carried by the icon badge instead. */
fun RatingEntry.visibleTags(): List<String> = tags.filter { it != RECLAIM_TAG }

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

// ============================================================
// MODE EVENTS  (persisted — beginner mode is replayed, never a live flag)
// ============================================================
//
// Storing "the user is in beginner mode" as one boolean would make history lie:
// yesterday's outcome would silently change the moment the toggle moved. So the
// toggle is an append-only log of (timestamp, enteredBeginner) instead, exactly
// like reclaim_spends, and every replay asks it what the mode was on each day.

private const val MODE_EVENTS_KEY = "mode_events"
private const val GRADUATION_SHOWN_KEY = "beginner_graduation_shown"

/** One flip of the mode toggle. `enteredBeginner == false` means "back to Master". */
data class ModeEvent(val timestamp: Long, val enteredBeginner: Boolean)

/** Oldest first — the replay and `isBeginnerMode` both rely on that order. */
fun loadModeEvents(context: android.content.Context): List<ModeEvent> =
    (context.getSharedPreferences("b", 0).getString(MODE_EVENTS_KEY, "") ?: "")
        .split(",")
        .mapNotNull { record ->
            val parts = record.split(":")
            if (parts.size != 2) return@mapNotNull null
            val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
            ModeEvent(ts, parts[1] == "1")
        }
        .sortedBy { it.timestamp }

fun recordModeEvent(context: android.content.Context, enteredBeginner: Boolean) {
    val prefs = context.getSharedPreferences("b", 0)
    val cur = prefs.getString(MODE_EVENTS_KEY, "") ?: ""
    val record = "${System.currentTimeMillis()}:${if (enteredBeginner) 1 else 0}"
    prefs.edit().putString(MODE_EVENTS_KEY, if (cur.isBlank()) record else "$cur,$record").apply()
}

/** The mode in effect right now — i.e. the latest event. No events = Master. */
fun isBeginnerMode(events: List<ModeEvent>): Boolean =
    events.lastOrNull()?.enteredBeginner == true

/** The daily goal a mode asks for. */
fun hoursRequiredFor(beginner: Boolean): Int =
    if (beginner) BEGINNER_HOURS_REQUIRED else STREAK_HOURS_REQUIRED

/** The mode in effect at a given instant — Master before the first event. */
private fun beginnerAt(timeMs: Long, events: List<ModeEvent>): Boolean =
    events.lastOrNull { it.timestamp <= timeMs }?.enteredBeginner ?: false

/**
 * Fresh installs land in beginner mode so day one is a 4-hour day, not an
 * 8-hour cliff. Same "key absent ⇒ never configured" idiom as tags_v2: anyone
 * who already has ratings is an existing user and stays in Master, with no
 * event written (so their whole history replays unchanged).
 */
fun ensureModeInitialized(context: android.content.Context) {
    val prefs = context.getSharedPreferences("b", 0)
    if (prefs.contains(MODE_EVENTS_KEY)) return
    if (loadRatings(context).isNotEmpty()) return
    recordModeEvent(context, true)
}

/** The graduation nudge is once per install, whatever the user answers. */
fun beginnerGraduationShown(context: android.content.Context): Boolean =
    context.getSharedPreferences("b", 0).getBoolean(GRADUATION_SHOWN_KEY, false)

fun markBeginnerGraduationShown(context: android.content.Context) {
    context.getSharedPreferences("b", 0).edit().putBoolean(GRADUATION_SHOWN_KEY, true).apply()
}

/** Bees already sent back today — the reclaim is free but capped per day. */
fun reclaimsUsedToday(spends: List<Long>): Int {
    val todayKey = Calendar.getInstance().dayKey()
    val cal = Calendar.getInstance()
    return spends.count { cal.timeInMillis = it; cal.dayKey() == todayKey }
}

data class StreakState(
    val currentStreak: Int,      // consecutive qualifying days ending today/yesterday
    val todayHours: Int,         // distinct hours rated today (may exceed the goal)
    val todayQualified: Boolean, // todayHours >= hoursRequired
    val extraToday: Int,         // hours today beyond the goal (UI flavour)
    val beginnerMode: Boolean,   // today's mode
    val hoursRequired: Int,      // today's goal: 4 in beginner mode, else 8
    val beginnerDaysCompleted: Int // 🌱 days ever completed (drives the graduation nudge)
) {
    /** In beginner mode the streak is frozen, not lost — say so, don't hide it. */
    val streakPaused: Boolean get() = beginnerMode && currentStreak > 0
}

private fun Calendar.dayKey(): Long = get(Calendar.YEAR) * 1000L + get(Calendar.DAY_OF_YEAR)

// ============================================================
// THE REPLAY  (one walk over history; every reader below shares it)
// ============================================================

enum class DayOutcome { QUALIFIED, REST, MISSED, BEGINNER_COMPLETE }

private class ReplayDay(
    val key: Long,                     // year * 1000 + dayOfYear
    val dayStartMs: Long,
    val hourTimestamps: List<Long>,    // distinct rated hours -> earliest ts each, sorted
    val reclaimedHours: Int,
    val spends: List<Long>,            // wall-clock ts of bees sent back this day
    val outcome: DayOutcome?,          // null = today, or a beginner day under the goal
    val streakAfter: Int,
    val brokeStreak: Boolean,          // this miss is the one that reset the hive
    val beginner: Boolean,             // the mode in effect at this day's END
    val isToday: Boolean
)

/**
 * Walks every calendar day from the first rating through today and decides its
 * outcome. `computeStreakState`, `computeDayOutcomes` and `computeStreakLog` all
 * read this one list, so they cannot disagree.
 *
 *  - a day with >=STREAK_HOURS_REQUIRED distinct rated hours QUALIFIES and
 *    extends the streak (reclaimed hours count toward the 8)
 *  - a missed past day is forgiven as a REST day when a streak is actually
 *    running and no other rest day falls inside the previous
 *    FORGIVE_WINDOW_DAYS - 1 days (so ~1 per week, rolling)
 *  - a second miss inside that window MISSES and resets the streak
 *  - a miss with no streak running just MISSES — there is nothing to forgive,
 *    and it does not burn the rest day
 *  - today is in progress: under 8 hours neither counts nor breaks
 *
 * BEGINNER DAYS ARE TRANSPARENT. A day whose mode at midnight was beginner is
 * scored against BEGINNER_HOURS_REQUIRED and then steps aside entirely: 4+ hours
 * is a BEGINNER_COMPLETE, under 4 has no outcome at all. Either way the streak
 * neither extends nor breaks and no rest day is consumed, so a streak entering
 * beginner mode is paused and resumes at its old count on the next Master day.
 * The mode is read at each day's END, so a mid-day toggle re-scores that whole
 * day under the mode the user finished it in.
 */
private fun replayDays(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long>,
    modeEvents: List<ModeEvent>
): List<ReplayDay> {
    if (ratings.isEmpty()) return emptyList()

    val hourTsByDay = HashMap<Long, HashMap<Int, Long>>()
    val reclaimedByDay = HashMap<Long, MutableSet<Int>>()
    var earliest = Long.MAX_VALUE
    val tmp = Calendar.getInstance()
    for (e in ratings) {
        tmp.timeInMillis = e.timestamp
        val key = tmp.dayKey()
        val hour = tmp.get(Calendar.HOUR_OF_DAY)
        val map = hourTsByDay.getOrPut(key) { HashMap() }
        val existing = map[hour]
        if (existing == null || e.timestamp < existing) map[hour] = e.timestamp
        if (e.isReclaimed) reclaimedByDay.getOrPut(key) { mutableSetOf() }.add(hour)
        if (e.timestamp < earliest) earliest = e.timestamp
    }
    val spendsByDay = HashMap<Long, MutableList<Long>>()
    for (p in reclaimSpends) {
        tmp.timeInMillis = p
        spendsByDay.getOrPut(tmp.dayKey()) { mutableListOf() }.add(p)
    }

    val todayKey = Calendar.getInstance().dayKey()
    val cursor = Calendar.getInstance().apply {
        timeInMillis = earliest
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    val days = ArrayList<ReplayDay>()
    var streak = 0
    var dayIndex = 0            // calendar days since the first rating
    var lastRestIndex: Int? = null

    while (true) {
        val key = cursor.dayKey()
        val isToday = key == todayKey
        val hourTs = hourTsByDay[key]?.values?.sorted() ?: emptyList()
        // The last instant of this calendar day — derived from the next day's
        // start so DST-shortened/lengthened days still end where they end.
        val dayEndMs = (cursor.clone() as Calendar)
            .apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis - 1
        val beginner = beginnerAt(dayEndMs, modeEvents)

        var brokeStreak = false
        val lastRest = lastRestIndex
        val outcome: DayOutcome? = when {
            // Beginner days never touch the streak — they only record themselves
            beginner -> if (hourTs.size >= BEGINNER_HOURS_REQUIRED)
                DayOutcome.BEGINNER_COMPLETE else null
            hourTs.size >= STREAK_HOURS_REQUIRED -> {
                streak += 1
                DayOutcome.QUALIFIED
            }
            isToday -> null                       // in progress, no verdict yet
            streak == 0 -> DayOutcome.MISSED      // nothing to forgive
            lastRest == null || dayIndex - lastRest >= FORGIVE_WINDOW_DAYS -> {
                lastRestIndex = dayIndex
                DayOutcome.REST
            }
            else -> {
                streak = 0
                brokeStreak = true
                DayOutcome.MISSED
            }
        }

        days.add(
            ReplayDay(
                key = key,
                dayStartMs = cursor.timeInMillis,
                hourTimestamps = hourTs,
                reclaimedHours = reclaimedByDay[key]?.size ?: 0,
                spends = spendsByDay[key]?.sorted() ?: emptyList(),
                outcome = outcome,
                streakAfter = streak,
                brokeStreak = brokeStreak,
                beginner = beginner,
                isToday = isToday
            )
        )

        if (isToday) break
        cursor.add(Calendar.DAY_OF_YEAR, 1)
        dayIndex += 1
    }
    return days
}

/** Current streak plus today's progress, read off the shared replay. */
fun computeStreakState(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long> = emptyList(),
    modeEvents: List<ModeEvent> = emptyList()
): StreakState {
    val days = replayDays(ratings, reclaimSpends, modeEvents)
    val today = days.lastOrNull()?.takeIf { it.isToday }
    val todayHours = today?.hourTimestamps?.size ?: 0
    // Today's mode comes from the events, not the replay: with no ratings at
    // all the replay is empty, and a brand-new install is exactly that case.
    val beginner = isBeginnerMode(modeEvents)
    val required = hoursRequiredFor(beginner)
    return StreakState(
        currentStreak = days.lastOrNull()?.streakAfter ?: 0,
        todayHours = todayHours,
        todayQualified = todayHours >= required,
        extraToday = maxOf(0, todayHours - required),
        beginnerMode = beginner,
        hoursRequired = required,
        beginnerDaysCompleted = days.count { it.outcome == DayOutcome.BEGINNER_COMPLETE }
    )
}

// ============================================================
// DAY OUTCOMES  (calendar: tinted dot built / rest icon / hollow ring missed)
// ============================================================

/**
 * Per-day outcome for every day from the first rating through yesterday.
 * Today appears only once it has qualified. Key = year * 1000 + dayOfYear.
 */
fun computeDayOutcomes(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long> = emptyList(),
    modeEvents: List<ModeEvent> = emptyList()
): Map<Long, DayOutcome> =
    replayDays(ratings, reclaimSpends, modeEvents)
        .mapNotNull { day -> day.outcome?.let { day.key to it } }
        .toMap()

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
    val context = LocalContext.current
    val goal = state.hoursRequired
    val filled = state.todayHours.coerceAtMost(goal)
    val animatedFill by animateFloatAsState(
        targetValue = filled.toFloat(),
        animationSpec = tween(700),
        label = "streakFill"
    )
    // Hours beyond the goal grow the pointy overflow head past 12 o'clock
    val extraHours = (state.todayHours - goal).coerceAtLeast(0)
    val animatedExtra by animateFloatAsState(
        targetValue = extraHours.toFloat(),
        animationSpec = tween(700),
        label = "extraFill"
    )
    // Long double throb + a small buzz for EVERY rated hour, incl. beyond 8
    val ringScale = remember { Animatable(1f) }
    var lastHours by remember { mutableIntStateOf(state.todayHours) }
    LaunchedEffect(state.todayHours) {
        if (state.todayHours > lastHours) {
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createWaveform(
                        longArrayOf(0, 35, 110, 70), intArrayOf(0, 120, 0, 200), -1
                    )
                )
            }
            repeat(2) {
                ringScale.animateTo(1.09f, tween(260, easing = FastOutSlowInEasing))
                ringScale.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            }
            ringScale.animateTo(1.05f, tween(200, easing = FastOutSlowInEasing))
            ringScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
        lastHours = state.todayHours
    }
    val accent = Color(0xFFFFE082) // pastel honey yellow
    val done = Color(0xFF66BB6A)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = if (state.todayQualified) done else accent

    // The ring shows TODAY's hours, so the count inside is hours; the hive
    // (one cell built per qualifying day) lives outside the ring.
    val streakTitle = when {
        state.streakPaused -> "Streak paused at ${state.currentStreak}"
        state.beginnerMode -> "Beginner mode"
        state.currentStreak > 0 -> "${state.currentStreak}-day streak"
        else -> "No streak yet"
    }
    val remaining = (goal - state.todayHours).coerceAtLeast(0)
    val statusText = when {
        state.todayQualified && state.beginnerMode -> "Beginner day complete ✓"
        state.todayQualified -> "Today is secured ✓"
        state.beginnerMode -> "$remaining more hour${if (remaining == 1) "" else "s"} to complete today"
        else -> "$remaining more hour${if (remaining == 1) "" else "s"} to secure today"
    }
    // A paused streak has to say it is paused, or beginner mode reads as a loss
    val pausedNote = if (state.streakPaused) "Resumes in Master mode" else null

    val card = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Card(modifier = modifier.then(card), shape = RoundedCornerShape(20.dp)) {
        // Two fixed layouts with an animated size change between them: nothing
        // is re-measured per frame, so the grow/shrink stays smooth to the end
        // (the old per-frame size/font lerp dropped frames as it settled).
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 130)) togetherWith fadeOut(tween(130)))
                    .using(SizeTransform(clip = false) { _, _ ->
                        tween(500, easing = FastOutSlowInEasing)
                    })
            },
            label = "meterLayout",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) { isExpanded ->
            if (isExpanded) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    StreakRing(
                        animatedFill = animatedFill,
                        animatedExtra = animatedExtra,
                        ringColor = ringColor,
                        trackColor = track,
                        hoursRequired = goal,
                        ringSize = 240.dp,
                        strokeWidth = 30.dp,
                        scale = ringScale.value
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${state.todayHours}/$goal",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "hours today",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(streakTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        statusText,
                        fontSize = 13.sp,
                        color = if (state.todayQualified) done
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    pausedNote?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    StreakRing(
                        animatedFill = animatedFill,
                        animatedExtra = animatedExtra,
                        ringColor = ringColor,
                        trackColor = track,
                        hoursRequired = goal,
                        ringSize = 112.dp,
                        strokeWidth = 15.dp,
                        scale = ringScale.value
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${state.todayHours}/$goal",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "hours",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(streakTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            statusText,
                            fontSize = 12.sp,
                            color = if (state.todayQualified) done
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        pausedNote?.let {
                            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The hour ring: one segment per hour the active mode asks for (8 in Master,
 * 4 in beginner mode), flat start edge at 12 o'clock, convex leading tip while
 * filling. Once closed it becomes one seamless circle, and hours beyond the
 * goal grow a pointy head past 12 o'clock (toward 1 o'clock), its color
 * darkening toward the tip.
 */
@Composable
private fun StreakRing(
    animatedFill: Float,
    animatedExtra: Float,
    ringColor: Color,
    trackColor: Color,
    hoursRequired: Int,
    ringSize: Dp,
    strokeWidth: Dp,
    scale: Float,
    centerContent: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(ringSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val gapDeg = 6f
            val slotDeg = 360f / hoursRequired
            val segSweep = slotDeg - gapDeg
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val a0 = -90f + gapDeg / 2
            val ringRadius = (size.minDimension - stroke) / 2f
            val complete = animatedFill >= hoursRequired - 0.001f

            // Track: all segments drawn FIRST so the fill always sits on top
            for (i in 0 until hoursRequired) {
                drawArc(
                    color = trackColor,
                    startAngle = a0 + i * slotDeg, sweepAngle = segSweep, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            if (complete) {
                // Closed ring: one seamless circle, no seam at 12 o'clock
                drawCircle(
                    color = ringColor,
                    radius = ringRadius,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = stroke)
                )

                // Overflow head: each hour beyond 8 pushes a tapering point
                // further past 12 o'clock, darkening toward the tip
                val headSweep = (animatedExtra * 4f).coerceAtMost(32f)
                if (headSweep > 0.1f) {
                    val tipColor = Color(0xFF1B5E20)
                    val steps = 20
                    val stepSweep = headSweep / steps
                    for (s in 0 until steps) {
                        val t = s / steps.toFloat()
                        drawArc(
                            color = lerpColor(ringColor, tipColor, t),
                            startAngle = -90f + s * stepSweep - 0.2f,
                            sweepAngle = stepSweep + 0.4f,
                            useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke * (1f - 0.82f * t), cap = StrokeCap.Butt)
                        )
                    }
                    // round off the very tip
                    val tipRad = Math.toRadians((-90f + headSweep).toDouble())
                    drawCircle(
                        color = tipColor,
                        radius = stroke * 0.09f,
                        center = Offset(
                            size.width / 2f + (ringRadius * cos(tipRad)).toFloat(),
                            size.height / 2f + (ringRadius * sin(tipRad)).toFloat()
                        )
                    )
                }
            } else {
                // Filling: ONE merged arc over the track. Butt cap keeps the
                // 12-o'clock starting edge a straight line; a half-disc at the
                // leading end makes it convex, pointing in the fill direction.
                val whole = animatedFill.toInt().coerceIn(0, hoursRequired)
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
                    val tipRad = Math.toRadians((a0 + fillSweep).toDouble())
                    drawCircle(
                        color = ringColor,
                        radius = stroke / 2f,
                        center = Offset(
                            size.width / 2f + (ringRadius * cos(tipRad)).toFloat(),
                            size.height / 2f + (ringRadius * sin(tipRad)).toFloat()
                        )
                    )
                }
            }
        }
        centerContent()
    }
}

// ============================================================
// STREAK LOG  (exhaustive, timestamped event history)
// ============================================================

enum class StreakEventType { COMPLETED, BEGINNER, REST, RESET, RECLAIMED }

data class StreakEvent(
    val timestamp: Long,
    val type: StreakEventType,
    val title: String,
    val detail: String
)

/** The log row's leading icon. Titles no longer repeat it as an emoji. */
private fun StreakEventType.sym(): Sym = when (this) {
    StreakEventType.COMPLETED -> Sym.Streak
    StreakEventType.BEGINNER -> Sym.Beginner
    StreakEventType.REST -> Sym.Rest
    StreakEventType.RESET -> Sym.Reset
    StreakEventType.RECLAIMED -> Sym.Reclaim
}

/**
 * Timestamped log of the four things that happen to a hive: a day completed,
 * a rest day taken, the streak reset, a bee sent back. Newest-first.
 */
fun computeStreakLog(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long> = emptyList(),
    modeEvents: List<ModeEvent> = emptyList()
): List<StreakEvent> {
    val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
    val events = ArrayList<StreakEvent>()

    for (day in replayDays(ratings, reclaimSpends, modeEvents)) {
        val dayLabel = dateFmt.format(Date(day.dayStartMs))
        val endOfDay = day.dayStartMs + 23L * 3600000L + 59L * 60000L

        when (day.outcome) {
            // the 8th distinct hour is the moment the cell closed
            DayOutcome.QUALIFIED -> events.add(
                StreakEvent(
                    day.hourTimestamps[STREAK_HOURS_REQUIRED - 1],
                    StreakEventType.COMPLETED,
                    if (day.streakAfter == 1) "Streak started" else "Day complete",
                    "Day ${day.streakAfter} of your streak — $STREAK_HOURS_REQUIRED hours on $dayLabel"
                )
            )
            // the 4th distinct hour closes a beginner day; the streak is untouched
            DayOutcome.BEGINNER_COMPLETE -> events.add(
                StreakEvent(
                    day.hourTimestamps[BEGINNER_HOURS_REQUIRED - 1],
                    StreakEventType.BEGINNER,
                    "Beginner day complete",
                    "$BEGINNER_HOURS_REQUIRED hours on $dayLabel" +
                            if (day.streakAfter > 0) " — streak paused at ${day.streakAfter}" else ""
                )
            )
            DayOutcome.REST -> events.add(
                StreakEvent(
                    endOfDay, StreakEventType.REST, "Rest day",
                    "$dayLabel came up short — the streak held anyway"
                )
            )
            // a miss with no streak running is not an event, only a reset is
            DayOutcome.MISSED -> if (day.brokeStreak) events.add(
                StreakEvent(
                    endOfDay, StreakEventType.RESET, "Streak reset",
                    "$dayLabel came up short, and a rest day was already used this week"
                )
            )
            null -> {} // today in progress, or a beginner day under the goal
        }

        day.spends.forEach { ts ->
            events.add(
                StreakEvent(
                    ts, StreakEventType.RECLAIMED, "Bee sent back",
                    "Revisited an hour from memory"
                )
            )
        }
    }

    return events.sortedByDescending { it.timestamp }
}

@Composable
fun StreakLogContent(
    ratings: List<RatingEntry>,
    modeEvents: List<ModeEvent>,
    refreshKey: Int = 0
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val events = remember(ratings, modeEvents, refreshKey) {
        // Only the last 3 months of history
        val cutoff = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis
        computeStreakLog(ratings, loadReclaimSpends(context), modeEvents)
            .filter { it.timestamp >= cutoff }
    }
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    val goal = hoursRequiredFor(isBeginnerMode(modeEvents))

    if (events.isEmpty()) {
        Text("Nothing in the last 3 months — rate $goal hours in a day to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column {
            events.take(60).forEach { ev ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    BeeIcon(
                        ev.type.sym(), size = 20.dp,
                        modifier = Modifier.padding(end = 12.dp, top = 2.dp)
                    )
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
