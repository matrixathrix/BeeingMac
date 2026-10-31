package com.example.beeing

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import com.example.beeing.ui.BandTopRowSpacer
import com.example.beeing.ui.GlassPanel
import com.example.beeing.ui.designDensity
import com.example.beeing.ui.LocalBandChrome
import com.example.beeing.ui.PillShape
import com.example.beeing.ui.fieldChipBorder
import com.example.beeing.ui.fieldChipColors
import com.example.beeing.ui.fieldEmptyCellColor
import com.example.beeing.ui.glass
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.icons.tagGlyphOf
import com.example.beeing.ui.icons.tagLabelOf
import com.example.beeing.ui.icons.tagSym
import com.example.beeing.ui.theme.DisplaySerif
import com.example.beeing.ui.theme.InstrumentSans
import com.example.beeing.ui.theme.LocalBeeExtras
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * NOW TAB — one job: this hour.
 *
 * The whole tab is the NIGHT FIELD: one surface, no band and no card, with
 * everything on it either glass or honey (spec 2026-10-31-night-field-design).
 *
 *  - rating (1–2 hours pending): the question, the hour pill (‹ range ›) and
 *    its countdown, the dial, the tags, "+ Add a note", and Save pinned above
 *    the nav bar. A fixed frame (RatingFrame): only the tags scroll, and only
 *    when they can't fit.
 *  - caught up (none pending): today's ring with "All caught up", the today
 *    strip in a glass panel, then the bee flying in the remaining height over
 *    the Lock button — the ONLY place lock exists.
 *
 * Status (streak / ring) is a slim strip in the top row, not a hero; it
 * expands into the full meter on the Streak tab. No per-save ceremony: the
 * state change plus one haptic IS the feedback; celebrations stay reserved for
 * the ring closing (full-screen) and rare milestones.
 */

/**
 * Broadcast sent when the user taps "Lock phone & go". The lock itself is
 * performed by the accessibility service (kept outside this branch); it
 * should register a receiver for this action and call
 * performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN).
 */
const val ACTION_LOCK_PHONE = "com.example.beeing.ACTION_LOCK_PHONE"

