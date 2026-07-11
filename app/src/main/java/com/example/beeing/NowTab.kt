package com.example.beeing

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import java.text.SimpleDateFormat
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
    onRingClosed: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Load ratings from ViewModel
    val allRatings = viewModel.allRatings

    // UI state
    var selectedScore by remember { mutableIntStateOf(1) }
    val selectedTags = remember { mutableStateListOf<String>() }
    var currentNote by remember { mutableStateOf("") }
    var availableTags by remember { mutableStateOf(loadTags(context)) }
    var isTagDeleteMode by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showWindowInfo by remember { mutableStateOf(false) }

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

    val bothHoursRated = isLatestHourLogged && isPreviousHourLogged

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

    // Ticking clock for the chip expiry countdown (updates twice a minute)
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

    // IMPORTANT: Use Box to layer celebration overlay on top
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                onClick = onOpenStreaks
            )

            // When both hours rated, show special message card and hide dial
            if (bothHoursRated) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF66BB6A))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "All caught up! Now go make this hour count 🐝",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Show rating chips and dial only when there are hours to rate

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
                                minutesLeft = if (offset == 1) minutesToNextHour else null,
                                selected = targetedHourOffset == offset,
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
                                containerColor = if (selectedScore == s) {
                                    when {
                                        s >= 8 -> Color(0xFF66BB6A)  // 8-10 GREEN
                                        s >= 5 -> Color(0xFFFFB300)  // 5-7 YELLOW
                                        else -> Color(0xFFB71C1C)     // 1-4 RED
                                    }
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
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

                    // Save Button
                    Button(
                        onClick = {
                            val entry = RatingEntry(
                                System.currentTimeMillis(),
                                selectedScore,
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

                            selectedScore = 1
                            selectedTags.clear()
                            currentNote = ""

                            // Auto-switch logic
                            if (targetedHourOffset == 0 && !isPreviousHourLogged) {
                                onTargetedHourOffsetChange(1)
                            } else if (targetedHourOffset == 1 && !isLatestHourLogged) {
                                onTargetedHourOffsetChange(0)
                            }
                        },
                        // A rating needs at least one tag
                        enabled = !isLoggedCurrent && selectedTags.isNotEmpty(),
                        modifier = Modifier.size(100.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(32.dp))
                    }
                }

                if (selectedTags.isEmpty() && !isLoggedCurrent) {
                    Text(
                        "Pick at least one tag below to save this hour",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Tags Section - visible only when the rating circle is visible
            if (!bothHoursRated) {
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

            // Today's single best hour, highlighted
            if (todayBest != null) {
                Spacer(Modifier.height(16.dp))
                BestHourCard(bestHour = todayBest)
            }

            Spacer(Modifier.height(96.dp))
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