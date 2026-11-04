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
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.SaverSlots
import com.example.beeing.ui.drawSuperLap
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.theme.LocalBeeExtras
import com.example.beeing.ui.theme.SuperGold
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ============================================================
// STREAK ENGINE  (pure logic — fully derived from ratings)
// ============================================================


// ============================================================
// RECLAIM SPENDS  (persisted — not derivable from ratings)
// ============================================================

private fun loadMillisList(context: android.content.Context, key: String): List<Long> =
    (context.getSharedPreferences("b", 0).getString(key, "") ?: "")
        .split(",").mapNotNull { it.toLongOrNull() }

fun loadReclaimSpends(context: android.content.Context) = loadMillisList(context, "reclaim_spends")

fun saveReclaimSpends(context: android.content.Context, spends: List<Long>) = synchronized(STORE_LOCK) {
    context.getSharedPreferences("b", 0).edit().putString("reclaim_spends", spends.joinToString(",")).apply()
}

fun recordReclaimSpend(context: android.content.Context) = synchronized(STORE_LOCK) {
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
    publishState(context)
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
// STREAK METER  (today's hour ring + the streak count, on the Streak tab's night field)
// ============================================================

@Composable
fun StreakMeter(
    state: StreakState,
    modifier: Modifier = Modifier,
    onStreakClick: () -> Unit = {},
    onGoalClick: () -> Unit = {},
    /** Hours still to go to a Super day, while 12 is reachable today (Master, goal met). */
    superHoursLeft: Int? = null
) {
    val context = LocalContext.current
    val goal = state.hoursRequired
    val filled = state.todayHours.coerceAtMost(goal)
    // Animated values travel as States and are read only in the ring's draw/layer lambdas
    val animatedFill = animateFloatAsState(
        targetValue = filled.toFloat(),
        animationSpec = tween(700),
        label = "streakFill"
    )
    // Hours beyond the goal fill the Super day lap (Master) or grow the pointy
    // overflow head past 12 o'clock (beginner)
    val extraHours = (state.todayHours - goal).coerceAtLeast(0)
    val animatedExtra = animateFloatAsState(
        targetValue = extraHours.toFloat(),
        animationSpec = tween(700),
        label = "extraFill"
    )
    // The Super day lap: Master mode only, there from the moment the ring closes
    val superLap = !state.beginnerMode
    val lapShown = animateFloatAsState(
        targetValue = if (superLap && state.todayQualified) 1f else 0f,
        animationSpec = tween(500),
        label = "superLap"
    )
    val superToday = state.recentDays.lastOrNull()?.let { it.isToday && it.superDay } == true
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
    // One honey on the field: the disc, the ring and the shields all share it
    val x = LocalBeeExtras.current
    val honey = x.accentFill
    val done = com.example.beeing.ui.theme.RingQualifiedGreen
    val track = x.fieldInk.copy(alpha = 0.14f)   // drawn straight on the field
    val ringColor = if (state.todayQualified) done else honey

    val atRisk = state.recoveryOpen
    val riskColor = x.fieldRed
    val soft = x.fieldInkMuted
    val remaining = (goal - state.todayHours).coerceAtLeast(0)
    val toRecover = (RECOVERY_HOURS - state.todayHours).coerceAtLeast(0)
    // Each caption belongs to the object above it, so both stay short
    // An open recovery window outranks the Super day copy
    val todayCaption = when {
        atRisk -> "$toRecover more wins it back"
        superHoursLeft != null -> "$superHoursLeft more for a Super day"
        superToday -> "Super day"
        state.todayQualified && state.beginnerMode -> "Beginner day done"
        state.todayQualified -> "Day secured"
        else -> "$remaining more to complete"
    }
    val todayCaptionColor = when {
        atRisk -> riskColor
        superHoursLeft != null || superToday -> x.fieldSuperText
        state.todayQualified -> x.fieldDone
        else -> soft
    }

    // Two equal columns floating on the field — the streak first (this tab's headline),
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
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = riskColor
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SaverSlots(
                            state.savers, fullColor = honey,
                            emptyColor = x.fieldInk.copy(alpha = 0.20f),
                            iconSize = 15.dp, spacing = 3.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${state.savers}/$SAVER_CAP",
                            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = soft
                        )
                    }
                }
            }
        }
        // Hairline split, inset top and bottom
        Box(
            Modifier
                .padding(vertical = 12.dp)
                .width(0.5.dp)
                .fillMaxHeight()
                .background(x.fieldInk.copy(alpha = 0.12f))
        )
        Column(
            Modifier.weight(1f).bandTapTarget("Today's goal", onGoalClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StreakRing(
                animatedFill = { animatedFill.value },
                animatedExtra = { animatedExtra.value },
                ringColor = ringColor,
                trackColor = track,
                hoursRequired = goal,
                ringSize = MEDALLION_SIZE,
                strokeWidth = 11.dp,
                scale = { ringScale.value },
                superLap = superLap,
                lapShown = { lapShown.value },
                lapColor = x.fieldSuper
            ) {
                MedallionStack(heroSize = COUNT_SIZE, hero = {
                    Text(
                        "${state.todayHours}",
                        fontSize = COUNT_SIZE, lineHeight = COUNT_SIZE,
                        fontWeight = FontWeight.ExtraBold, color = x.fieldInk
                    )
                }) {
                    // SemiBold, like the streak disc's label: the two read as a pair
                    Text(
                        "of $goal hours",
                        fontSize = 11.sp, lineHeight = MedallionLabelLineHeight, fontWeight = FontWeight.SemiBold, color = soft
                    )
                }
            }
            Spacer(Modifier.height(CAPTION_GAP))
            Box(Modifier.height(CAPTION_HEIGHT), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!atRisk && superHoursLeft == null && superToday) {
                        BeeIcon(Sym.Sparkle, size = 14.dp, tint = x.fieldSuperText, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        todayCaption,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = todayCaptionColor
                    )
                }
            }
        }
    }
}

