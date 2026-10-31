package com.example.beeing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.BeeButtonDefaults
import com.example.beeing.ui.BandTopRowSpacer
import com.example.beeing.ui.GlassPanel
import com.example.beeing.ui.designDensity
import com.example.beeing.ui.PillShape
import com.example.beeing.ui.DailyGoalDialog
import com.example.beeing.ui.LocalBandChrome
import com.example.beeing.ui.SaverExplainerDialog
import com.example.beeing.ui.SaverSlots
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.icons.SymTitle
import com.example.beeing.ui.icons.tagDisplayText
import com.example.beeing.ui.theme.LocalBeeExtras
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * HIVE TAB — the long game lives here:
 *  - monthly calendar of day outcomes (tinted dot built / 🌙 rest / hollow missed)
*  - add a past hour: any unrated hour of the last RECLAIM_WINDOW_HOURS clock
 *    hours (free, RECLAIM_PER_DAY a day, tags required, note optional, may cross
 *    midnight). Added hours show in every list but never count toward a streak
 *  - the full hive event log
 */
@Composable
fun StreaksTab(
    viewModel: RatingsViewModel,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val allRatings = viewModel.allRatings

    var spendsVersion by remember { mutableIntStateOf(0) }
    val reclaimSpends = remember(spendsVersion) { loadReclaimSpends(context) }
    val modeEvents = viewModel.modeEvents
    // Start of today, so a tab left open across midnight stops showing yesterday as
    // today (its ring, its "Super day" caption). The clock ticks in the effect, and the
    // state is written only when the day actually changes: no recomposition per tick.
    var todayStart by remember { mutableLongStateOf(startOfToday()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            val day = startOfToday()
            if (day != todayStart) todayStart = day
        }
    }
    val streakState = remember(allRatings, modeEvents, viewModel.refreshTrigger, spendsVersion, todayStart) {
        computeStreakState(allRatings, modeEvents)
    }
    val outcomes = remember(allRatings, modeEvents, viewModel.refreshTrigger, spendsVersion, todayStart) {
        computeDayOutcomes(allRatings, modeEvents)
    }
    val superDays = remember(allRatings, modeEvents, viewModel.refreshTrigger, spendsVersion, todayStart) {
        computeSuperDays(allRatings, modeEvents)
    }

    var monthOffset by remember { mutableIntStateOf(0) } // 0 = current month
    var showPickHour by remember { mutableStateOf(false) }
    var reclaimStartMillis by remember { mutableStateOf<Long?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var showSavers by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    // Day tapped on the calendar (start-of-day millis) → stats popup
    var statsDayMillis by remember { mutableStateOf<Long?>(null) }

    // Expired unrated hours that can still be added: hour starts 3..12 behind the
    // current hour (the last 2 are still normally ratable). Crosses midnight —
    // an early-morning reclaim can retroactively qualify yesterday. Most
    // recent first. Values are hour-start millis.
    val reclaimableHours = remember(allRatings, viewModel.refreshTrigger) {
        val currentHourStart = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val ratedStarts = allRatings.map { it.timestamp }.toSet()
        (3..RECLAIM_WINDOW_HOURS)
            .map { currentHourStart - it * 3_600_000L }
            .filter { it !in ratedStarts }
    }

    // Free, but two a day — so adding a past hour stays a repair, not a habit.
    val beesLeftToday = remember(spendsVersion, reclaimSpends) {
        (RECLAIM_PER_DAY - reclaimsUsedToday(reclaimSpends)).coerceAtLeast(0)
    }

    val x = LocalBeeExtras.current
    CompositionLocalProvider(LocalContentColor provides x.fieldInk, LocalDensity provides designDensity()) {
    // The whole tab sits on the night field, the same surface as Now, so the two
    // "you, right now" tabs swipe as one place. Containers on it are glass.
    Column(modifier = Modifier.fillMaxSize().background(x.fieldBrush)) {
        // ---- Hero: streak + today ring, floating on the field. Pinned above the
        // scroller, so nothing scrolls up under the fixed top row. ----
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
        ) {
            BandTopRowSpacer()
            Spacer(Modifier.height(14.dp))
            // Savers ride under the streak they protect: streak + savers is one tap
            // target (Streak and Savers), the ring is the other (Today's goal)
            StreakMeter(
                state = streakState,
                onStreakClick = { showSavers = true },
                onGoalClick = { showGoal = true },
                superHoursLeft = LocalBandChrome.current?.superHoursLeft
            )
            Spacer(Modifier.height(4.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

        // ---- ACTION: add a past hour — the one action on this tab, right under
        // the goal it protects; hidden when nothing to recover. The whole panel
        // is the tap target. Spent: faded and inert, the copy says why. ----
        if (reclaimableHours.isNotEmpty()) {
            val reclaimEnabled = beesLeftToday > 0
            GlassPanel(
                modifier = Modifier.alpha(if (reclaimEnabled) 1f else 0.55f),
                onClick = if (reclaimEnabled) ({ showPickHour = true }) else null,
                onClickLabel = "Pick an hour"
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(x.accentFill.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        BeeIcon(Sym.AddPast, size = 22.dp, tint = x.fieldHoney, contentDescription = null)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Missed an hour?",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (reclaimEnabled) "Add a past hour"
                            else "You've added $RECLAIM_PER_DAY past hours today. More tomorrow.",
                            fontSize = 13.sp,
                            color = x.fieldInkMuted
                        )
                    }
                }
            }
        }

        // ---- Monthly calendar, with its legend under a hairline ----
        GlassPanel(contentPadding = PaddingValues(12.dp)) {
            MonthHeader(
                monthOffset = monthOffset,
                onPrev = { monthOffset++ },
                onNext = { if (monthOffset > 0) monthOffset-- }
            )
            Spacer(Modifier.height(4.dp))
            // Slide toward the direction of travel: earlier months enter
            // from the left, later months from the right
            AnimatedContent(
                targetState = monthOffset,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                    } else {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                    }
                },
                label = "monthSlide"
            ) { offset ->
                MonthGrid(
                    monthOffset = offset,
                    outcomes = outcomes,
                    superDays = superDays,
                    onDayClick = { statsDayMillis = it }
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = x.fieldInk.copy(alpha = 0.08f))
            Spacer(Modifier.height(10.dp))
            CalendarLegend(beginner = streakState.beginnerMode)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(PillShape)
                    .clickable(onClickLabel = "How streaks work") { showRules = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info, contentDescription = null,
                    tint = x.fieldInkMuted, modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (streakState.beginnerMode) "How beginner mode works" else "How streaks work",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = x.fieldInkMuted
                )
            }
        }

        // ---- History (collapsed to a peek by default) ----
        StreakHistoryPanel(
            ratings = allRatings,
            modeEvents = modeEvents,
            refreshKey = viewModel.refreshTrigger + spendsVersion
        )

        Spacer(Modifier.height(24.dp)) // trailing breathing room (nav is a bottomBar)
        } // page column
    }
    }

    // ---- Pick which missed hour to add ----
    if (showPickHour) {
        AlertDialog(
            onDismissRequest = { showPickHour = false },
            title = { SymTitle(Sym.AddPast, "Which hour?") },
            text = {
                Column {
                    Text(
                        "You'll add a score and tags. $beesLeftToday left today.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    reclaimableHours.forEach { startMs ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    reclaimStartMillis = startMs
                                    showPickHour = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            // Muted fill: a plain card would vanish on the dialog surface.
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                reclaimHourLabel(startMs),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPickHour = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Add the hour: score + tags, optional note ----
    reclaimStartMillis?.let { startMs ->
        ReclaimHourDialog(
            startMillis = startMs,
            availableTags = remember { loadTags(context) },
            onDismiss = { reclaimStartMillis = null },
            onReclaim = { score, tags, note ->
                // Guard the daily cap here too: the dialog can outlive the
                // tap that opened it (rotation, a second hour added meanwhile).
                if (beesLeftToday <= 0) {
                    reclaimStartMillis = null
                    return@ReclaimHourDialog
                }
                val startHour = Calendar.getInstance()
                    .apply { timeInMillis = startMs }.get(Calendar.HOUR_OF_DAY)
                val endHour = startHour + 1
                val entry = RatingEntry(
                    System.currentTimeMillis(),
                    score,
                    startMs,
                    "$endHour${getOrdinalSuffix(endHour)}",
                    note,
                    tags + RECLAIM_TAG
                )
                viewModel.saveRating(context, entry)
                recordReclaimSpend(context)
                spendsVersion++
                reclaimStartMillis = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
    }

    // ---- Stats popup for a tapped calendar day ----
    statsDayMillis?.let { dayMillis ->
        val dayCal = Calendar.getInstance().apply { timeInMillis = dayMillis }
        val key = dayCal.get(Calendar.YEAR) * 1000L + dayCal.get(Calendar.DAY_OF_YEAR)
        DayStatsDialog(
            dayMillis = dayMillis,
            outcome = outcomes[key],
            ratings = allRatings,
            onDismiss = { statsDayMillis = null }
        )
    }

    if (showSavers) SaverExplainerDialog(streakState.savers, streakState.beginnerMode) { showSavers = false }
    if (showGoal) DailyGoalDialog(
        todayHours = streakState.todayHours,
        hoursRequired = streakState.hoursRequired,
        beginner = streakState.beginnerMode
    ) { showGoal = false }

    // ---- The one explainer: how streaks work ----
    if (showRules) {
        val beginner = streakState.beginnerMode
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = {
                SymTitle(
                    if (beginner) Sym.Beginner else Sym.Fire,
                    if (beginner) "How beginner mode works" else "How streaks work"
                )
            },
            text = {
                Column {
                    if (beginner) {
                        Text("• Rate $BEGINNER_HOURS_REQUIRED hours in a day and it's a beginner day — one goal, no pressure.", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (streakState.currentStreak > 0)
                                "• Beginner days sit outside the streak: yours is paused at ${streakState.currentStreak} and picks up there in Master mode. A short day can't break it."
                            else "• Beginner days sit outside the streak — nothing to break, nothing to lose. Streaks start once you switch to Master mode.",
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("• Master mode asks for $STREAK_HOURS_REQUIRED hours a day and turns complete days into a streak. Savers neither earn nor fade in beginner mode. Switch any time from Settings.", fontSize = 14.sp)
                    } else {
                        Text("• Rate $STREAK_HOURS_REQUIRED hours in a day and that day is complete — complete days are your streak.", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("• Rate $SUPER_DAY_HOURS hours and it's a Super day, which earns a streak saver (up to $SAVER_CAP). Miss a day and a saver steps in, so your streak still grows.", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("• With no streak running, a missed day makes a saver fade — so start one. $COMEBACK_IDLE_DAYS days in a row without a complete day earns $COMEBACK_GIFT savers on the house.", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("• Out of savers? The streak breaks, but you get $RECOVERY_DAYS days to win it back: rate $RECOVERY_HOURS hours on any one of them.", fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("• Missed an hour? Add a past hour from the last $RECLAIM_WINDOW_HOURS — even into yesterday. $RECLAIM_PER_DAY a day, free. Added hours stay in your record but don't count toward the ${streakState.hoursRequired}.", fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showRules = false }) { Text("Got it!") }
            }
        )
    }
}

@Composable
private fun MonthHeader(monthOffset: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val x = LocalBeeExtras.current
    val cal = Calendar.getInstance().apply {
        add(Calendar.MONTH, -monthOffset)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val fmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.KeyboardArrowLeft, "Earlier month", tint = x.fieldInk) }
        Text(fmt.format(cal.time), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = monthOffset > 0) {
            Icon(
                Icons.Default.KeyboardArrowRight, "Later month",
                tint = if (monthOffset > 0) x.fieldInk else x.fieldInk.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun MonthGrid(
    monthOffset: Int,
    outcomes: Map<Long, DayOutcome>,
    superDays: Set<Long>,
    onDayClick: (Long) -> Unit
) {
    val monthStart = Calendar.getInstance().apply {
        add(Calendar.MONTH, -monthOffset)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leadingBlanks = (monthStart.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
    val today = Calendar.getInstance()
    val todayKey = today.get(Calendar.YEAR) * 1000L + today.get(Calendar.DAY_OF_YEAR)

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalBeeExtras.current.fieldInkMuted
                )
            }
        }
        var day = 1
        while (day <= daysInMonth) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    // leading blanks only apply on the first row
                    val inLead = day == 1 && col < leadingBlanks
                    if (inLead || day > daysInMonth) {
                        Box(Modifier.weight(1f).height(44.dp))
                    } else {
                        val cellCal = (monthStart.clone() as Calendar).apply {
                            set(Calendar.DAY_OF_MONTH, day)
                        }
                        val key = cellCal.get(Calendar.YEAR) * 1000L + cellCal.get(Calendar.DAY_OF_YEAR)
                        val dayMillis = cellCal.timeInMillis
                        DayCell(
                            dayNumber = day,
                            outcome = outcomes[key],
                            superDay = key in superDays,
                            isToday = key == todayKey,
                            enabled = key <= todayKey,
                            onClick = { onDayClick(dayMillis) },
                            modifier = Modifier.weight(1f)
                        )
                        day++
                    }
                }
            }
        }
    }
}

/** What a calendar tile shows. The legend draws the same kinds, so it matches exactly. */
private enum class TileKind { Complete, Super, Saved, Missed, Beginner, Plain, Future }

private fun tileKind(outcome: DayOutcome?, superDay: Boolean, enabled: Boolean): TileKind = when {
    outcome == DayOutcome.QUALIFIED || outcome == DayOutcome.RECOVERED ->
        if (superDay) TileKind.Super else TileKind.Complete
    outcome == DayOutcome.SAVED -> TileKind.Saved
    outcome == DayOutcome.MISSED || outcome == DayOutcome.BROKEN -> TileKind.Missed
    outcome == DayOutcome.BEGINNER_COMPLETE -> TileKind.Beginner
    enabled -> TileKind.Plain
    else -> TileKind.Future
}

/**
 * One calendar tile, shared by the grid and the legend. A complete day is a FILLED
 * honey tile (the streak's colour, never the score bands: a complete 4.5 day must
 * not read as a red alarm next to the hollow red missed tile), so a run reads as one
 * block. A Super day is a complete tile with a thin Super-gold rim and a small spark
 * in its top-right corner ("complete, and then some"), glowing gold in dark mode. A
 * saved day is a honey wash with the saver glyph, a beginner day carries the leaf, a
 * missed day is a hollow red outline, any other past day faint glass, and today an
 * outline in field ink. Colour never travels alone: fill vs. outline vs. glyph carry
 * the outcome, and tapping a day opens the stats dialog with digits.
 */
@Composable
private fun DayTile(
    kind: TileKind,
    modifier: Modifier = Modifier,
    isToday: Boolean = false,
    corner: Dp = 9.dp,
    glyphSize: Dp = 9.dp,
    sparkSize: Dp = 10.dp,
    glyphCentered: Boolean = false,
    // The click (and its ripple) belongs to the clipped tile, not the box around it:
    // clipping the outer box would also cut off the Super day spark and glow.
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val x = LocalBeeExtras.current
    val shape = RoundedCornerShape(corner)
    val fill = when (kind) {
        TileKind.Complete, TileKind.Super -> x.accentFill
        TileKind.Saved -> x.accentFill.copy(alpha = 0.22f)
        TileKind.Plain, TileKind.Beginner -> x.fieldInk.copy(alpha = 0.06f)
        TileKind.Missed, TileKind.Future -> Color.Transparent
    }
    val glyphAt = if (glyphCentered) Modifier else Modifier.padding(bottom = 3.dp)
    val glyphAlign = if (glyphCentered) Alignment.Center else Alignment.BottomCenter
    val border = when {
        isToday -> BorderStroke(1.5.dp, x.fieldInk)
        kind == TileKind.Super -> BorderStroke(1.5.dp, x.fieldSuper)
        kind == TileKind.Missed -> BorderStroke(1.5.dp, x.fieldRed.copy(alpha = 0.85f))
        else -> null
    }
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (kind == TileKind.Super && x.fieldDark)
                        Modifier.shadow(6.dp, shape, ambientColor = x.fieldSuper, spotColor = x.fieldSuper)
                    else Modifier
                )
                .clip(shape)
                .background(fill)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        )
        when (kind) {
            TileKind.Saved -> BeeIcon(
                Sym.Saver, size = glyphSize, tint = x.fieldHoney, contentDescription = null,
                modifier = Modifier.align(glyphAlign).then(glyphAt)
            )
            TileKind.Beginner -> BeeIcon(
                Sym.Beginner, size = glyphSize, tint = x.fieldInk, contentDescription = null,
                modifier = Modifier.align(glyphAlign).then(glyphAt)
            )
            else -> Unit
        }
        content()
        if (kind == TileKind.Super) {
            // Rides on the corner, half outside the tile, so it is never clipped
            BeeIcon(
                Sym.Sparkle, size = sparkSize, tint = x.fieldSuper, contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = sparkSize * 0.25f, y = -sparkSize * 0.25f)
            )
        }
    }
}

@Composable
private fun DayCell(
    dayNumber: Int,
    outcome: DayOutcome?,
    superDay: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val x = LocalBeeExtras.current
    val kind = tileKind(outcome, superDay, enabled)
    val ink = when (kind) {
        TileKind.Complete, TileKind.Super -> x.onAccentFill
        TileKind.Saved -> x.fieldHoney
        TileKind.Missed -> x.fieldRed
        TileKind.Future -> x.fieldInk.copy(alpha = 0.3f)
        else -> if (isToday) x.fieldInk else x.fieldInkMuted
    }
    DayTile(
        kind = kind,
        isToday = isToday,
        modifier = modifier
            .padding(2.dp)
            .height(40.dp),
        onClick = if (enabled) onClick else null
    ) {
        Text(
            "$dayNumber",
            fontSize = 12.sp,
            color = ink,
            fontWeight = if (isToday || kind == TileKind.Complete || kind == TileKind.Super) FontWeight.SemiBold
            else FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** The legend: a 2x2 grid of mini tiles drawn by [DayTile], plus "Beginner day" in beginner mode. */
@Composable
private fun CalendarLegend(beginner: Boolean) {
    val keys = buildList {
        add(TileKind.Complete to "Complete")
        add(TileKind.Super to "Super day")
        add(TileKind.Saved to "Saved")
        add(TileKind.Missed to "Missed")
        if (beginner) add(TileKind.Beginner to "Beginner day")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                pair.forEach { (kind, label) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        DayTile(
                            kind = kind, modifier = Modifier.size(15.dp),
                            corner = 4.dp, glyphSize = 10.dp, sparkSize = 8.dp, glyphCentered = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = LocalBeeExtras.current.fieldInkMuted
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "9 - 10 PM" with a "Yesterday" prefix when the hour start falls before today. */
private fun reclaimHourLabel(startMillis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = startMillis }
    val h = c.get(Calendar.HOUR_OF_DAY)
    val prefix = if (isSameDay(c, Calendar.getInstance())) "" else "Yesterday · "
    return "$prefix${formatHour(h)} - ${formatHour((h + 1) % 24)}"
}

/** Stats popup for a tapped calendar day. */
@Composable
private fun DayStatsDialog(
    dayMillis: Long,
    outcome: DayOutcome?,
    ratings: List<RatingEntry>,
    onDismiss: () -> Unit
) {
    val dayCal = remember(dayMillis) { Calendar.getInstance().apply { timeInMillis = dayMillis } }
    val entries = remember(dayMillis, ratings) {
        ratings.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            isSameDay(c, dayCal)
        }
    }
    val distinctHours = remember(entries) {
        entries.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }.distinct().size
    }
    val avg = if (entries.isEmpty()) 0.0 else entries.map { it.score }.average()
    val best = entries.maxWithOrNull(compareBy<RatingEntry> { it.score }.thenBy { it.timestamp })
    val reclaimed = entries.count { it.isReclaimed }
    val isToday = isSameDay(Calendar.getInstance(), dayCal)

    val title = remember(dayMillis) {
        SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(dayCal.time)
    }
    val outcomeSym = when {
        outcome == DayOutcome.QUALIFIED || outcome == DayOutcome.RECOVERED -> Sym.Streak
        outcome == DayOutcome.BEGINNER_COMPLETE -> Sym.Beginner
        outcome == DayOutcome.SAVED -> Sym.Saver
        outcome == null && isToday -> Sym.Hourglass
        else -> null   // missed / nothing rated: the words stand alone
    }
    val outcomeLine = when {
        outcome == DayOutcome.QUALIFIED -> "Day complete"
        outcome == DayOutcome.RECOVERED -> "Streak recovered"
        outcome == DayOutcome.BEGINNER_COMPLETE -> "Beginner day complete"
        outcome == DayOutcome.SAVED -> "Saver used — the streak still grew"
        outcome == DayOutcome.BROKEN -> "Streak broke"
        outcome == DayOutcome.MISSED || outcome == DayOutcome.FADED -> "Missed"
        isToday -> "In progress"
        else -> "No ratings"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    outcomeSym?.let {
                        BeeIcon(it, size = 16.dp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(outcomeLine, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                if (entries.isEmpty()) {
                    Text(
                        "Nothing was rated this day.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    StatRow("Hours rated", "$distinctHours / 24")
                    StatRow("Average score", String.format("%.1f", avg), valueColor = scoreTextColor(avg))
                    best?.let {
                        val h = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                            .get(Calendar.HOUR_OF_DAY)
                        StatRow("Best hour", "${formatHour(h)} - ${formatHour((h + 1) % 24)} · ${it.score}")
                    }
                    if (reclaimed > 0) StatRow("Rated from memory", "$reclaimed")
                    val topTags = entries.flatMap { it.visibleTags() }
                        .groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.value }
                        .take(3)
                    if (topTags.isNotEmpty()) {
                        StatRow("Top tags", topTags.joinToString(", ") { tagDisplayText(it.key) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/**
 * Adding a past hour: a score, at least one tag (the same chips as the Now tab),
 * and an optional note. The entry is stored with RECLAIM_TAG, which keeps it out
 * of every streak count while it still shows in the journal and Trends.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReclaimHourDialog(
    startMillis: Long,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onReclaim: (score: Int, tags: List<String>, note: String) -> Unit
) {
    var score by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { SymTitle(Sym.AddPast, reclaimHourLabel(startMillis)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("How was that hour?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..10).forEach { s ->
                        val isSelected = score == s
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 48.dp else 40.dp)
                                .clip(CircleShape)
                                .background(scoreBandColor(s).copy(alpha = if (isSelected) 1f else 0.3f))
                                .clickable { score = s },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$s",
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = if (isSelected) 18.sp else 14.sp,
                                color = if (s >= 5) Color.Black else Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("What were you doing?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    availableTags.forEach { tag ->
                        InputChip(
                            selected = tag in selectedTags,
                            onClick = {
                                if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                            },
                            label = { TagChipContent(tag) },
                            shape = TagChipShape,
                            colors = tagChipColors(),
                            border = null
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Add a note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                colors = BeeButtonDefaults.colors(),
                onClick = { score?.let { onReclaim(it, selectedTags.toList(), note.trim()) } },
                enabled = score != null && selectedTags.isNotEmpty()
            ) { Text("Add hour") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Epoch ms of local midnight today. */
private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
