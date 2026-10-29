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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.example.beeing.STREAK_HOURS_REQUIRED
import com.example.beeing.SUPER_DAY_HOURS
import com.example.beeing.StreakState
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.theme.HoneyGold
import com.example.beeing.ui.theme.LocalBeeExtras
import com.example.beeing.ui.theme.RingQualifiedGreen
import com.example.beeing.ui.theme.StreakAtRiskRed
import com.example.beeing.ui.theme.SuperGold

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
    val onMenuClick: () -> Unit,
    val onTopRowHidden: (Boolean) -> Unit = {},
    /**
     * How much of a band is under the row right now, 0…1, off the pager's
     * continuous position: Streak and Now have one, Past doesn't (it opens on the
     * plain page under a large title). The row crossfades its ink with the swipe.
     */
    val bandUnder: () -> Float = { 1f },
    /** How much of the Now tab is showing, 0…1: the status strip lives there only. */
    val nowShown: () -> Float = { 1f },
    /** Hours still to go to a Super day, while 12 is reachable today (else null). */
    val superHoursLeft: Int? = null
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
        onMenuClick = chrome.onMenuClick, titleModifier = titleModifier, titleStyle = titleStyle,
        bandUnder = chrome.bandUnder(), nowShown = chrome.nowShown(),
        superHoursLeft = chrome.superHoursLeft
    )
}

/**
 * "Beeing" · streak strip · bell (with an unread badge) · ⋮ settings. The ⋮ has no
 * circle so the bell reads as the actionable one.
 *
 * The strip shows on Now only: the Streak tab's hero says the same thing in large
 * type, and Past is the record. Over the plain page (Past) the wordmark fades out
 * under the tab's own large title and the ink turns from white to onBackground.
 */
@Composable
fun BandTopRow(
    state: StreakState,
    unread: Int,
    onStreakClick: () -> Unit,
    onBellClick: () -> Unit,
    onMenuClick: () -> Unit,
    titleModifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    bandUnder: Float = 1f,
    nowShown: Float = 1f,
    superHoursLeft: Int? = null
) {
    val ink = lerp(MaterialTheme.colorScheme.onBackground, Color.White, bandUnder)
    CompositionLocalProvider(LocalContentColor provides ink) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (bandUnder > 0.01f) Text(
                "Beeing", style = titleStyle, maxLines = 1, overflow = TextOverflow.Clip,
                modifier = titleModifier.graphicsLayer { alpha = bandUnder }
            )
            Spacer(Modifier.weight(1f))
            if (nowShown > 0.01f) {
                StatusStrip(
                    state = state, onClick = onStreakClick, superHoursLeft = superHoursLeft,
                    modifier = Modifier.graphicsLayer { alpha = nowShown }
                )
                Spacer(Modifier.width(8.dp))
            }
            BellButton(unread, onBellClick, bandUnder)
            IconButton(onClick = onMenuClick, modifier = Modifier.size(width = 28.dp, height = 40.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Settings", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun BellButton(unread: Int, onClick: () -> Unit, bandUnder: Float = 1f) {
    val extras = LocalBeeExtras.current
    val ink = LocalContentColor.current
    // On the band: translucent white. On the page: a faint ink wash.
    val fill = lerp(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f), OnBandFill, bandUnder)
    val ringBehindBadge = lerp(MaterialTheme.colorScheme.background, extras.bandTop, bandUnder)
    val description = if (unread > 0) "Warnings, $unread unread" else "Warnings"
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClickLabel = "Open warnings", onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(20.dp), tint = ink)
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .border(2.dp, ringBehindBadge, CircleShape)
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
    modifier: Modifier = Modifier,
    superHoursLeft: Int? = null
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
        "$streakLabel · ${state.todayHours} of ${state.hoursRequired} hours rated today" +
            (superHoursLeft?.let { " · $it more for a Super day" } ?: "")
    // While a Super day is still reachable the count aims at 12, not the goal already met
    val countTarget = if (superHoursLeft != null) SUPER_DAY_HOURS else state.hoursRequired
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
                qualified = state.todayQualified,
                superLap = state.todayQualified && !state.beginnerMode
            )
            Text(
                "${state.todayHours}/$countTarget",
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
private fun MiniHourRing(hours: Int, hoursRequired: Int, qualified: Boolean, superLap: Boolean) {
    val track = Color.White.copy(alpha = 0.22f)   // on the band
    val fill = if (qualified) RingQualifiedGreen else HoneyGold
    val fraction = (hours.toFloat() / hoursRequired).coerceIn(0f, 1f)
    // Hours past the goal, as a share of the Super day lap (hours 9–12)
    val lapFraction = ((hours - hoursRequired).toFloat() / (SUPER_DAY_HOURS - STREAK_HOURS_REQUIRED))
        .coerceIn(0f, 1f)
    Canvas(Modifier.size(24.dp)) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        // The Super day lap here is the brief's fallback, a single 1.5dp SuperGold arc
        // sweeping extra/4, not the four-segment drawSuperLap: at this size its 4° gaps
        // come out under 1dp, so the segments blur into one thin dotted halo, and the
        // "/12" count beside the ring already says what the lap is for. It sits 1dp
        // outside the ring, spilling 2.5dp past the canvas into the strip's 8dp padding,
        // so neither the ring nor the strip's width changes. (Not yet checked on device.)
        if (superLap && lapFraction > 0f) {
            val lapStroke = 1.5.dp.toPx()
            val r = size.minDimension / 2f + 1.dp.toPx() + lapStroke / 2f
            drawArc(
                color = SuperGold,
                startAngle = -90f, sweepAngle = 360f * lapFraction, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                style = Stroke(width = lapStroke, cap = StrokeCap.Round)
            )
        }
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

// 10 of 12 with a Super day still reachable: the count reads "/12" and the gold lap arc
// sweeps half way. A 3-digit streak and an unread badge keep it the widest Master case.
@Preview(widthDp = 360, backgroundColor = 0xFF4A1D4F, showBackground = true)
@Composable
private fun BandTopRowSuperLapPreview() {
    BandTopRow(
        state = StreakState(
            currentStreak = 124, displayStreak = 124, todayHours = 10, todayQualified = true, extraToday = 2,
            beginnerMode = false, hoursRequired = 8, beginnerDaysCompleted = 0, savers = 3,
            recoveryOpen = false, recoveryDaysLeft = 0, todayEarnedSaver = false
        ),
        unread = 3, onStreakClick = {}, onBellClick = {}, onMenuClick = {},
        superHoursLeft = 2
    )
}

/** The row's height: the overlay draws it once; each tab reserves exactly this much under its band. */
val BandTopRowHeight = 40.dp

/** Placeholder under a tab's band where the (fixed) top row is drawn over it. */
@Composable
fun BandTopRowSpacer() {
    Spacer(Modifier.height(BandTopRowHeight))
}
