package com.example.beeing

import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Decagon Comb Dial — the Beeing rating input.
 *
 * A 10-sided readout core with ten hexagonal cells docked edge-parallel on its
 * edges (each rotated +36° from the last, so the ring's silhouette maps onto
 * itself at every detent). Scores ascend counter-clockwise, so a CLOCKWISE
 * glide raises the score at the fixed top notch — physical dial convention.
 *
 * Interaction:
 *  - glide anywhere on the dial: whole comb spins as one unit, 36° detents,
 *    haptic + click tick per detent, digits stay upright
 *  - 6° dead-zone before the wheel engages (accidental brushes do nothing);
 *    the value itself only flips past the 18° half-detent midpoint
 *  - tightly damped fling (tau = 120 ms, travel capped ~5 detents), then
 *    eased snap onto the nearest detent
 *  - tap a cell: shortest-path spin brings it under the notch
 *  - the decagon shows the notch-pointed score INSTANTLY once the wheel is
 *    spun — before the first settle
 *
 * Usage (e.g. in NowTab):
 *
 *   var score by remember { mutableStateOf<Int?>(null) }   // null = unrated
 *   DecagonCombDial(rating = score, onRatingChange = { score = it })
 *
 * Set `rating` back to null after saving to reset the dial to its resting
 * state (muted "Pick a rating" core, comb at 5-on-top, sleeping notch).
 */

private const val DETENT = 36f
private const val DEADZONE_DEG = 6f
private const val TAU_MS = 120f          // fling damping time-constant
private const val FLING_MIN_V = 0.06f    // deg/ms to trigger a fling
private const val FLING_STOP_V = 0.02f   // deg/ms considered stopped

val DIAL_WORDS = mapOf(
    1 to "terrible", 2 to "bad", 3 to "rough", 4 to "meh", 5 to "okay",
    6 to "fine", 7 to "pretty good", 8 to "great", 9 to "excellent", 10 to "golden"
)

/** Smooth red -> amber -> green: HSL hue 5° -> 45° -> 125°. */
fun dialScoreColor(score: Int): Color {
    val t = (score - 1) / 9f
    val hue = if (t <= 0.5f) 5f + (t / 0.5f) * 40f else 45f + ((t - 0.5f) / 0.5f) * 80f
    return Color.hsl(hue, 0.70f, 0.46f)
}

fun dialDigitColor(score: Int): Color =
    if (score >= 6) Color(0xFF141404) else Color(0xFFF4F4F6)

/** Ring position p (clockwise from top) -> score. Ascending counter-clockwise. */
private fun scoreAt(p: Int): Int = (4 - p).mod(10) + 1

/** Score currently under the fixed top notch for a given wheel rotation. */
private fun topScore(thetaDeg: Float): Int =
    scoreAt((-(thetaDeg / DETENT).roundToInt()).mod(10))

private fun nearestDetent(thetaDeg: Float): Float =
    (thetaDeg / DETENT).roundToInt() * DETENT

/** Regular n-gon with softly rounded corners, vertices starting at a0 degrees. */
private fun roundedPoly(
    cx: Float, cy: Float, r: Float, n: Int, a0: Float,
    roundness: Float = 0.14f
): Path {
    val pts = List(n) { k ->
        val a = Math.toRadians((a0 + 360f / n * k).toDouble())
        Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
    val rad = r * roundness
    val path = Path()
    for (i in 0 until n) {
        val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]
        val l1 = hypot(p1.x - p0.x, p1.y - p0.y)
        val l2 = hypot(p2.x - p1.x, p2.y - p1.y)
        val rr = min(rad, min(l1, l2) / 2f)
        val a = Offset(p1.x - (p1.x - p0.x) / l1 * rr, p1.y - (p1.y - p0.y) / l1 * rr)
        val b = Offset(p1.x + (p2.x - p1.x) / l2 * rr, p1.y + (p2.y - p1.y) / l2 * rr)
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticBezierTo(p1.x, p1.y, b.x, b.y)
    }
    path.close()
    return path
}