/** A hero column as one pressable area, rounded so its ripple reads as a tile. */
private fun Modifier.bandTapTarget(label: String, onClick: () -> Unit) =
    clip(RoundedCornerShape(20.dp))
        .clickable(onClickLabel = label, onClick = onClick)
        .padding(vertical = 6.dp)

private val MEDALLION_SIZE = 112.dp
private val COUNT_SIZE = 46.sp
/** The medallion labels' line height: tight to the 11sp text (see MedallionStack). */
private val MedallionLabelLineHeight = 13.sp
private val CAPTION_GAP = 10.dp
private val CAPTION_HEIGHT = 28.dp

// 10 of 12: the ring closed, two of the four lap segments gold, the Super day caption.
@Preview(widthDp = 360, backgroundColor = 0xFF4A1D53, showBackground = true)
@Composable
private fun StreakMeterSuperLapPreview() = com.example.beeing.ui.theme.BeeingTheme {
    StreakMeter(
        state = StreakState(
            currentStreak = 6, displayStreak = 6, todayHours = 10, todayQualified = true, extraToday = 2,
            beginnerMode = false, hoursRequired = STREAK_HOURS_REQUIRED, beginnerDaysCompleted = 0, savers = 3,
            recoveryOpen = false, recoveryDaysLeft = 0, todayEarnedSaver = false
        ),
        superHoursLeft = 2
    )
}

/**
 * The streak's own object, the same size as the hour ring beside it: a solid honey disc
 * with the flame over the count and a soft honey glow. Paused / beginner swap the flame
 * for the leaf, an open recovery window turns it red with a "!", and no streak dims it
 * to a faint glass disc.
 */
@Composable
private fun StreakMedallion(state: StreakState, size: Dp, riskColor: Color) {
    val x = LocalBeeExtras.current
    val honey = x.accentFill
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
    // reading as a second progress ring. No streak = a faint glass disc.
    val fill = when {
        atRisk -> riskColor
        live -> honey
        else -> x.fieldInk.copy(alpha = 0.10f)
    }
    val ink = when {
        // The field's red is light in dark mode and deep in light mode: ink flips with it
        atRisk -> if (riskColor.luminance() > 0.4f) x.onAccentFill else Color.White
        live -> x.onAccentFill
        else -> x.fieldInkMuted
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
            .then(
                // The honey glow: the streak is honey's identity on the field
                if (live && !atRisk) Modifier.shadow(18.dp, CircleShape, ambientColor = honey, spotColor = honey)
                else Modifier
            )
            .clip(CircleShape)
            .background(fill)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        // Flame beside the count, not above it, so the count gets the height.
        // Steps down with length so "123!" still fits inside the disc.
        val countText = if (atRisk) "$count!" else "$count"
        val countSize = when (countText.length) {
            1, 2 -> COUNT_SIZE   // the same size as the ring's hour count
            3 -> 38.sp
            else -> 30.sp
        }
        MedallionStack(heroSize = countSize, hero = {
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
        }) {
            Text(label, fontSize = 11.sp, lineHeight = MedallionLabelLineHeight, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = ink.alpha * 0.8f))
        }
    }
}

