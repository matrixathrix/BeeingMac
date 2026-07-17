package com.example.beeing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
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
const val HOURS_PER_SAVER = 10        // banked extra hours per streak saver
const val MAX_SAVERS = 5              // max savers a user can hold
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
// DAY DETAILS  (calendar view: 💐 qualified / 🛡️ saved / 🥀 missed,
// plus the exact streak-day number and flower/saver activity for that day)
// ============================================================

enum class DayOutcome { QUALIFIED, SAVED, MISSED }

data class DayDetail(
    val outcome: DayOutcome,
    val streakDay: Int = 0,     // this day's position in the current streak (QUALIFIED only)
    val flowersEarned: Int = 0, // flowers actually banked this day, after the cap
    val saversEarned: Int = 0,  // savers forged this day when the bank crossed a threshold
    val saverUsed: Boolean = false, // a saver was spent to cover this missed day
    val dayMillis: Long = 0L,
    val hoursRated: Int = 0,
    val flowersSpent: Int = 0,  // flowers spent reclaiming a missed hour this day
    val saversAfter: Int = 0,   // running saver count once this day is accounted for
    val wasReset: Boolean = false // a MISSED day that actually broke an active streak
)

/**
 * Per-day outcome (+ streak-day number, flower/saver activity) for every day
 * from the first rating through today, replaying the exact same rules as
 * computeStreakState so the calendar can never disagree with the meter.
 * Today appears only once it has qualified.
 * Key = year * 1000 + dayOfYear.
 */
