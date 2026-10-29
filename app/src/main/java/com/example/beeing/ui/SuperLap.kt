package com.example.beeing.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.beeing.STREAK_HOURS_REQUIRED
import com.example.beeing.SUPER_DAY_HOURS

/** Angular gap between two outer-lap segments, in degrees. */
private const val LAP_GAP_DEG = 4f

/**
 * The Super day outer lap: [segments] arcs (one per hour past the goal) clockwise
 * from 12 o'clock around [center] at [radius] (the stroke's centre line), each in
 * [track], then the first [filled] of them in [fill]. [filled] is fractional so a
 * caller can animate the lap drawing itself in; butt caps keep the gaps crisp.
 */
fun DrawScope.drawSuperLap(
    center: Offset,
    radius: Float,
    stroke: Float,
    filled: Float,
    track: Color,
    fill: Color,
    segments: Int = SUPER_DAY_HOURS - STREAK_HOURS_REQUIRED
) {
    if (segments <= 0) return
    val sweep = (360f - segments * LAP_GAP_DEG) / segments
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val style = Stroke(width = stroke, cap = StrokeCap.Butt)
    for (i in 0 until segments) {
        // Half a gap either side of 12 o'clock, so the lap opens symmetrically there
        val start = -90f + LAP_GAP_DEG / 2f + i * (sweep + LAP_GAP_DEG)
        drawArc(track, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        val part = (filled - i).coerceIn(0f, 1f)
        if (part > 0f) {
            drawArc(fill, start, sweep * part, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        }
    }
}
