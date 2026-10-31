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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.example.beeing.ui.theme.LocalBeeExtras
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
 * A round readout core ringed by ten round glass cells (the ring has 10-fold
 * symmetry, so its silhouette maps onto itself at every detent). The name is
 * historical: the core was a decagon until the night-field pass (2026-10-31)
 * made every shape on the field a circle or a pill.
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
 * state (glass "Pick a rating" core, comb at 5-on-top, sleeping notch).
 *
 * It is drawn for the night field: glass cells with field-ink digits, so the
 * only coloured fills are the selected cell and the rated core.
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

/** Ring position p (clockwise from top) -> score. Ascending counter-clockwise. */
private fun scoreAt(p: Int): Int = (4 - p).mod(10) + 1

/** Score currently under the fixed top notch for a given wheel rotation. */
private fun topScore(thetaDeg: Float): Int =
    scoreAt((-(thetaDeg / DETENT).roundToInt()).mod(10))

private fun nearestDetent(thetaDeg: Float): Float =
    (thetaDeg / DETENT).roundToInt() * DETENT

// ---- ring geometry (shared by the tap hit-test and the draw pass) ----
// s = the dial's min dimension. The ring's OUTER extent is fixed at 0.394*s, so
// the dial's footprint, the notch and NowTab's rim trims never move; inside it
// the proportions follow the night-field mockup, as fractions of that outer
// radius R: cells 0.20R (with clear gaps between them, not touching), cell
// centres at 0.80R, the core 0.47R, its halo centred on 0.54R. The halo's outer
// edge (0.223*s) stays clear of the cells' inner edge (0.237*s), and cells
// clear their neighbours: at 36° the centre-to-centre chord is 2*sin(18°)*dn =
// 0.195*s against 0.158*s of two radii.
private const val RING_OUTER_FRAC = 0.3941f
private const val CELL_R_FRAC = 0.0788f                          // 0.20R
private const val CELL_D_FRAC = RING_OUTER_FRAC - CELL_R_FRAC    // 0.80R
private const val CORE_CIRCLE_FRAC = 0.185f                      // 0.47R
private const val HALO_R_FRAC = 0.213f                           // 0.54R
private const val HALO_W_FRAC = 0.0206f

private fun cellRadius(s: Float): Float = s * CELL_R_FRAC

/** Dial centre -> cell centre. */
private fun cellDistance(s: Float): Float = s * CELL_D_FRAC

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
    val field = LocalBeeExtras.current
    val density = LocalDensity.current
    // The app's typeface, not the phone's system bold: the dial is drawn on a
    // native canvas, which doesn't inherit Compose's font family.
    val faces = remember {
        fun load(id: Int) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
            ?: android.graphics.Typeface.DEFAULT_BOLD
        val sans = load(R.font.instrument_sans)
        DialFaces(
            cell = DialFace(sans, "'wght' 600"),
            numeral = DialFace(sans, "'wght' 700"),
            word = DialFace(load(R.font.instrument_serif_italic), null),
            hint = DialFace(sans, "'wght' 500")
        )
    }
    val colors = DialColors(
        glass = field.glassFill,
        glassLine = field.glassLine,
        ink = field.fieldInk,
        lineWidth = with(density) { 1.dp.toPx() },
        selectedStroke = with(density) { 2.dp.toPx() },
        faces = faces
    )
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

    // A rating set from outside (the notification's pre-selected score) turns
    // the wheel too — otherwise the core reads 3 while the notch sits on 5.
    // Snapped, not animated: no ticks/haptics for a value the user didn't
    // dial. Ratings the dial published itself already match and are skipped,
    // and a running settle/fling is left alone (it publishes when it lands).
    LaunchedEffect(rating) {
        if (rating != null && settleJob?.isActive != true &&
            topScore(nearestDetent(theta)) != rating
        ) {
            var diff = (rating - topScore(nearestDetent(theta))).mod(10)
            if (diff > 5) diff -= 10
            theta = nearestDetent(theta) + diff * DETENT
            lastDetent = (theta / DETENT).roundToInt()
            awake = true
        }
    }

    // "This spins" hint on first appearance: two decaying swings of ±14° over
    // ~2 s, no haptics. Stays inside the 18° half-detent so the value never
    // flips and the top cell never changes.
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (!awake) {
            val now = withFrameNanos { it }
            if (awake) break   // an external rating snapped the wheel this frame
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
                        // Generous slop for the smaller cells, still under half the
                        // gap to a neighbour (1.22 * 0.0788 < 0.195 / 2)
                        if (hypot(pos.x - nx, pos.y - ny) < rc * 1.22f) {
                            jumpToScore(scoreAt(p)); break
                        }
                    }
                }
            }
    ) {
        drawDial(theta, spin, rating, awake, colors)
    }
}

/** The dial's field colours, resolved once per composition and handed to the draw pass. */
private class DialColors(
    val glass: Color,
    val glassLine: Color,
    val ink: Color,
    val lineWidth: Float,
    val selectedStroke: Float,
    val faces: DialFaces
)

