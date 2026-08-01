package com.example.beeing

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * NOW TAB — one job: this hour.
 *
 * Three queue states (0–2 hours can be pending at any time):
 *  - two pending: hour chips (the expiring hour targeted first) + comb + tags + save
 *  - one pending: the hour moves into the card title, no chip row
 *  - none pending: caught-up hero with the next-hour countdown and the Lock
 *    phone button — the ONLY place lock exists — plus the today strip
 *
 * Status (streak / ring / flowers) is a slim strip, not a hero; it expands
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
    pendingScore: Int? = null,
    onPendingScoreConsumed: () -> Unit = {},
    onRingClosed: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val allRatings = viewModel.allRatings

    // UI state
    var selectedScore by remember { mutableStateOf<Int?>(null) }
    val selectedTags = remember { mutableStateListOf<String>() }
    var currentNote by remember { mutableStateOf("") }
    var availableTags by remember { mutableStateOf(loadTags(context)) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showManageTags by remember { mutableStateOf(false) }
    var showWindowInfo by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<RatingEntry?>(null) }
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEnableAccessibility by remember { mutableStateOf(false) }

    // Score chosen on the notification arrives pre-selected
    LaunchedEffect(pendingScore) {
        pendingScore?.let {
            selectedScore = it
            onPendingScoreConsumed()
        }
    }

    // Calculate hour info based on targetedHourOffset
    val displayHourInfo = remember(targetedHourOffset) {
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -targetedHourOffset) }
        val endH = cal.get(Calendar.HOUR_OF_DAY)
        val range = "${formatHour(if (endH == 0) 23 else endH - 1)} - ${formatHour(endH)}"
        val label = "${if(endH == 0) 24 else endH}${getOrdinalSuffix(if(endH == 0) 24 else endH)}"
        Triple(range, endH, label)
    }

    // Check if current targeted hour is logged
    val isLoggedCurrent by remember(allRatings, displayHourInfo, targetedHourOffset, viewModel.refreshTrigger) {
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

    val isLatestHourLogged = remember(allRatings, viewModel.refreshTrigger) { isHourLogged(0) }
    val isPreviousHourLogged = remember(allRatings, viewModel.refreshTrigger) { isHourLogged(1) }

    val allCaughtUp = isLatestHourLogged && isPreviousHourLogged

    val streakState = remember(allRatings, viewModel.refreshTrigger) {
        computeStreakState(allRatings, loadReclaimSpends(context))
    }

    // Fire the full-screen celebration exactly when today flips to qualified
    var wasQualified by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(streakState.todayQualified) {
        val prev = wasQualified
        wasQualified = streakState.todayQualified
        if (prev == false && streakState.todayQualified) {
            onRingClosed(streakState.currentStreak)
        }
    }

    // Dismiss notification when both hours are rated
    LaunchedEffect(allCaughtUp) {
        if (allCaughtUp) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1) // Cancel the hourly notification (ID = 1)
        }
    }

    // Today's ratings (for the best hour and the today strip)
    val todayRatings = remember(allRatings, viewModel.refreshTrigger) {
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
    val missedHoursToday = remember(allRatings, viewModel.refreshTrigger) {
        val today = Calendar.getInstance()
        val currentHour = today.get(Calendar.HOUR_OF_DAY)
        val ratedStarts = todayRatings.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }.toSet()
        ((currentHour - 3) downTo 0).filter { it !in ratedStarts }
    }

    // Ticking clock for chip expiry + the caught-up countdown (twice a minute)
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: the app title, plus the status card (cell-hive · ring ·
            // 🌸) where taps open the Hive. The ⋮ menu carries data options
            // and "How it works".
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hamburger — carries all data options; sized to sit level
                // with the "Beeing" title beside it.
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Beeing",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                StatusStrip(
                    state = streakState,
                    onClick = onOpenStreaks
                )
            }

            if (allCaughtUp) {
                // ---- CAUGHT UP: countdown hero + the only Lock button ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("All caught up 🐝", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Next hour opens in $minutesToNextHour m",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (isLockAccessibilityServiceEnabled(context)) {
                                    context.sendBroadcast(
                                        Intent(ACTION_LOCK_PHONE).setPackage(context.packageName)
                                    )
                                } else {
                                    showEnableAccessibility = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.inverseSurface,
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        ) {
                            Text("🔒 Lock phone and bee mindful🐝", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                if (missedHoursToday.isNotEmpty()) {
                    TextButton(onClick = onOpenStreaks) {
                        Text(
                            "${missedHoursToday.size} hour${if (missedHoursToday.size == 1) "" else "s"} slipped past the window · recover with 🌸 in the Hive ›",
                            fontSize = 12.sp,
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
            } else {
                // ---- RATING: one card, ordered the way you act ----
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { onRatingCardYPosition(it.positionInParent().y) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        // Compact hour range: collapse the shared meridiem
                        // ("3–4pm") so the whole thing fits the title line;
                        // spell both out only across the noon boundary ("11am–12pm").
                        fun hourRange(offset: Int): String {
                            val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -offset) }
                            val endH = cal.get(Calendar.HOUR_OF_DAY)
                            val startH = if (endH == 0) 23 else endH - 1
                            val startNum = if (startH % 12 == 0) 12 else startH % 12
                            return if ((startH < 12) == (endH < 12)) {
                                "$startNum–${formatHour(endH)}"
                            } else {
                                "${formatHour(startH)}–${formatHour(endH)}"
                            }
                        }
                        fun minutesFor(offset: Int) =
                            if (offset == 1) minutesToNextHour else minutesToNextHour + 60

                        // Header, all on one line: prompt · picker chip (‹ › carets,
                        // each live only when that neighbouring hour is still
                        // pending — offset 1 = earlier/expiring, offset 0 = latest)
                        // · countdown to the right · info at the far edge.
                        val mins = minutesFor(targetedHourOffset)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "How was your",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            HourWindowPicker(
                                rangeLabel = hourRange(targetedHourOffset),
                                canGoEarlier = targetedHourOffset == 0 && !isPreviousHourLogged,
                                canGoLater = targetedHourOffset == 1 && !isLatestHourLogged,
                                onEarlier = { onTargetedHourOffsetChange(1) },
                                onLater = { onTargetedHourOffsetChange(0) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "⏳${mins}m",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = if (mins <= 15) Color(0xFFFFB300)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showWindowInfo = true }, modifier = Modifier.size(22.dp)) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Why only these hours?",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        DecagonCombDial(
                            rating = selectedScore,
                            onRatingChange = { if (!isLoggedCurrent) selectedScore = it },
                            // The dial is square but its bottom ~5% is empty rim
                            // below the lowest cell; drop it from the measured
                            // height so the tags sit right under the comb.
                            modifier = Modifier.layout { measurable, constraints ->
                                val p = measurable.measure(constraints)
                                val trimmed = (p.height * 0.95f).roundToInt()
                                layout(p.width, trimmed) { p.place(0, 0) }
                            }
                        )

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
                            onManageTags = { showManageTags = true }
                        )

                        Spacer(Modifier.height(6.dp))
                        NotesSection(
                            noteText = currentNote,
                            onNoteChange = { currentNote = it },
                            enabled = true
                        )

                        Spacer(Modifier.height(10.dp))
                        // The save button IS the state machine — its label
                        // explains what's missing instead of a dead checkmark
                        val earnsFlower = streakState.todayHours >= STREAK_HOURS_REQUIRED
                        val saveLabel = when {
                            selectedScore == null -> "Pick a rating"
                            selectedTags.isEmpty() -> "Tag it to save"
                            earnsFlower -> "Save ${displayHourInfo.first} · +1 🌸"
                            else -> "Save ${displayHourInfo.first}"
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
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(saveLabel, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // Today's single best hour, highlighted
            if (todayBest != null) {
                Spacer(Modifier.height(16.dp))
                BestHourCard(bestHour = todayBest)
            }

            Spacer(Modifier.height(112.dp)) // clearance for the floating nav pill
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

        // Tag Dialog
        if (showTagDialog) {
            var newTag by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showTagDialog = false },
                title = { Text("Add new tag") },
                text = {
                    Column {
                        OutlinedTextField(
                            newTag,
                            onValueChange = { if (it.length <= 20) newTag = it },
                            label = { Text("Tag name") },
                            supportingText = { Text("${newTag.length}/20") },
                            singleLine = true
                        )
                        if (availableTags.size >= 30) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Maximum of 30 tags reached",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${availableTags.size}/30 tags",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newTag.isNotBlank() && availableTags.size < 30) {
                                availableTags = (availableTags + newTag.trim()).distinct()
                                saveTags(context, availableTags)
                                selectedTags.add(newTag.trim())
                            }
                            showTagDialog = false
                        },
                        enabled = newTag.isNotBlank() && availableTags.size < 30
                    ) {
                        Text("Add")
                    }
                }
            )
        }

        // Manage tags: reorder (long-press drag) / rename / delete / add
        if (showManageTags) {
            ManageTagsDialog(
                tags = availableTags,
                onReorder = { newOrder ->
                    saveTags(context, newOrder)
                    availableTags = newOrder
                    viewModel.triggerRefresh()
                },
                onRename = { old, new ->
                    // Past entries keep the old name on purpose — a rename
                    // only changes the picker going forward.
                    val newTags = availableTags.map { if (it == old) new else it }
                    saveTags(context, newTags)
                    availableTags = newTags
                    val i = selectedTags.indexOf(old)
                    if (i >= 0) selectedTags[i] = new
                    viewModel.triggerRefresh()
                },
                onDelete = { tag ->
                    val newTags = availableTags.filter { it != tag }
                    saveTags(context, newTags)
                    availableTags = newTags
                    selectedTags.remove(tag)
                    viewModel.triggerRefresh()
                },
                onAddNew = { showTagDialog = true },
                onDismiss = { showManageTags = false }
            )
        }

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
                        viewModel.deleteRating(context, id)
                        editingEntry = null
                    }
                )
            }
        }
    }
}

