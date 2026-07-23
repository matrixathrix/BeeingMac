package com.example.beeing

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * COMB STRIP — the rating input, now a single large hive cell.
 *
 * Two states, driven only by whether a score exists yet:
 *  - **no score:** ten stacked, individually-outlined zone-blocks that together
 *    read as one flat-top hexagon, band-tinted (1–4 red, 5–7 amber, 8–10 green),
 *    each carrying its number centered inside the block. The whole cell breathes
 *    gently to say "touch me"; the visible scale teaches the mapping — up is
 *    better — before a finger ever lands.
 *  - **score set:** the whole hexagon floods uniformly in that one band color
 *    with a single big numeral in the middle. Dragging up/down slides through
 *    1–10; the flood color and the number change live. It stays filled after
 *    you lift, so you can tag and save with your value still shown.
 *
 * The narrowing top and bottom zones are deliberate: 1 and 10 are the rarest
 * ratings, so they take the smallest footprint — and on a vertical drag the
 * ends of travel are the easiest targets anyway, so nothing is lost.
 */

private val SCORE_WORDS = listOf(
    "", "awful", "rough", "meh", "below par", "okay",
    "decent", "pretty good", "good", "great", "perfect"
)

fun scoreWord(score: Int): String = SCORE_WORDS.getOrElse(score) { "" }

/**
 * Flat-top hexagon: flat horizontal top and bottom edges, points on the left
 * and right at mid-height. Chosen over a pointy-top so the extreme scores (the
 * top and bottom zones) still get real width for a legible number instead of
 * collapsing to a tiny triangle — while staying the thinnest zones, keeping the
 * "rare extremes take the smallest footprint" idea.
 */
val FlatHexShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val radius = size.minDimension * 0.10f // softly rounded vertices
    val pts = listOf(
        Offset(w * 0.25f, 0f),  // top-left of the flat top edge
        Offset(w * 0.75f, 0f),  // top-right of the flat top edge
        Offset(w, h * 0.5f),    // right point
        Offset(w * 0.75f, h),   // bottom-right of the flat bottom edge
        Offset(w * 0.25f, h),   // bottom-left of the flat bottom edge
        Offset(0f, h * 0.5f)    // left point
    )
    val n = pts.size
    for (i in 0 until n) {
        val curr = pts[i]
        val prev = pts[(i - 1 + n) % n]
        val next = pts[(i + 1) % n]
        val start = pointToward(curr, prev, radius)
        val end = pointToward(curr, next, radius)
        if (i == 0) moveTo(start.x, start.y) else lineTo(start.x, start.y)
        quadraticBezierTo(curr.x, curr.y, end.x, end.y)
    }
    close()
}

/** A point [dist] away from [from] along the line toward [to]. */
private fun pointToward(from: Offset, to: Offset, dist: Float): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.hypot(dx, dy)
    if (len == 0f) return from
    val f = dist / len
    return Offset(from.x + dx * f, from.y + dy * f)
}

/** Half-width of the flat-top hexagon interior at height [y] (box [w]×[h]). */
private fun flatHexHalfWidth(y: Float, w: Float, h: Float): Float =
    if (y <= h * 0.5f) w * 0.25f + w * 0.5f * (y / h)
    else w * 0.75f - w * 0.5f * (y / h)

private fun dist(a: Offset, b: Offset) = kotlin.math.hypot(a.x - b.x, a.y - b.y)

/** A closed polygon through [pts] with each vertex eased into a soft corner. */
private fun roundedPolygonPath(pts: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = pts.size
    for (i in 0 until n) {
        val curr = pts[i]
        val prev = pts[(i - 1 + n) % n]
        val next = pts[(i + 1) % n]
        val rPrev = minOf(radius, dist(curr, prev) / 2f)
        val rNext = minOf(radius, dist(curr, next) / 2f)
        val start = pointToward(curr, prev, rPrev)
        val end = pointToward(curr, next, rNext)
        if (i == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
        path.quadraticBezierTo(curr.x, curr.y, end.x, end.y)
    }
    path.close()
    return path
}

@Composable
fun CombStrip(
    selectedScore: Int?,
    onScoreChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    var hexHeightPx by remember { mutableIntStateOf(0) }

    // Gentle breathing while nothing is picked, cueing "this is the action area".
    val pulse = rememberInfiniteTransition(label = "hexPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hexPulseScale"
    )

    // Top of the cell is 10, bottom is 1 — up = better.
    fun scoreForY(y: Float): Int {
        if (hexHeightPx <= 0) return selectedScore ?: 5
        val f = (y / hexHeightPx).coerceIn(0f, 1f)
        return (10 - (f * 10).toInt()).coerceIn(1, 10)
    }

    val restingScale = if (selectedScore == null) pulseScale else 1f

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1.1547f) // regular flat-top hex: width = height * 2/√3
                .alpha(if (enabled) 1f else 0.5f)
                .graphicsLayer {
                    scaleX = restingScale
                    scaleY = restingScale
                }
                .onSizeChanged { hexHeightPx = it.height }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var last = scoreForY(down.position.y)
                        onScoreChange(last)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        drag(down.id) { change ->
                            val s = scoreForY(change.position.y)
                            if (s != last) {
                                last = s
                                onScoreChange(s)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val sel = selectedScore
            if (sel == null) {
                RestingScaleHex()
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(FlatHexShape)
                        .background(scoreBandColor(sel)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$sel",
                        fontSize = 70.sp,
                        fontWeight = FontWeight.Black,
                        color = if (sel >= 5) Color.Black else Color.White
                    )
                }
            }
        }

        // Qualitative echo of the picked value (no empty-state prompt).
        val sel = selectedScore
        if (sel != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "$sel · ${scoreWord(sel)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = scoreBandColor(sel)
            )
        }
    }
}

/**
 * The resting cell: ten filled, soft-tinted island bands separated by gaps that
 * together read as one rounded flat-top hexagon, band-colored (1–4 red, 5–7
 * amber, 8–10 green), narrow at the 1 and 10 tips and widest in the middle. Each
 * island carries its number centered inside. Score 10 is the top island, 1 the
 * bottom. Deliberately low-saturation so it recedes until touched.
 */
@Composable
private fun RestingScaleHex() {
    val textMeasurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val halfGap = 2.5.dp.toPx()   // half the space between two islands
        val radius = 5.dp.toPx()      // softly rounded island corners
        for (s in 1..10) {
            // 10 at the top, 1 at the bottom; each score owns one h/10 slot,
            // inset top and bottom so the islands don't touch.
            val top = (10 - s) / 10f * h + halfGap
            val bot = (11 - s) / 10f * h - halfGap
            if (bot <= top) continue
            val hwTop = flatHexHalfWidth(top, w, h)
            val hwBot = flatHexHalfWidth(bot, w, h)
            val color = scoreBandColor(s)

            val island = roundedPolygonPath(
                listOf(
                    Offset(cx - hwTop, top),
                    Offset(cx + hwTop, top),
                    Offset(cx + hwBot, bot),
                    Offset(cx - hwBot, bot)
                ),
                radius
            )
            drawPath(island, color.copy(alpha = 0.35f))

            val layout = textMeasurer.measure(
                text = "$s",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            val cy = (top + bot) / 2f
            drawText(
                layout,
                topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f)
            )
        }
    }
}