@Composable
fun DecagonCombDial(
    rating: Int?,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tickSound: Boolean = true
) {
    val view = LocalView.current
    val context = LocalContext.current
    val restingCoreFill = MaterialTheme.colorScheme.surfaceVariant
    val restingOutline = MaterialTheme.colorScheme.outline
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val awakeNotchColor = MaterialTheme.colorScheme.onSurface
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val scope = rememberCoroutineScope()

    var theta by remember { mutableFloatStateOf(0f) }
    var awake by remember { mutableStateOf(false) }
    var lastDetent by remember { mutableIntStateOf(0) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun tick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (tickSound) view.playSoundEffect(SoundEffectConstants.CLICK)
    }

    fun detentCheck() {
        val d = (theta / DETENT).roundToInt()
        if (d != lastDetent) {
            lastDetent = d
            tick()
            onRatingChange(topScore(d * DETENT))
        }
    }

    /** Eased glide of the wheel to [target] degrees, ticking through detents. */
    suspend fun animateThetaTo(target: Float, perDetentMs: Float = 70f) {
        val start = theta
        val dist = target - start
        val dur = maxOf(140f, abs(dist) / DETENT * perDetentMs)
        val t0 = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val k = min(1f, (now - t0) / 1_000_000f / dur)
            val e = 1f - (1f - k) * (1f - k) * (1f - k)      // cubic ease-out
            theta = start + dist * e
            detentCheck()
            if (k >= 1f) break
        }
        theta = target
        onRatingChange(topScore(target))
    }

    fun settleFrom(velocityDegPerMs: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            var v = velocityDegPerMs
            val vMax = 200f / TAU_MS                          // travel cap ~5 detents
            if (abs(v) > vMax) v = vMax * if (v > 0) 1f else -1f
            if (abs(v) > FLING_MIN_V) {
                var prev = withFrameNanos { it }
                while (isActive && abs(v) > FLING_STOP_V) {
                    val now = withFrameNanos { it }
                    val dt = min(40f, (now - prev) / 1_000_000f)
                    prev = now
                    theta += v * dt
                    v *= exp(-dt / TAU_MS)
                    detentCheck()
                }
            }
            animateThetaTo(nearestDetent(theta))
        }
    }

    fun jumpToScore(target: Int) {
        val cur = topScore(nearestDetent(theta))
        var diff = (target - cur).mod(10)
        if (diff > 5) diff -= 10
        settleJob?.cancel()
        settleJob = scope.launch { animateThetaTo(nearestDetent(theta) + diff * DETENT) }
    }

    // Reset to resting state when the parent clears the rating (e.g. after save).
    LaunchedEffect(rating == null) {
        if (rating == null) {
            settleJob?.cancel()
            theta = 0f; lastDetent = 0; awake = false
        }
    }

    // Idle settle-wobble on first appearance: ±4° over ~1.1 s, no haptics.
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (!awake) {
            val now = withFrameNanos { it }
            val t = (now - t0) / 1_100_000_000f
            if (t >= 1f) { theta = 0f; break }
            theta = 4f * sin(t * 2f * PI.toFloat()) * (1f - t)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                var lastAng = 0f; var movedAbs = 0f; var engaged = false
                var lastT = 0L; var vTheta = 0f
                val angleOf = { pos: Offset ->
                    (atan2(pos.y - size.height / 2f, pos.x - size.width / 2f)
                            * 180f / PI.toFloat())
                }
                detectDragGestures(
                    onDragStart = { pos ->
                        settleJob?.cancel()
                        awake = true
                        engaged = false; movedAbs = 0f; vTheta = 0f
                        lastAng = angleOf(pos); lastT = SystemClock.uptimeMillis()
                    },
                    onDrag = { change, _ ->
                        val a = angleOf(change.position)
                        var d = a - lastAng
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        lastAng = a
                        movedAbs += abs(d)
                        val now = change.uptimeMillis
                        val dt = maxOf(1L, now - lastT).toFloat()
                        lastT = now
                        if (!engaged) {
                            if (movedAbs >= DEADZONE_DEG) {
                                engaged = true                     // engage in place, no jump
                                onRatingChange(topScore(nearestDetent(theta)))  // instant fill
                            }
                        } else {
                            theta += d
                            vTheta = 0.75f * vTheta + 0.25f * (d / dt)
                            detentCheck()
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (engaged) {
                            if (SystemClock.uptimeMillis() - lastT > 90) vTheta = 0f  // paused: no flick
                            settleFrom(vTheta)
                        }
                    },
                    onDragCancel = { if (engaged) settleFrom(0f) }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    awake = true
                    val s = size.width.toFloat()
                    val rd = s * 0.2557f
                    val rr = s * 0.0966f
                    val dn = rd * cos(Math.toRadians(18.0)).toFloat() + s * 0.0199f +
                            rr * 0.8660254f
                    val cx = size.width / 2f; val cy = size.height / 2f
                    for (p in 0 until 10) {
                        val a = Math.toRadians((270.0 + 36.0 * p + theta))
                        val nx = cx + dn * cos(a).toFloat()
                        val ny = cy + dn * sin(a).toFloat()
                        if (hypot(pos.x - nx, pos.y - ny) < rr * 1.1f) {
                            jumpToScore(scoreAt(p)); break
                        }
                    }
                }
            }
    ) {
        drawDial(theta, rating, awake, restingCoreFill, restingOutline, hintColor, awakeNotchColor)
    }
}

