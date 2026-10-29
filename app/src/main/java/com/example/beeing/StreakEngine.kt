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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.SaverSlots
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.theme.LocalBeeExtras
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
private const val SUPER_INVITE_SHOWN_KEY = "super_invite_shown_day"

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

/** The Super day invite is once a day: the start millis of the day it last showed on, or -1. */
fun superInviteShownDay(context: android.content.Context): Long =
    context.getSharedPreferences("b", 0).getLong(SUPER_INVITE_SHOWN_KEY, -1L)

fun markSuperInviteShown(context: android.content.Context, dayStartMs: Long) {
    context.getSharedPreferences("b", 0).edit().putLong(SUPER_INVITE_SHOWN_KEY, dayStartMs).apply()
}

/** Past hours already added today — free, but capped per day. */
fun reclaimsUsedToday(spends: List<Long>): Int {
    val todayKey = Calendar.getInstance().dayKey()
    val cal = Calendar.getInstance()
    return spends.count { cal.timeInMillis = it; cal.dayKey() == todayKey }
}


// ============================================================
// STREAK METER  (today's hour ring + the streak count, on the Streak tab's brand band)
// ============================================================

@Composable
fun StreakMeter(
    state: StreakState,
    modifier: Modifier = Modifier,
    onStreakClick: () -> Unit = {},
    onGoalClick: () -> Unit = {}
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
    // One honey on the band: the disc, the ring and the shields all share it
    val honey = LocalBeeExtras.current.accentFill
    val done = com.example.beeing.ui.theme.RingQualifiedGreen
    val track = Color.White.copy(alpha = 0.14f)   // drawn on the brand band
    val ringColor = if (state.todayQualified) done else honey

    val atRisk = state.recoveryOpen
    val riskColor = com.example.beeing.ui.theme.StreakAtRiskRed
    val soft = Color.White.copy(alpha = 0.72f)
    val remaining = (goal - state.todayHours).coerceAtLeast(0)
    val toRecover = (RECOVERY_HOURS - state.todayHours).coerceAtLeast(0)
    // Each caption belongs to the object above it, so both stay short
    val todayCaption = when {
        atRisk -> "$toRecover more wins it back"
        state.todayQualified && state.beginnerMode -> "Beginner day done"
        state.todayQualified -> "Day secured"
        else -> "$remaining more to complete"
    }
    val todayCaptionColor = when {
        atRisk -> riskColor
        state.todayQualified -> done
        else -> soft
    }

    // Two equal columns on the band — the streak first (this tab's headline),
    // today beside it — each an object over one caption, split by a hairline.
    // Each whole column is one tap target: streak + savers, and the ring.
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f).bandTapTarget("Streak and Savers", onStreakClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StreakMedallion(state = state, size = MEDALLION_SIZE, riskColor = riskColor)
            Spacer(Modifier.height(CAPTION_GAP))
            Box(
                Modifier
                    .height(CAPTION_HEIGHT)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (atRisk) {
                    val d = state.recoveryDaysLeft
                    Text(
                        "$d ${if (d == 1) "day" else "days"} to win it back",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = riskColor
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SaverSlots(
                            state.savers, fullColor = honey,
                            emptyColor = Color.White.copy(alpha = 0.20f),
                            iconSize = 15.dp, spacing = 3.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${state.savers}/$SAVER_CAP",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = soft
                        )
                    }
                }
            }
        }
        // Hairline split, inset so it never touches the band's edges
        Box(
            Modifier
                .padding(vertical = 12.dp)
                .width(0.5.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.14f))
        )
        Column(
            Modifier.weight(1f).bandTapTarget("Today's goal", onGoalClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StreakRing(
                animatedFill = animatedFill,
                animatedExtra = animatedExtra,
                ringColor = ringColor,
                trackColor = track,
                hoursRequired = goal,
                ringSize = MEDALLION_SIZE,
                strokeWidth = 11.dp,
                scale = ringScale.value
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${state.todayHours}",
                        fontSize = COUNT_SIZE, lineHeight = COUNT_SIZE,
                        fontWeight = FontWeight.ExtraBold, color = Color.White
                    )
                    Text(
                        "of $goal hours",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = soft
                    )
                }
            }
            Spacer(Modifier.height(CAPTION_GAP))
            Box(Modifier.height(CAPTION_HEIGHT), contentAlignment = Alignment.Center) {
                Text(
                    todayCaption,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = todayCaptionColor
                )
            }
        }
    }
}

/** A band column as one pressable area, rounded so its ripple reads as a tile. */
private fun Modifier.bandTapTarget(label: String, onClick: () -> Unit) =
    clip(RoundedCornerShape(20.dp))
        .clickable(onClickLabel = label, onClick = onClick)
        .padding(vertical = 6.dp)

private val MEDALLION_SIZE = 112.dp
private val COUNT_SIZE = 46.sp
private val CAPTION_GAP = 10.dp
private val CAPTION_HEIGHT = 28.dp

/**
 * The streak's own object, the same size as the hour ring beside it: a solid honey disc
 * with the flame over the count. Paused / beginner swap the flame for the leaf,
 * an open recovery window turns it red with a "!", and no streak dims it.
 */
