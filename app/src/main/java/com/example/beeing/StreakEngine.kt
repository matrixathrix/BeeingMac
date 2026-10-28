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

/** Past hours already added today — free, but capped per day. */
fun reclaimsUsedToday(spends: List<Long>): Int {
    val todayKey = Calendar.getInstance().dayKey()
    val cal = Calendar.getInstance()
    return spends.count { cal.timeInMillis = it; cal.dayKey() == todayKey }
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
    val track = Color.White.copy(alpha = 0.18f)   // drawn on the brand band
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

    // No card: the meter sits directly on the Streak tab's brand band.
    Box(modifier = modifier.then(card)) {
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
                .padding(vertical = 4.dp)
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
                                color = Color.White
                            )
                            Text(
                                "hours today",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(streakTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        statusText,
                        fontSize = 13.sp,
                        color = if (state.todayQualified) done
                        else Color.White.copy(alpha = 0.75f)
                    )
                    pausedNote?.let {
                        Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
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
                                color = Color.White
                            )
                            Text(
                                "hours",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(streakTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            statusText,
                            fontSize = 12.sp,
                            color = if (state.todayQualified) done
                            else Color.White.copy(alpha = 0.75f)
                        )
                        pausedNote?.let {
                            Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
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
// STREAK LOG  (timestamped event history; the bell shows a filtered view)
// ============================================================

/** The log row's leading icon. Titles no longer repeat it as an emoji. */
internal fun StreakEventType.sym(): Sym = when (this) {
    StreakEventType.COMPLETED -> Sym.Streak
    StreakEventType.BEGINNER -> Sym.Beginner
    StreakEventType.SAVER_EARNED, StreakEventType.SAVER_USED, StreakEventType.SAVER_FADED,
    StreakEventType.LOW_SAVERS, StreakEventType.COMEBACK_GIFT -> Sym.Star
    StreakEventType.STREAK_BROKE -> Sym.Reset
    StreakEventType.RECOVERY_DAYS_LEFT, StreakEventType.RECOVERED -> Sym.Fire
    StreakEventType.PAST_HOUR_ADDED -> Sym.FromMemory
}

private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) one else many

internal fun StreakEvent.logTitle(): String = when (type) {
    StreakEventType.COMPLETED -> if (streak == 1) "Streak started" else "Day complete"
    StreakEventType.BEGINNER -> "Beginner day complete"
    StreakEventType.SAVER_EARNED -> "Streak saver earned"
    StreakEventType.SAVER_USED -> "A streak saver kept your streak alive"
    StreakEventType.SAVER_FADED -> "A streak saver faded"
    StreakEventType.LOW_SAVERS -> "Only $savers ${plural(savers, "saver")} left"
    StreakEventType.COMEBACK_GIFT -> "$COMEBACK_GIFT streak savers on the house"
    StreakEventType.STREAK_BROKE -> "Your $streak-day streak broke"
    StreakEventType.RECOVERY_DAYS_LEFT ->
        "Only $daysLeft ${plural(daysLeft, "day")} left to win back your $streak-day streak"
    StreakEventType.RECOVERED -> "Streak recovered"
    StreakEventType.PAST_HOUR_ADDED -> "Past hour added"
}

internal fun StreakEvent.logDetail(): String {
    val day = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(dayStartMs))
    return when (type) {
        StreakEventType.COMPLETED -> "Day $streak of your streak — $STREAK_HOURS_REQUIRED hours on $day"
        StreakEventType.BEGINNER -> "$BEGINNER_HOURS_REQUIRED hours on $day" +
            if (streak > 0) " — streak paused at $streak" else ""
        StreakEventType.SAVER_EARNED -> "$SAVER_EARN_HOURS hours on $day — $savers of $SAVER_CAP saved up"
        StreakEventType.SAVER_USED -> "You missed $day. Your $streak-day streak is safe. $savers ${plural(savers, "saver")} left."
        StreakEventType.SAVER_FADED -> "No streak was running on $day." +
            if (savers == 0) " That was your last saver." else " Start one today to keep the $savers you have left."
        StreakEventType.LOW_SAVERS -> "Rate $SAVER_EARN_HOURS hours in one day to earn another."
        StreakEventType.COMEBACK_GIFT -> "Rate a few hours today to start a new streak."
        StreakEventType.STREAK_BROKE -> "Rate $RECOVERY_HOURS hours on one of the next $RECOVERY_DAYS days to win it back."
        StreakEventType.RECOVERY_DAYS_LEFT -> "Rate $RECOVERY_HOURS hours on one of them."
        StreakEventType.RECOVERED -> "Your $streak-day streak is back."
        StreakEventType.PAST_HOUR_ADDED -> "Added an hour from earlier"
    }
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
        computeStreakEvents(ratings, loadReclaimSpends(context), modeEvents)
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
                        Text(ev.logTitle(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(ev.logDetail(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