// ============================================================
// STATUS STRIP  (🔥 streak · ring x/8 · 🌸 flowers)
// ============================================================

@Composable
private fun StatusStrip(
    state: StreakState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A neutral gray fill (not the tinted surfaceVariant) so the strip pops off
    // the header in both themes; grays chosen to sit above the app background.
    val stripBg = if (isSystemInDarkTheme()) Color(0xFF3B3B3E) else Color(0xFFDDDCE0)
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(
            containerColor = stripBg,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⬢",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    color = Color(0xFFFFB300) // honey gold — hive identity
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${state.currentStreak}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
            StripDivider()
            MiniHourRing(
                hours = state.todayHours,
                qualified = state.todayQualified
            )
            Text(
                "${state.todayHours}/$STREAK_HOURS_REQUIRED",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            StripDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🌸",
                    fontSize = 21.sp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${state.flowers}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun StripDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 18.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    )
}

@Composable
private fun MiniHourRing(hours: Int, qualified: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (qualified) Color(0xFF66BB6A) else Color(0xFFFFB300)
    val fraction = (hours.toFloat() / STREAK_HOURS_REQUIRED).coerceIn(0f, 1f)
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "TODAY SO FAR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
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
                                    isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
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
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatHour((window.first + window.last) / 2),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatHour((window.last + 1) % 24),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap a colored hour to edit it (up to 10 h back)",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ============================================================
// TAG PICKER  (capped at ~2 rows; expands to the full editor)
// ============================================================

private const val COLLAPSED_TAG_COUNT = 9

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
    } else {
        // Keep the original order, but never hide a tag that's selected
        val visibleTags = availableTags.filterIndexed { index, tag ->
            index < COLLAPSED_TAG_COUNT || tag in selectedTags
        }
        val hiddenCount = availableTags.size - visibleTags.size
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy((-8).dp, Alignment.CenterVertically)
        ) {
            visibleTags.forEach { tag ->
                InputChip(
                    selected = tag in selectedTags,
                    onClick = {
                        if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                    },
                    label = { Text(tag) },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
            AssistChip(
                onClick = {
                    // "+N more" just reveals the hidden tags; "edit tags" means
                    // the user wants to reorder/rename/delete/add, so open the
                    // manage popup directly.
                    if (hiddenCount > 0) onExpandedChange(true) else onManageTags()
                },
                label = {
                    Text(if (hiddenCount > 0) "+ $hiddenCount more" else "edit tags")
                },
                border = null,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Manage-tags popup: one row per tag — long-press-drag anywhere on the row to
 * reorder (the ☰ glyph is the affordance), pencil renames inline, ✕ deletes.
 * A footer row adds a new tag via the existing add-tag dialog.
 */
@Composable
private fun ManageTagsDialog(
    tags: List<String>,
    onReorder: (List<String>) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    val localTags = remember(tags) { tags.toMutableStateList() }
    var draggingTag by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var renamingTag by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmDeleteTag by remember { mutableStateOf<String?>(null) }
    val rowHeight = 46.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            "Manage tags",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Hold & drag to reorder", fontSize = 11.sp, color = hint)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close", Modifier.size(18.dp), tint = hint)
                    }
                }
                Spacer(Modifier.height(8.dp))

                Column(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    localTags.forEach { tag ->
                        key(tag) {
                            val isDragging = draggingTag == tag
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            translationY = dragOffset
                                            scaleX = 1.02f; scaleY = 1.02f
                                            shadowElevation = 12f
                                        }
                                    }
                                    .then(
                                        if (isDragging) Modifier.background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(10.dp)
                                        ) else Modifier
                                    )
                                    .pointerInput(tag) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                renamingTag = null
                                                draggingTag = tag
                                                dragOffset = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val from = localTags.indexOf(tag)
                                                val shift = (dragOffset / rowHeightPx).roundToInt()
                                                val to = (from + shift).coerceIn(0, localTags.lastIndex)
                                                if (to != from) {
                                                    localTags.removeAt(from)
                                                    localTags.add(to, tag)
                                                    dragOffset -= (to - from) * rowHeightPx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            },
                                            onDragEnd = {
                                                draggingTag = null
                                                dragOffset = 0f
                                                onReorder(localTags.toList())
                                            },
                                            onDragCancel = {
                                                draggingTag = null
                                                dragOffset = 0f
                                            }
                                        )
                                    }
                            ) {
                                Icon(
                                    Icons.Default.Menu, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = hint.copy(alpha = 0.45f)
                                )
                                Spacer(Modifier.width(10.dp))
                                if (renamingTag == tag) {
                                    val sanitized = renameText.replace(Regex("[,;|]"), " ").trim()
                                    val valid = sanitized.isNotBlank() &&
                                            (sanitized == tag || sanitized !in localTags)
                                    BasicTextField(
                                        value = renameText,
                                        onValueChange = { if (it.length <= 20) renameText = it },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                                    IconButton(
                                        onClick = {
                                            if (sanitized != tag) onRename(tag, sanitized)
                                            renamingTag = null
                                        },
                                        enabled = valid,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check, "Save name",
                                            Modifier.size(18.dp),
                                            tint = if (valid) Color(0xFF4CAF50)
                                            else hint.copy(alpha = 0.3f)
                                        )
                                    }
                                } else {
                                    Text(
                                        tag, fontSize = 14.sp, maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { renamingTag = tag; renameText = tag },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit, "Rename $tag",
                                            Modifier.size(16.dp), tint = hint
                                        )
                                    }
                                    IconButton(
                                        onClick = { confirmDeleteTag = tag },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete, "Delete $tag",
                                            Modifier.size(16.dp), tint = hint
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = localTags.size < 30) { onAddNew() }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = hint)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (localTags.size < 30) "New tag" else "Tag limit reached (30)",
                        fontSize = 13.sp, color = hint
                    )
                }
            }
        }
    }

    // Confirm before deleting — deletion can't be undone from the picker.
    confirmDeleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTag = null },
            title = { Text("Delete \"$tag\"?") },
            text = {
                Text(
                    "Removed from the picker. Past hours keep it.",
                    fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tag)
                    confirmDeleteTag = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTag = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Compact, single-line hour-window picker that sits on the "How was your" line:
 * the targeted hour range flanked by ‹ › carets. A caret is enabled only when
 * the neighbouring hour is itself still pending, so the user can only step to a
 * window that actually needs a rating. The countdown lives beside the chip, not
 * inside it, so the whole control stays one text-height tall.
 */
@Composable
private fun HourWindowPicker(
    rangeLabel: String,
    canGoEarlier: Boolean,
    canGoLater: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onEarlier,
                enabled = canGoEarlier,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Earlier hour",
                    tint = onContainer.copy(alpha = if (canGoEarlier) 1f else 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                rangeLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                color = onContainer
            )
            IconButton(
                onClick = onLater,
                enabled = canGoLater,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Later hour",
                    tint = onContainer.copy(alpha = if (canGoLater) 1f else 0.25f),
                    modifier = Modifier.size(20.dp)
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
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
                Text("⭐ Today's best hour", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "${formatHour(startH)} - ${formatHour(endH)}" +
                            if (bestHour.tags.isNotEmpty()) " · ${bestHour.tags.joinToString(", ")}" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