/**
 * The inside of both medallions (the streak disc and the hour ring), so they match:
 * the big [hero] over its [label], centred by what you SEE, not by text boxes.
 * Instrument Sans digits fill only the top of their 46sp line box, leaving ~9dp
 * and its box starts ~12dp above them; centring the boxes pushed everything low,
 * onto the ring's stroke. So the visible block runs from the digits' top
 * (baseline − cap height) to the label's baseline, the label hangs 4dp under the
 * hero's baseline, and that block is centred in the disc. Labels pass [MedallionLabelLineHeight].
 */
@Composable
private fun MedallionStack(
    /** The hero numeral's font size: its cap height sets where the digits really start. */
    heroSize: TextUnit,
    hero: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val gap = with(density) { 4.dp.roundToPx() }
    // Instrument Sans digits are cap height: 720/1000 em (OS/2 sCapHeight)
    val cap = with(density) { (heroSize.toPx() * 0.72f).toInt() }
    Layout(contents = listOf(hero, label)) { (heroM, labelM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val h = heroM.first().measure(loose)
        val l = labelM.first().measure(loose)
        val w = constraints.maxWidth
        val ht = constraints.maxHeight
        layout(w, ht) {
            fun baseline(p: androidx.compose.ui.layout.Placeable) =
                p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
            // The visible block: the digits' top (baseline − cap height) down to the
            // label's baseline. The hero's box starts ~12dp above its digits (the
            // face's tall ascender), so centring boxes sat everything low.
            val heroBase = baseline(h)
            val seen = cap + gap + baseline(l)
            val inkTop = (ht - seen) / 2
            val heroY = inkTop - (heroBase - cap)
            h.place((w - h.width) / 2, heroY)
            l.place((w - l.width) / 2, heroY + heroBase + gap)
        }
    }
}

/**
 * The hour ring: one continuous arc that fills toward the goal (8 hours in Master,
 * 4 in beginner mode), with a thin tick at each hour so the count is still legible
 * without chunky segments. Once closed it becomes a solid circle. Shared by the
 * Streak hero and the Now tab's caught-up ring.
 *
 * Hours beyond the goal: in Master mode ([superLap]) they fill the Super day lap, four
 * segments just outside the ring. The lap fades in over [lapShown] (0…1) as the ring
 * closes, and the main ring shrinks by the lap's width as it does, so the lap's outer
 * edge sits where the ring's used to and the column's footprint never changes. In
 * beginner mode they grow a pointy head past 12 o'clock, darkening toward the tip.
 *
 * The animated inputs are lambdas, read only in the draw / layer phase.
 */
@Composable
internal fun StreakRing(
    animatedFill: () -> Float,
    animatedExtra: () -> Float,
    ringColor: Color,
    trackColor: Color,
    hoursRequired: Int,
    ringSize: Dp,
    strokeWidth: Dp,
    scale: () -> Float,
    superLap: Boolean,
    lapShown: () -> Float,
    lapColor: Color = SuperGold,
    centerContent: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(ringSize)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fill = animatedFill()
            val extra = animatedExtra()
            val stroke = strokeWidth.toPx()
            val lapStroke = SUPER_LAP_STROKE.toPx()
            val lapGap = SUPER_LAP_GAP.toPx()
            val lap = if (superLap) lapShown().coerceIn(0f, 1f) else 0f
            val c = Offset(size.width / 2f, size.height / 2f)
            val fullRadius = (size.minDimension - stroke) / 2f
            val ringRadius = fullRadius - lap * (lapGap + lapStroke)
            val topLeft = Offset(c.x - ringRadius, c.y - ringRadius)
            val arcSize = Size(ringRadius * 2f, ringRadius * 2f)
            val fraction = (fill / hoursRequired).coerceIn(0f, 1f)
            val complete = fill >= hoursRequired - 0.001f

            drawCircle(color = trackColor, radius = ringRadius, center = c, style = Stroke(width = stroke))

            if (complete) {
                drawCircle(color = ringColor, radius = ringRadius, center = c, style = Stroke(width = stroke))

                // Overflow head (beginner only): each hour beyond the goal pushes a
                // tapering point further past 12 o'clock, darkening toward the tip
                val headSweep = if (superLap) 0f else (extra * 4f).coerceAtMost(32f)
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

            // The Super day lap, hours 9–12, faded in with the shrink (and out again if
            // the ring reopens, so it never pops)
            if (lap > 0.001f) {
                drawSuperLap(
                    center = c,
                    radius = ringRadius + stroke / 2f + lapGap + lapStroke / 2f,
                    stroke = lapStroke,
                    filled = extra.coerceAtMost(4f),
                    track = trackColor.copy(alpha = trackColor.alpha * lap),
                    fill = lapColor.copy(alpha = lap)
                )
            }

            // One hairline tick per hour, in the field's own dark, across the stroke
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

/** The Super day lap's stroke, and its gap off the main ring: together, what the ring gives up. */
private val SUPER_LAP_STROKE = 3.5.dp
private val SUPER_LAP_GAP = 2.5.dp

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

/**
 * The History rows: a Super day and its saver are one moment (same timestamp), so the saver row is
 * folded into the Super day's detail line instead of listing twice. The flag says whether a
 * SAVER_EARNED shares the Super day's [StreakEvent.dayStartMs] (absent at the cap).
 */
internal fun logRows(events: List<StreakEvent>): List<Pair<StreakEvent, Boolean>> {
    val earned = events.filter { it.type == StreakEventType.SAVER_EARNED }.associateBy { it.dayStartMs }
    return events.filter { it.type != StreakEventType.SAVER_EARNED }.map { ev ->
        val saver = if (ev.type == StreakEventType.SUPER_DAY) earned[ev.dayStartMs] else null
        // The Super day is logged just before the saver lands, so its own count is one short.
        if (saver != null) ev.copy(savers = saver.savers) to true else ev to false
    }
}

internal fun StreakEvent.logDetail(earnedSaver: Boolean = false): String {
    val day = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(dayStartMs))
    return when (type) {
        StreakEventType.COMPLETED -> "Day $streak of your streak — $STREAK_HOURS_REQUIRED hours on $day"
        StreakEventType.BEGINNER -> "$BEGINNER_HOURS_REQUIRED hours on $day" +
            if (streak > 0) " — streak paused at $streak" else ""
        StreakEventType.SAVER_EARNED -> "$SAVER_EARN_HOURS hours on $day — $savers of $SAVER_CAP saved up"
        StreakEventType.PAST_HOUR_ADDED -> "Added an hour from earlier"
        StreakEventType.SUPER_DAY -> "$SUPER_DAY_HOURS hours on $day — " +
            if (earnedSaver) "streak saver earned, $savers of $SAVER_CAP saved up"
            else "all $SAVER_CAP savers saved up"
        else -> warningDetail(day)
    }
}

/**
 * History on the Streak tab: a glass panel, collapsed by default to a peek at the
 * latest event (or the empty-state line), so it never reads as empty. The whole
 * panel toggles it. Expanded, it opens in place into rows grouped by recency
 * ("THIS WEEK", "EARLIER IN OCTOBER", then month names): the last 3 months,
 * capped at 60 rows. Each row is a round tile (colour and icon together), the
 * title over its one-line detail, and the date on the right.
 */
@Composable
fun StreakHistoryPanel(
    ratings: List<RatingEntry>,
    modeEvents: List<ModeEvent>,
    refreshKey: Int = 0
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val x = LocalBeeExtras.current
    val events = remember(ratings, modeEvents, refreshKey) {
        // Only the last 3 months of history
        val cutoff = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis
        computeStreakEvents(ratings, loadReclaimSpends(context), modeEvents)
            .filter { it.timestamp >= cutoff }
    }
    val rows = remember(events) { logRows(events) }
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val goal = hoursRequiredFor(isBeginnerMode(modeEvents))
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "historyChevron")

    com.example.beeing.ui.GlassPanel(
        modifier = Modifier.animateContentSize(),
        onClick = { expanded = !expanded },
        onClickLabel = if (expanded) "Collapse history" else "Expand history"
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "History",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = x.fieldInkMuted,
                modifier = Modifier.rotate(chevron)
            )
        }
        val latest = rows.firstOrNull()
        when {
            latest == null -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Nothing in the last 3 months — rate $goal hours in a day to begin.",
                    fontSize = 13.sp, color = x.fieldInkMuted
                )
            }
            !expanded -> {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EventTile(latest.first.type, size = 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = x.fieldInk)) {
                                append(latest.first.logTitle())
                            }
                            append(" · ${dateFmt.format(Date(latest.first.timestamp))}")
                        },
                        fontSize = 13.sp, color = x.fieldInkMuted,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            else -> {
                val now = remember(refreshKey) { Calendar.getInstance() }
                var lastGroup: String? = null
                rows.take(60).forEach { (ev, earnedSaver) ->
                    val group = historyGroup(ev.timestamp, now)
                    if (group != lastGroup) {
                        lastGroup = group
                        Text(
                            group,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp,
                            color = x.fieldInkMuted,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EventTile(ev.type, size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ev.logTitle(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                ev.logDetail(earnedSaver), fontSize = 12.sp, color = x.fieldInkMuted,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(dateFmt.format(Date(ev.timestamp)), fontSize = 11.sp, color = x.fieldInkMuted)
                    }
                }
                if (rows.size > 60) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Showing latest 60 of ${rows.size} events",
                        fontSize = 11.sp,
                        color = x.fieldInkMuted
                    )
                }
            }
        }
    }
}

