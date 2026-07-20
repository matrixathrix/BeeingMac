package com.example.beeing

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

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
    var isTagDeleteMode by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
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
    val twoPending = !isLatestHourLogged && !isPreviousHourLogged

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
            // 🍯) where taps open the Hive. The ⋮ menu carries data options
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
                        // Header: fixed prompt + the "why only these hours" info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "How was your hour?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showWindowInfo = true }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Why only these hours?",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // The hour(s) up for rating. Both chips carry a countdown
                        // (the newer hour lives one hour longer) so they read at
                        // the same height; a lone hour spans the full width.
                        fun hourRange(offset: Int): String {
                            val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -offset) }
                            val endH = cal.get(Calendar.HOUR_OF_DAY)
                            return "${formatHour(if (endH == 0) 23 else endH - 1)} - ${formatHour(endH)}"
                        }
                        fun minutesFor(offset: Int) =
                            if (offset == 1) minutesToNextHour else minutesToNextHour + 60

                        Spacer(Modifier.height(10.dp))
                        if (twoPending) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // The expiring hour first — it dies at the top of the hour
                                listOf(1, 0).forEach { offset ->
                                    PendingHourChip(
                                        rangeLabel = hourRange(offset),
                                        minutesLeft = minutesFor(offset),
                                        selected = targetedHourOffset == offset,
                                        onClick = { onTargetedHourOffsetChange(offset) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            PendingHourChip(
                                rangeLabel = hourRange(targetedHourOffset),
                                minutesLeft = minutesFor(targetedHourOffset),
                                selected = true,
                                onClick = {},
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        CombStrip(
                            selectedScore = selectedScore,
                            onScoreChange = { selectedScore = it },
                            enabled = !isLoggedCurrent
                        )

                        Spacer(Modifier.height(14.dp))
                        Text(
                            "TAG IT · PICK AT LEAST ONE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TagPickerSection(
                            allRatings = allRatings,
                            availableTags = availableTags,
                            selectedTags = selectedTags,
                            expanded = tagsExpanded,
                            onExpandedChange = { tagsExpanded = it },
                            isTagDeleteMode = isTagDeleteMode,
                            onTagDeleteModeChange = { isTagDeleteMode = it },
                            onTagsUpdate = {
                                availableTags = it
                                viewModel.triggerRefresh()
                            },
                            onShowTagDialog = { showTagDialog = true }
                        )

                        Spacer(Modifier.height(12.dp))
                        NotesSection(
                            noteText = currentNote,
                            onNoteChange = { currentNote = it },
                            enabled = true
                        )

                        Spacer(Modifier.height(14.dp))
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
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        // Subtler, thinner hairline outline than before
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⬢",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFFFFB300) // honey gold — hive identity
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${state.currentStreak}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
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
                fontSize = 14.sp
            )
            StripDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🍯",
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${state.savers}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun StripDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 14.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
    )
}

@Composable
private fun MiniHourRing(hours: Int, qualified: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (qualified) Color(0xFF66BB6A) else Color(0xFFFFB300)
    val fraction = (hours.toFloat() / STREAK_HOURS_REQUIRED).coerceIn(0f, 1f)
    Canvas(Modifier.size(18.dp)) {
        val stroke = 3.dp.toPx()
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

private const val COLLAPSED_TAG_COUNT = 15

@Composable
private fun TagPickerSection(
    allRatings: List<RatingEntry>,
    availableTags: List<String>,
    selectedTags: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isTagDeleteMode: Boolean,
    onTagDeleteModeChange: (Boolean) -> Unit,
    onTagsUpdate: (List<String>) -> Unit,
    onShowTagDialog: () -> Unit
) {
    if (expanded) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SoulFuelTagsSection(
                allRatings = allRatings,
                availableTags = availableTags,
                selectedTags = selectedTags,
                isTagDeleteMode = isTagDeleteMode,
                isEnabled = true,
                // Tapping the tick means "done" — close the editor back to the
                // tag row instead of dropping to an expanded pencil-only state.
                onTagDeleteModeChange = { editing ->
                    if (editing) {
                        onTagDeleteModeChange(true)
                    } else {
                        onTagDeleteModeChange(false)
                        onExpandedChange(false)
                    }
                },
                onTagsUpdate = onTagsUpdate,
                onShowTagDialog = onShowTagDialog
            )
            TextButton(onClick = {
                onExpandedChange(false)
                onTagDeleteModeChange(false)
            }) {
                Text("Show less", fontSize = 12.sp)
            }
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
                    onExpandedChange(true)
                    // "+N more" just reveals the hidden tags; "edit tags" means
                    // the user wants to add/delete, so drop straight into edit
                    // mode instead of making them tap the pencil again.
                    if (hiddenCount == 0) onTagDeleteModeChange(true)
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
 * One rateable hour as a tappable chip. Selected = filled with the primary
 * container color; the expiring hour shows minutes left, turning amber under 15.
 */
@Composable
private fun PendingHourChip(
    rangeLabel: String,
    minutesLeft: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) null else BorderStroke(
            1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                rangeLabel,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1
            )
            if (minutesLeft != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "⏳ ${minutesLeft}m left",
                    fontSize = 11.sp,
                    color = if (minutesLeft <= 15) Color(0xFFFFB300)
                    else MaterialTheme.colorScheme.onSurfaceVariant
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
