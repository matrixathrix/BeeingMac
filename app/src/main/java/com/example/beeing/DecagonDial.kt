package com.example.beeing

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
 * A 10-sided readout core with ten round cells docked one per edge (the ring
 * has 10-fold symmetry, so its silhouette maps onto itself at every detent).
 * Scores ascend counter-clockwise, so a CLOCKWISE
 * glide raises the score at the fixed top notch — physical dial convention.
 *
 * Interaction:
 *  - glide anywhere on the dial: whole comb spins as one unit, 36° detents,
 *    haptic + click tick per detent, digits stay upright
 *  - 6° dead-zone before the wheel engages (accidental brushes do nothing);
 *    the value itself only flips past the 18° half-detent midpoint
 *  - damped fling, then an eased snap onto the nearest detent. An ordinary
 *    release coasts at most ~1.7 turns; a deliberate hard flick (past
 *    FIDGET_FLICK_V) unlocks the long fidget-spinner spin, up to ~30 turns
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
// Two fling regimes, gated on how hard the wheel was released. A normal rating
// drag gets a short, controllable coast; only a deliberate hard flick past
// FIDGET_FLICK_V unlocks the long fidget-spinner spin.
private const val FIDGET_FLICK_V = 2f       // deg/ms release speed to unlock it
private const val TAU_SETTLE_MS = 180f      // damping: ordinary drag
private const val TAU_FIDGET_MS = 720f      // damping: hard flick
private const val SETTLE_MAX_TRAVEL = 300f  // deg an ordinary drag may coast (<1 turn)
private const val FIDGET_MAX_TRAVEL = 10800f // deg a hard flick may coast (30 turns)
private const val SETTLE_GAIN = 0.7f       // ordinary drags coast slower than the finger
private const val FLING_GAIN = 3.5f        // hard-flick-only release boost
private const val MIN_TICK_GAP_MS = 55L    // feedback floor; see tick()
private const val WHIRR_V = 3f             // deg/ms above which ticks go light
private const val VALUE_PUSH_MAX_V = 1.2f  // deg/ms above which the score stops
                                           // being pushed out per detent
private const val FLING_MIN_V = 0.06f    // deg/ms to trigger a fling
private const val FLING_STOP_V = 0.02f   // deg/ms considered stopped
private const val TICK_SAMPLE_RATE = 44100
private const val TICK_MS = 9            // click length
private const val TICK_VOLUME = 0.45f

// Above WHIRR_V, discrete per-detent clicks stop firing entirely — retriggering
// a short sample hundreds of times a second is what read as "stuttery messy
// noise". A continuous whine tone was tried as a replacement but crashed the
// audio HAL (see DialTicker.updateWhine()), so fast spin is silent except for
// haptics.

val DIAL_WORDS = mapOf(
    1 to "terrible", 2 to "bad", 3 to "rough", 4 to "meh", 5 to "okay",
    6 to "fine", 7 to "pretty good", 8 to "great", 9 to "excellent", 10 to "golden"
)

/**
 * The dial speaks the app's score bands (red 1–4 / amber 5–7 / green 8–10),
 * same as every other readout — the fixed AccentOrange it used to carry made
 * the core say nothing about the rating it was showing.
 */
fun dialScoreColor(score: Int): Color = scoreBandColor(score)

/** Text on a full-strength band fill: flipped on the band's own lightness. */
fun dialDigitColor(score: Int): Color =
    if (scoreBandColor(score).luminance() > 0.35f) Color(0xFF1B1B1B) else Color.White

/**
 * Ring cell that is NOT under the notch: one flat gray for all nine, so the
 * ring is a quiet track and the ONLY colored fill on the dial is the selection.
 * The score still reads per cell — through its digit ([cellDigitColor]), not
 * its fill.
 */
private fun cellGrayColor(surfaceVariant: Color, onSurfaceVariant: Color): Color =
    lerp(surfaceVariant, onSurfaceVariant, 0.18f)

/**
 * Digit on an unselected (gray) cell: its score band, nudged for legibility —
 * the 1–4 red is a deep 0xFFB71C1C that disappears on a dark gray, so bands are
 * lifted toward white on dark cells and pushed toward black on light ones.
 */
