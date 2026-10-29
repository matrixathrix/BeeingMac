package com.example.beeing

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.BeeButtonDefaults
import com.example.beeing.ui.SaverSlots
import com.example.beeing.ui.drawSuperLap
import com.example.beeing.ui.theme.BandBottomLight
import com.example.beeing.ui.theme.BeeingTheme
import com.example.beeing.ui.theme.HoneyGold
import com.example.beeing.ui.theme.SuperGold
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The two Super day screens. Both sit on the flat BandBottomLight field that the
 * ring-closed takeover fades into, so beat 1 → beat 2 is one continuous surface.
 * Animated values are read only in draw / graphicsLayer lambdas.
 */

private val RING_SIZE = 188.dp
private val SLOT_SIZE = 26.dp           // SaverSlots' defaults, pinned so the slot glow can find its slot
private val SLOT_GAP = 8.dp
private val LapTrack = Color.White.copy(alpha = 0.14f)
private val SuperSparks = listOf(SuperGold, HoneyGold, Color.White)
private val LAP_SEGMENTS = SUPER_DAY_HOURS - STREAK_HOURS_REQUIRED

/** System animations off ⇒ every screen here shows its end state. Previews count as off. */
@Composable
private fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    return remember {
        !inspection &&
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** The full-screen aubergine field; swallows taps, as StreakMomentCelebration does. */
@Composable
private fun SuperField(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BandBottomLight)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Both laps, centred in the draw area: the closed HoneyGold inner ring and the outer
 * lap. [trackIn] (0..[LAP_SEGMENTS]) is how much of the outer track has drawn itself
 * in; [lapFilled] how many of its segments are gold.
 */
private fun DrawScope.drawSuperRings(trackIn: Float, lapFilled: Float) {
    val s = size.minDimension
    drawCircle(HoneyGold, radius = s * 0.33f, center = center, style = Stroke(width = s * 0.07f))
    // The track "draws in" as a fill in the track colour over an invisible track
    drawSuperLap(center, s * 0.445f, s * 0.045f, filled = trackIn, track = Color.Transparent, fill = LapTrack)
    if (lapFilled > 0f) {
        drawSuperLap(center, s * 0.445f, s * 0.045f, filled = lapFilled, track = Color.Transparent, fill = SuperGold)
    }
}

/** The ring's centre readout: a big numeral over a small caption. */
@Composable
private fun RingReadout(numeral: String, caption: String, numeralColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(numeral, fontSize = 52.sp, fontWeight = FontWeight.Black, color = numeralColor, lineHeight = 52.sp)
        Text(caption, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun Overline(text: String, color: Color) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = color)
}

@Composable
private fun TitleAndBody(title: String, body: String) {
    Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(body, fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
}

/**
 * Beat 2, "Make it a Super day": shown straight after the ring closes when the day
 * can still reach [SUPER_DAY_HOURS]. The outer lap draws itself in around the closed
 * ring and its first empty segment breathes; the saver the day would earn glows.
 * One button, system back also dismisses, no haptic (beat 1 spent the budget).
 */
@Composable
fun SuperDayInvite(invite: SuperInvite, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val animationsOn = animationsEnabled()
    // 0..1 over 1.5s: content rises in over the first ~0.45s, the lap draws over the last 1.2s
    val intro = remember { Animatable(if (animationsOn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animationsOn) intro.animateTo(1f, tween(1500, easing = LinearEasing))
    }
    // No infinite transition at all with animations off (animationsOn never changes)
    val wave = if (animationsOn) {
        rememberInfiniteTransition(label = "superInvite").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath"
        )
    } else null
    // The breath, faded in once the lap has drawn; a still mid-glow with animations off
    fun breath(): Float =
        if (wave == null) 0.5f
        else ((intro.value - 0.85f) / 0.15f).coerceIn(0f, 1f) * wave.value

    val evening = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 17 }
    val hours = if (invite.hoursNeeded == 1) "1 more hour" else "${invite.hoursNeeded} more hours"
    val body = "$hours ${if (evening) "tonight" else "today"}. " +
        if (invite.atCap) "All $SAVER_CAP streak savers are already saved up."
        else "A Super day earns a streak saver."

    SuperField {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    val rise = FastOutSlowInEasing.transform((intro.value / 0.3f).coerceIn(0f, 1f))
                    alpha = rise
                    translationY = (1f - rise) * 16.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Overline("DAY COMPLETE", HoneyGold)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val drawn = FastOutSlowInEasing.transform(((intro.value - 0.2f) / 0.8f).coerceIn(0f, 1f))
                    drawSuperRings(trackIn = drawn * LAP_SEGMENTS, lapFilled = 0f)
                    // The next hour's segment, breathing; it sweeps in with its own track
                    // segment, so nothing shows before the lap has drawn there
                    val s = size.minDimension
                    drawSuperLap(
                        center, s * 0.445f, s * 0.045f, filled = (drawn * LAP_SEGMENTS).coerceAtMost(1f),
                        track = Color.Transparent, fill = SuperGold.copy(alpha = 0.15f + 0.45f * breath())
                    )
                }
                RingReadout("${SUPER_DAY_HOURS - invite.hoursNeeded}", "of $SUPER_DAY_HOURS", Color.White)
            }
            Spacer(Modifier.height(24.dp))
            TitleAndBody("Make it a Super day", body)
            Spacer(Modifier.height(22.dp))
            if (invite.atCap) {
                SaverSlots(invite.savers, iconSize = SLOT_SIZE, spacing = SLOT_GAP)
            } else {
                // The slot this Super day would fill, glowing in time with the lap
                val slot = invite.savers
                SaverSlots(
                    invite.savers + 1, highlightIndex = slot, iconSize = SLOT_SIZE, spacing = SLOT_GAP,
                    modifier = Modifier.drawBehind {
                        val r = 24.dp.toPx()
                        val c = Offset((SLOT_SIZE + SLOT_GAP).toPx() * slot + SLOT_SIZE.toPx() / 2f, size.height / 2f)
                        drawCircle(
                            Brush.radialGradient(
                                listOf(SuperGold.copy(alpha = 0.10f + 0.40f * breath()), Color.Transparent),
                                center = c, radius = r
                            ),
                            radius = r, center = c
                        )
                    }
                )
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDone, colors = BeeButtonDefaults.colors()) {
                Text("Let's go", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * The 12th counted hour of a Master day (replaces "Streak saver earned"): both laps
 * full inside a faint gold halo drop in with overshoot, sparks fly, and the saver row
 * shows the new one when it was earned. One 40ms haptic on the drop.
 */
@Composable
fun SuperDayCelebration(moment: StreakMoment.SuperDay, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val animationsOn = animationsEnabled()
    val drop = remember { Animatable(if (animationsOn) 0f else 1f) }
    val float = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (animationsOn) {
            drop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, 200))
            }
            float.animateTo(1f, tween(1400))
        }
    }
    // One slow float after landing; float stays 0 with animations off, so no bob
    fun bob(): Float = sin(float.value * PI.toFloat() * 2f) * 6f

    val title = if (moment.late) "Yesterday was a Super day" else "Super day"
    val body = if (moment.earnedSaver) "Streak saver earned. ${moment.savers} of $SAVER_CAP saved up."
    else "All $SAVER_CAP streak savers saved up."

    SuperField {
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Overline("SUPER DAY", SuperGold)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
                // Glow, halo and sparks stay outside the drop's layer: a faded layer is
                // drawn offscreen and would clip them to the ring's bounds mid-bounce.
                Canvas(Modifier.fillMaxSize()) {
                    val p = drop.value
                    val s = size.minDimension
                    translate(top = bob()) {
                        val settle = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
                        drawCircle(
                            Brush.radialGradient(
                                listOf(SuperGold.copy(alpha = 0.22f * settle), Color.Transparent),
                                center = center, radius = s * 0.95f
                            ),
                            radius = s * 0.95f, center = center
                        )
                        drawCircle(
                            SuperGold.copy(alpha = 0.35f * settle), radius = s * 0.53f, center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        // Sparks burst to just outside the halo (0.50–0.58 of the ring size,
                        // short of the overline and title around the box) and fade out over
                        // the float; the end state, animations off included, has none
                        val sparkAlpha = if (animationsOn) 0.85f * (1f - float.value) else 0f
                        if (p > 0.55f && sparkAlpha > 0f) for (i in 0 until 14) {
                            val a = i * (2f * PI.toFloat() / 14f)
                            // Clamped: the spring overshoots past 1 and would fling them into the text
                            val burst = ((p - 0.55f) / 0.45f).coerceAtMost(1f)
                            val dist = s * (0.50f + 0.08f * ((i * 7) % 5) / 4f) * burst
                            drawCircle(
                                SuperSparks[i % SuperSparks.size].copy(alpha = sparkAlpha),
                                radius = 3.dp.toPx() + (i % 3).dp.toPx(),
                                center = Offset(center.x + cos(a) * dist, center.y + sin(a) * dist)
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val d = drop.value
                            scaleX = d; scaleY = d
                            translationY = (1f - d) * -420f + bob()
                            alpha = d.coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawSuperRings(trackIn = LAP_SEGMENTS.toFloat(), lapFilled = LAP_SEGMENTS.toFloat())
                    }
                    RingReadout("$SUPER_DAY_HOURS", "hours", SuperGold)
                }
            }
            Spacer(Modifier.height(24.dp))
            TitleAndBody(title, body)
            Spacer(Modifier.height(22.dp))
            SaverSlots(moment.savers, highlightIndex = if (moment.earnedSaver) moment.savers - 1 else null)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDone, colors = BeeButtonDefaults.colors()) {
                Text("Keep going", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun SuperDayInvitePreview() = BeeingTheme {
    SuperDayInvite(SuperInvite(hoursNeeded = 4, savers = 2, dayStartMs = 0L), onDone = {})
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun SuperDayInviteCappedPreview() = BeeingTheme {
    SuperDayInvite(SuperInvite(hoursNeeded = 4, savers = SAVER_CAP, dayStartMs = 0L), onDone = {})
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun SuperDayCelebrationPreview() = BeeingTheme {
    SuperDayCelebration(StreakMoment.SuperDay(savers = 3, earnedSaver = true, late = false), onDone = {})
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun SuperDayCelebrationCappedLatePreview() = BeeingTheme {
    SuperDayCelebration(StreakMoment.SuperDay(savers = SAVER_CAP, earnedSaver = false, late = true), onDone = {})
}