/** A typeface plus, for the variable Instrument Sans, the weight to dial it to. */
private class DialFace(val typeface: android.graphics.Typeface, val variation: String?)

/** Points [this] paint at [face]: a static face just clears the variation. */
private fun android.graphics.Paint.use(face: DialFace) {
    typeface = face.typeface
    fontVariationSettings = face.variation
}

/**
 * The dial's text on the app's type scale: the cell digits are values (Instrument
 * Sans 600), the core's numeral a hero numeral (700, the face's heaviest), the
 * "Pick a rating" prompt 500, and the score word a reflective line, so it is the
 * display serif (Instrument Serif Italic), sized up like every serif call site.
 */
private class DialFaces(
    val cell: DialFace,
    val numeral: DialFace,
    val word: DialFace,
    val hint: DialFace
)

private fun DrawScope.drawDial(
    theta: Float,
    spinDegPerMs: Float,
    rating: Int?,
    awake: Boolean,
    colors: DialColors
) {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rd = s * CORE_CIRCLE_FRAC                 // round core radius
    val rc = cellRadius(s)                        // cell radius
    val dn = cellDistance(s)                      // center -> cell center

    val digitPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // ---- ring: ten glass cells, spinning with theta ----
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
            val c = Offset(nx, ny)
            if (isSelected) {
                drawCircle(dialScoreColor(sc), radius = rc, center = c, alpha = cellAlpha)
                drawCircle(Color.White, radius = rc, center = c, style = Stroke(width = colors.selectedStroke))
            } else {
                // Glass: the faint fill inside a hairline, like every container on the field
                drawCircle(colors.glass, radius = rc, center = c, alpha = cellAlpha)
                drawCircle(
                    colors.glassLine, radius = rc - colors.lineWidth / 2f, center = c,
                    style = Stroke(width = colors.lineWidth), alpha = cellAlpha
                )
            }
            // digits drawn unrotated at the rotated centre -> they stay upright for free.
            // Unselected digits are plain field ink: the only colour on the ring is the
            // selection. On the selected cell the digit flips on the band's lightness
            // (dark on amber/green, white on the deep 1-4 red, where dark ink fails).
            if (blur < 0.2f) {
                val digit = if (isSelected) dialDigitColor(sc) else colors.ink.copy(alpha = 0.85f)
                digitPaint.use(colors.faces.cell)
                digitPaint.color = digit.copy(alpha = digit.alpha * (1f - blur * 5f)).toArgbInt()
                digitPaint.textSize = rc * 0.73f
                drawContext.canvas.nativeCanvas.drawText(
                    sc.toString(), nx, ny + rc * 0.27f, digitPaint
                )
            }
        }
    }
    if (blur > 0f) {
        // the ten cells, smeared into the glass annulus they sweep
        drawCircle(
            color = colors.glass,
            radius = dn,
            style = Stroke(width = rc * 2f),
            alpha = blur
        )
    }

    // ---- round core ----
    // Everything inside is sized off `rd`, so the type scales with the shape.
    if (rating == null) {
        // Unrated: glass, with the prompt in field ink
        drawCircle(colors.glass, radius = rd, center = Offset(cx, cy))
        drawCircle(
            colors.glassLine, radius = rd - colors.lineWidth / 2f, center = Offset(cx, cy),
            style = Stroke(width = colors.lineWidth)
        )
        digitPaint.color = colors.ink.toArgbInt()
        digitPaint.use(colors.faces.hint)
        digitPaint.textSize = rd * 0.24f
        drawContext.canvas.nativeCanvas.apply {
            drawText("Pick a", cx, cy - rd * 0.04f, digitPaint)
            drawText("rating", cx, cy + rd * 0.26f, digitPaint)
        }
    } else {
        // Rated: the score floods the core, with a faint halo in the same colour
        val band = dialScoreColor(rating)
        drawCircle(
            band.copy(alpha = 0.25f), radius = s * HALO_R_FRAC, center = Offset(cx, cy),
            style = Stroke(width = s * HALO_W_FRAC)
        )
        drawCircle(band, radius = rd, center = Offset(cx, cy))
        digitPaint.color = dialDigitColor(rating).toArgbInt()
        digitPaint.use(colors.faces.numeral)
        digitPaint.textSize = rd * 0.80f
        drawContext.canvas.nativeCanvas.drawText(
            rating.toString(), cx, cy + rd * 0.15f, digitPaint
        )
        val word = DIAL_WORDS[rating].orEmpty()
            .split(' ')
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        digitPaint.use(colors.faces.word)
        // The serif reads smaller than the sans it replaced, so it's set larger
        digitPaint.textSize = rd * 0.27f
        drawContext.canvas.nativeCanvas.drawText(
            word, cx, cy + rd * 0.50f, digitPaint
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
    drawPath(notch, if (awake) colors.ink else colors.ink.copy(alpha = 0.45f))
}

private fun Color.toArgbInt(): Int = toArgb()