private fun cellDigitColor(score: Int, cellGray: Color): Color {
    val band = scoreBandColor(score)
    return if (cellGray.luminance() < 0.5f) lerp(band, Color.White, 0.38f)
    else lerp(band, Color.Black, 0.18f)
}

/** Ring position p (clockwise from top) -> score. Ascending counter-clockwise. */
private fun scoreAt(p: Int): Int = (4 - p).mod(10) + 1

/** Score currently under the fixed top notch for a given wheel rotation. */
private fun topScore(thetaDeg: Float): Int =
    scoreAt((-(thetaDeg / DETENT).roundToInt()).mod(10))

private fun nearestDetent(thetaDeg: Float): Float =
    (thetaDeg / DETENT).roundToInt() * DETENT

// ---- ring geometry (shared by the tap hit-test and the draw pass) ----
// s = the dial's min dimension. The ring is DERIVED from the core again
// (apothem + gap + cell radius), so shrinking the core pulls the cells in with
// it — that is what "closer to the decagon" means, and an independent ring
// distance could not do it.
//
// The cell radius is fixed and the gap is tight, so the ring closes in on the
// smaller core rather than growing to meet the old footprint: outer extent is
// 0.394*s, down from 0.4435*s, and that ~11% is handed back as vertical space
// by NowTab's rim trim. The one hard constraint is that cells must clear their
// own neighbours — at 36° spacing the centre-to-centre chord is 2*sin(18°)*dn,
// so CELL_R_FRAC <= 0.309*dn (0.0902 against a 0.0939 ceiling here).
private const val CORE_R_FRAC = 0.2100f      // decagon circumradius
private const val CELL_R_FRAC = 0.0902f      // cell radius
private const val CELL_GAP_FRAC = 0.0140f    // core edge -> cell

private fun cellRadius(s: Float): Float = s * CELL_R_FRAC

/** Dial centre -> cell centre. */
private fun cellDistance(s: Float): Float =
    s * (CORE_R_FRAC * 0.9510565f + CELL_GAP_FRAC + CELL_R_FRAC)

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

/**
 * The wheel's audible detent click.
 *
 * `View.playSoundEffect` was the obvious route, but it is the system TOUCH
 * sound: silent unless the user has "Touch sounds" enabled in system settings
 * (off by default on One UI), and it is the generic UI click rather than a
 * wheel tick. So the click is synthesized once — a ~9 ms exponentially damped
 * 2.6 kHz ping with a noise transient — and replayed from a MODE_STATIC
 * AudioTrack. Sonification usage means it follows the system/ring volume and
 * stays quiet in silent mode.
 */
