package com.example.beeing.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.beeing.DIAL_WORDS
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Crown travel (px of rotary scroll) per score step. */
private const val CROWN_STEP_PX = 36f

/**
 * Step 1: the score. The crown turns it a step at a time with a tick, or tap a dot.
 * Ten dots sit on the bezel, 1 at the top running clockwise, like the phone's dial.
 */
@Composable
fun ScoreScreen(
    hourLabel: String, moreWaiting: Boolean, score: Int?, notice: String?,
    onScore: (Int) -> Unit, onSwitchHour: (() -> Unit)?, onNext: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    val crown = remember { FloatArray(1) }
    val current = remember { IntArray(1) }.also { it[0] = score ?: 0 }
    fun set(s: Int) {
        if (s != score) { onScore(s); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(FieldBottom)
            .onRotaryScrollEvent { e ->
                // One event can carry several detents: step from a running value, not the
                // score captured at composition, or a fast turn moves a single step.
                val r = applyCrown(current[0].takeIf { it > 0 }, crown[0] + e.verticalScrollPixels, CROWN_STEP_PX)
                crown[0] = r.remainder
                r.score?.let { current[0] = it; set(it) }
                true
            }
            .focusRequester(focus).focusable(),
        contentAlignment = Alignment.Center
    ) {
        BezelDots(score, onTap = ::set)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            notice?.let { Text(it, color = Honey, fontSize = 11.sp) }
            Text(
                hourLabel + if (onSwitchHour != null) "  ⇄" else "",
                color = InkMuted, fontSize = 12.sp,
                modifier = if (onSwitchHour != null) Modifier.clickable(onClick = onSwitchHour) else Modifier
            )
            if (moreWaiting) Text("1 more", color = InkMuted, fontSize = 11.sp)
            if (score == null) {
                Spacer(Modifier.height(10.dp))
                Text("Pick a rating", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
            } else {
                Text("$score", color = scoreTextColor(score),
                    fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(DIAL_WORDS[score].orEmpty().replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 13.sp,
                    fontStyle = FontStyle.Italic)
            }
            Spacer(Modifier.height(6.dp))
            HoneyPill("Next", enabled = score != null, height = 32.dp, onClick = onNext)
        }
    }
}

@Composable
private fun BezelDots(score: Int?, onTap: (Int) -> Unit) {
    Layout(
        content = {
            for (s in 1..10) {
                val selected = s == score
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(if (selected) scoreColor(s) else GlassFill)
                        .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else GlassLine, CircleShape)
                        .clickable { onTap(s) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (selected) onScoreColor(s) else Ink.copy(alpha = 0.85f))
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val dot = placeables.first().width
        val r = min(w, h) / 2f - dot / 2f - 4.dp.toPx()
        layout(w, h) {
            placeables.forEachIndexed { i, p ->
                val a = Math.toRadians(i * 36.0 - 90.0)
                p.place((w / 2f + r * cos(a) - p.width / 2f).roundToInt(), (h / 2f + r * sin(a) - p.height / 2f).roundToInt())
            }
        }
    }
}

/** Where a crown turn of [accumulatedPx] leaves the score: ±1 per [stepPx], 1..10, first detent lands on 5. */
data class Crown(val score: Int?, val remainder: Float)

fun applyCrown(score: Int?, accumulatedPx: Float, stepPx: Float): Crown {
    var s = score
    var acc = accumulatedPx
    while (acc >= stepPx) { acc -= stepPx; s = ((s ?: 4) + 1).coerceAtMost(10) }
    while (acc <= -stepPx) { acc += stepPx; s = ((s ?: 6) - 1).coerceAtLeast(1) }
    return Crown(s, acc)
}
