package com.example.beeing

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * NOW TAB - UPDATED
 * - More top padding for status bar
 * - 7 is now yellow (8-10 green)
 * - Score cards only shown when both hours rated
 * - Dismisses notification when both rated
 * - Special overlay for score 10
 */
@Composable
fun NowTab(
    viewModel: RatingsViewModel,
    scrollState: ScrollState,
    targetedHourOffset: Int,
    onTargetedHourOffsetChange: (Int) -> Unit,
    ratingCardYPosition: Float,
    onRatingCardYPosition: (Float) -> Unit,
    onOpenStreaks: () -> Unit = {},
    pendingScore: Int? = null,
    onPendingScoreConsumed: () -> Unit = {},
    onRingClosed: (Int) -> Unit = {},
    header: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Load ratings from ViewModel
    val allRatings = viewModel.allRatings

    // UI state — no score is pre-selected; the user must explicitly pick one
    // (mirrors the existing "tags are mandatory" rule).
    var selectedScore by remember { mutableStateOf<Int?>(null) }
    val selectedTags = remember { mutableStateListOf<String>() }
    var currentNote by remember { mutableStateOf("") }
    var availableTags by remember { mutableStateOf(loadTags(context)) }
    var isTagDeleteMode by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showWindowInfo by remember { mutableStateOf(false) }
    // The "all caught up" card is tucked away behind a tap on the big meter
    // card instead of always showing — collapses shut again the moment
    // there's a new pending hour to rate.
    var showCaughtUpInfo by remember { mutableStateOf(false) }
    // Sticky once the user taps Save while incomplete; cleared on a
    // successful save or when the targeted hour changes, so it never
    // bleeds into a fresh hour.
    var attemptedSubmit by remember { mutableStateOf(false) }

    // Score chosen on the notification arrives pre-selected
    LaunchedEffect(pendingScore) {
        pendingScore?.let {
            selectedScore = it
            onPendingScoreConsumed()
        }
    }

    // Helper: check if a given hour-slot is already logged.
    // offset=0 → the most-recent completed hour (e.g. 12PM-1PM when it's 1PM)
    // offset=1 → the grace-period hour before that
    // Matches on the entry's own timestamp falling in that real calendar hour —
    // never on a separately-computed label, which can drift stale if this
    // composable sits open across an hour boundary without recomposing.
    fun isHourLogged(offset: Int): Boolean {
        val target = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -(offset + 1)) }
        val targetHour = target.get(Calendar.HOUR_OF_DAY)
        val targetDoy = target.get(Calendar.DAY_OF_YEAR)
        val targetYear = target.get(Calendar.YEAR)
        return allRatings.any { entry ->
            val c = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            c.get(Calendar.HOUR_OF_DAY) == targetHour &&
                    c.get(Calendar.DAY_OF_YEAR) == targetDoy &&
                    c.get(Calendar.YEAR) == targetYear
        }
    }

    // Ticking clock (updates twice a minute) — drives the chip expiry
    // countdown AND acts as the fallback that rolls the rateable-hour state
    // over an hour boundary even if the alarm broadcast never arrives.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val hourKey = nowMillis / 3_600_000L

    val isLatestHourLogged = remember(allRatings, viewModel.refreshTrigger, hourKey) { isHourLogged(0) }
    val isPreviousHourLogged = remember(allRatings, viewModel.refreshTrigger, hourKey) { isHourLogged(1) }

    // The hour actually being rated: prefers the caller's/user's choice, but
    // falls back the instant that choice stops being valid — synchronously,
    // every recomposition, with no async round-trip to get wrong. This is
    // what used to require a manual chip tap to un-stick.
    val effectiveOffset = remember(targetedHourOffset, isLatestHourLogged, isPreviousHourLogged) {
        when {
            targetedHourOffset == 0 && !isLatestHourLogged -> 0
            targetedHourOffset == 1 && !isPreviousHourLogged -> 1
            !isLatestHourLogged -> 0
            !isPreviousHourLogged -> 1
            else -> targetedHourOffset
        }
    }
    val isLoggedCurrent = remember(allRatings, effectiveOffset, viewModel.refreshTrigger, hourKey) {
        isHourLogged(effectiveOffset)
    }

    val bothHoursRated = isLatestHourLogged && isPreviousHourLogged

    // A fresh hour context starts with a clean slate
    LaunchedEffect(effectiveOffset) { attemptedSubmit = false }

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
    LaunchedEffect(bothHoursRated) {
        if (bothHoursRated) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1) // Cancel the hourly notification (ID = 1)
        } else {
            showCaughtUpInfo = false
        }
    }

    // Today's single best hour
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

    val minutesToNextHour = remember(nowMillis) {
        60 - Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.MINUTE)
    }
    // Offset 1 expires at the next hour boundary; offset 0 doesn't expire
    // until the boundary after that (it slides into offset 1's slot first).
    fun minutesLeftFor(offset: Int) = minutesToNextHour + if (offset == 0) 60 else 0

    // IMPORTANT: Use Box to layer celebration overlay on top
    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        // This tab owns the app header — it lives inside the page so tab
        // swipes carry it along horizontally.
        header()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Prominent streak meter (core feature)
            StreakMeter(
                state = streakState,
                expanded = bothHoursRated,
                modifier = Modifier.padding(bottom = 16.dp),
                onClick = if (bothHoursRated) {
                    { showCaughtUpInfo = !showCaughtUpInfo }
                } else onOpenStreaks
            )

            // When both hours are rated, the whole rating flow (chips, dial,
            // tags, notes) smoothly collapses down to the caught-up message;
            // when a fresh hour opens it grows back into existence the same way.
            AnimatedContent(
                targetState = bothHoursRated,
                transitionSpec = {
                    ((fadeIn(tween(320, delayMillis = 140)) +
                            slideInVertically(tween(320, delayMillis = 140)) { it / 8 }) togetherWith
                            (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 8 }))
                        .using(SizeTransform(clip = false) { _, _ ->
                            tween(550, easing = FastOutSlowInEasing)
                        })
                },
                label = "dialCollapse",
                modifier = Modifier.fillMaxWidth()
            ) { allRated ->
            if (allRated) {
                // Only reveals once the big meter card above is tapped, with
                // the same slide+fade used for the rating warning below.
                AnimatedVisibility(
                    visible = showCaughtUpInfo,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)) +
                            slideInVertically(tween(220)) { -it / 2 },
                    exit = fadeOut(tween(220)) + shrinkVertically(tween(220)) +
                            slideOutVertically(tween(220)) { -it / 2 }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = appCardColor()),
                        border = BorderStroke(1.5.dp, Color(0xFF66BB6A).copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (streakState.todayQualified)
                                    "Streak extended for today! Come back with a good rating for this hour too 🐝"
                                else
                                    "All caught up! Make the best use of this hour too 🐝",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF66BB6A),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Both rateable hours as always-visible chips: selected = filled,
                // the older one carries a quiet expiry countdown.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "How was your…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            onRatingCardYPosition(coordinates.positionInParent().y)
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // offset 1 (older, expires at the next hour boundary) then offset 0
                    listOf(1, 0).forEach { offset ->
                        val logged = if (offset == 0) isLatestHourLogged else isPreviousHourLogged
                        if (!logged) {
                            val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -offset) }
                            val endH = cal.get(Calendar.HOUR_OF_DAY)
                            val range = "${formatHour(if (endH == 0) 23 else endH - 1)} - ${formatHour(endH)}"
                            PendingHourChip(
                                rangeLabel = range,
                                minutesLeft = minutesLeftFor(offset),
                                selected = effectiveOffset == offset,
                                onClick = { onTargetedHourOffsetChange(offset) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Rating Dial - 7 is now YELLOW (8-10 GREEN)
                Box(
                    Modifier
                        .padding(vertical = 20.dp)
                        .height(300.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    (1..10).forEach { s ->
                        val angle = (s - 1) * 36f - 90f
                        val x = (120f * cos(Math.toRadians(angle.toDouble()))).toFloat()
                        val y = (120f * sin(Math.toRadians(angle.toDouble()))).toFloat()

                        FilledTonalButton(
                            onClick = {
                                selectedScore = s
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier.offset(x.dp, y.dp).size(70.dp),
                            shape = CircleShape,
                            enabled = !isLoggedCurrent,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selectedScore == s) scoreBandColor(s)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = s.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selectedScore == s) {
                                    // Dark text on green/yellow, white on red for contrast
                                    if (s >= 5) Color.Black else Color.White
                                } else Color.Unspecified
                            )
                        }
                    }

                    // Save Button — stays tappable even when incomplete (just
                    // styled to look inert), so tapping it while missing a
                    // score/tag is what actually surfaces the warning below.
                    val canSave = selectedScore != null && selectedTags.isNotEmpty()
                    Button(
                        onClick = {
                            val score = selectedScore
                            if (!canSave || score == null) {
                                attemptedSubmit = true
                                return@Button
                            }
                            // timestamp = START of the rated hour (one hour before the end);
                            // the label is derived from that SAME instant so it can never
                            // drift out of sync with its own timestamp.
                            val ratedHourStart = Calendar.getInstance().apply {
                                add(Calendar.HOUR_OF_DAY, -(effectiveOffset + 1))
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val entry = RatingEntry(
                                System.currentTimeMillis(),
                                score,
                                ratedHourStart.timeInMillis,
                                ordinalHourLabel(ratedHourStart.get(Calendar.HOUR_OF_DAY)),
                                currentNote,
                                selectedTags.toList()
                            )
                            viewModel.saveRating(context, entry)

                            selectedScore = null
                            selectedTags.clear()
                            currentNote = ""
                            attemptedSubmit = false
                        },
                        enabled = !isLoggedCurrent,
                        modifier = Modifier.size(100.dp).alpha(if (canSave) 1f else 0.5f),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canSave) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Default.Check, null, Modifier.size(32.dp),
                            tint = if (canSave) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val warningMessage = when {
                    selectedScore == null && selectedTags.isEmpty() ->
                        "Pick a rating and at least one tag to save this hour"
                    selectedScore == null -> "Pick a rating above to save this hour"
                    selectedTags.isEmpty() -> "Pick at least one tag below to save this hour"
                    else -> null
                }
                AnimatedVisibility(
                    visible = attemptedSubmit && !isLoggedCurrent && warningMessage != null,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)) +
                            slideInVertically(tween(220)) { -it / 2 },
                    exit = fadeOut(tween(220)) + shrinkVertically(tween(220)) +
                            slideOutVertically(tween(220)) { -it / 2 }
                ) {
                    Text(
                        warningMessage ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }

                // Tags + notes are part of the same rating flow — they
                // collapse away with the dial as one unit.
                SoulFuelTagsSection(
                    allRatings = allRatings,
                    availableTags = availableTags,
                    selectedTags = selectedTags,
                    isTagDeleteMode = isTagDeleteMode,
                    isEnabled = true,
                    onTagDeleteModeChange = { isTagDeleteMode = it },
                    onTagsUpdate = {
                        availableTags = it
                        viewModel.triggerRefresh()
                    },
                    onShowTagDialog = { showTagDialog = true }
                )

                Spacer(Modifier.height(12.dp))

                // Notes Section
                NotesSection(
                    noteText = currentNote,
                    onNoteChange = { currentNote = it },
                    enabled = true
                )
                }
            }
            }

            // Today's single best hour, highlighted
            if (todayBest != null) {
                Spacer(Modifier.height(16.dp))
                BestHourCard(bestHour = todayBest)
            }

            Spacer(Modifier.height(96.dp))
        }
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
                            onValueChange = { newTag = it },
                            label = { Text("Tag name") }
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

    }
}

/**
 * One rateable hour as a tappable chip. Selected = filled with the primary
 * container color; each chip shows its own time-left-to-rate, turning amber
 * under 15 minutes.
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
                val timeText = if (minutesLeft >= 60) {
                    "⏳ ${minutesLeft / 60}h ${minutesLeft % 60}m left"
                } else {
                    "⏳ ${minutesLeft}m left"
                }
                Text(
                    timeText,
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
        colors = CardDefaults.cardColors(containerColor = appCardColor(0.5f))
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