/**
 * The History group an event falls in, relative to [now]: "THIS WEEK" (since the
 * locale's first day of this week), "EARLIER IN OCTOBER" (the rest of this month),
 * then the month's name, with the year once it isn't this year's.
 */
internal fun historyGroup(timestamp: Long, now: Calendar): String {
    val weekStart = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        while (get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) add(Calendar.DAY_OF_YEAR, -1)
    }
    val c = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val month = SimpleDateFormat(if (sameYear) "MMMM" else "MMMM yyyy", Locale.getDefault())
        .format(c.time).uppercase(Locale.getDefault())
    return when {
        timestamp >= weekStart.timeInMillis -> "THIS WEEK"
        sameYear && c.get(Calendar.MONTH) == now.get(Calendar.MONTH) -> "EARLIER IN $month"
        else -> month
    }
}

/**
 * An event's round tile: colour and icon together, from the field palette. Honey =
 * the streak grew (complete, recovered), SuperGold = a Super day, honey wash = a
 * saver moving, red wash = a break or its countdown, glass = everything quieter.
 */
@Composable
private fun EventTile(type: StreakEventType, size: Dp) {
    val x = LocalBeeExtras.current
    val honeyWash = x.accentFill.copy(alpha = 0.22f)
    val redWash = x.fieldRed.copy(alpha = 0.18f)
    val glass = x.fieldInk.copy(alpha = 0.08f)
    val (fill, tint) = when (type) {
        StreakEventType.COMPLETED, StreakEventType.RECOVERED -> x.accentFill to x.onAccentFill
        StreakEventType.SUPER_DAY -> SuperGold to x.onAccentFill
        StreakEventType.SAVER_USED, StreakEventType.SAVER_EARNED, StreakEventType.COMEBACK_GIFT,
        StreakEventType.LOW_SAVERS -> honeyWash to x.fieldHoney
        StreakEventType.STREAK_BROKE, StreakEventType.RECOVERY_DAYS_LEFT -> redWash to x.fieldRed
        StreakEventType.BEGINNER, StreakEventType.PAST_HOUR_ADDED,
        StreakEventType.SAVER_FADED -> glass to x.fieldInkMuted
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center
    ) {
        if (type == StreakEventType.STREAK_BROKE) {
            Icon(
                Icons.Default.Close, contentDescription = null,
                tint = tint, modifier = Modifier.size(size * 0.5f)
            )
        } else {
            BeeIcon(type.sym(), size = size * 0.52f, contentDescription = null, tint = tint)
        }
    }
}
