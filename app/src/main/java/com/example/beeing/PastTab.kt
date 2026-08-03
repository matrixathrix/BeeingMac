package com.example.beeing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * PAST TAB — a journal first, analytics second.
 *
 *   The default view is the log itself: one card per rated day, newest first,
 *   each showing the day's average and a tick-strip of its hours. Tapping a day
 *   opens its hours inline; tapping an hour opens the edit sheet. The header's
 *   date-jump puts any past hour two taps away.
 *
 *   Analytics live behind the header's "Trends" button, which opens as a
 *   full-screen view over the journal (system back returns). Trends is for the
 *   rare deep-dive, so it is deliberately short: summary stats, one written
 *   line of insight, the bar chart, the hour-by-hour grid, the tag table.
 *   It zooms only to Weeks (across a month) and Months (across a year) —
 *   there is no day level, because looking up a day is what the journal is for.
 *
 * The pattern grid is the reason an hourly app exists: rows pinned to the
 * stable active window so the same hour lands on the same row in every period.
 * Ratings outside the window collapse into cap pills instead of stretching the
 * grid — the journal's day strips follow the same rule.
 */

/** Trends only zooms to periods a single screen can't already show you: weeks
 *  across a month, months across a year. Day-level lookup is the journal's job. */
private enum class Zoom { W, M }

/** Lower rank = finer grain. Drilling M→W lowers the rank (a zoom-in). */
private fun Zoom.rank(): Int = when (this) { Zoom.W -> 0; Zoom.M -> 1 }

/** Identifies exactly what the animated region is showing, so a change to any
 *  field drives one transition. */
private data class PastViewKey(
    val zoom: Zoom,
    val monthOffset: Int,
    val yearOffset: Int
)

/** How the current view change should animate. Zoom-in/out fly toward/away from
 *  the tapped bar; the steps slide the whole region left or right. */
private enum class PastNav { StepPrev, StepNext, ZoomIn, ZoomOut, None }

private data class PeriodUnit(
    val label: String,        // x-axis label under the bar
    val colLabel: String,     // short grid column header
    val startMs: Long,
    val endMs: Long,          // exclusive
    val future: Boolean,
    val isCurrent: Boolean
)

private data class PeriodStats(
    val avg: Double?,
    val ratedHours: Int,
    val possibleHours: Int
)

private data class TagStat(
    val tag: String,
    val avg: Double,
    val count: Int,
    val delta: Double? // vs the previous period; null when it wasn't used there
)

/** One row of the journal: a calendar day that has at least one rating. */
private data class JournalDay(
    val dayStartMs: Long,
    val entries: List<RatingEntry>,      // oldest → newest
    val avg: Double,
    val byHour: Map<Int, RatingEntry>,
    val early: Int,                      // ratings before the active window
    val late: Int                        // ratings after it
)

// ---------- calendar helpers ----------