// Small, NowTab-local type scale — replaces the ad hoc raw-sp literals that
// used to be scattered per call site. Deliberately not on the shared
// MaterialTheme.typography (titleMedium etc. are also read by StreaksTab and
// EditEntrySheet; a global override would leak beyond this screen).
private val NowHeroSize = 19.sp        // hero/CTA labels
private val NowBodySize = 14.sp        // secondary text
private val NowCaptionSize = 12.sp     // small inline text
private val NowMicroSize = 10.sp       // tiny labels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowTab(
    viewModel: RatingsViewModel,
    scrollState: ScrollState,
    targetedHourOffset: Int,
    onTargetedHourOffsetChange: (Int) -> Unit,
    ratingCardYPosition: Float,
    onRatingCardYPosition: (Float) -> Unit,
    onOpenStreaks: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onOpenTags: () -> Unit = {},
    onCaughtUpChange: (Boolean) -> Unit = {},
    pendingScore: Int? = null,
    pendingTags: List<String> = emptyList(),
    onPendingScoreConsumed: () -> Unit = {},
    onRingClosed: (RingCelebration) -> Unit = {},
    onStreakMoment: (StreakMoment) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allRatings = viewModel.allRatings

    // UI state
    var selectedScore by remember { mutableStateOf<Int?>(null) }
    val selectedTags = remember { mutableStateListOf<String>() }
    var currentNote by remember { mutableStateOf("") }
    var availableTags by remember(viewModel.refreshTrigger) { mutableStateOf(loadTags(context)) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var showWindowInfo by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<RatingEntry?>(null) }
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEnableAccessibility by remember { mutableStateOf(false) }

    // Score chosen on the notification arrives pre-selected
    LaunchedEffect(pendingScore) {
        pendingScore?.let {
            selectedScore = it
            // Tags picked on the notification arrive with it (only ones that still exist)
            val known = loadTags(context)
            pendingTags.filter { t -> t in known }.forEach { t -> if (t !in selectedTags) selectedTags.add(t) }
            onPendingScoreConsumed()
        }
    }

    // Ticking clock for chip expiry + the caught-up countdown (twice a minute)
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    // Start of the current clock hour. Everything hour-derived below keys on it,
    // so the screen moves on when the hour turns — including while the app sat
    // in the background (refreshTrigger bumps on ON_RESUME, before the 30s tick
    // catches up). Without it, a notification tapped after the hour turned
    // reopened a frame still describing the previous, already-rated hour.
    val hourStart = remember(nowMillis, viewModel.refreshTrigger) {
        Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Calculate hour info based on targetedHourOffset
    val displayHourInfo = remember(targetedHourOffset, hourStart) {
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -targetedHourOffset) }
        val endH = cal.get(Calendar.HOUR_OF_DAY)
        val range = "${formatHour(if (endH == 0) 23 else endH - 1)} – ${formatHour(endH)}"
        val label = "${if(endH == 0) 24 else endH}${getOrdinalSuffix(if(endH == 0) 24 else endH)}"
        Triple(range, endH, label)
    }

    // Check if current targeted hour is logged
    val isLoggedCurrent by remember(allRatings, displayHourInfo, targetedHourOffset, viewModel.refreshTrigger, hourStart) {
        derivedStateOf {
            // dayKey is based on the START of the rated hour (offset+1 hours back)
            val targetDayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
                Date(System.currentTimeMillis() - ((targetedHourOffset + 1) * 3600000L))
            )
            allRatings.any {
                it.hourLabel == displayHourInfo.third &&
                        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.timestamp)) == targetDayKey
            }
        }
    }

    // Helper: check if a given hour-slot is already logged.
    // offset=0 → the most-recent completed hour (e.g. 12PM-1PM when it's 1PM)
    // offset=1 → the grace-period hour before that
    // timestamp is stored at the START of the rated hour, so the dayKey uses (offset+1) hours back.
    fun isHourLogged(offset: Int): Boolean {
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -offset) }
        val endH = cal.get(Calendar.HOUR_OF_DAY)
        val label = "${if (endH == 0) 24 else endH}${getOrdinalSuffix(if (endH == 0) 24 else endH)}"
        val dayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
            Date(System.currentTimeMillis() - ((offset + 1) * 3600000L))
        )
        return allRatings.any {
            it.hourLabel == label &&
                    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.timestamp)) == dayKey
        }
    }

    val isLatestHourLogged = remember(allRatings, viewModel.refreshTrigger, hourStart) { isHourLogged(0) }
    val isPreviousHourLogged = remember(allRatings, viewModel.refreshTrigger, hourStart) { isHourLogged(1) }

    val allCaughtUp = isLatestHourLogged && isPreviousHourLogged
    // The root's back handler points at the bee when there is one to catch.
    LaunchedEffect(allCaughtUp) { onCaughtUpChange(allCaughtUp) }

    // Keyed on the day too: left open across midnight, yesterday must stop reading as
    // today (the late Save hint needs it). A recompute that adds no hour is a 0 delta,
    // so it can't trip the celebrations below.
    val todayStart = remember(hourStart) {
        Calendar.getInstance().apply { timeInMillis = hourStart; set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
    }
    val streakState = remember(allRatings, viewModel.modeEvents, viewModel.refreshTrigger, todayStart) {
        computeStreakState(allRatings, viewModel.modeEvents)
    }

    // The day's ring closing (the one daily ceremony, 4 hours in beginner mode, 8 in
    // Master), a Super day, a streak won back. ringClosed() and streakMoment() only
    // fire when exactly one new counted hour landed, so replays, imports, backdated
    // saves and mode toggles can never celebrate; the first composition just sets
    // the baseline. A ring close may carry the once-a-day "Make it a Super day"
    // invite, gated here on the once-a-day flag; the root writes the flag the moment
    // the invite actually shows.
    var previousState by remember { mutableStateOf<StreakState?>(null) }
    LaunchedEffect(streakState) {
        val prev = previousState
        ringClosed(prev, streakState)?.let { closed ->
            val day = closed.day
            val reachable = reachableHours(day.dayStartMs, day.countedStarts, System.currentTimeMillis())
            val invite = gateInvite(
                superInviteFor(day, streakState.savers, reachable), day.dayStartMs, superInviteShownDay(context)
            )
            onRingClosed(RingCelebration(closed.count, closed.beginner, closed.late, invite))
        }
        streakMoment(prev, streakState)?.let(onStreakMoment)
        previousState = streakState
    }

    // Dismiss notification when both hours are rated
    LaunchedEffect(allCaughtUp) {
        if (allCaughtUp) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1) // Cancel the hourly notification (ID = 1)
        }
    }

    // Today's ratings (for the today strip and its average)
    val todayRatings = remember(allRatings, viewModel.refreshTrigger, hourStart) {
        val today = Calendar.getInstance()
        allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        }
    }
    val todayByHour = remember(todayRatings) {
        todayRatings.associateBy {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }
    }
    val activeWindow = remember(allRatings, viewModel.refreshTrigger) { computeActiveWindow(allRatings) }

    // Today's expired unrated hours (start hours 0..H-3) — recoverable in the Hive
    val missedHoursToday = remember(allRatings, viewModel.refreshTrigger, hourStart) {
        val today = Calendar.getInstance()
        val currentHour = today.get(Calendar.HOUR_OF_DAY)
        val ratedStarts = todayRatings.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }.toSet()
        ((currentHour - 3) downTo 0).filter { it !in ratedStarts }
    }

    val minutesToNextHour = remember(nowMillis) {
        60 - Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.MINUTE)
    }

    // If the targeted hour got rated elsewhere (e.g. from the notification),
    // retarget to the one still pending.
    LaunchedEffect(isLatestHourLogged, isPreviousHourLogged) {
        if (targetedHourOffset == 0 && isLatestHourLogged && !isPreviousHourLogged) {
            onTargetedHourOffsetChange(1)
        } else if (targetedHourOffset == 1 && isPreviousHourLogged && !isLatestHourLogged) {
            onTargetedHourOffsetChange(0)
        }
    }

    // With BOTH hours pending, default to the expiring one — unless a
    // notification deep-link explicitly targeted the other.
    LaunchedEffect(isLatestHourLogged, isPreviousHourLogged) {
        if (pendingScore == null && !isLatestHourLogged && !isPreviousHourLogged &&
            targetedHourOffset == 0
        ) {
            onTargetedHourOffsetChange(1)
        }
    }

    // The rating state is a FIXED frame, not a scrolling page: header on top,
    // save button pinned at the bottom (directly above the nav bar), and the
    // rating card taking whatever is left. Only the activity tags scroll
    // inside the card — everything else stays put — and even they wrap
    // whenever they fit, becoming a viewport only when they don't (expanded,
    // or a screen too short for the collapsed picker). On a short screen the
    // commit action is therefore always on screen — which is the whole point.
    val tagsScroll = rememberScrollState()

    val field = LocalBeeExtras.current
    // Picker range in the app's one time format: "8pm – 9pm" (same as the
    // journal and the save button). The dash is set dimmer so the two times
    // carry the weight.
    fun hourRange(offset: Int): AnnotatedString {
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -offset) }
        val endH = cal.get(Calendar.HOUR_OF_DAY)
        val startH = if (endH == 0) 23 else endH - 1
        fun clock(h: Int) = formatHour(h)
        return buildAnnotatedString {
            append(clock(startH))
            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = field.fieldInkMuted)) {
                append(" – ")
            }
            append(clock(endH))
        }
    }
    fun minutesFor(offset: Int) =
        if (offset == 1) minutesToNextHour else minutesToNextHour + 60

    // Shared by the bee and the Lock button in the caught-up footer.
    val lockPhone = {
        if (isLockAccessibilityServiceEnabled(context)) {
            context.sendBroadcast(
                Intent(ACTION_LOCK_PHONE).setPackage(context.packageName)
            )
        } else {
            showEnableAccessibility = true
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(field.fieldBrush)) {
        CompositionLocalProvider(LocalContentColor provides field.fieldInk, LocalDensity provides designDensity()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            // The fixed top row is drawn over this, above the pager
            Box(Modifier.padding(horizontal = 16.dp)) { BandTopRowSpacer() }
            if (!allCaughtUp) {
                // ---- RATING: a fixed frame, ordered the way you act ----
                // The words and Save share the 16dp edge of the top row and the other
                // tabs; the dial and the tags share the centre line.
                Spacer(Modifier.height(36.dp)) // a clear breath between the quiet header and the hero
                // The app's signature line, in the serif: a journal asking, not a form
                Text(
                    "How was your hour?",
                    style = DisplaySerif.copy(fontSize = 42.sp, lineHeight = 46.sp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(6.dp))
                // The hour and its ‹ › in one glass pill, then the countdown. A tap on
                // the countdown explains why only these hours can be rated.
                val mins = minutesFor(targetedHourOffset)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HourWindowPicker(
                        rangeLabel = hourRange(targetedHourOffset),
                        canGoEarlier = targetedHourOffset == 0 && !isPreviousHourLogged,
                        canGoLater = targetedHourOffset == 1 && !isLatestHourLogged,
                        onEarlier = { onTargetedHourOffsetChange(1) },
                        onLater = { onTargetedHourOffsetChange(0) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        // "2h left" / "1h 5m left" / "40m left", never "120m left"
                        when {
                            mins < 60 -> "${mins}m left"
                            mins % 60 == 0 -> "${mins / 60}h left"
                            else -> "${mins / 60}h ${mins % 60}m left"
                        },
                        fontSize = 18.sp,
                        fontWeight = if (mins <= 15) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = field.fieldHoney,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(PillShape)
                            .clickable(onClickLabel = "Why only these hours?") { showWindowInfo = true }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }

                RatingFrame(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    dial = {
                        DecagonCombDial(
                            rating = selectedScore,
                            onRatingChange = { if (!isLoggedCurrent) selectedScore = it },
                            // The hero keeps the width it had inside the old card. Its art
                            // is square but not its box: the notch tops out 6.3% below the
                            // box and the lowest cell ends 10.6% above its bottom, so both
                            // are trimmed (the top by placing at a negative offset) and
                            // neither the hour line nor the tags pay for empty rim.
                            modifier = Modifier
                                .padding(horizontal = 32.dp)
                                .onGloballyPositioned { onRatingCardYPosition(it.positionInParent().y) }
                                .layout { measurable, constraints ->
                                    val p = measurable.measure(constraints)
                                    val top = (p.height * 0.063f).roundToInt()
                                    val bottom = (p.height * 0.106f).roundToInt()
                                    layout(p.width, p.height - top - bottom) { p.place(0, -top) }
                                }
                        )
                    },
                    // Collapsed, the tags show as many chips as fit and fold the rest
                    // into "+N more"; expanded, they scroll here with the hairline thumb.
                    tags = {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (tagsExpanded) Modifier.verticalScroll(tagsScroll) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TagPickerSection(
                                    allRatings = allRatings,
                                    availableTags = availableTags,
                                    selectedTags = selectedTags,
                                    expanded = tagsExpanded,
                                    onExpandedChange = { tagsExpanded = it },
                                    onTagsUpdate = {
                                        availableTags = it
                                        viewModel.triggerRefresh()
                                    },
                                    onManageTags = onOpenTags
                                )
                            }
                            // matchParentSize: the thumb takes the tags' height without
                            // adding to it, so the box still wraps the chips.
                            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                                FadingScrollbar(
                                    scrollState = tagsScroll,
                                    modifier = Modifier.fillMaxHeight().padding(vertical = 10.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    },
                    note = {
                        // The "+" glyph's stroke, not its 4dp-padded tap row, sits on
                        // the 16dp edge (NotesSection nudges it in field mode).
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            NotesSection(
                                noteText = currentNote,
                                onNoteChange = { currentNote = it },
                                enabled = true,
                                field = true
                            )
                        }
                    }
                )

                // Save: a full-width honey pill pinned just above the nav bar, so
                // the commit action is on screen at any height. The label IS the
                // state machine — it says what's missing instead of a dead tick.
                val saveLabel = when {
                    // Not "Pick a rating": the dial's core already says that
                    selectedScore == null -> "Rate and tag to save"
                    selectedTags.isEmpty() -> "Add a tag to save"
                    else -> {
                        // A late hour that tips yesterday over says so
                        val targetHourStartMs = hourStart - (targetedHourOffset + 1) * 3_600_000L
                        "Save ${displayHourInfo.first}" +
                            (lateHourHint(targetHourStartMs, streakState.recentDays) ?: "")
                    }
                }
                val canSave = !isLoggedCurrent && selectedScore != null && selectedTags.isNotEmpty()
                Button(
                    onClick = {
                        val score = selectedScore ?: return@Button
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val entry = RatingEntry(
                            System.currentTimeMillis(),
                            score,
                            // timestamp = START of the rated hour (one hour before the end)
                            Calendar.getInstance().apply {
                                add(Calendar.HOUR_OF_DAY, -(targetedHourOffset + 1))
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis,
                            displayHourInfo.third,
                            currentNote,
                            selectedTags.toList()
                        )
                        viewModel.saveRating(context, entry)

                        // No ceremony: reset and swap to the next pending hour
                        selectedScore = null
                        selectedTags.clear()
                        currentNote = ""
                        if (targetedHourOffset == 0 && !isPreviousHourLogged) {
                            onTargetedHourOffsetChange(1)
                        } else if (targetedHourOffset == 1 && !isLatestHourLogged) {
                            onTargetedHourOffsetChange(0)
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        // 16dp on all three sides; the gap above it is the frame's last
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        // The soft honey glow, only while it can act
                        .then(
                            if (canSave) Modifier.shadow(
                                14.dp, PillShape, ambientColor = field.accentFill, spotColor = field.accentFill
                            ) else Modifier
                        ),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = field.accentFill,
                        contentColor = field.onAccentFill,
                        // Disabled is honey at low opacity, never a grey slab; its
                        // label is honey-as-text, which reads on that wash
                        disabledContainerColor = field.accentFill.copy(alpha = 0.18f),
                        disabledContentColor = field.fieldHoney
                    )
                ) {
                    // A hint-long label wraps to two centred lines, never ellipsizes
                    Text(
                        saveLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InstrumentSans,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // ---- CAUGHT UP: today's ring, the day so far, then the bee ----
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(18.dp))
                    CaughtUpRing(state = streakState, superHoursLeft = LocalBandChrome.current?.superHoursLeft)
                    Spacer(Modifier.height(14.dp))
                    Text("All caught up", style = DisplaySerif.copy(fontSize = 31.sp, lineHeight = 35.sp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Next hour opens in ${minutesToNextHour}m",
                        fontSize = NowBodySize,
                        color = field.fieldInkMuted
                    )
                    Spacer(Modifier.height(20.dp))

                    // The day paints itself — one cell per hour of the active window
                    TodayStripPanel(
                        window = activeWindow,
                        entriesByHour = todayByHour,
                        average = todayRatings.takeIf { it.isNotEmpty() }?.map { it.score }?.average(),
                        nowMillis = nowMillis,
                        onCellClick = { entry -> editingEntry = entry }
                    )
                    if (missedHoursToday.isNotEmpty()) {
                        Text(
                            "${missedHoursToday.size} hour${if (missedHoursToday.size == 1) "" else "s"} slipped past · add a past hour from the Streak tab ›",
                            fontSize = NowCaptionSize,
                            textAlign = TextAlign.Center,
                            color = field.fieldInkMuted,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(PillShape)
                                .clickable(onClickLabel = "Open Streak tab", onClick = onOpenStreaks)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // The earned way out: catch the bee. It flies in whatever height
                // is left, its prompt riding just above the Lock button.
                CatchTheBee(
                    onCatch = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lockPhone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )

                // The muscle-memory way out, always in the same spot. Outlined
                // glass, not honey: leaving is not the "act here" action.
                Row(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(PillShape)
                        .border(1.5.dp, field.fieldInk.copy(alpha = 0.25f), PillShape)
                        .clickable(onClick = lockPhone),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The 🔒 is an icon; the phrase itself is owner-chosen and stays word for word.
                    BeeIcon(Sym.Lock, size = 18.dp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Lock phone and bee mindful",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InstrumentSans
                    )
                }
            }
        }
        }

        // First-run: locking needs the accessibility service turned on once
        if (showEnableAccessibility) {
            AlertDialog(
                onDismissRequest = { showEnableAccessibility = false },
                title = { Text("Turn on phone locking") },
                text = {
                    Text(
                        "Beeing locks your screen the same way the power button does, so " +
                                "Face/Fingerprint unlock keeps working next time. Turn on " +
                                "\"Beeing\" under Accessibility once to enable it.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showEnableAccessibility = false
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        )
                    }) { Text("Open settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showEnableAccessibility = false }) { Text("Not now") }
                }
            )
        }

        // Why-only-two-hours explainer
        if (showWindowInfo) {
            AlertDialog(
                onDismissRequest = { showWindowInfo = false },
                title = { Text("Why only these hours?") },
                text = {
                    Text(
                        "You can rate the last completed hour and the one before it. " +
                                "Rating close to the moment keeps you honest — and mindful of your day as it happens.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showWindowInfo = false }) { Text("Got it") }
                }
            )
        }

        // Undo for a deleted hour, floating just above the pinned footer.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 88.dp)
        )

        // Edit a rated hour from the today strip (inside the 10h window)
        if (editingEntry != null) {
            ModalBottomSheet(
                onDismissRequest = { editingEntry = null },
                sheetState = editSheetState
            ) {
                EditEntrySheet(
                    entry = editingEntry!!,
                    dayEntries = todayRatings,
                    onPersist = { updated -> viewModel.saveRating(context, updated) },
                    onClose = { editingEntry = null },
                    onDelete = { id ->
                        val deleted = allRatings.find { it.id == id }
                        viewModel.deleteRating(context, id)
                        editingEntry = null
                        // Undo lives here, not in the sheet: the sheet is gone
                        // the moment the hour is deleted.
                        if (deleted != null) scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Rating deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.saveRating(context, deleted)
                        }
                    }
                )
            }
        }
    }
}

// ============================================================
// TODAY STRIP  (the day painting itself, hour by hour)
// ============================================================

@Composable
private fun TodayStripPanel(
    window: IntRange,
    entriesByHour: Map<Int, RatingEntry>,
    average: Double?,
    nowMillis: Long,
    onCellClick: (RatingEntry) -> Unit
) {
    val x = LocalBeeExtras.current
    val currentHour = remember(nowMillis) {
        Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    }
    GlassPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TODAY",
                fontSize = NowMicroSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                color = x.fieldInkMuted,
                modifier = Modifier.weight(1f)
            )
            if (average != null) {
                Text(
                    "avg ${String.format(Locale.getDefault(), "%.1f", average)}",
                    fontSize = NowCaptionSize,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (h in window) {
                val entry = entriesByHour[h]
                val isFuture = h >= currentHour && entry == null
                val editable = entry != null &&
                        nowMillis - entry.timestamp <= EDIT_WINDOW_MS
                Box(
                    Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                entry != null -> scoreBandColor(entry.score)
                                else -> fieldEmptyCellColor(x, future = isFuture)
                            }
                        )
                        .then(
                            if (editable) Modifier.clickable { onCellClick(entry!!) }
                            else Modifier
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatHour(window.first), fontSize = NowMicroSize, color = x.fieldInkMuted)
            Text(formatHour((window.first + window.last) / 2), fontSize = NowMicroSize, color = x.fieldInkMuted)
            Text(formatHour((window.last + 1) % 24), fontSize = NowMicroSize, color = x.fieldInkMuted)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap a colored hour to edit it (up to 10 h back)",
            fontSize = NowMicroSize,
            color = x.fieldInkMuted
        )
    }
}

/**
 * Today's hours against the goal, on a glass track: the Streak tab's ring at
 * hero size, so it follows `hoursRequired` (4 segments in beginner mode) and
 * draws the Super day lap once a Master ring closes. The count inside aims at
 * 12 while a Super day is still in reach, like the status strip.
 */
@Composable
private fun CaughtUpRing(state: StreakState, superHoursLeft: Int?) {
    val x = LocalBeeExtras.current
    val goal = state.hoursRequired
    val filled = state.todayHours.coerceAtMost(goal).toFloat()
    val extra = (state.todayHours - goal).coerceAtLeast(0).toFloat()
    val superLap = !state.beginnerMode
    val lap = if (superLap && state.todayQualified) 1f else 0f
    val superReached = superLap && state.todayHours >= SUPER_DAY_HOURS
    val target = if (superHoursLeft != null || superReached) SUPER_DAY_HOURS else goal
    Box(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${state.todayHours} of $target hours rated today"
        }
    ) {
        StreakRing(
            animatedFill = { filled },
            animatedExtra = { extra },
            ringColor = x.accentFill,
            trackColor = x.fieldInk.copy(alpha = 0.12f),
            hoursRequired = goal,
            ringSize = 112.dp,
            strokeWidth = 9.dp,
            scale = { 1f },
            superLap = superLap,
            lapShown = { lap },
            lapColor = x.fieldSuper
        ) {
            Text(
                "${state.todayHours}/$target",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ============================================================
// RATING FRAME  (dial · tags · note between the hour pill and Save)
// ============================================================

/** The chips' 48dp touch rows reach this far above the first chip and below the last. */
private val TagTouchInset = 8.dp

/** The collapsed "+ Add a note" row's tap padding above and below its text. */
private val NoteTapInset = 4.dp

/**
 * The height between the hour pill and Save, shared by the dial, the tags and the
 * note. The dial and the note keep their size; the tags get as many rows as fit
 * with every gap at its minimum, and whatever is left is spread over the four
 * gaps by [shareRatingGaps], so the rhythm holds at any zoom or aspect ratio
 * instead of one gap swallowing the leftover. Gaps are measured between what you
 * see, so the chips' touch padding and the note's tap padding are taken out.
 */
@Composable
private fun RatingFrame(
    modifier: Modifier,
    dial: @Composable () -> Unit,
    tags: @Composable () -> Unit,
    note: @Composable () -> Unit
) {
    Layout(contents = listOf(dial, tags, note), modifier = modifier) { (dialM, tagsM, noteM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val touch = TagTouchInset.roundToPx()
        val tap = NoteTapInset.roundToPx()
        val dialP = dialM.first().measure(loose)
        val noteP = noteM.first().measure(loose)
        val noteSeen = noteP.height - 2 * tap
        val minGaps = RATING_GAP_MIN.sumOf { it.dp.roundToPx() }
        val tagsRoom = constraints.maxHeight - dialP.height - noteSeen - minGaps + 2 * touch
        val tagsP = tagsM.first().measure(loose.copy(maxHeight = tagsRoom.coerceAtLeast(0)))
        val tagsSeen = (tagsP.height - 2 * touch).coerceAtLeast(0)
        val spare = constraints.maxHeight - dialP.height - tagsSeen - noteSeen
        val gaps = shareRatingGaps(spare.toDp().value).map { it.dp.roundToPx() }
        layout(constraints.maxWidth, constraints.maxHeight) {
            var y = gaps[0]
            dialP.place((constraints.maxWidth - dialP.width) / 2, y)
            y += dialP.height + gaps[1] - touch
            tagsP.place(0, y)
            y += tagsP.height - touch + gaps[2] - tap
            noteP.place(0, y)
        }
    }
}

// ============================================================
// TAG PICKER  (as many rows as fit; the overflow folds into "+N more")
// ============================================================

/**
 * Collapsed, the chips (glass pills, honey when selected) fill whatever height
 * the frame gives them, in the user's order, and only the ones that don't fit
 * fold into a "+N more" button: a pill with a DASHED outline and no fill, so it
 * never reads as one more tag. The FlowRow decides
 * what fits (ContextualFlowRow's overflow indicator), so the count is always true for this
 * screen, font scale and tag names. The last item is "Edit tags", which opens
 * the Tags page; when only it would spill, the indicator itself becomes
 * "Edit tags". A selected tag that sits in the fold is counted on the button,
 * so a choice is never invisible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSection(
    allRatings: List<RatingEntry>,
    availableTags: List<String>,
    selectedTags: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTagsUpdate: (List<String>) -> Unit,
    onManageTags: () -> Unit
) {
    if (expanded) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SoulFuelTagsSection(
                allRatings = allRatings,
                availableTags = availableTags,
                selectedTags = selectedTags,
                isEnabled = true,
                onTagsUpdate = onTagsUpdate,
                onManageTags = onManageTags,
                onCollapse = { onExpandedChange(false) },
                field = true
            )
        }
        return
    }
    // ContextualFlowRow, not FlowRow: its indicator learns shownItemCount during
    // composition (FlowRow's only knows it at draw time and throws if read here).
    // Items are the tags, then "Edit tags" last.
    ContextualFlowRow(
        itemCount = availableTags.size + 1,
        // Wraps its rows: the frame's height limit decides how many fit
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy((-8).dp, Alignment.Top),
        overflow = ContextualFlowRowOverflow.expandIndicator {
            // Items are the tags in order, then "Edit tags": everything from
            // shownItemCount on is in the fold.
            val shownTags = shownItemCount.coerceAtMost(availableTags.size)
            val hidden = availableTags.size - shownTags
            if (hidden > 0) {
                val selectedHidden = availableTags.drop(shownTags).count { it in selectedTags }
                // The text never depends on the selection: the row measures this
                // button for several possible fold points, and a wider "· 1 selected"
                // variant made it drop a visible chip whenever a tag was chosen. A
                // chosen tag inside the fold turns the label honey instead.
                TagFoldButton(
                    text = "+$hidden more",
                    holdsSelection = selectedHidden > 0,
                    onClick = { onExpandedChange(true) }
                )
            } else {
                TagFoldButton(text = "Edit tags", onClick = onManageTags)
            }
        }
    ) { index ->
        if (index < availableTags.size) {
            val tag = availableTags[index]
            val selected = tag in selectedTags
            InputChip(
                selected = selected,
                onClick = {
                    if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                },
                label = { TagChipContent(tag, fontSize = FieldChipTextSize, fontWeight = fieldChipWeight(selected)) },
                shape = TagChipShape,
                colors = fieldChipColors(),
                border = fieldChipBorder(selected)
            )
        } else {
            TagFoldButton(text = "Edit tags", onClick = onManageTags)
        }
    }
}

/** "+N more" / "Edit tags": a pill with a dashed glass outline and no fill, so
 *  it reads as a control beside the tags rather than one of them. */
@Composable
private fun TagFoldButton(text: String, onClick: () -> Unit, holdsSelection: Boolean = false) {
    val x = LocalBeeExtras.current
    val dash = x.fieldInk.copy(alpha = 0.32f)
    // Laid out at the InputChips' 48dp (8dp touch padding + 32dp body) and
    // pinned to the TOP of its slot, so it lines up with the chips beside it.
    // The flow row hands its trailing items a squeezed height: honouring it
    // crushed the button to a sliver, and centring in it floated it 8dp high.
    Box(
        Modifier.layout { m, c ->
            val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            layout(p.width, p.height.coerceAtMost(c.maxHeight)) { p.place(0, 0) }
        }
    ) {
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .height(32.dp)
                .clip(PillShape)
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        color = dash,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(
                            width = w,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                        )
                    )
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                color = if (holdsSelection) x.fieldHoney else x.fieldInkMuted
            )
        }
    }
}

/**
 * The hour being rated and its ‹ › carets in one glass pill, so the arrows read
 * as part of the time they change (on this screen glass already means "tap me":
 * the tags). A caret is enabled only when the neighbouring hour is itself still
 * pending, so the user can only step to a window that actually needs a rating.
 */
@Composable
private fun HourWindowPicker(
    rangeLabel: AnnotatedString,
    canGoEarlier: Boolean,
    canGoLater: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val x = LocalBeeExtras.current
    val ink = x.fieldInk
    Row(modifier.height(36.dp).glass(x, PillShape), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEarlier, enabled = canGoEarlier, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Earlier hour",
                tint = ink.copy(alpha = if (canGoEarlier) 1f else 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }
        Text(rangeLabel, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, color = ink)
        IconButton(onClick = onLater, enabled = canGoLater, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Later hour",
                tint = ink.copy(alpha = if (canGoLater) 1f else 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
