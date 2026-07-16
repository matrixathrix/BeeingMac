package com.example.beeing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * STREAKS TAB — the long game lives here:
 *  - monthly calendar of day outcomes (💐 qualified / 🛡️ saved / 🥀 missed)
 *  - savers + flower wallet
 *  - reclaim a missed hour from today (5 🌸, mandatory note)
 *  - the full streak event log
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
    val streakState = remember(allRatings, viewModel.refreshTrigger, spendsVersion) {
        computeStreakState(allRatings, reclaimSpends)
    }
    val dayDetails = remember(allRatings, viewModel.refreshTrigger, spendsVersion) {
        computeDayDetails(allRatings, reclaimSpends)
    }

    var monthOffset by remember { mutableIntStateOf(0) } // 0 = current month
    var showPickHour by remember { mutableStateOf(false) }
    var reclaimStartHour by remember { mutableStateOf<Int?>(null) }
    var showRules by remember { mutableStateOf(false) }
    // (title, body) of the currently open quick explainer, if any
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Day tapped on the calendar (start-of-day millis) → stats popup
    var statsDayMillis by remember { mutableStateOf<Long?>(null) }

    // Today's expired unrated hours (start hours 0..H-3), most recent first
    val missedHoursToday = remember(allRatings, viewModel.refreshTrigger) {
        val today = Calendar.getInstance()
        val currentHour = today.get(Calendar.HOUR_OF_DAY)
        val ratedStarts = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            isSameDay(cal, today)
        }.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }.toSet()
        ((currentHour - 3) downTo 0).filter { it !in ratedStarts }
    }

    val canAfford = streakState.bankProgress >= RECLAIM_COST

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            // No app header on this tab — it clears the status bar itself
            // and keeps every reclaimed pixel for its own content.
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Monthly calendar (info) ----
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appCardColor())
        ) {
            Column(Modifier.padding(16.dp)) {
                // Nothing before the first rated month — don't let the
                // calendar scrub back into empty history.
                val earliestRatingTs = remember(allRatings) { allRatings.minOfOrNull { it.timestamp } }
                val viewedMonthStart = remember(monthOffset) {
                    Calendar.getInstance().apply {
                        add(Calendar.MONTH, -monthOffset)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                val canGoEarlierMonth = earliestRatingTs != null && viewedMonthStart > earliestRatingTs
                val monthController = rememberSwipeScrubController(
                    onEarlier = { if (canGoEarlierMonth) monthOffset++ },
                    onLater = { if (monthOffset > 0) monthOffset-- }
                )
                MonthHeader(
                    monthOffset = monthOffset,
                    controller = monthController,
                    canPrev = canGoEarlierMonth,
                    onPrev = { monthController.stepEarlier() },
                    onNext = { monthController.stepLater() }
                )
                Spacer(Modifier.height(8.dp))
                // Live finger-tracking: the current month and whichever
                // neighbor is being dragged toward both move with the touch.
                SwipeScrubStack(
                    controller = monthController,
                    canEarlier = canGoEarlierMonth,
                    canLater = monthOffset > 0,
                    current = {
                        MonthGrid(monthOffset = monthOffset, dayDetails = dayDetails, onDayClick = { statsDayMillis = it })
                    },
                    earlierPreview = {
                        if (canGoEarlierMonth) {
                            MonthGrid(monthOffset = monthOffset + 1, dayDetails = dayDetails, onDayClick = { statsDayMillis = it })
                        } else {
                            Box(Modifier.fillMaxWidth())
                        }
                    },
                    laterPreview = {
                        if (monthOffset > 0) {
                            MonthGrid(monthOffset = monthOffset - 1, dayDetails = dayDetails, onDayClick = { statsDayMillis = it })
                        } else {
                            Box(Modifier.fillMaxWidth())
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem("💐", "streak day")
                    LegendItem("🛡️", "saved")
                    LegendItem("🥀", "missed")
                    IconButton(onClick = { showRules = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "How streaks work",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ---- Savers & flowers at a glance — tap a card for a quick explainer ----
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoMiniCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = {
                    infoDialog = "🛡️ Savers" to
                            "A missed day spends one saver automatically and your streak survives. " +
                            "None left = streak resets. $HOURS_PER_SAVER 🌸 forge a new one."
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 0 until MAX_SAVERS) {
                        Text(
                            "🛡️",
                            fontSize = 20.sp,
                            modifier = Modifier.alpha(if (i < streakState.savers) 1f else 0.22f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${streakState.savers} / $MAX_SAVERS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                InfoMiniCaption("Savers")
            }

            InfoMiniCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = {
                    infoDialog = "🌸 Flowers" to
                            "Hours beyond 8 in a day each earn a flower. " +
                            "Spend $RECLAIM_COST to rate an hour you missed today."
                }
            ) {
                Text("🌸", fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${streakState.bankProgress} / $FLOWER_CAP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                InfoMiniCaption("Flowers")
            }
        }

        // ---- ACTION: reclaim a missed hour from today ----
        // Reads as a button: bright + bordered + chevron when available,
        // outlined + dimmed + lock when not.
        val reclaimEnabled = canAfford && missedHoursToday.isNotEmpty()
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (reclaimEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            ),
            border = if (reclaimEnabled)
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
            modifier = Modifier.clickable(enabled = reclaimEnabled) { showPickHour = true }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .alpha(if (reclaimEnabled) 1f else 0.55f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💧", fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Rate an older hour from today",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (reclaimEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    val subtitle = when {
                        missedHoursToday.isEmpty() -> "No missed hours today 🎉"
                        !canAfford -> "Needs $RECLAIM_COST 🌸 — you have ${streakState.bankProgress}"
                        else -> "${missedHoursToday.size} missed hour${if (missedHoursToday.size == 1) "" else "s"} · $RECLAIM_COST 🌸 each"
                    }
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = if (reclaimEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (reclaimEnabled) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "Pick an hour",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Unavailable",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ---- Streak log (collapsed by default) ----
        var logExpanded by remember { mutableStateOf(false) }
        val logChevron by animateFloatAsState(if (logExpanded) 180f else 0f, label = "logChevron")
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appCardColor())
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { logExpanded = !logExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Streak log",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (logExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(logChevron)
                    )
                }
                AnimatedVisibility(
                    visible = logExpanded,
                    enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) +
                            fadeIn(tween(220, delayMillis = 80)),
                    exit = shrinkVertically(tween(280, easing = FastOutSlowInEasing)) +
                            fadeOut(tween(120))
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        StreakLogContent(
                            ratings = allRatings,
                            refreshKey = viewModel.refreshTrigger + spendsVersion
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp)) // clearance for the floating nav pill
    }

    // ---- Pick which missed hour to reclaim ----
    if (showPickHour) {
        AlertDialog(
            onDismissRequest = { showPickHour = false },
            title = { Text("Pick an hour to reclaim") },
            text = {
                Column {
                    Text(
                        "Each costs $RECLAIM_COST 🌸. You'll add a score and a short note.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    missedHoursToday.forEach { h ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    reclaimStartHour = h
                                    showPickHour = false
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "${formatHour(h)} - ${formatHour(h + 1)}",
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

    // ---- Reclaim ceremony (mandatory note) ----
    reclaimStartHour?.let { startHour ->
        ReclaimHourDialog(
            startHour = startHour,
            onDismiss = { reclaimStartHour = null },
            onReclaim = { score, note ->
                val entry = RatingEntry(
                    System.currentTimeMillis(),
                    score,
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis,
                    ordinalHourLabel(startHour),
                    note,
                    listOf(RECLAIM_TAG)
                )
                viewModel.saveRating(context, entry)
                recordReclaimSpend(context)
                spendsVersion++
                reclaimStartHour = null
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
            detail = dayDetails[key],
            ratings = allRatings,
            onDismiss = { statsDayMillis = null }
        )
    }

    // ---- Quick explainer for a tapped info card ----
    infoDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(title) },
            text = { Text(body, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) { Text("Got it") }
            }
        )
    }

    // ---- Rules ----
    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("How streaks work") },
            text = {
                Column {
                    Text("• Rate at least 8 hours in a day to keep your streak.", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("• Each extra hour beyond 8 gives you a 🌸 flower (max $FLOWER_CAP). $HOURS_PER_SAVER flowers become a 🛡️ saver (max $MAX_SAVERS).", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("• Miss a day and one saver is used automatically. With no savers left, the streak resets.", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("• Spend $RECLAIM_COST 🌸 to rate an hour you missed today. Those hours count toward the 8 but don't earn flowers.", fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showRules = false }) { Text("Got it!") }
            }
        )
    }
}

@Composable
private fun MonthHeader(
    monthOffset: Int,
    controller: SwipeScrubController,
    canPrev: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
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
        IconButton(onClick = onPrev, enabled = canPrev) {
            Icon(
                Icons.Default.KeyboardArrowLeft, "Earlier month",
                tint = if (canPrev) LocalContentColor.current else Color.Gray
            )
        }
        ScrubLabel(
            controller,
            fmt.format(cal.time),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext, enabled = monthOffset > 0) {
            Icon(
                Icons.Default.KeyboardArrowRight, "Later month",
                tint = if (monthOffset > 0) LocalContentColor.current else Color.Gray
            )
        }
    }
}

@Composable
private fun MonthGrid(
    monthOffset: Int,
    dayDetails: Map<Long, DayDetail>,
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Box(Modifier.weight(1f).height(58.dp))
                    } else {
                        val cellCal = (monthStart.clone() as Calendar).apply {
                            set(Calendar.DAY_OF_MONTH, day)
                        }
                        val key = cellCal.get(Calendar.YEAR) * 1000L + cellCal.get(Calendar.DAY_OF_YEAR)
                        val dayMillis = cellCal.timeInMillis
                        DayCell(
                            dayNumber = day,
                            outcome = dayDetails[key]?.outcome,
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

@Composable
private fun DayCell(
    dayNumber: Int,
    outcome: DayOutcome?,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .then(
                if (isToday) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$dayNumber",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            when (outcome) {
                DayOutcome.QUALIFIED -> "💐"
                DayOutcome.SAVED -> "🛡️"
                DayOutcome.MISSED -> "🥀"
                null -> " "
            },
            fontSize = 24.sp
        )
    }
}

/**
 * Small untitled info card: centered content, whole card tappable to open a
 * quick explainer. Flat surfaceVariant fill = "info", vs the bordered/bright
 * action card.
 */
@Composable
private fun InfoMiniCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColor())
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** Caption under a mini card's number, with a subtle ⓘ hinting it's tappable. */
@Composable
private fun InfoMiniCaption(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        Icon(
            Icons.Default.Info,
            contentDescription = "About $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(12.dp)
        )
    }
}

/** Stats popup for a tapped calendar day. */
@Composable
private fun DayStatsDialog(
    dayMillis: Long,
    detail: DayDetail?,
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
    val reclaimed = entries.count { RECLAIM_TAG in it.tags }
    // Reclaims only ever apply to today's own missed hours, so this is exact
    // without needing the raw spend log threaded down here.
    val flowersSpent = reclaimed * RECLAIM_COST
    val isToday = isSameDay(Calendar.getInstance(), dayCal)

    val title = remember(dayMillis) {
        SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(dayCal.time)
    }
    val outcomeLine = when {
        detail?.outcome == DayOutcome.QUALIFIED -> "💐 Streak day ${detail.streakDay}"
        detail?.outcome == DayOutcome.SAVED -> "🛡️ Missed, but a saver covered it"
        detail?.outcome == DayOutcome.MISSED -> "🥀 Missed"
        isToday -> "⏳ In progress"
        else -> "No ratings"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(outcomeLine, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                if ((detail?.flowersEarned ?: 0) > 0) {
                    StatRow("Flowers earned", "+${detail!!.flowersEarned} 🌸")
                }
                if ((detail?.saversEarned ?: 0) > 0) {
                    val n = detail!!.saversEarned
                    StatRow("Saver${if (n == 1) "" else "s"} earned", "+$n 🛡️")
                }
                if (detail?.saverUsed == true) StatRow("Saver used", "-1 🛡️")
                if (flowersSpent > 0) StatRow("Flowers spent", "-$flowersSpent 🌸 (reclaimed an hour)")

                if (entries.isEmpty()) {
                    Text(
                        "Nothing was rated this day.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    StatRow("Hours rated", "$distinctHours / 24")
                    StatRow("Average score", String.format("%.1f", avg), valueColor = getScoreColor(avg))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    // The day's rated hours in the same layout Recent History
                    // uses — time range, tags, note, score dot.
                    Column(
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val ordered = entries.sortedByDescending { it.timestamp }
                        ordered.forEachIndexed { i, entry ->
                            RatedHourRow(entry, showDate = false)
                            if (i < ordered.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                            }
                        }
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

@Composable
private fun LegendItem(emoji: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The reclaim ceremony: rating a lost hour requires actually re-entering
 * the memory — the reflection note is mandatory.
 */
@Composable
fun ReclaimHourDialog(
    startHour: Int,
    onDismiss: () -> Unit,
    onReclaim: (score: Int, note: String) -> Unit
) {
    var score by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💧 Reclaim ${formatHour(startHour)} - ${formatHour(startHour + 1)}") },
        text = {
            Column {
                Text("What were you doing?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("A short note is required") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
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
            }
        },
        confirmButton = {
            Button(
                onClick = { score?.let { onReclaim(it, note.trim()) } },
                enabled = score != null && note.isNotBlank()
            ) { Text("Reclaim · $RECLAIM_COST 🌸") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