private fun atMidnight(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

private fun weekStartOf(cal: Calendar): Calendar = atMidnight(cal).apply {
    while (get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) add(Calendar.DAY_OF_YEAR, -1)
}

private fun monthStartOf(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun shiftMonth(monthStartMs: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = monthStartMs
    add(Calendar.MONTH, delta)
}.timeInMillis

private fun buildUnits(zoom: Zoom, monthOffset: Int, yearOffset: Int): List<PeriodUnit> {
    val now = System.currentTimeMillis()
    val units = ArrayList<PeriodUnit>()
    when (zoom) {
        Zoom.W -> {
            val monthStart = atMidnight(Calendar.getInstance()).apply {
                set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -monthOffset)
            }
            val monthEndExcl = (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            val curWeekStartMs = weekStartOf(Calendar.getInstance()).timeInMillis
            var ws = weekStartOf(monthStart)
            var idx = 1
            while (ws.timeInMillis < monthEndExcl.timeInMillis) {
                val weekEndExcl = (ws.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
                val selStart = maxOf(ws.timeInMillis, monthStart.timeInMillis)
                val selEndExcl = minOf(weekEndExcl.timeInMillis, monthEndExcl.timeInMillis)
                val a = Calendar.getInstance().apply { timeInMillis = selStart }
                val b = Calendar.getInstance().apply { timeInMillis = selEndExcl - 1 }
                units.add(
                    PeriodUnit(
                        label = "${a.get(Calendar.DAY_OF_MONTH)}–${b.get(Calendar.DAY_OF_MONTH)}",
                        colLabel = "w$idx",
                        startMs = selStart,
                        endMs = selEndExcl,
                        future = selStart > now,
                        isCurrent = ws.timeInMillis == curWeekStartMs
                    )
                )
                ws = weekEndExcl
                idx++
            }
        }
        Zoom.M -> {
            val year = Calendar.getInstance().get(Calendar.YEAR) - yearOffset
            val monthLetters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
            val cur = Calendar.getInstance()
            for (m in 0 until 12) {
                val start = Calendar.getInstance().apply {
                    clear(); set(year, m, 1)
                }
                val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                units.add(
                    PeriodUnit(
                        label = monthLetters[m],
                        colLabel = monthLetters[m],
                        startMs = start.timeInMillis,
                        endMs = end.timeInMillis,
                        future = start.timeInMillis > now,
                        isCurrent = year == cur.get(Calendar.YEAR) && m == cur.get(Calendar.MONTH)
                    )
                )
            }
        }
    }
    return units
}

private fun periodRange(zoom: Zoom, monthOffset: Int, yearOffset: Int): Pair<Long, Long> {
    return when (zoom) {
        Zoom.W -> {
            val ms = atMidnight(Calendar.getInstance()).apply {
                set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -monthOffset)
            }
            val end = (ms.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            ms.timeInMillis to end.timeInMillis
        }
        Zoom.M -> {
            val year = Calendar.getInstance().get(Calendar.YEAR) - yearOffset
            val start = Calendar.getInstance().apply { clear(); set(year, 0, 1) }
            val end = Calendar.getInstance().apply { clear(); set(year + 1, 0, 1) }
            start.timeInMillis to end.timeInMillis
        }
    }
}

private fun computePeriodStats(
    ratings: List<RatingEntry>,
    startMs: Long,
    endMs: Long,
    window: IntRange
): PeriodStats {
    val entries = ratings.filter { it.timestamp in startMs until endMs }
    val cal = Calendar.getInstance()

    val hoursByDay = HashMap<Long, MutableSet<Int>>()
    var scoreSum = 0L
    for (e in entries) {
        cal.timeInMillis = e.timestamp
        val key = cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
        hoursByDay.getOrPut(key) { mutableSetOf() }.add(cal.get(Calendar.HOUR_OF_DAY))
        scoreSum += e.score
    }
    val ratedHours = hoursByDay.values.sumOf { it.size }

    // Elapsed days of the period (never counting the future)
    val now = System.currentTimeMillis()
    var elapsedDays = 0
    val cursor = Calendar.getInstance().apply { timeInMillis = startMs }
    while (cursor.timeInMillis < endMs && cursor.timeInMillis <= now) {
        elapsedDays++
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }
    val windowSize = window.last - window.first + 1

    return PeriodStats(
        avg = if (entries.isEmpty()) null else scoreSum.toDouble() / entries.size,
        ratedHours = ratedHours,
        possibleHours = elapsedDays * windowSize
    )
}

private fun computeTagStats(
    ratings: List<RatingEntry>,
    startMs: Long,
    endMs: Long,
    prevStartMs: Long,
    prevEndMs: Long,
    sortByScore: Boolean
): List<TagStat> {
    fun aggregate(a: Long, b: Long): Map<String, Pair<Double, Int>> =
        ratings.asSequence()
            .filter { it.timestamp in a until b }
            .flatMap { e -> e.tags.asSequence().map { it to e.score } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, scores) -> scores.average() to scores.size }

    val cur = aggregate(startMs, endMs)
    val prev = aggregate(prevStartMs, prevEndMs)
    val stats = cur.map { (tag, pair) ->
        TagStat(
            tag = tag,
            avg = pair.first,
            count = pair.second,
            delta = prev[tag]?.let { pair.first - it.first }
        )
    }
    return if (sortByScore) stats.sortedByDescending { it.avg }
    else stats.sortedByDescending { it.count }
}

// ---------- the one-line insight ----------

/** Below this many rated hours in the period, any "pattern" is noise — say
 *  nothing rather than something confidently wrong. */
private const val INSIGHT_MIN_HOURS = 20
/** A part of day / a tag needs this many ratings before it can be named. */
private const val INSIGHT_MIN_SAMPLES = 3
/** Gaps under this are within the rounding of a 1–10 scale. */
private const val INSIGHT_MIN_GAP = 0.5

private data class DayPart(val cap: String, val lower: String, val hours: IntRange)

private val DAY_PARTS = listOf(
    DayPart("Mornings", "mornings", 5..11),
    DayPart("Afternoons", "afternoons", 12..16),
    DayPart("Evenings", "evenings", 17..23)
)

private fun oneDecimal(v: Double) = String.format(Locale.getDefault(), "%.1f", v)

/**
 * One sentence about the period, or null when the data can't support one.
 *
 * Two clauses, either of which may be missing: the widest gap between parts of
 * the day, and the tag that scores worst. Both are gated on sample size — the
 * whole point of a single auto-written line is that it is never wrong, so it
 * stays silent far more readily than it speaks.
 */
private fun buildInsight(entries: List<RatingEntry>, ratedHours: Int): String? {
    if (ratedHours < INSIGHT_MIN_HOURS) return null
    val cal = Calendar.getInstance()
    val parts = mutableListOf<String>()

    val partScores = HashMap<Int, MutableList<Int>>()
    for (e in entries) {
        cal.timeInMillis = e.timestamp
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val idx = DAY_PARTS.indexOfFirst { h in it.hours }
        if (idx >= 0) partScores.getOrPut(idx) { mutableListOf() }.add(e.score)
    }
    val partAvgs = partScores
        .filterValues { it.size >= INSIGHT_MIN_SAMPLES }
        .mapValues { (_, scores) -> scores.average() }
    if (partAvgs.size >= 2) {
        val best = partAvgs.maxByOrNull { it.value }!!
        val worst = partAvgs.minByOrNull { it.value }!!
        val gap = best.value - worst.value
        if (gap >= INSIGHT_MIN_GAP) {
            parts += "${DAY_PARTS[best.key].cap} average ${oneDecimal(gap)} " +
                    "higher than ${DAY_PARTS[worst.key].lower}"
        }
    }

    val tagAvgs = entries
        .flatMap { e -> e.visibleTags().map { it to e.score } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size >= INSIGHT_MIN_SAMPLES }
        .mapValues { (_, scores) -> scores.average() }
    if (tagAvgs.size >= 2) {
        val low = tagAvgs.minByOrNull { it.value }!!
        parts += "lowest tag: ${low.key} (${oneDecimal(low.value)})"
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Groups every rating into its calendar day, newest day first. Days without a
 *  single rating are not rows — the journal lists what was written, not what
 *  wasn't (the Streak tab's calendar is where absences read). */
private fun buildJournalDays(ratings: List<RatingEntry>, window: IntRange): List<JournalDay> {
    if (ratings.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    val groups = HashMap<Long, MutableList<RatingEntry>>()
    for (e in ratings) {
        cal.timeInMillis = e.timestamp
        val dayStart = atMidnight(cal).timeInMillis
        groups.getOrPut(dayStart) { mutableListOf() }.add(e)
    }
    return groups.entries
        .sortedByDescending { it.key }
        .map { (dayStart, list) ->
            val sorted = list.sortedBy { it.timestamp }
            val byHour = HashMap<Int, RatingEntry>()
            var early = 0
            var late = 0
            for (e in sorted) {
                cal.timeInMillis = e.timestamp
                val h = cal.get(Calendar.HOUR_OF_DAY)
                byHour[h] = e
                if (h < window.first) early++ else if (h > window.last) late++
            }
            JournalDay(
                dayStartMs = dayStart,
                entries = sorted,
                avg = sorted.map { it.score }.average(),
                byHour = byHour,
                early = early,
                late = late
            )
        }
}

/** "Today" / "Yesterday" / "Sat 2 Aug" (with the year once it stops being this one). */
private fun journalDayLabel(dayStartMs: Long): String {
    val today = atMidnight(Calendar.getInstance())
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when (dayStartMs) {
        today.timeInMillis -> "Today"
        yesterday.timeInMillis -> "Yesterday"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
            val pattern = if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) "EEE d MMM"
            else "EEE d MMM yyyy"
            SimpleDateFormat(pattern, Locale.getDefault()).format(cal.time)
        }
    }
}

// ============================================================
// THE TAB
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastTab(
    viewModel: RatingsViewModel,
    scrollState: ScrollState,
    chartYPosition: Float,
    onChartYPosition: (Float) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val allRatings = viewModel.allRatings
    val refresh = viewModel.refreshTrigger
    val window = remember(allRatings, refresh) { computeActiveWindow(allRatings) }

    // Journal is home; Trends is a full-screen view over it (back returns).
    var showTrends by remember { mutableStateOf(false) }
    BackHandler(enabled = showTrends) { showTrends = false }

    var editingEntry by remember { mutableStateOf<RatingEntry?>(null) }
    val editSheetState = rememberModalBottomSheetState()

    // Journal state lives here so a round-trip through Trends comes back to the
    // same scroll position and the same open day.
    val journalListState = rememberLazyListState()
    var expandedDay by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        AnimatedContent(
            targetState = showTrends,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = {
                val fspec = tween<Float>(300, easing = FastOutSlowInEasing)
                val ispec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                val forward = targetState // journal → trends slides in from the right
                (if (forward)
                    (slideInHorizontally(ispec) { it / 3 } + fadeIn(fspec)) togetherWith
                            (slideOutHorizontally(ispec) { -it / 3 } + fadeOut(fspec))
                else
                    (slideInHorizontally(ispec) { -it / 3 } + fadeIn(fspec)) togetherWith
                            (slideOutHorizontally(ispec) { it / 3 } + fadeOut(fspec)))
                    .using(SizeTransform(clip = false))
            },
            label = "pastMode"
        ) { trends ->
            if (trends) {
                TrendsView(
                    allRatings = allRatings,
                    refresh = refresh,
                    window = window,
                    scrollState = scrollState,
                    onBack = { showTrends = false },
                    onChartYPosition = onChartYPosition
                )
            } else {
                JournalView(
                    allRatings = allRatings,
                    refresh = refresh,
                    window = window,
                    listState = journalListState,
                    expandedDay = expandedDay,
                    onExpandedDayChange = { expandedDay = it },
                    onOpenTrends = { showTrends = true },
                    onEdit = { editingEntry = it }
                )
            }
        }
    }

    // ---- Edit Bottom Sheet (with undo on delete) ----
    if (editingEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { editingEntry = null },
            sheetState = editSheetState
        ) {
            EditEntrySheet(
                entry = editingEntry!!,
                dayEntries = run {
                    val ref = Calendar.getInstance().apply { timeInMillis = editingEntry!!.timestamp }
                    allRatings.filter {
                        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        c.get(Calendar.DAY_OF_YEAR) == ref.get(Calendar.DAY_OF_YEAR) &&
                                c.get(Calendar.YEAR) == ref.get(Calendar.YEAR)
                    }
                },
                onPersist = { updated -> viewModel.saveRating(context, updated) },
                onClose = { editingEntry = null },
                onDelete = { id ->
                    val entryToDelete = allRatings.find { it.id == id }
                    viewModel.deleteRating(context, id)
                    editingEntry = null
                    if (entryToDelete != null) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Rating deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.saveRating(context, entryToDelete)
                            }
                        }
                    }
                }
            )
        }
    }
}