private class DialTicker {
    private val samples: ShortArray = ShortArray(TICK_SAMPLE_RATE * TICK_MS / 1000).also { buf ->
        var noise = 0.0
        for (i in buf.indices) {
            val t = i.toDouble() / TICK_SAMPLE_RATE
            val env = exp(-t * 520.0)
            noise = if (i < 40) (Math.random() * 2.0 - 1.0) else noise * 0.6
            val body = sin(2.0 * PI * 2600.0 * t) * 0.75 + noise * 0.25
            buf[i] = (body * env * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
    }

    private var track: AudioTrack? = null
    private var lastClickMs = 0L

    private fun ensureTrack(): AudioTrack? {
        track?.let { return it }
        return runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(TICK_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .also {
                    it.write(samples, 0, samples.size)
                    it.setVolume(TICK_VOLUME)
                }
        }.getOrNull().also { track = it }
    }

    // The actual crash cause (traced via logcat, not guesswork): calling
    // stop()+reloadStaticData()+play() on a MODE_STATIC track many times a
    // second reliably aborts the audio HAL with a native SIGABRT (ubsan
    // sub-overflow inside AudioTrack::processAudioBuffer) that no try/catch
    // can stop. This floor is enforced HERE, inside play() itself, so no
    // caller-side gating (or its absence) can ever restart the native track
    // faster than this — the discrete "1:1 with every detent" request from
    // earlier is capped by this to stay crash-safe, not truly unthrottled.
    private val MIN_CLICK_GAP_MS = 20L

    fun play() {
        val now = SystemClock.uptimeMillis()
        if (now - lastClickMs < MIN_CLICK_GAP_MS) return
        lastClickMs = now
        val t = ensureTrack() ?: return
        runCatching {
            if (t.playState != AudioTrack.PLAYSTATE_STOPPED) t.stop()
            t.reloadStaticData()          // rewind: a static track does not auto-rewind
            t.play()
        }
    }

    // The whine tone (a looped MODE_STATIC AudioTrack, volume-only per-frame
    // updates) was removed after it reliably crashed the audio HAL with a
    // native SIGABRT (ubsan sub-overflow inside AudioTrack::processAudioBuffer)
    // during sustained fast spins — confirmed NOT caused by pitch-shifting
    // (removing setPlaybackParams left the identical crash) nor by the
    // discrete click's restart rate (throttling play() didn't stop it
    // either). Whatever the exact native trigger, it's specific to this
    // looped-static-track construction, so updateWhine()/stopWhine() are now
    // no-ops: fast spin is silent except for haptics, which is unaffected.
    fun updateWhine(spinDegPerMs: Float) {}

    fun stopWhine() {}

    fun release() {
        runCatching { track?.release() }
        track = null
    }
}

@Composable
private fun rememberDialTicker(): DialTicker {
    val ticker = remember { DialTicker() }
    DisposableEffect(ticker) { onDispose { ticker.release() } }
    return ticker
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
    val cellGray = cellGrayColor(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant
    )
    val awakeNotchColor = MaterialTheme.colorScheme.onSurface
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val ticker = rememberDialTicker()
    val scope = rememberCoroutineScope()

    var theta by remember { mutableFloatStateOf(0f) }
    var awake by remember { mutableStateOf(false) }
    var lastDetent by remember { mutableIntStateOf(0) }
    var lastTickMs by remember { mutableLongStateOf(0L) }
    var spin by remember { mutableFloatStateOf(0f) }   // signed deg/ms, for blur+feedback
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // A full fling crosses a detent every ~2 ms — far faster than the vibrator
    // can service an 18 ms pulse. So haptics stay rate-limited to one per
    // MIN_TICK_GAP_MS and go light above WHIRR_V, turning a fast spin into a
    // ratchet whirr instead of one long smeared buzz. The tick sound plays
    // once per detent crossing, 1:1, ONLY below WHIRR_V — above it,
    // updateWhine()'s continuous tone takes over instead of retriggering a
    // short click hundreds of times a second (that's what read as stuttery,
    // messy noise, not a whine).
    fun tick() {
        val now = SystemClock.uptimeMillis()
        val fast = abs(spin) > WHIRR_V
        if (now - lastTickMs >= MIN_TICK_GAP_MS) {
            lastTickMs = now
            val v = vibrator
            when {
                v != null && v.hasAmplitudeControl() ->
                    v.vibrate(VibrationEffect.createOneShot(if (fast) 10L else 18L, if (fast) 80 else 160))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    v?.vibrate(
                        VibrationEffect.createPredefined(
                            if (fast) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_HEAVY_CLICK
                        )
                    )
                else -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        if (tickSound && !fast) ticker.play()
    }

    fun detentCheck() {
        val d = (theta / DETENT).roundToInt()
        if (d != lastDetent) {
            lastDetent = d
            tick()
            // Mid-blur scores are noise the user can't even read, and pushing
            // ~500 of them a second would recompose the whole rating card.
            // animateThetaTo always publishes the landed value.
            if (abs(spin) < VALUE_PUSH_MAX_V) onRatingChange(topScore(d * DETENT))
        }
    }

    /** Eased glide of the wheel to [target] degrees, ticking through detents. */
    suspend fun animateThetaTo(target: Float, perDetentMs: Float = 70f) {
        val start = theta
        val dist = target - start
        val dur = maxOf(140f, abs(dist) / DETENT * perDetentMs)
        val t0 = withFrameNanos { it }
        var prevTheta = start
        var prevT = t0
        while (true) {
            val now = withFrameNanos { it }
            val k = min(1f, (now - t0) / 1_000_000f / dur)
            val e = 1f - (1f - k) * (1f - k) * (1f - k)      // cubic ease-out
            theta = start + dist * e
            val dtMs = maxOf(1f, (now - prevT) / 1_000_000f)
            spin = (theta - prevTheta) / dtMs
            prevTheta = theta; prevT = now
            detentCheck()
            ticker.updateWhine(spin)
            if (k >= 1f) break
        }
        theta = target
        spin = 0f
        ticker.stopWhine()
        onRatingChange(topScore(target))
    }

    fun settleFrom(velocityDegPerMs: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            val fidget = abs(velocityDegPerMs) >= FIDGET_FLICK_V
            val tau = if (fidget) TAU_FIDGET_MS else TAU_SETTLE_MS
            var v = velocityDegPerMs * (if (fidget) FLING_GAIN else SETTLE_GAIN)
            val vMax = (if (fidget) FIDGET_MAX_TRAVEL else SETTLE_MAX_TRAVEL) / tau
            if (abs(v) > vMax) v = vMax * if (v > 0) 1f else -1f
            if (abs(v) > FLING_MIN_V) {
                var prev = withFrameNanos { it }
                while (isActive && abs(v) > FLING_STOP_V) {
                    val now = withFrameNanos { it }
                    val dt = min(40f, (now - prev) / 1_000_000f)
                    prev = now
                    theta += v * dt
                    spin = v
                    v *= exp(-dt / tau)
                    detentCheck()
                    ticker.updateWhine(spin)
                }
            }
            spin = 0f
            ticker.stopWhine()
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
            settleJob?.cancel()   // cancelled mid-fling, so clear spin or the blur sticks
            theta = 0f; lastDetent = 0; awake = false; spin = 0f
            ticker.stopWhine()
        }
    }

    // "This spins" hint on first appearance: two decaying swings of ±14° over
    // ~2 s, no haptics. Stays inside the 18° half-detent so the value never
    // flips and the top cell never changes.
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (!awake) {
            val now = withFrameNanos { it }
            val t = (now - t0) / 2_000_000_000f
            if (t >= 1f) { theta = 0f; break }
            theta = 14f * sin(t * 4f * PI.toFloat()) * (1f - t)
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
                        engaged = false; movedAbs = 0f; vTheta = 0f; spin = 0f
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
                            // Weighted toward the newest sample: the old 0.75/0.25
                            // lag meant a hard flick released at a fraction of the
                            // speed the finger was actually doing, so the wheel
                            // could never get near vMax.
                            vTheta = 0.35f * vTheta + 0.65f * (d / dt)
                            spin = vTheta
                            detentCheck()
                            ticker.updateWhine(spin)
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (engaged) {
                            if (SystemClock.uptimeMillis() - lastT > 90) vTheta = 0f  // paused: no flick
                            settleFrom(vTheta)
                        } else {
                            spin = 0f
                            ticker.stopWhine()
                        }
                    },
                    onDragCancel = { if (engaged) settleFrom(0f) }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    awake = true
                    val s = size.width.toFloat()
                    val rc = cellRadius(s)
                    val dn = cellDistance(s)
                    val cx = size.width / 2f; val cy = size.height / 2f
                    for (p in 0 until 10) {
                        val a = Math.toRadians((270.0 + 36.0 * p + theta))
                        val nx = cx + dn * cos(a).toFloat()
                        val ny = cy + dn * sin(a).toFloat()
                        if (hypot(pos.x - nx, pos.y - ny) < rc * 1.18f) {
                            jumpToScore(scoreAt(p)); break
                        }
                    }
                }
            }
    ) {
        drawDial(
            theta, spin, rating, awake,
            restingCoreFill, restingOutline, hintColor, awakeNotchColor, cellGray
        )
    }
}

