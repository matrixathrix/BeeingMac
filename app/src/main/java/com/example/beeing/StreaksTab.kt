package com.example.beeing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
 *  - monthly calendar of day outcomes (💐 qualified / 🍯 saved / 🥀 missed)
 *  - honey pots + flower wallet
 *  - reclaim a missed hour from today (5 🌸, mandatory note)
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
    val streakState = remember(allRatings, viewModel.refreshTrigger, spendsVersion) {
        computeStreakState(allRatings, reclaimSpends)
    }
    val outcomes = remember(allRatings, viewModel.refreshTrigger, spendsVersion) {
        computeDayOutcomes(allRatings, reclaimSpends)
    }
    // Per-day average score — tints the calendar dots
    val dayAvgs = remember(allRatings, viewModel.refreshTrigger) {
        val cal = Calendar.getInstance()
        allRatings.groupBy { entry ->
            cal.timeInMillis = entry.timestamp
            cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
        }.mapValues { (_, entries) -> entries.map { it.score }.average() }
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
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Hero: streak + today ring — the Now tab's status strip, expanded ----
        StreakMeter(
            state = streakState,
            expanded = false
        )

        // ---- ACTION: reclaim a missed hour — the one action on this tab,
        // right under the goal it protects; hidden when nothing to recover ----
        if (missedHoursToday.isNotEmpty()) {
            val reclaimEnabled = canAfford
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
                        Text(
                            if (reclaimEnabled)
                                "${missedHoursToday.size} missed hour${if (missedHoursToday.size == 1) "" else "s"} · $RECLAIM_COST 🌸 each"
                            else "Needs $RECLAIM_COST 🌸 — you have ${streakState.bankProgress}",
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
        }

        // ---- Garden: flowers and honey pots are ONE economy — ten slots fill
        // left to right and crystallize into the next honey pot ----
        GardenCard(
            state = streakState,
            onClick = {
                infoDialog = "🌸 The Garden" to
                        "Each hour you rate beyond 8 in a day grows a flower (bank holds $FLOWER_CAP).\n\n" +
                        "$HOURS_PER_SAVER flowers automatically fill a 🍯 honey pot (max $MAX_SAVERS). " +
                        "Miss a day and one honey pot is spent for you — your hive survives. " +
                        "No honey pots left? The hive resets.\n\n" +
                        "You can also spend $RECLAIM_COST 🌸 to rate an hour you missed today."
            }
        )

        // ---- Monthly calendar (info) ----
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                MonthHeader(
                    monthOffset = monthOffset,
                    onPrev = { monthOffset++ },
                    onNext = { if (monthOffset > 0) monthOffset-- }
                )
                Spacer(Modifier.height(8.dp))
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
                        dayAvgs = dayAvgs,
                        onDayClick = { statsDayMillis = it }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(color = getScoreColor(6.0), hollow = false, label = "hive day")
                    LegendItem("🍯", "saved")
                    LegendDot(color = Color(0xFFC62828), hollow = true, label = "missed")
                    IconButton(onClick = { showRules = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "How the hive works",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ---- Streak log (collapsed by default) ----
        var logExpanded by remember { mutableStateOf(false) }
        val logChevron by animateFloatAsState(if (logExpanded) 180f else 0f, label = "logChevron")
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { logExpanded = !logExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Hive log",
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
                if (logExpanded) {
                    Spacer(Modifier.height(8.dp))
                    StreakLogContent(
                        ratings = allRatings,
                        refreshKey = viewModel.refreshTrigger + spendsVersion
                    )
                }
            }
        }

        Spacer(Modifier.height(112.dp)) // clearance for the floating nav pill
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
                val endHour = startHour + 1
                val entry = RatingEntry(
                    System.currentTimeMillis(),
                    score,
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis,
                    "$endHour${getOrdinalSuffix(endHour)}",
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
            outcome = outcomes[key],
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
            title = { Text("How the hive works") },
            text = {
                Column {
                    Text("• Rate at least 8 hours in a day to build a cell — your hive grows one cell per day.", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("• Each extra hour beyond 8 gives you a 🌸 flower (max $FLOWER_CAP). $HOURS_PER_SAVER flowers fill a 🍯 honey pot (max $MAX_SAVERS).", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("• Miss a day and one honey pot is used automatically. With no honey pots left, the hive resets.", fontSize = 14.sp)
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
private fun MonthHeader(monthOffset: Int, onPrev: () -> Unit, onNext: () -> Unit) {
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
        IconButton(onClick = onPrev) { Icon(Icons.Default.KeyboardArrowLeft, "Earlier month") }
        Text(fmt.format(cal.time), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
    outcomes: Map<Long, DayOutcome>,
    dayAvgs: Map<Long, Double>,
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
                            outcome = outcomes[key],
                            avgScore = dayAvgs[key],
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

/**
 * A calendar day. Repeated identical emoji carry no information, so a hive
 * day shows a dot tinted by that day's average score, a saved day shows the
 * small honey pot, and a missed day is a hollow red ring. Today gets the
 * primary-color outline. Color never travels alone here — tapping any day
 * opens the stats dialog with the digits.
 */
@Composable
private fun DayCell(
    dayNumber: Int,
    outcome: DayOutcome?,
    avgScore: Double?,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .then(
                if (isToday) Modifier
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$dayNumber",
            fontSize = 12.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(4.dp))
        when (outcome) {
            DayOutcome.QUALIFIED -> Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(getScoreColor(avgScore ?: 0.0))
            )
            DayOutcome.SAVED -> Text("🍯", fontSize = 11.sp)
            DayOutcome.MISSED -> Box(
                Modifier
                    .size(9.dp)
                    .border(1.5.dp, Color(0xFFC62828), CircleShape)
            )
            null -> Spacer(Modifier.size(9.dp))
        }
    }
}

/**
 * The Garden: flowers and honey pots rendered as one economy. Ten slots fill
 * left to right; every full row of ten crystallizes into the next honey pot.
 * The whole card is tappable for the explainer.
 */
@Composable
private fun GardenCard(
    state: StreakState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "GARDEN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Info,
                    contentDescription = "About the garden",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Flower slots toward the next saver
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val filled = state.bankProgress.coerceAtMost(HOURS_PER_SAVER)
                    for (i in 0 until HOURS_PER_SAVER) {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (i < filled) Text("🌸", fontSize = 11.sp)
                        }
                    }
                }
                Text(
                    "  →  ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 0 until MAX_SAVERS) {
                        Text(
                            "🍯",
                            fontSize = 17.sp,
                            modifier = Modifier.alpha(if (i < state.savers) 1f else 0.22f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (state.savers >= MAX_SAVERS)
                    "Honey pots full · ${state.bankProgress}/$FLOWER_CAP 🌸 banked"
                else
                    "${(HOURS_PER_SAVER - state.bankProgress).coerceAtLeast(0)} more 🌸 fill your next 🍯 · " +
                            "$RECLAIM_COST 🌸 reclaims a missed hour",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
    val reclaimed = entries.count { RECLAIM_TAG in it.tags }
    val flowers = (distinctHours - STREAK_HOURS_REQUIRED - reclaimed).coerceAtLeast(0)
    val isToday = isSameDay(Calendar.getInstance(), dayCal)

    val title = remember(dayMillis) {
        SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(dayCal.time)
    }
    val outcomeLine = when {
        outcome == DayOutcome.QUALIFIED -> "💐 Streak day"
        outcome == DayOutcome.SAVED -> "🍯 Missed, but a honey pot covered it"
        outcome == DayOutcome.MISSED -> "🥀 Missed"
        isToday -> "⏳ In progress"
        else -> "No ratings"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(outcomeLine, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (entries.isEmpty()) {
                    Text(
                        "Nothing was rated this day.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    StatRow("Hours rated", "$distinctHours / 24")
                    StatRow("Average score", String.format("%.1f", avg), valueColor = getScoreColor(avg))
                    best?.let {
                        val h = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                            .get(Calendar.HOUR_OF_DAY)
                        StatRow("Best hour", "${formatHour(h)} - ${formatHour((h + 1) % 24)} · ${it.score}")
                    }
                    if (flowers > 0) StatRow("Flowers earned", "+$flowers 🌸")
                    if (reclaimed > 0) StatRow("Reclaimed hours", "$reclaimed 💧")
                    val topTags = entries.flatMap { it.tags }
                        .filter { it != RECLAIM_TAG }
                        .groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.value }
                        .take(3)
                    if (topTags.isNotEmpty()) {
                        StatRow("Top tags", topTags.joinToString(", ") { it.key })
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

@Composable
private fun LegendDot(color: Color, hollow: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .then(
                    if (hollow) Modifier.border(1.5.dp, color, CircleShape)
                    else Modifier.clip(CircleShape).background(color)
                )
        )
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
