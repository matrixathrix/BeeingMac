package com.example.beeing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.StreakState
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.theme.HoneyGold
import com.example.beeing.ui.theme.LocalBeeExtras
import com.example.beeing.ui.theme.RingQualifiedGreen
import com.example.beeing.ui.theme.StreakAtRiskRed

/**
 * What the shared top row needs from the app root. Provided once above the pager
 * so every tab's band can mount [BandTopRow] without threading five parameters
 * through three tab signatures.
 */
class BandChrome(
    val state: StreakState,
    val unread: Int,
    val onStreakClick: () -> Unit,
    val onBellClick: () -> Unit,
    val onMenuClick: () -> Unit
)

val LocalBandChrome = compositionLocalOf<BandChrome?> { null }

/** The row at the top of every tab's band, reading [LocalBandChrome]. */
@Composable
fun BandTopRow(
    titleModifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall
) {
    val chrome = LocalBandChrome.current ?: return
    BandTopRow(
        state = chrome.state, unread = chrome.unread,
        onStreakClick = chrome.onStreakClick, onBellClick = chrome.onBellClick,
        onMenuClick = chrome.onMenuClick, titleModifier = titleModifier, titleStyle = titleStyle
    )
}

/**
 * "Beeing" · streak strip · bell (with an unread badge) · ⋮ settings. The ⋮ has no
 * circle so the bell reads as the actionable one.
 */
@Composable
fun BandTopRow(
    state: StreakState,
    unread: Int,
    onStreakClick: () -> Unit,
    onBellClick: () -> Unit,
    onMenuClick: () -> Unit,
    titleModifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Beeing", style = titleStyle, maxLines = 1, overflow = TextOverflow.Clip,
            modifier = titleModifier.weight(1f, fill = false)
        )
        Spacer(Modifier.weight(1f))
        StatusStrip(state = state, onClick = onStreakClick)
        Spacer(Modifier.width(8.dp))
        BellButton(unread, onBellClick)
        IconButton(onClick = onMenuClick, modifier = Modifier.size(width = 28.dp, height = 40.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Settings", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun BellButton(unread: Int, onClick: () -> Unit) {
    val extras = LocalBeeExtras.current
    val description = if (unread > 0) "Warnings, $unread unread" else "Warnings"
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(OnBandFill)
            .clickable(onClickLabel = "Open warnings", onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .border(2.dp, extras.bandTop, CircleShape)
                    .padding(2.dp)
                    .background(extras.accentFill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (unread > 9) "9+" else "$unread",
                    fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = extras.onAccentFill
                )
            }
        }
    }
}

// ============================================================
// STATUS STRIP  (streak icon + count · ring x/goal — leaf + x/4 in beginner mode)
// ============================================================

@Composable
internal fun StatusStrip(
    state: StreakState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sits on the brand band: a translucent white pill, white content.
    val stripBg = OnBandFill
    val atRisk = state.recoveryOpen
    val shownStreak = state.displayStreak
    val riskColor = StreakAtRiskRed
    // Screen readers would otherwise announce only the bare numbers ("5", "3/8"),
    // so the strip speaks as one labelled target instead of its two children.
    val streakLabel = when {
        atRisk -> "${state.displayStreak}-day streak at risk · ${state.recoveryDaysLeft} " +
            "${if (state.recoveryDaysLeft == 1) "day" else "days"} left to win it back"
        state.streakPaused -> "Beginner mode · streak paused at ${state.currentStreak}"
        state.beginnerMode -> "Beginner mode · no streak yet"
        state.currentStreak > 0 -> "${state.currentStreak}-day streak"
        else -> "No streak yet"
    }
    val stripDescription =
        "$streakLabel · ${state.todayHours} of ${state.hoursRequired} hours rated today"
    Card(
        modifier = modifier
            .clickable(onClickLabel = "Open Streak tab", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = stripDescription },
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(containerColor = stripBg, contentColor = Color.White)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The leaf replaces the flame while beginner mode is on: the mode has
                // to be visible from the main screen, and the paused count beside it
                // reads as held, not lost.
                if (state.beginnerMode && !atRisk) {
                    BeeIcon(Sym.Beginner, size = 17.dp, contentDescription = null)
                } else {
                    BeeIcon(
                        Sym.Fire, size = 17.dp, contentDescription = null,
                        tint = if (atRisk) riskColor else HoneyGold
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "$shownStreak",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = if (atRisk) riskColor else Color.Unspecified
                )
                // Colour never travels alone: the mark says "at risk" too.
                if (atRisk) Text(
                    "!", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                    color = riskColor, modifier = Modifier.padding(start = 2.dp)
                )
            }
            StripDivider()
            MiniHourRing(
                hours = state.todayHours,
                hoursRequired = state.hoursRequired,
                qualified = state.todayQualified
            )
            Text(
                "${state.todayHours}/${state.hoursRequired}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun StripDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 18.dp)
            .background(Color.White.copy(alpha = 0.3f))
    )
}

@Composable
private fun MiniHourRing(hours: Int, hoursRequired: Int, qualified: Boolean) {
    val track = Color.White.copy(alpha = 0.22f)   // on the band
    val fill = if (qualified) RingQualifiedGreen else HoneyGold
    val fraction = (hours.toFloat() / hoursRequired).coerceIn(0f, 1f)
    Canvas(Modifier.size(24.dp)) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        drawArc(
            color = track,
            startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        if (fraction > 0f) {
            drawArc(
                color = fill,
                startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

// Worst case at 360dp: a 3-digit at-risk streak, the "!", an unread badge.
@Preview(widthDp = 360, backgroundColor = 0xFF4A1D4F, showBackground = true)
@Composable
private fun BandTopRowPreview() {
    BandTopRow(
        state = StreakState(
            currentStreak = 0, displayStreak = 124, todayHours = 3, todayQualified = false, extraToday = 0,
            beginnerMode = false, hoursRequired = 8, beginnerDaysCompleted = 0, savers = 0,
            recoveryOpen = true, recoveryDaysLeft = 2, todayEarnedSaver = false
        ),
        unread = 3, onStreakClick = {}, onBellClick = {}, onMenuClick = {}
    )
}