private fun DrawScope.drawDial(
    theta: Float,
    spinDegPerMs: Float,
    rating: Int?,
    awake: Boolean,
    restingCoreFill: Color,
    restingOutline: Color,
    hintColor: Color,
    awakeNotchColor: Color,
    cellGray: Color
) {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rd = s * CORE_R_FRAC                      // decagon circumradius
    val rc = cellRadius(s)                        // cell radius
    val dn = cellDistance(s)                      // center -> cell center

    val digitPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // ---- ring: ten cells, docked edge-parallel, spinning with theta ----
    //
    // Motion blur is not decoration here, it is a correctness fix. The ring has
    // 10-fold symmetry, so its silhouette repeats every 36°; once the wheel
    // turns more than 18° between frames the discrete cells alias (the
    // wagon-wheel effect) and a fast spin reads as a slow backwards crawl. So
    // past ~10°/frame the cells crossfade into a smeared band, which is both
    // what a real fidget spinner looks like and cheaper than drawing ghosts.
    val degPerFrame = abs(spinDegPerMs) * 8.3f          // assume ~120 Hz
    val blur = ((degPerFrame - 10f) / 40f).coerceIn(0f, 1f)
    val cellAlpha = (if (awake) 1f else 0.85f) * (1f - blur)
    // Cell currently parked under the notch — outlined so the selection reads
    // on the ring, not only in the core.
    val selected =
        if (rating == null || blur > 0.05f) -1 else (-(theta / DETENT).roundToInt()).mod(10)
    if (blur < 1f) {
        for (p in 0 until 10) {
            val sc = scoreAt(p)
            val a = Math.toRadians((270.0 + 36.0 * p + theta))
            val nx = cx + dn * cos(a).toFloat()
            val ny = cy + dn * sin(a).toFloat()
            val isSelected = p == selected
            val fill = if (isSelected) dialScoreColor(sc) else cellGray
            drawCircle(fill, radius = rc, center = Offset(nx, ny), alpha = cellAlpha)
            if (isSelected) {
                drawCircle(
                    Color.White, radius = rc, center = Offset(nx, ny),
                    style = Stroke(width = s * 0.011f)
                )
            }
            // digits drawn unrotated at the rotated centre -> they stay upright for free.
            // On a gray cell the digit is the ONLY thing carrying the score band, so
            // it is colored (red/amber/green); on the selected cell the fill already
            // is the band, so the digit flips for contrast instead.
            if (blur < 0.2f) {
                digitPaint.color =
                    (if (isSelected) dialDigitColor(sc) else cellDigitColor(sc, cellGray))
                        .copy(alpha = 1f - blur * 5f).toArgbInt()
                digitPaint.textSize = rc * 0.73f
                drawContext.canvas.nativeCanvas.drawText(
                    sc.toString(), nx, ny + rc * 0.27f, digitPaint
                )
            }
        }
    }
    if (blur > 0f) {
        // the ten cells, smeared into the annulus they sweep
        drawCircle(
            color = cellGray,
            radius = dn,
            style = Stroke(width = rc * 2f),
            alpha = blur * 0.9f
        )
    }

    // ---- decagon core (10-fold symmetric, so it may spin with the unit) ----
    // Barely-rounded vertices: ten edges already read as round, so the core
    // keeps crisp corners — the contrast against the round cells is the point.
    // Everything inside is sized off `rd`, not `s`, so shrinking the core
    // carries its own type down with it instead of overflowing the shape.
    if (rating == null) {
        val core = roundedPoly(cx, cy, rd, 10, 252f + theta, roundness = 0.05f)
        drawPath(core, restingCoreFill)
        digitPaint.color = hintColor.toArgbInt()
        digitPaint.textSize = rd * 0.211f
        drawContext.canvas.nativeCanvas.apply {
            drawText("Pick a", cx, cy - rd * 0.055f, digitPaint)
            drawText("rating", cx, cy + rd * 0.211f, digitPaint)
        }
    } else {
        drawPath(roundedPoly(cx, cy, rd, 10, 252f + theta, roundness = 0.05f), dialScoreColor(rating))
        digitPaint.color = dialDigitColor(rating).toArgbInt()
        digitPaint.textSize = rd * 0.684f
        drawContext.canvas.nativeCanvas.drawText(
            rating.toString(), cx, cy + rd * 0.125f, digitPaint
        )
        val word = DIAL_WORDS[rating].orEmpty()
            .split(' ')
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        digitPaint.textSize = rd * 0.180f
        drawContext.canvas.nativeCanvas.drawText(
            word, cx, cy + rd * 0.411f, digitPaint
        )
    }

    // ---- fixed notch above the top cell ----
    val ntop = cy - dn - rc - s * 0.043f
    val notch = Path().apply {
        moveTo(cx - s * 0.027f, ntop)
        lineTo(cx + s * 0.027f, ntop)
        lineTo(cx, ntop + s * 0.034f)
        close()
    }
    drawPath(notch, if (awake) awakeNotchColor else restingOutline)
}

private fun Color.toArgbInt(): Int = toArgb()
