package com.example.beeing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * COMB STRIP — the rating input.
 *
 * Ten pointy-top hexagons fused into one horizontal comb ribbon:
 *  - at rest each cell carries a whisper of its band hue (1-4 red, 5-7 amber,
 *    8-10 green) so you aim by feeling before landing on a number
 *  - tap or drag anywhere; while the finger is down a loupe hexagon rises
 *    above the strip so the value is never hidden under the thumb
 *  - hybrid honey fill: cells up to the score fill solid in the band color,
 *    cells beyond it take a faint tint of the same hue — the whole comb wears
 *    the hour's color while the fill level carries the magnitude
 *  - a haptic tick fires on every cell boundary, a stronger one on release
 */

private val SCORE_WORDS = listOf(
    "", "awful", "rough", "meh", "below par", "okay",
    "decent", "pretty good", "good", "great", "perfect"
)

fun scoreWord(score: Int): String = SCORE_WORDS.getOrElse(score) { "" }

/**
 * Pointy-top hexagon with softly rounded vertices — friendly, not strict and
 * angular. Same six-point silhouette (flat left/right edges tessellate in a
 * horizontal row), but every corner is eased with a quadratic so the top and
 * bottom points read as gentle curves rather than sharp spikes.
 */
val PointyHexShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val radius = size.minDimension * 0.12f
    val pts = listOf(
        Offset(w * 0.5f, 0f),      // top point
        Offset(w, h * 0.25f),      // upper-right
        Offset(w, h * 0.75f),      // lower-right
        Offset(w * 0.5f, h),       // bottom point
        Offset(0f, h * 0.75f),     // lower-left
        Offset(0f, h * 0.25f)      // upper-left
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

@Composable
fun CombStrip(
    selectedScore: Int?,
    onScoreChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var stripWidthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    // Raw finger X within the strip, so the loupe travels with the thumb
    // rather than snapping to the selected cell's center.
    var dragX by remember { mutableFloatStateOf(0f) }

    fun scoreForX(x: Float): Int {
        if (stripWidthPx <= 0) return 1
        return (1 + (x / stripWidthPx * 10f).toInt()).coerceIn(1, 10)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth()) {
        // The loupe hangs in a zero-height anchor at the top of the comb so it
        // reserves no vertical space; while dragging it rises ABOVE the strip
        // and is free to overlap whatever sits above it — it's only temporary.
        Box(Modifier.fillMaxWidth().height(0.dp)) {
            if (dragging && selectedScore != null && stripWidthPx > 0) {
                val loupeWpx = with(density) { 58.dp.toPx() }
                // Track the finger, not the cell center — the loupe slides
                // smoothly under the thumb across the whole strip.
                val x = (dragX - loupeWpx / 2f)
                    .coerceIn(0f, (stripWidthPx - loupeWpx).coerceAtLeast(0f))
                Box(
                    Modifier
                        .zIndex(2f)
                        .offset { IntOffset(x.roundToInt(), -with(density) { 74.dp.toPx() }.roundToInt()) }
                        .size(width = 58.dp, height = 66.dp)
                        .clip(PointyHexShape)
                        .background(scoreBandColor(selectedScore)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$selectedScore",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (selectedScore >= 5) Color.Black else Color.White
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { stripWidthPx = it.width }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragging = true
                        dragX = down.position.x
                        var last = scoreForX(down.position.x)
                        onScoreChange(last)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        drag(down.id) { change ->
                            dragX = change.position.x
                            val s = scoreForX(change.position.x)
                            if (s != last) {
                                last = s
                                onScoreChange(s)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                        }
                        dragging = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (s in 1..10) {
                CombCell(
                    score = s,
                    selectedScore = selectedScore,
                    dragging = dragging,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        } // comb + loupe overlay box

        Spacer(Modifier.height(8.dp))
        val sel = selectedScore
        Text(
            text = if (sel != null) "$sel · ${scoreWord(sel)}" else "how was it?",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (sel != null) scoreBandColor(sel)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CombCell(
    score: Int,
    selectedScore: Int?,
    dragging: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val sel = selectedScore
    val bandColor = sel?.let { scoreBandColor(it) }
    val isSelected = score == sel && !dragging

    val background = when {
        sel == null -> scoreBandColor(score).copy(alpha = 0.14f) // zoned whisper
        score <= sel -> bandColor!!                              // honey fill
        else -> bandColor!!.copy(alpha = 0.18f)                  // faint same hue
    }
    val numberColor = when {
        sel == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        score == sel -> if (sel >= 5) Color.Black else Color.White
        score < sel -> (if (sel >= 5) Color.Black else Color.White).copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.28f else 1f,
        label = "combCellScale"
    )

    Box(
        modifier = modifier
            .aspectRatio(0.866f) // pointy-top hex: height = width / 0.866
            .zIndex(if (isSelected) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(PointyHexShape)
            .background(background)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$score",
            fontSize = if (isSelected) 14.sp else 12.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            color = numberColor
        )
    }
}