// ============================================================
// JOURNAL  (the default view)
// ============================================================

@Composable
private fun JournalView(
    allRatings: List<RatingEntry>,
    refresh: Int,
    window: IntRange,
    listState: androidx.compose.foundation.lazy.LazyListState,
    expandedDay: Long?,
    onExpandedDayChange: (Long?) -> Unit,
    onOpenTrends: () -> Unit,
    onEdit: (RatingEntry) -> Unit
) {
    val scope = rememberCoroutineScope()
    val days = remember(allRatings, window, refresh) { buildJournalDays(allRatings, window) }
    var showJump by remember { mutableStateOf(false) }

    if (days.isEmpty()) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 48.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                "Rate a few hours and your days will appear here 🐝",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    // The month of whatever day is at the top of the list — the header reads as
    // a heading for what you're looking at, not a fixed "this month".
    val monthLabel by remember(days, listState) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex.coerceIn(0, days.lastIndex)
            SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(Date(days[idx].dayStartMs))
        }
    }

    Column(Modifier.fillMaxSize()) {
        JournalHeader(
            monthLabel = monthLabel,
            onJumpClick = { showJump = true },
            onOpenTrends = onOpenTrends
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(days, key = { it.dayStartMs }) { day ->
                JournalDayCard(
                    day = day,
                    window = window,
                    expanded = day.dayStartMs == expandedDay,
                    onToggle = {
                        onExpandedDayChange(
                            if (day.dayStartMs == expandedDay) null else day.dayStartMs
                        )
                    },
                    onEditEntry = onEdit
                )
            }
            item { Spacer(Modifier.height(112.dp)) } // clearance for the floating nav pill
        }
    }

    if (showJump) {
        DateJumpDialog(
            days = days,
            initialDayMs = days.getOrNull(listState.firstVisibleItemIndex)?.dayStartMs
                ?: days.first().dayStartMs,
            onPick = { dayMs ->
                showJump = false
                val idx = days.indexOfFirst { it.dayStartMs == dayMs }
                if (idx >= 0) {
                    // Open the day as we land on it: the jump is tap 2 of 2.
                    onExpandedDayChange(dayMs)
                    scope.launch { listState.animateScrollToItem(idx) }
                }
            },
            onDismiss = { showJump = false }
        )
    }
}