private fun DrawScope.drawDial(
    theta: Float,
    rating: Int?,
    awake: Boolean,
    restingCoreFill: Color,
    restingOutline: Color,
    hintColor: Color,
    awakeNotchColor: Color
) {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rd = s * 0.2557f                          // decagon circumradius
    val rr = s * 0.0966f                          // cell circumradius
    val gap = s * 0.0199f
    val apo = rd * 0.9510565f                     // cos 18°
    val dn = apo + gap + rr * 0.8660254f          // center -> cell center

    val digitPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // ---- ring: ten cells, docked edge-parallel, spinning with theta ----
    val cellAlpha = if (awake) 1f else 0.85f
    for (p in 0 until 10) {
        val sc = scoreAt(p)
        val a = Math.toRadians((270.0 + 36.0 * p + theta))
        val nx = cx + dn * cos(a).toFloat()
        val ny = cy + dn * sin(a).toFloat()
        val rot = (36f * p) % 60f + theta         // orientation rides with the wheel
        drawPath(roundedPoly(nx, ny, rr, 6, rot), dialScoreColor(sc), alpha = cellAlpha)
        // digits drawn unrotated at the rotated centre -> they stay upright for free
        digitPaint.color = dialDigitColor(sc).toArgbInt()
        digitPaint.textSize = rr * 0.68f
        drawContext.canvas.nativeCanvas.drawText(
            sc.toString(), nx, ny + rr * 0.25f, digitPaint
        )
    }

    // ---- decagon core (10-fold symmetric, so it may spin with the unit) ----
    // Barely-rounded vertices: ten edges already read as round, so the core
    // keeps crisp corners (the docked cells stay at the default roundness).
    if (rating == null) {
        val core = roundedPoly(cx, cy, rd, 10, 252f + theta, roundness = 0.05f)
        drawPath(core, restingCoreFill)
        digitPaint.color = hintColor.toArgbInt()
        digitPaint.textSize = s * 0.054f
        drawContext.canvas.nativeCanvas.apply {
            drawText("Pick a", cx, cy - s * 0.014f, digitPaint)
            drawText("rating", cx, cy + s * 0.054f, digitPaint)
        }
    } else {
        drawPath(roundedPoly(cx, cy, rd, 10, 252f + theta, roundness = 0.05f), dialScoreColor(rating))
        digitPaint.color = dialDigitColor(rating).toArgbInt()
        digitPaint.textSize = s * 0.175f
        drawContext.canvas.nativeCanvas.drawText(
            rating.toString(), cx, cy + s * 0.032f, digitPaint
        )
        val word = DIAL_WORDS[rating].orEmpty()
            .split(' ')
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        digitPaint.textSize = s * 0.046f
        drawContext.canvas.nativeCanvas.drawText(
            word, cx, cy + s * 0.105f, digitPaint
        )
    }

    // ---- fixed notch above the top cell ----
    val ntop = cy - dn - rr * 0.8660254f - s * 0.043f
    val notch = Path().apply {
        moveTo(cx - s * 0.027f, ntop)
        lineTo(cx + s * 0.027f, ntop)
        lineTo(cx, ntop + s * 0.034f)
        close()
    }
    drawPath(notch, if (awake) awakeNotchColor else restingOutline)
}

private fun Color.toArgbInt(): Int = toArgb()
