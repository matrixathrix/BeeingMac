package com.example.beeing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.SAVER_CAP
import com.example.beeing.SAVER_EARN_HOURS
import com.example.beeing.STREAK_HOURS_REQUIRED
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.icons.SymTitle
import com.example.beeing.ui.theme.HoneyGold

/**
 * [SAVER_CAP] shield slots: honey shields up to [savers], the rest the same shield
 * faded back, so earning one visibly "lights it up". Drawn for dark surfaces (the
 * brand band, the celebration); [highlightIndex] pops one slot.
 */
@Composable
fun SaverSlots(
    savers: Int,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    emptyColor: Color = Color.White.copy(alpha = 0.22f),
    fullColor: Color = HoneyGold,
    iconSize: Dp = 26.dp,
    spacing: Dp = 8.dp
) {
    Row(
        modifier.semantics { contentDescription = "$savers of $SAVER_CAP streak savers" },
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(SAVER_CAP) { i ->
            BeeIcon(
                Sym.Saver, size = iconSize,
                tint = if (i < savers) fullColor else emptyColor,
                contentDescription = null,
                modifier = Modifier.scale(if (highlightIndex == i) 1.25f else 1f)
            )
        }
    }
}

/**
 * "Streak and Savers" — only what a user acts on: how the streak grows, how a saver
 * is earned, what it does. Fading, the comeback gift and recovery explain themselves
 * when they happen (the bell and the at-risk state), so they stay out of here.
 * Shared by the Streak tab and Settings.
 */
@Composable
fun SaverExplainerDialog(savers: Int, beginner: Boolean = false, onDismiss: () -> Unit) {
    BandExplainerDialog(
        sym = Sym.Fire,
        title = "Streak and Savers",
        lead = "You have $savers of $SAVER_CAP savers.",
        points = listOf(
            "Savers make sure you don't lose your streak on a day you rate fewer than $STREAK_HOURS_REQUIRED hours.",
            if (beginner) "Streaks grow in Master mode: rate $STREAK_HOURS_REQUIRED hours a day."
            else "Rate $STREAK_HOURS_REQUIRED hours a day to grow your streak.",
            "Rate $SAVER_EARN_HOURS hours in a day to earn a saver, up to $SAVER_CAP."
        ),
        onDismiss = onDismiss
    )
}

/** What the hour ring is for: the daily goal that grows a streak. */
@Composable
fun DailyGoalDialog(todayHours: Int, hoursRequired: Int, beginner: Boolean, onDismiss: () -> Unit) {
    val remaining = (hoursRequired - todayHours).coerceAtLeast(0)
    val more = "$remaining more ${if (remaining == 1) "hour" else "hours"}"
    BandExplainerDialog(
        sym = Sym.Hourglass,
        title = "Today's goal",
        lead = "$todayHours of $hoursRequired hours rated today.",
        points = listOfNotNull(
            when {
                beginner && remaining == 0 -> "Today is a beginner day. Beginner days don't grow your streak; Master mode's $STREAK_HOURS_REQUIRED-hour days do."
                beginner -> "Rate $more today and it's a beginner day. Beginner days don't grow your streak; Master mode's $STREAK_HOURS_REQUIRED-hour days do."
                remaining == 0 -> "Today is complete. Each complete day grows your streak by one."
                else -> "Rate $more today and the day is complete. Each complete day grows your streak by one."
            },
            "Only hours you rate on time count. Hours added later don't.",
            // Beginner days never earn savers, so don't promise one
            if (beginner) null
            else "Rating $SAVER_EARN_HOURS hours in a day earns a streak saver, up to $SAVER_CAP."
        ),
        onDismiss = onDismiss
    )
}

@Composable
private fun BandExplainerDialog(
    sym: Sym,
    title: String,
    lead: String,
    points: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { SymTitle(sym, title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(lead, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
                points.forEach { Text("• $it", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}
