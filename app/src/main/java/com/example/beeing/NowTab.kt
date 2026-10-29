package com.example.beeing

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
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
import com.example.beeing.ui.BandFillLayout
import com.example.beeing.ui.BeeButtonDefaults
import com.example.beeing.ui.BeeCardDefaults
import com.example.beeing.ui.BrandBand
import com.example.beeing.ui.BandTopRowSpacer
import com.example.beeing.ui.OnBandFill
import com.example.beeing.ui.animatedHeight
import com.example.beeing.ui.scaledLayout
import com.example.beeing.ui.emptyCellColor
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.icons.tagDisplayText
import com.example.beeing.ui.icons.tagGlyphOf
import com.example.beeing.ui.icons.tagLabelOf
import com.example.beeing.ui.icons.tagSym
import com.example.beeing.ui.theme.HoneyGold
import com.example.beeing.ui.theme.JakartaFontFamily
import com.example.beeing.ui.theme.LocalBeeExtras
import com.example.beeing.ui.theme.RingQualifiedGreen
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * NOW TAB — one job: this hour.
 *
 * Three queue states (0–2 hours can be pending at any time):
 *  - two pending: hour chips (the expiring hour targeted first) + comb + tags + save
 *  - one pending: the hour moves into the card title, no chip row
 *  - none pending: caught-up hero with the next-hour countdown and the Lock
 *    phone button — the ONLY place lock exists — plus the today strip
 *
 * Status (streak / ring) is a slim strip, not a hero; it expands
 * into the full meter on the Hive tab. No per-save ceremony: the state change
 * plus one haptic IS the feedback; celebrations stay reserved for the ring
 * closing (full-screen, existing) and rare milestones.
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
            val reachable = reachableHours(
                day.dayStartMs, day.countedStarts, loadNotificationPrefs(context), System.currentTimeMillis()
            )
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

    // Today's ratings (for the best hour and the today strip)
    val todayRatings = remember(allRatings, viewModel.refreshTrigger, hourStart) {
        val today = Calendar.getInstance()
        allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        }
    }
    val todayBest = remember(todayRatings) {
        todayRatings.maxWithOrNull(compareBy<RatingEntry> { it.score }.thenBy { it.timestamp })
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
    var tagsViewportHeightPx by remember { mutableIntStateOf(0) }

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
            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))) {
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
    Box(modifier = Modifier.fillMaxSize()) {
        // Three slots: the band, the screen's content, and the pinned bottom
        // (Save, or the bee + Lock). Spare height goes to the BAND, never to an
        // empty gap — see BandFillLayout.
        BandFillLayout(
            modifier = Modifier.fillMaxSize(),
            // Rating: the band stays at rest and the card takes every pixel
            // between it and Save, which is pinned just above the nav bar; the
            // tags spend that height (see TagPickerSection). Caught-up keeps
            // the growing band over the bee field.
            compact = !allCaughtUp,
            maxBandGrow = if (allCaughtUp) 160.dp else 0.dp,
            band = { target ->
                // Animated, so expanding or collapsing the tags slides the band
                // up/down (and the tags into the freed room) instead of snapping.
                // Read only at layout time (lambdas below), so the animation never
                // recomposes the band: each frame is a relayout of a few boxes.
                val growState = animateFloatAsState(
                    targetValue = target,
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    label = "bandGrow"
                )
                val grow = { growState.value }
                // Hero type for the band: 22sp at rest, up to 32sp when there is
                // room, so a taller band reads as bigger, not emptier. Laid out once
                // at 32sp and scaled (scaledLayout), so the text is never reshaped.
                val heroStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp, lineHeight = 38.sp)
                val heroScale = { (24f + 6f * grow()) / 32f }
            // ---- The brand band: who you are (title, streak) and what this
            // screen is asking — the question + hour picker while an hour is
            // pending, "All caught up" + the countdown once none is.
            BrandBand {
                BandTopRowSpacer() // the fixed top row is drawn over this, above the pager
                Spacer(Modifier.animatedHeight { 14.dp + (if (allCaughtUp) 56.dp else 24.dp) * grow() })
                // Rounding slack only: keeps the question on the band's bottom edge.
                Spacer(Modifier.weight(1f))
                if (allCaughtUp) {
                    // The tick spans BOTH lines: the text column is measured first
                    // and the tick is drawn as a square exactly that tall, so the
                    // pair reads as one block with one shared left edge. Measured in
                    // layout, so it tracks the title's scale as the band grows.
                    Layout(content = {
                        Icon(
                            painterResource(Sym.CaughtUp.res),
                            contentDescription = null,
                            tint = RingQualifiedGreen
                        )
                        Column {
                            Text("All caught up", style = heroStyle, modifier = Modifier.scaledLayout(heroScale))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Next hour opens in ${minutesToNextHour}m",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }) { m, c ->
                        val gap = 12.dp.roundToPx()
                        val reserve = 72.dp.roundToPx() // room kept for the tick
                        val text = m[1].measure(
                            c.copy(minWidth = 0, minHeight = 0, maxWidth = (c.maxWidth - reserve - gap).coerceAtLeast(0))
                        )
                        val side = text.height
                        val tick = m[0].measure(Constraints.fixed(side, side))
                        layout(side + gap + text.width, side) {
                            tick.place(0, 0)
                            text.place(side + gap, 0)
                        }
                    }
                } else {
                    val mins = minutesFor(targetedHourOffset)
                    Text("How was your hour?", style = heroStyle, modifier = Modifier.scaledLayout(heroScale))
                    Spacer(Modifier.height(12.dp))
                    // Three tiers of emphasis on one line: the picker is the control
                    // (solid pill, 18sp), the countdown is its caption (14sp, no
                    // container), the info icon is a quiet aside. All three share the
                    // picker's 44dp centre line so nothing floats.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HourWindowPicker(
                            rangeLabel = hourRange(targetedHourOffset),
                            canGoEarlier = targetedHourOffset == 0 && !isPreviousHourLogged,
                            canGoLater = targetedHourOffset == 1 && !isLatestHourLogged,
                            onEarlier = { onTargetedHourOffsetChange(1) },
                            onLater = { onTargetedHourOffsetChange(0) }
                        )
                        Spacer(Modifier.width(12.dp))
                        // Countdown turns honey in its last 15 minutes. It takes the
                        // leftover width (and ellipsizes in the worst case, "11 AM to
                        // 12 PM" on a 360dp phone) so the info icon is never pushed off.
                        val countdownColor = if (mins <= 15) HoneyGold else Color.White.copy(alpha = 0.8f)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            BeeIcon(Sym.Hourglass, size = 16.dp, tint = countdownColor, contentDescription = null)
                            Text(
                                // "2h left" / "1h 5m left" / "40m left", never "120m left"
                                when {
                                    mins < 60 -> "${mins}m left"
                                    mins % 60 == 0 -> "${mins / 60}h left"
                                    else -> "${mins / 60}h ${mins % 60}m left"
                                },
                                fontSize = NowBodySize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = countdownColor,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        IconButton(onClick = { showWindowInfo = true }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Why only these hours?",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            },
            middle = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            if (allCaughtUp) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                if (missedHoursToday.isNotEmpty()) {
                    TextButton(onClick = onOpenStreaks) {
                        Text(
                            "${missedHoursToday.size} hour${if (missedHoursToday.size == 1) "" else "s"} slipped past · add a past hour from the Streak tab ›",
                            fontSize = NowCaptionSize,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                }

                // The day paints itself — one cell per hour of the active window
                TodayStripCard(
                    window = activeWindow,
                    entriesByHour = todayByHour,
                    nowMillis = nowMillis,
                    onCellClick = { entry -> editingEntry = entry }
                )

                // Today's single best hour, highlighted — shown only when the
                // queue is empty: in the rating state the card must stay
                // entirely focused on the pending hour and the pinned save
                // button, and anything below the card would push it off the
                // bottom of the fixed frame anyway.
                if (todayBest != null) {
                    Spacer(Modifier.height(16.dp))
                    BestHourCard(bestHour = todayBest)
                }
                Spacer(Modifier.height(12.dp))
                }
            } else {
                // ---- RATING: one card, ordered the way you act ----
                Spacer(Modifier.height(12.dp))
                // BandFillLayout hands this slot exactly the height between the
                // band and Save; the card fills it (less a 12dp gap above Save).
                Box(Modifier.weight(1f).padding(bottom = 12.dp), contentAlignment = Alignment.TopCenter) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { onRatingCardYPosition(it.positionInParent().y) },
                    shape = BeeCardDefaults.Shape,
                    colors = BeeCardDefaults.colors(),
                    elevation = BeeCardDefaults.elevation()
                ) {
                    // The card is a fixed frame now — only the activity tags
                    // scroll (see the TagPickerSection wrapper below).
                    Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        DecagonCombDial(
                            rating = selectedScore,
                            onRatingChange = { if (!isLoggedCurrent) selectedScore = it },
                            // The dial is square, but its art is not: with the
                            // ring tightened onto the core the notch tops out
                            // 6.3% below the box and the lowest cell ends 10.6%
                            // above its bottom. Trim both — the top by placing
                            // at a negative offset — so the layout box is the
                            // art's actual extent and neither the meta row above
                            // nor the tags below pay for empty rim.
                            modifier = Modifier.layout { measurable, constraints ->
                                val p = measurable.measure(constraints)
                                val top = (p.height * 0.063f).roundToInt()
                                val bottom = (p.height * 0.106f).roundToInt()
                                layout(p.width, p.height - top - bottom) { p.place(0, -top) }
                            }
                        )

                        // The tags own all the height the dial and the note leave.
                        // Collapsed, they show as many chips as fit it and fold
                        // the rest into "+N more"; expanded, they scroll here
                        // with the hairline fading thumb.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onSizeChanged { tagsViewportHeightPx = it.height }
                        ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
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
                            FadingScrollbar(
                                scrollState = tagsScroll,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    // Read in the layout phase, not composition: the viewport
                                    // height changes every frame while the band animates, and a
                                    // composition read would rebuild every tag chip per frame.
                                    .layout { m, c ->
                                        val h = tagsViewportHeightPx
                                        val p = m.measure(c.copy(minHeight = h, maxHeight = h))
                                        layout(p.width, h) { p.place(0, 0) }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        NotesSection(
                            noteText = currentNote,
                            onNoteChange = { currentNote = it },
                            enabled = true
                        )

                    }
                    }
                }
                }

            }
            }
            },
            footer = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            if (allCaughtUp) {
                // The earned way out: catch the bee. Its field is the lower
                // part of the screen — reachable one-handed — with the prompt
                // riding just above where the bee flies.
                CatchTheBee(
                    onCatch = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lockPhone()
                    },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.38f)
                )

                // The muscle-memory way out: always in the same spot.
                Button(
                    onClick = lockPhone,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    )
                ) {
                    // Both glyphs of "🔒 … 🐝" become icons; the phrase
                    // itself is owner-chosen and stays word for word.
                    BeeIcon(Sym.Lock, size = 18.dp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Lock phone and bee mindful",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(Modifier.width(6.dp))
                    BeeIcon(Sym.Hive, size = 16.dp, contentDescription = null)
                }
                Spacer(Modifier.height(8.dp)) // clear of the nav bar
            } else {
                // The save button lives OUTSIDE the rating card and outside its
                // (tags-only) scroller — it is pinned to the bottom of the
                // frame, right above the nav bar, so it is reachable at any
                // screen height.
                Spacer(Modifier.height(12.dp))
                run {
                    // The save button IS the state machine — its label
                    // explains what's missing instead of a dead checkmark
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
                            enabled = !isLoggedCurrent && selectedScore != null && selectedTags.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalBeeExtras.current.accentFill,
                                contentColor = LocalBeeExtras.current.onAccentFill
                            )
                        ) {
                            // A hint-long label wraps to two centred lines, never ellipsizes
                            Text(
                                saveLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                        }
                    }
                Spacer(Modifier.height(8.dp)) // clear of the nav bar
            }
            }
            }
        )

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
private fun TodayStripCard(
    window: IntRange,
    entriesByHour: Map<Int, RatingEntry>,
    nowMillis: Long,
    onCellClick: (RatingEntry) -> Unit
) {
    val currentHour = remember(nowMillis) {
        Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = BeeCardDefaults.colors(),
        elevation = BeeCardDefaults.elevation()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "TODAY SO FAR",
                fontSize = NowMicroSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                                    isFuture -> emptyCellColor(future = true)
                                    else -> emptyCellColor(future = false)
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
                Text(
                    formatHour(window.first),
                    fontSize = NowMicroSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatHour((window.first + window.last) / 2),
                    fontSize = NowMicroSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatHour((window.last + 1) % 24),
                    fontSize = NowMicroSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap a colored hour to edit it (up to 10 h back)",
                fontSize = NowMicroSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ============================================================
// TAG PICKER  (fills its height; the overflow folds into "+N more")
// ============================================================

/**
 * Collapsed, the chips fill whatever height the card gives them, in the user's
 * order, and only the ones that don't fit fold into a "+N more" button, a
 * rounded RECTANGLE so it never reads as one more tag pill. The FlowRow decides
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
                onCollapse = { onExpandedChange(false) }
            )
        }
        return
    }
    // ContextualFlowRow, not FlowRow: its indicator learns shownItemCount during
    // composition (FlowRow's only knows it at draw time and throws if read here).
    // Items are the tags, then "Edit tags" last.
    ContextualFlowRow(
        itemCount = availableTags.size + 1,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy((-8).dp, Alignment.Top),
        overflow = ContextualFlowRowOverflow.expandIndicator {
            // Items are the tags in order, then "Edit tags": everything from
            // shownItemCount on is in the fold.
            val shownTags = shownItemCount.coerceAtMost(availableTags.size)
            val hidden = availableTags.size - shownTags
            if (hidden > 0) {
                val selectedHidden = availableTags.drop(shownTags).count { it in selectedTags }
                TagFoldButton(
                    text = if (selectedHidden > 0) "+$hidden more · $selectedHidden selected" else "+$hidden more",
                    onClick = { onExpandedChange(true) }
                )
            } else {
                TagFoldButton(text = "Edit tags", onClick = onManageTags)
            }
        }
    ) { index ->
        if (index < availableTags.size) {
            val tag = availableTags[index]
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
        } else {
            TagFoldButton(text = "Edit tags", onClick = onManageTags)
        }
    }
}

/** "+N more" / "Edit tags": an outlined rounded rectangle, not a pill, so it
 *  reads as a control beside the tags rather than one of them. */
@Composable
private fun TagFoldButton(text: String, onClick: () -> Unit) {
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
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Compact, single-line hour-window picker that sits on the "How was your" line:
 * the targeted hour range flanked by ‹ › carets. A caret is enabled only when
 * the neighbouring hour is itself still pending, so the user can only step to a
 * window that actually needs a rating. The countdown lives beside the chip, not
 * inside it, so the pill holds only the one thing it controls.
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
    // Lives on the brand band: the band's pill language (OnBandFill, like the
    // StatusStrip) but a step louder — 44dp tall, a hairline white edge and an
    // 18sp ExtraBold range matching the strip's numerals — so the hour being
    // rated is the first thing read under the question.
    val onContainer = Color.White
    Card(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(
            containerColor = OnBandFill,
            contentColor = Color.White
        )
    ) {
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onEarlier,
                enabled = canGoEarlier,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Earlier hour",
                    tint = onContainer.copy(alpha = if (canGoEarlier) 1f else 0.25f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                rangeLabel,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                maxLines = 1,
                color = onContainer
            )
            IconButton(
                onClick = onLater,
                enabled = canGoLater,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Later hour",
                    tint = onContainer.copy(alpha = if (canGoLater) 1f else 0.25f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Today's single best hour, highlighted.
 */
@Composable
fun BestHourCard(bestHour: RatingEntry, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = BeeCardDefaults.colors(),
        elevation = BeeCardDefaults.elevation()
    ) {
        val cal = Calendar.getInstance().apply { timeInMillis = bestHour.timestamp }
        val startH = cal.get(Calendar.HOUR_OF_DAY)
        val endH = if (startH == 23) 0 else startH + 1
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(getScoreColor(bestHour.score.toDouble())),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${bestHour.score}",
                    fontWeight = FontWeight.ExtraBold,
                    color = if (bestHour.score >= 5) Color.Black else Color.White,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BeeIcon(
                        Sym.Star, size = 14.dp, contentDescription = null, tint = HoneyGold
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Today's best hour", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    "${formatHour(startH)} - ${formatHour(endH)}" +
                            bestHour.visibleTags().let { if (it.isEmpty()) "" else " · ${it.joinToString(", ") { t -> tagDisplayText(t) }}" },
                    fontSize = NowCaptionSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