fun computeDayDetails(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long> = emptyList()
): Map<Long, DayDetail> {
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

    val details = HashMap<Long, DayDetail>()
    var streak = 0
    var savers = GIFTED_SAVERS
    var bank = 0

    while (true) {
        val key = cursor.dayKey()
        val hours = hoursByDay[key]?.size ?: 0
        val spentFlowers = RECLAIM_COST * (spendsByDay[key] ?: 0)

        if (hours >= STREAK_HOURS_REQUIRED) {
            streak += 1
            val reclaimed = reclaimedByDay[key]?.size ?: 0
            val bankBefore = bank
            bank = (bank + (hours - STREAK_HOURS_REQUIRED - reclaimed).coerceAtLeast(0)).coerceAtMost(FLOWER_CAP)
            val flowersEarned = bank - bankBefore
            var saversEarned = 0
            while (bank >= HOURS_PER_SAVER && savers < MAX_SAVERS) {
                bank -= HOURS_PER_SAVER
                savers += 1
                saversEarned += 1
            }
            details[key] = DayDetail(
                DayOutcome.QUALIFIED, streak, flowersEarned, saversEarned,
                dayMillis = cursor.timeInMillis, hoursRated = hours,
                flowersSpent = spentFlowers, saversAfter = savers
            )
        } else if (key != todayKey) {
            // Only running out of savers actually breaks the streak — a saved
            // day survives it, so `streak` must keep counting through those.
            val hadActiveStreak = streak > 0
            if (savers > 0) {
                savers -= 1
                details[key] = DayDetail(
                    DayOutcome.SAVED, saverUsed = true,
                    dayMillis = cursor.timeInMillis, hoursRated = hours,
                    flowersSpent = spentFlowers, saversAfter = savers
                )
            } else {
                bank = 0
                details[key] = DayDetail(
                    DayOutcome.MISSED,
                    dayMillis = cursor.timeInMillis, hoursRated = hours,
                    flowersSpent = spentFlowers, saversAfter = savers,
                    wasReset = hadActiveStreak
                )
                streak = 0
            }
        }

        bank -= spentFlowers
        if (bank < 0) bank = 0

        if (key == todayKey) break
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }
    return details
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
    val filled = state.todayHours.coerceAtMost(STREAK_HOURS_REQUIRED)
    val animatedFill by animateFloatAsState(
        targetValue = filled.toFloat(),
        animationSpec = tween(700),
        label = "streakFill"
    )
    // Hours beyond 8 grow the pointy overflow head past 12 o'clock
    val extraHours = (state.todayHours - STREAK_HOURS_REQUIRED).coerceAtLeast(0)
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
    val track = MaterialTheme.colorScheme.surfaceVariant
    // Same red/amber/green bands as everywhere else — scaled to today's
    // filled hours (0..8) rather than a 1-10 score. Once complete (>=8) the
    // ring is green, which is also the base the overflow head darkens from.
    val ringColor = when {
        state.todayHours >= STREAK_HOURS_REQUIRED -> Color(0xFF66BB6A)
        state.todayHours >= 4 -> Color(0xFFFFB300)
        else -> Color(0xFFB71C1C)
    }

    val remaining = (STREAK_HOURS_REQUIRED - state.todayHours).coerceAtLeast(0)
    // The streak's own accent — deliberately not the red/amber/green hour
    // bands (that's a status color), so it reads as this card's second
    // "hero" stat rather than a variant of the ring's meaning. A fixed warm
    // amber (echoing the 🔥) rather than the theme's dynamic primary, which
    // can land muted/desaturated in dark mode depending on device wallpaper.
    val streakColor = when {
        state.currentStreak <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        isSystemInDarkTheme() -> Color(0xFFFFB74D)
        else -> Color(0xFFEF6C00)
    }

    val cardShape = RoundedCornerShape(20.dp)
    fun Modifier.tappable(onTap: (() -> Unit)?) = this
        .clip(cardShape)
        .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)

    // Which twin card's pointer-tip is showing (0 = none, 1 = hours, 2 = streak)
    var activeTip by remember { mutableIntStateOf(0) }
    val hoursTip = when {
        state.todayQualified -> "Streak extended today 🎉"
        state.currentStreak > 0 ->
            "Rate $remaining more hour${if (remaining == 1) "" else "s"} to extend streak today"
        else ->
            "Rate $remaining more hour${if (remaining == 1) "" else "s"} today to start a streak"
    }

    // Two states, one motion: while hours are still open, today's ring and
    // the streak sit as twin equal cards; once both rateable hours are done
    // the pair merges into a single full-width hero card that carries the
    // "streak extended" moment. SizeTransform morphs the container while the
    // content slides gently, so the merge/split reads as one sliding motion.
    AnimatedContent(
        targetState = expanded,
        transitionSpec = {
            ((fadeIn(tween(300, delayMillis = 120)) +
                    slideInVertically(tween(300, delayMillis = 120)) { it / 8 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 8 }))
                .using(SizeTransform(clip = false) { _, _ ->
                    tween(500, easing = FastOutSlowInEasing)
                })
        },
        label = "meterLayout",
        modifier = modifier.fillMaxWidth()
    ) { isExpanded ->
        if (isExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .tappable(onClick),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = appCardColor())
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        StreakRing(
                            animatedFill = animatedFill,
                            animatedExtra = animatedExtra,
                            ringColor = ringColor,
                            trackColor = track,
                            ringSize = 150.dp,
                            strokeWidth = 18.dp,
                            scale = ringScale.value
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "${state.todayHours}/$STREAK_HOURS_REQUIRED",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "hours rated",
                                    fontSize = 12.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "today",
                                    fontSize = 12.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (state.currentStreak > 0) "🔥" else "🌱", fontSize = 30.sp)
                            Text(
                                "${state.currentStreak}",
                                fontSize = 46.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = streakColor
                            )
                            Text(
                                "day streak",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .tappable { activeTip = 1 },
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = appCardColor())
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        StreakRing(
                            animatedFill = animatedFill,
                            animatedExtra = animatedExtra,
                            ringColor = ringColor,
                            trackColor = track,
                            ringSize = 116.dp,
                            strokeWidth = 14.dp,
                            scale = ringScale.value
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "${state.todayHours}/$STREAK_HOURS_REQUIRED",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "hours rated",
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "today",
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        TapTip(
                            text = hoursTip,
                            visible = activeTip == 1,
                            onDismiss = { activeTip = 0 }
                        )
                    }
                }
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .tappable { activeTip = 2 },
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = appCardColor())
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(if (state.currentStreak > 0) "🔥" else "🌱", fontSize = 26.sp)
                        Text(
                            "${state.currentStreak}",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = streakColor
                        )
                        Text(
                            "day streak",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TapTip(
                            text = "You can check Streak logs in the Streak tab",
                            visible = activeTip == 2,
                            onDismiss = { activeTip = 0 }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The 8-segment hour ring: flat start edge at 12 o'clock, convex leading tip
 * while filling. Once closed it becomes one seamless circle, and hours beyond
 * 8 grow a pointy head past 12 o'clock (toward 1 o'clock), its color darkening
 * toward the tip.
 */
@Composable
private fun StreakRing(
    animatedFill: Float,
    animatedExtra: Float,
    ringColor: Color,
    trackColor: Color,
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
            val slotDeg = 360f / STREAK_HOURS_REQUIRED
            val segSweep = slotDeg - gapDeg
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val a0 = -90f + gapDeg / 2
            val ringRadius = (size.minDimension - stroke) / 2f
            val complete = animatedFill >= STREAK_HOURS_REQUIRED - 0.001f

            // Track: all segments drawn FIRST so the fill always sits on top
            for (i in 0 until STREAK_HOURS_REQUIRED) {
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
// STREAK LOG  (one row per day — outcome callout + at-a-glance stats,
// reusing computeDayDetails so it can never disagree with the calendar)
// ============================================================

@Composable
fun StreakLogContent(ratings: List<RatingEntry>, refreshKey: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val days = remember(ratings, refreshKey) {
        // Only the last 3 months of history
        val cutoff = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis
        computeDayDetails(ratings, loadReclaimSpends(context)).values
            .filter { it.dayMillis >= cutoff }
            .sortedByDescending { it.dayMillis }
    }
    // Collapsible month sections (only the newest starts open, so the log
    // never dumps everything at once); each day inside reads as a compact
    // handful of at-a-glance stat lines instead of a full event diary.
    val byMonth = remember(days) {
        val cal = Calendar.getInstance()
        days.groupBy {
            cal.timeInMillis = it.dayMillis
            cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
        }
    }
    val expandedMonths = remember(days) {
        mutableStateMapOf<Int, Boolean>().apply {
            byMonth.keys.firstOrNull()?.let { put(it, true) }
        }
    }
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()) }

    if (days.isEmpty()) {
        Text("Nothing in the last 3 months — rate 8 hours in a day to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column {
            byMonth.entries.forEach { (monthKey, monthDays) ->
                val monthOpen = expandedMonths[monthKey] == true
                val chevron by animateFloatAsState(if (monthOpen) 180f else 0f, label = "logMonthChevron")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expandedMonths[monthKey] = !monthOpen }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        monthFmt.format(Date(monthDays.first().dayMillis)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${monthDays.size} day${if (monthDays.size == 1) "" else "s"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (monthOpen) "Collapse month" else "Expand month",
                        modifier = Modifier.rotate(chevron)
                    )
                }
                AnimatedVisibility(
                    visible = monthOpen,
                    enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) +
                            fadeIn(tween(220, delayMillis = 80)),
                    exit = shrinkVertically(tween(280, easing = FastOutSlowInEasing)) +
                            fadeOut(tween(120))
                ) {
                    Column {
                        monthDays.forEach { detail ->
                            StreakLogDayRow(detail, dayFmt)
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakLogDayRow(detail: DayDetail, dayFmt: SimpleDateFormat) {
    val (calloutEmoji, calloutText, calloutColor) = when {
        detail.outcome == DayOutcome.QUALIFIED && detail.streakDay == 1 ->
            Triple("🌱", "Streak started", MaterialTheme.colorScheme.primary)
        detail.outcome == DayOutcome.QUALIFIED ->
            Triple("🔥", "Streak day ${detail.streakDay}", MaterialTheme.colorScheme.primary)
        detail.outcome == DayOutcome.SAVED ->
            Triple("🛡️", "Saver used — streak protected", MaterialTheme.colorScheme.onSurfaceVariant)
        detail.wasReset ->
            Triple("💔", "Streak reset", Color(0xFFB71C1C))
        else ->
            Triple("🥀", "Missed", MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dayFmt.format(Date(detail.dayMillis)),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("$calloutEmoji $calloutText", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = calloutColor)
        if (detail.saversEarned > 0) {
            Text(
                "🛡️ New saver forged — now ${detail.saversAfter}/$MAX_SAVERS",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LogStatLine("Hours rated", "${detail.hoursRated}")
        if (detail.flowersEarned > 0 || detail.flowersSpent > 0) {
            LogStatLine("Flowers earned", "+${detail.flowersEarned}", "Spent", "-${detail.flowersSpent}")
        }
        if (detail.saverUsed) {
            LogStatLine("Savers used", "1", "Left", "${detail.saversAfter}/$MAX_SAVERS")
        }
    }
}

/** One or two label/value pairs on a single quiet stat line. */
@Composable
private fun LogStatLine(label1: String, value1: String, label2: String? = null, value2: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Row {
            Text("$label1: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value1, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (label2 != null && value2 != null) {
            Row {
                Text("$label2: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