@Composable
private fun JournalHeader(
    monthLabel: String,
    onJumpClick: () -> Unit,
    onOpenTrends: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onJumpClick, onClickLabel = "Jump to a date")
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(monthLabel, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .clickable(onClick = onOpenTrends)
                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Trends",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "Open Trends",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * One day, collapsed to a headline (date · hours · average) over a tick strip
 * of that day's hours, and expanded in place to every rated hour. The strip
 * uses the same active window as the Now tab's today-strip; anything outside it
 * rides in the ▲/▼ cap pills rather than widening the strip.
 */
@Composable
private fun JournalDayCard(
    day: JournalDay,
    window: IntRange,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditEntry: (RatingEntry) -> Unit
) {
    val now = System.currentTimeMillis()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    journalDayLabel(day.dayStartMs),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${day.entries.size} h",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(getScoreColor(day.avg))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        String.format(Locale.getDefault(), "%.1f", day.avg),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tick strip: one slot per active-window hour, positioned by hour
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (day.early > 0) {
                    TickCapPill("▲${day.early}")
                    Spacer(Modifier.width(5.dp))
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                ) {
                    for (h in window) {
                        val entry = day.byHour[h]
                        val future = entry == null && day.dayStartMs + (h + 1) * 3600_000L > now
                        Box(
                            Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        entry != null -> scoreBandColor(entry.score)
                                        future -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }
                if (day.late > 0) {
                    Spacer(Modifier.width(5.dp))
                    TickCapPill("▼${day.late}")
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                day.entries.forEach { entry ->
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    )
                    HourEntryRow(entry = entry, onClick = { onEditEntry(entry) })
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                )
                Spacer(Modifier.height(8.dp))
                val elapsedWindowHours = window.count { h ->
                    day.dayStartMs + (h + 1) * 3600_000L <= now
                }
                val ratedInWindow = day.byHour.keys.count { it in window }
                val unrated = (elapsedWindowHours - ratedInWindow).coerceAtLeast(0)
                Text(
                    if (unrated == 0) "Every hour rated 🐝"
                    else "$unrated hour${if (unrated == 1) "" else "s"} unrated",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** The ▲/▼ badge carrying the ratings that fall outside the active window. */
@Composable
private fun TickCapPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One rated hour inside an expanded journal day: time range, score chip, tags
 * (🕐 when it was rated from memory), note preview.
 */
@Composable
private fun HourEntryRow(
    entry: RatingEntry,
    onClick: () -> Unit
) {
    val startH = remember(entry.timestamp) {
        Calendar.getInstance().apply { timeInMillis = entry.timestamp }.get(Calendar.HOUR_OF_DAY)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${formatHour(startH)} – ${formatHour((startH + 1) % 24)}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp)
        )
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(scoreBandColor(entry.score)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${entry.score}",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (entry.score >= 5) Color.Black else Color.White
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 🕐 = rated from memory via a bee, not in the moment
                if (entry.isReclaimed) {
                    Text("🕐", fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                }
                val shown = entry.visibleTags()
                Text(
                    if (shown.isEmpty()) "—" else shown.joinToString(" · "),
                    fontSize = 12.5.sp,
                    maxLines = 1
                )
            }
            if (entry.note.isNotBlank()) {
                Text(
                    "📝 ${entry.note}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "Edit this hour",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * Date jump: a month grid where only days that actually hold ratings are live,
 * each tinted by that day's average. Picking one scrolls the journal to it and
 * opens it — any past hour in two taps.
 */
@Composable
private fun DateJumpDialog(
    days: List<JournalDay>,
    initialDayMs: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val avgByDay = remember(days) { days.associate { it.dayStartMs to it.avg } }
    var monthStart by remember(initialDayMs) { mutableLongStateOf(monthStartOf(initialDayMs)) }

    val oldestMonth = remember(days) { monthStartOf(days.last().dayStartMs) }
    val newestMonth = remember(days) { monthStartOf(days.first().dayStartMs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to a date") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { monthStart = shiftMonth(monthStart, -1) },
                        enabled = monthStart > oldestMonth,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Earlier month")
                    }
                    Text(
                        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(monthStart)),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { monthStart = shiftMonth(monthStart, 1) },
                        enabled = monthStart < newestMonth,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, "Later month")
                    }
                }
                Spacer(Modifier.height(8.dp))

                val cal = remember(monthStart) {
                    Calendar.getInstance().apply { timeInMillis = monthStart }
                }
                val firstDayOfWeek = cal.firstDayOfWeek
                val letters = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        Text(
                            letters[(firstDayOfWeek - 1 + i) % 7],
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val lead = ((cal.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek) + 7) % 7
                val cells = List(lead) { 0 } + (1..daysInMonth).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i) ?: 0
                            if (d == 0) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val dayMs = Calendar.getInstance().apply {
                                    timeInMillis = monthStart
                                    set(Calendar.DAY_OF_MONTH, d)
                                }.timeInMillis
                                val avg = avgByDay[dayMs]
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            avg?.let { getScoreColor(it) } ?: Color.Transparent
                                        )
                                        .then(
                                            if (avg != null) Modifier.clickable { onPick(dayMs) }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$d",
                                        fontSize = 12.sp,
                                        fontWeight = if (avg != null) FontWeight.ExtraBold
                                        else FontWeight.Normal,
                                        color = if (avg != null) Color.White
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
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

// ============================================================
// TRENDS  (the former Past tab, relocated behind one button)
// ============================================================

@Composable
private fun TrendsView(
    allRatings: List<RatingEntry>,
    refresh: Int,
    window: IntRange,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onChartYPosition: (Float) -> Unit
) {
    val context = LocalContext.current

    var zoom by remember { mutableStateOf(Zoom.W) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var yearOffset by remember { mutableIntStateOf(0) }
    var sortByScore by remember { mutableStateOf(true) }
    var gridExpanded by remember { mutableStateOf(false) }
    // The pattern grid is the reason to come here, so at the year scale it is
    // open on arrival; the toggle only exists to get it out of the way.
    var hourByHourExpanded by remember { mutableStateOf(true) }

    // How the next view change should animate — set by the action that triggers it.
    var navKind by remember { mutableStateOf(PastNav.None) }

    // Kept at the top only for the control bar's range label. All the per-view
    // data (stats, avgs, grid cells, tags) is computed inside the animated
    // region so the outgoing and incoming levels each render their own.
    val range = remember(zoom, monthOffset, yearOffset) {
        periodRange(zoom, monthOffset, yearOffset)
    }

    val rangeLabel = remember(zoom, monthOffset, yearOffset) {
        when (zoom) {
            Zoom.W -> SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(Date(range.first))
            Zoom.M -> SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(range.first))
        }
    }

    fun goEarlier() {
        navKind = PastNav.StepPrev
        when (zoom) {
            Zoom.W -> monthOffset++
            Zoom.M -> yearOffset++
        }
    }

    fun goLater() {
        navKind = PastNav.StepNext
        when (zoom) {
            Zoom.W -> if (monthOffset > 0) monthOffset--
            Zoom.M -> if (yearOffset > 0) yearOffset--
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Back to the journal"
                )
            }
            Text("Trends", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp)
        ) {
            // ---- One card: control bar (static) + an animated region that
            // slides on period steps and flies into the tapped bar on drill ----
            val canForward = when (zoom) {
                Zoom.W -> monthOffset > 0
                Zoom.M -> yearOffset > 0
            }
            val viewKey = PastViewKey(zoom, monthOffset, yearOffset)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onChartYPosition(it.positionInParent().y) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    // Control bar (never animates): arrows + range label + D/W/M
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { goEarlier() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowLeft, "Earlier",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            rangeLabel,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        IconButton(
                            onClick = { goLater() },
                            enabled = canForward,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight, "Later",
                                tint = if (canForward) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        ZoomPicker(
                            zoom = zoom,
                            onSelect = { target ->
                                if (target != zoom) {
                                    navKind = if (target.rank() < zoom.rank())
                                        PastNav.ZoomIn else PastNav.ZoomOut
                                    zoom = target
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    AnimatedContent(
                        targetState = viewKey,
                        transitionSpec = {
                            val fspec = tween<Float>(360, easing = FastOutSlowInEasing)
                            val ispec = tween<IntOffset>(360, easing = FastOutSlowInEasing)
                            val origin = TransformOrigin(0.5f, 0.28f)
                            when (navKind) {
                                PastNav.StepNext ->
                                    (slideInHorizontally(ispec) { it } + fadeIn(fspec)) togetherWith
                                            (slideOutHorizontally(ispec) { -it } + fadeOut(fspec))
                                PastNav.StepPrev ->
                                    (slideInHorizontally(ispec) { -it } + fadeIn(fspec)) togetherWith
                                            (slideOutHorizontally(ispec) { it } + fadeOut(fspec))
                                PastNav.ZoomIn ->
                                    (scaleIn(fspec, initialScale = 0.7f, transformOrigin = origin) + fadeIn(fspec)) togetherWith
                                            (scaleOut(fspec, targetScale = 1.35f, transformOrigin = origin) + fadeOut(fspec))
                                PastNav.ZoomOut ->
                                    (scaleIn(fspec, initialScale = 1.35f, transformOrigin = origin) + fadeIn(fspec)) togetherWith
                                            (scaleOut(fspec, targetScale = 0.7f, transformOrigin = origin) + fadeOut(fspec))
                                PastNav.None ->
                                    fadeIn(fspec) togetherWith fadeOut(fspec)
                            }.using(SizeTransform(clip = false))
                        },
                        label = "pastView"
                    ) { key ->
                        // Everything below is derived from THIS slot's key, so
                        // the outgoing and incoming levels each show their own
                        // data during the transition.
                        val kUnits = remember(key, allRatings, refresh) {
                            buildUnits(key.zoom, key.monthOffset, key.yearOffset)
                        }
                        val kRange = remember(key) {
                            periodRange(key.zoom, key.monthOffset, key.yearOffset)
                        }
                        val kPrevRange = remember(key) {
                            periodRange(
                                key.zoom,
                                if (key.zoom == Zoom.W) key.monthOffset + 1 else key.monthOffset,
                                if (key.zoom == Zoom.M) key.yearOffset + 1 else key.yearOffset
                            )
                        }
                        val kStats = remember(key, allRatings, window, refresh) {
                            computePeriodStats(allRatings, kRange.first, kRange.second, window)
                        }
                        val kRestDays = remember(key, allRatings, refresh) {
                            computeStreakLog(allRatings, loadReclaimSpends(context))
                                .count { it.type == StreakEventType.REST && it.timestamp in kRange.first until kRange.second }
                        }
                        val kUnitAvgs = remember(key, allRatings, refresh) {
                            kUnits.map { u ->
                                if (u.future) null else {
                                    val scores = allRatings.filter { it.timestamp in u.startMs until u.endMs }.map { it.score }
                                    if (scores.isEmpty()) null else scores.average()
                                }
                            }
                        }
                        val kPeriodEntries = remember(key, allRatings, refresh) {
                            allRatings.filter { it.timestamp in kRange.first until kRange.second }
                        }
                        val kEarly = remember(kPeriodEntries, window) {
                            val cal = Calendar.getInstance()
                            kPeriodEntries.count { cal.timeInMillis = it.timestamp; cal.get(Calendar.HOUR_OF_DAY) < window.first }
                        }
                        val kLate = remember(kPeriodEntries, window) {
                            val cal = Calendar.getInstance()
                            kPeriodEntries.count { cal.timeInMillis = it.timestamp; cal.get(Calendar.HOUR_OF_DAY) > window.last }
                        }
                        val kTagStats = remember(key, allRatings, sortByScore, refresh) {
                            computeTagStats(allRatings, kRange.first, kRange.second, kPrevRange.first, kPrevRange.second, sortByScore)
                        }
                        val kInsight = remember(kPeriodEntries, kStats) {
                            buildInsight(kPeriodEntries, kStats.ratedHours)
                        }

                        Column(Modifier.fillMaxWidth()) {
                            // Summary for the selected period
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatCell(
                                    value = kStats.avg?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "–",
                                    valueColor = kStats.avg?.let { getScoreColor(it) } ?: Color.Unspecified,
                                    label = "avg score",
                                    modifier = Modifier.weight(1f)
                                )
                                StatDivider()
                                StatCell(
                                    value = "${kStats.ratedHours}/${kStats.possibleHours}",
                                    label = "hours rated",
                                    modifier = Modifier.weight(1f)
                                )
                                StatDivider()
                                StatCell(
                                    value = "🌙 $kRestDays",
                                    label = "rest days",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // One auto-written line under the numbers, or nothing
                            // at all when the period is too thin to say anything.
                            if (kInsight != null) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    kInsight,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            )
                            Spacer(Modifier.height(16.dp))

                            // Scores chart
                            SectionLabel(
                                when (key.zoom) {
                                    Zoom.W -> "WEEK SCORES"
                                    Zoom.M -> "MONTH SCORES"
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                            ScoreBarChart(units = kUnits, avgs = kUnitAvgs)

                            // Hour-by-hour pattern grid. Only at the year scale:
                            // a month's worth of weeks leaves cells averaging a
                            // handful of ratings, which reads as noise.
                            if (key.zoom == Zoom.M) {
                                Spacer(Modifier.height(18.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                )
                                Spacer(Modifier.height(16.dp))

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { hourByHourExpanded = !hourByHourExpanded },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SectionLabel(
                                        "YOUR YEAR, HOUR BY HOUR",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (hourByHourExpanded) "hide" else "show",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (hourByHourExpanded) {
                                    Spacer(Modifier.height(10.dp))
                                    if (!gridExpanded && kEarly > 0) {
                                        CapPill(
                                            "▲ $kEarly early rating${if (kEarly == 1) "" else "s"} (before ${formatHour(window.first)}) · show full day"
                                        ) { gridExpanded = true }
                                    }
                                    if (gridExpanded) {
                                        CapPill("collapse to active window (${formatHour(window.first)} – ${formatHour((window.last + 1) % 24)})") {
                                            gridExpanded = false
                                        }
                                    }
                                    PatternGrid(
                                        units = kUnits,
                                        ratings = kPeriodEntries,
                                        hourRange = if (gridExpanded) 0..23 else window
                                    )
                                    if (!gridExpanded && kLate > 0) {
                                        Spacer(Modifier.height(6.dp))
                                        CapPill(
                                            "▼ $kLate late rating${if (kLate == 1) "" else "s"} (after ${formatHour((window.last + 1) % 24)}) · show full day"
                                        ) { gridExpanded = true }
                                    }
                                }
                            }

                            Spacer(Modifier.height(18.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            )
                            Spacer(Modifier.height(16.dp))

                            // How tags score, with trend vs the previous period —
                            // a short inner scroll (≈5 rows) with a fading bar
                            TagScoresSection(
                                tagStats = kTagStats,
                                sortByScore = sortByScore,
                                onSortChange = { sortByScore = it }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(112.dp)) // clearance for the floating nav pill
        }
    }
}

// ============================================================
// PIECES
// ============================================================

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified
) {
    Column(
        modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1
        )
        Text(
            label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .height(30.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
    )
}

private fun Zoom.fullLabel(): String = when (this) {
    Zoom.W -> "Weeks"
    Zoom.M -> "Months"
}

/**
 * The W/M zoom selector: a stadium-shaped pill. The active level expands into
 * a filled primary stadium showing its full word (Weeks/Months); the other
 * collapses to a single bare letter. The width change is animated.
 */
@Composable
private fun ZoomPicker(zoom: Zoom, onSelect: (Zoom) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Zoom.entries.forEach { z ->
            val selected = z == zoom
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(220),
                label = "zoomBg"
            )
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(z) }
                    .animateContentSize(animationSpec = tween(220))
                    .padding(horizontal = if (selected) 14.dp else 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (selected) z.fullLabel() else z.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CapPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Band-colored bars over a light grid. The goal line is dashed with its
 * label INSIDE the plot; only the best unit carries a value label. The bars
 * are a readout, not a control — nothing here is tappable.
 */
@Composable
private fun ScoreBarChart(
    units: List<PeriodUnit>,
    avgs: List<Double?>
) {
    val plotHeight = 150.dp
    val labelZone = 18.dp
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val goalPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 26f
            isFakeBoldText = true
            isAntiAlias = true
        }
    }
    val maxIdx = remember(avgs) {
        var best = -1
        var bestV = -1.0
        avgs.forEachIndexed { i, v -> if (v != null && v > bestV) { bestV = v; best = i } }
        best
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            // y-axis: 10 / 5 / 0
            Column(
                Modifier.height(plotHeight + labelZone).padding(top = labelZone),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("10", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("5", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(plotHeight + labelZone)
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(plotHeight)
                        .align(Alignment.BottomCenter)
                ) {
                    // gridlines at 0, 5, 10
                    listOf(0f, 0.5f, 1f).forEach { f ->
                        val y = size.height * (1f - f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y), end = Offset(size.width, y),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }
                    // goal line + in-plot label (never clipped)
                    val goalY = size.height * (1f - 0.7f)
                    drawLine(
                        color = Color(0xFF9E9E9E),
                        start = Offset(0f, goalY), end = Offset(size.width, goalY),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                    drawContext.canvas.nativeCanvas.drawText("goal 7", 8f, goalY - 10f, goalPaint)
                }
                Row(
                    Modifier.matchParentSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    units.forEachIndexed { i, u ->
                        val avg = avgs[i]
                        // Animate height + color so period changes glide instead
                        // of snapping. Future/empty bars stay at their placeholder.
                        val animFrac by animateFloatAsState(
                            targetValue = if (u.future || avg == null) 0f else (avg / 10.0).toFloat(),
                            animationSpec = tween(450, easing = FastOutSlowInEasing),
                            label = "barFrac$i"
                        )
                        val animColor by animateColorAsState(
                            targetValue = avg?.let { getScoreColor(it) }
                                ?: MaterialTheme.colorScheme.surfaceVariant,
                            animationSpec = tween(450),
                            label = "barColor$i"
                        )
                        val barShape = RoundedCornerShape(
                            topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp
                        )
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (avg != null && i == maxIdx) {
                                Text(
                                    String.format(Locale.getDefault(), "%.1f", avg),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            val barModifier = Modifier
                                .fillMaxWidth()
                                .clip(barShape)
                            when {
                                u.future -> Box(
                                    barModifier
                                        .height(10.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                )
                                avg == null -> Box(
                                    barModifier
                                        .height(4.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                else -> Box(
                                    barModifier
                                        .height((animFrac * plotHeight.value).dp.coerceAtLeast(6.dp))
                                        .background(animColor)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            units.forEach { u ->
                Text(
                    u.label,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Hour-of-day rows × period columns — each cell that hour's average across the
 * whole column, so a recurring slump reads as a band straight down the grid.
 * Rows are pinned to the active window so periods stay comparable.
 */
@Composable
private fun PatternGrid(
    units: List<PeriodUnit>,
    ratings: List<RatingEntry>,
    hourRange: IntRange
) {
    // hour -> per-column average
    val cellAvgs = remember(units, ratings, hourRange) {
        val cal = Calendar.getInstance()
        val sums = HashMap<Long, DoubleArray>() // hour*100+col -> [sum, count]
        for (e in ratings) {
            cal.timeInMillis = e.timestamp
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h !in hourRange) continue
            val col = units.indexOfFirst { e.timestamp in it.startMs until it.endMs }
            if (col < 0) continue
            val key = h * 100L + col
            val arr = sums.getOrPut(key) { doubleArrayOf(0.0, 0.0) }
            arr[0] += e.score
            arr[1] += 1
        }
        sums.mapValues { it.value[0] / it.value[1] }
    }
    val now = System.currentTimeMillis()
    val labelEvery = if (hourRange.count() > 18) 6 else 5

    // A slim gutter (was 34dp) reclaims width for the grid. Cells are square and
    // HEIGHT-CAPPED at the 7-column (Day) size: with fewer columns (Week's ~5)
    // the tiles stop ballooning and the grid keeps a near-constant height across
    // D/W/M — the leftover width on the right is intentionally left blank.
    val gutter = 24.dp
    val spacing = 2.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = units.size.coerceAtLeast(1)
        val avail = maxWidth - gutter
        val fitCols = (avail - spacing * (cols - 1)) / cols
        val cap7 = (avail - spacing * 6) / 7
        val cell = minOf(fitCols, cap7).coerceAtLeast(8.dp)

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            // column headers
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                Spacer(Modifier.width(gutter))
                units.forEach { u ->
                    Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                        Text(
                            u.colLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (u.isCurrent) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
            for (h in hourRange) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    // Hour marker sits on the row's TOP edge (the h:00 boundary),
                    // straddling the seam between the previous hour and this one.
                    Box(
                        Modifier.width(gutter).height(cell),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        if ((h - hourRange.first) % labelEvery == 0 || h == hourRange.last) {
                            Text(
                                formatHour(h),
                                fontSize = 8.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier
                                    .offset(y = (-5).dp)
                                    .padding(end = 4.dp)
                            )
                        }
                    }
                    units.forEachIndexed { col, u ->
                        val avg = cellAvgs[h * 100L + col]
                        // For a single-day column an hour can be individually in
                        // the future; multi-day columns only dim when fully future
                        val singleDay = u.endMs - u.startMs <= 25L * 3600_000L
                        val cellFuture = u.future || (singleDay && u.startMs + h * 3600_000L > now)
                        val cellColor by animateColorAsState(
                            targetValue = when {
                                avg != null -> getScoreColor(avg)
                                cellFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            animationSpec = tween(450),
                            label = "cell"
                        )
                        Box(
                            Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(3.dp))
                                .background(cellColor)
                        )
                    }
                }
            }
        }
    }
}

/**
 * "How tags score" folded into the big card. The rows live in a short inner
 * scroll (~5 rows tall) so the section can't push the grid off-screen; a thin
 * scrollbar fades in while scrolling and disappears when it stops.
 */
@Composable
private fun TagScoresSection(
    tagStats: List<TagStat>,
    sortByScore: Boolean,
    onSortChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("HOW TAGS SCORE", modifier = Modifier.weight(1f))
            FilterChip(
                selected = sortByScore,
                onClick = { onSortChange(true) },
                label = { Text("by score", fontSize = 11.sp) }
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = !sortByScore,
                onClick = { onSortChange(false) },
                label = { Text("by count", fontSize = 11.sp) }
            )
        }
        Spacer(Modifier.height(6.dp))
        if (tagStats.isEmpty()) {
            Text(
                "No tagged hours in this period.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            val listScroll = rememberScrollState()
            Box(Modifier.fillMaxWidth().heightIn(max = 210.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(listScroll)
                        .padding(end = 8.dp)
                ) {
                    tagStats.forEach { stat -> TagStatRow(stat) }
                }
                FadingScrollbar(
                    scrollState = listScroll,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

/** A slim scrollbar that appears while [scrollState] is moving and fades out
 *  shortly after it settles. Hidden entirely when there's nothing to scroll. */
@Composable
private fun FadingScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    if (scrollState.maxValue <= 0) return
    val active = scrollState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (active) 0.5f else 0f,
        animationSpec = tween(durationMillis = if (active) 120 else 700),
        label = "scrollbarAlpha"
    )
    val density = LocalDensity.current
    BoxWithConstraints(modifier.width(3.dp)) {
        val trackH = maxHeight
        val contentExtra = with(density) { scrollState.maxValue.toDp() }
        val thumbFrac = (trackH / (trackH + contentExtra)).coerceIn(0.12f, 1f)
        val thumbH = trackH * thumbFrac
        val prog = scrollState.value.toFloat() / scrollState.maxValue
        val thumbY = (trackH - thumbH) * prog
        Box(
            Modifier
                .offset(y = thumbY)
                .width(3.dp)
                .height(thumbH)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        )
    }
}

@Composable
private fun TagStatRow(stat: TagStat) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stat.tag,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                maxLines = 1
            )
            val delta = stat.delta
            if (delta != null && kotlin.math.abs(delta) >= 0.05) {
                Text(
                    (if (delta > 0) "▲ " else "▼ ") + String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(delta)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (delta > 0) Color(0xFF66BB6A) else Color(0xFFC62828)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                String.format(Locale.getDefault(), "%.1f", stat.avg),
                color = getScoreColor(stat.avg),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "(${stat.count})",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { (stat.avg / 10f).toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = getScoreColor(stat.avg),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

fun getScoreColor(score: Double): Color {
    return when {
        score >= 8.0 -> Color(0xFF2E7D32)
        score >= 5.0 -> Color(0xFFF57C00)
        score > 0.0 -> Color(0xFFC62828)
        else -> Color.Gray
    }
}