@Composable
private fun StreakMedallion(state: StreakState, size: Dp, riskColor: Color) {
    val honey = LocalBeeExtras.current.accentFill
    val atRisk = state.recoveryOpen
    val leaf = !atRisk && (state.streakPaused || state.beginnerMode)
    val count = when {
        atRisk -> state.displayStreak
        state.streakPaused -> state.currentStreak
        state.beginnerMode -> state.beginnerDaysCompleted
        else -> state.currentStreak
    }
    val label = when {
        atRisk -> "at risk"
        state.streakPaused -> "paused"
        state.beginnerMode -> if (count == 1) "beginner day" else "beginner days"
        else -> "day streak"
    }
    val live = count > 0
    // A solid disc beside the open ring: the two weigh the same without the disc
    // reading as a second progress ring. No streak = a faint white disc.
    val fill = when {
        atRisk -> riskColor
        live -> honey
        else -> Color.White.copy(alpha = 0.10f)
    }
    val ink = when {
        atRisk -> Color.White
        live -> LocalBeeExtras.current.onAccentFill
        else -> Color.White.copy(alpha = 0.55f)
    }
    val description = when {
        atRisk -> "$count-day streak at risk"
        state.streakPaused -> "Streak paused at $count"
        state.beginnerMode -> "$count $label"
        live -> "$count-day streak"
        else -> "No streak yet"
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Flame beside the count, not above it, so the count gets the height.
            // Steps down with length so "123!" still fits inside the disc.
            val countText = if (atRisk) "$count!" else "$count"
            val countSize = when (countText.length) {
                1, 2 -> COUNT_SIZE   // the same size as the ring's hour count
                3 -> 38.sp
                else -> 30.sp
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BeeIcon(
                    if (leaf) Sym.Beginner else Sym.Fire,
                    size = 28.dp, tint = ink, contentDescription = null
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    countText,
                    fontSize = countSize, lineHeight = countSize, fontWeight = FontWeight.ExtraBold,
                    color = ink
                )
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = ink.alpha * 0.8f))
        }
    }
}

/**
 * The hour ring: one continuous arc that fills toward the goal (8 hours in Master,
 * 4 in beginner mode), with a thin tick at each hour so the count is still legible
 * without chunky segments. Once closed it becomes a solid circle, and hours beyond
 * the goal grow a pointy head past 12 o'clock, its color darkening toward the tip.
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
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val ringRadius = (size.minDimension - stroke) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val fraction = (animatedFill / hoursRequired).coerceIn(0f, 1f)
            val complete = animatedFill >= hoursRequired - 0.001f

            drawCircle(color = trackColor, radius = ringRadius, center = c, style = Stroke(width = stroke))

            if (complete) {
                drawCircle(color = ringColor, radius = ringRadius, center = c, style = Stroke(width = stroke))

                // Overflow head: each hour beyond the goal pushes a tapering point
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
                    val tipRad = Math.toRadians((-90f + headSweep).toDouble())
                    drawCircle(
                        color = tipColor,
                        radius = stroke * 0.09f,
                        center = Offset(
                            c.x + (ringRadius * cos(tipRad)).toFloat(),
                            c.y + (ringRadius * sin(tipRad)).toFloat()
                        )
                    )
                }
            } else if (fraction > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            // One hairline tick per hour, in the band's own dark, across the stroke
            val tickColor = Color(0xFF1C0920).copy(alpha = 0.55f)
            val half = stroke / 2f + 0.5f
            for (i in 0 until hoursRequired) {
                val a = Math.toRadians((-90f + i * 360f / hoursRequired).toDouble())
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                drawLine(
                    color = tickColor,
                    start = Offset(c.x + dx * (ringRadius - half), c.y + dy * (ringRadius - half)),
                    end = Offset(c.x + dx * (ringRadius + half), c.y + dy * (ringRadius + half)),
                    strokeWidth = 2.dp.toPx()
                )
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
    StreakEventType.LOW_SAVERS, StreakEventType.COMEBACK_GIFT -> Sym.Saver
    StreakEventType.STREAK_BROKE -> Sym.Reset
    StreakEventType.RECOVERY_DAYS_LEFT, StreakEventType.RECOVERED -> Sym.Fire
    StreakEventType.PAST_HOUR_ADDED -> Sym.FromMemory
    StreakEventType.SUPER_DAY -> Sym.Sparkle
}

internal fun StreakEvent.logTitle(): String = when (type) {
    StreakEventType.COMPLETED -> if (streak == 1) "Streak started" else "Day complete"
    StreakEventType.BEGINNER -> "Beginner day complete"
    StreakEventType.SAVER_EARNED -> "Streak saver earned"
    StreakEventType.PAST_HOUR_ADDED -> "Past hour added"
    StreakEventType.SUPER_DAY -> "Super day"
    else -> warningTitle()
}

internal fun StreakEvent.logDetail(): String {
    val day = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(dayStartMs))
    return when (type) {
        StreakEventType.COMPLETED -> "Day $streak of your streak — $STREAK_HOURS_REQUIRED hours on $day"
        StreakEventType.BEGINNER -> "$BEGINNER_HOURS_REQUIRED hours on $day" +
            if (streak > 0) " — streak paused at $streak" else ""
        StreakEventType.SAVER_EARNED -> "$SAVER_EARN_HOURS hours on $day — $savers of $SAVER_CAP saved up"
        StreakEventType.PAST_HOUR_ADDED -> "Added an hour from earlier"
        StreakEventType.SUPER_DAY -> "$SUPER_DAY_HOURS hours on $day"
        else -> warningDetail(day)
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
