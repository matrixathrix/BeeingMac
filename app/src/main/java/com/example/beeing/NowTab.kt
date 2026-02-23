package com.example.beeing

import android.app.NotificationManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
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
    onRatingCardYPosition: (Float) -> Unit
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
    var showCelebration by remember { mutableStateOf(false) }
    var showEpicCelebration by remember { mutableStateOf(false) }

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
            val targetDayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
                Date(System.currentTimeMillis() - (targetedHourOffset * 3600000L))
            )
            allRatings.any {
                it.hourLabel == displayHourInfo.third &&
                        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.timestamp)) == targetDayKey
            }
        }
    }

    // Check if latest hour is logged
    val now = remember(viewModel.refreshTrigger) { Calendar.getInstance() }
    val currentHour = now.get(Calendar.HOUR_OF_DAY)

    val isLatestHourLogged = remember(allRatings, viewModel.refreshTrigger) {
        allRatings.any { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.HOUR_OF_DAY) == currentHour &&
                    cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        }
    }

    val isPreviousHourLogged = remember(allRatings, viewModel.refreshTrigger) {
        val previousHour = if (currentHour == 0) 23 else currentHour - 1
        allRatings.any { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val entryHour = cal.get(Calendar.HOUR_OF_DAY)
            val entryDay = cal.get(Calendar.DAY_OF_YEAR)
            val entryYear = cal.get(Calendar.YEAR)

            if (currentHour == 0) {
                val yesterday = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                entryHour == 23 && entryDay == yesterday.get(Calendar.DAY_OF_YEAR) &&
                        entryYear == yesterday.get(Calendar.YEAR)
            } else {
                entryHour == previousHour && entryDay == now.get(Calendar.DAY_OF_YEAR) &&
                        entryYear == now.get(Calendar.YEAR)
            }
        }
    }

    val bothHoursRated = isLatestHourLogged && isPreviousHourLogged
    val streak = calculateStreak(allRatings)

    // Dismiss notification when both hours are rated
    LaunchedEffect(bothHoursRated) {
        if (bothHoursRated) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1) // Cancel the hourly notification (ID = 1)
        }
    }

    // Calculate yesterday's and today's average scores (ONLY when both hours rated)
    val yesterdayAvg = remember(allRatings, viewModel.refreshTrigger, bothHoursRated) {
        if (!bothHoursRated) return@remember 0.0
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayRatings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)

            val dayToCheck = if (hour == 0) {
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            } else {
                cal
            }

            dayToCheck.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
                    dayToCheck.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
        }
        if (yesterdayRatings.isEmpty()) 0.0 else yesterdayRatings.map { it.score }.average()
    }

    val todayAvg = remember(allRatings, viewModel.refreshTrigger, bothHoursRated) {
        if (!bothHoursRated) return@remember 0.0
        val today = Calendar.getInstance()
        val todayRatings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)

            val dayToCheck = if (hour == 0) {
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            } else {
                cal
            }

            dayToCheck.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                    dayToCheck.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        }
        if (todayRatings.isEmpty()) 0.0 else todayRatings.map { it.score }.average()
    }

    // Pulse animation
    val pulseAlpha = remember { Animatable(0.3f) }
    LaunchedEffect(targetedHourOffset, isLoggedCurrent) {
        if (!isLoggedCurrent) {
            repeat(3) {
                pulseAlpha.animateTo(0.8f, animationSpec = tween(300))
                pulseAlpha.animateTo(0.3f, animationSpec = tween(300))
            }
        } else {
            pulseAlpha.snapTo(0.15f)
        }
    }

    // IMPORTANT: Use Box to layer celebration overlay on top
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(top = 24.dp), // EXTRA padding for status bar
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Yesterday and Today Score Cards - ONLY when both hours rated
            if (bothHoursRated && (yesterdayAvg > 0 || todayAvg > 0)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Yesterday's Score Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                yesterdayAvg >= 8.0 -> Color(0xFF66BB6A).copy(alpha = 0.2f)
                                yesterdayAvg >= 5.0 -> Color(0xFFFFB300).copy(alpha = 0.2f)
                                yesterdayAvg > 0.0 -> Color(0xFFB71C1C).copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Yesterday's Average:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (yesterdayAvg > 0) String.format("%.1f", yesterdayAvg) else "-",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    yesterdayAvg >= 8.0 -> Color(0xFF2E7D32)
                                    yesterdayAvg >= 5.0 -> Color(0xFFF57C00)
                                    yesterdayAvg > 0.0 -> Color(0xFFC62828)
                                    else -> Color.Gray
                                }
                            )
                        }
                    }

                    // Today's Score Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                todayAvg >= 8.0 -> Color(0xFF66BB6A).copy(alpha = 0.2f)
                                todayAvg >= 5.0 -> Color(0xFFFFB300).copy(alpha = 0.2f)
                                todayAvg > 0.0 -> Color(0xFFB71C1C).copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Today's Average:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (todayAvg > 0) String.format("%.1f", todayAvg) else "-",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    todayAvg >= 8.0 -> Color(0xFF2E7D32)
                                    todayAvg >= 5.0 -> Color(0xFFF57C00)
                                    todayAvg > 0.0 -> Color(0xFFC62828)
                                    else -> Color.Gray
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Streak Reset Warning - ONLY when streak = 0
            if (streak == 0 && allRatings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "💔 Your streak was reset because you didn't rate your time for more than 2 hours",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

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
                            "Great job rating your past hours, now go make your current hour amazing!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Show rating card and dial only when there are hours to rate

                // SINGLE Rating Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onTargetedHourOffsetChange(if (targetedHourOffset == 0) 1 else 0)
                        }
                        .onGloballyPositioned { coordinates ->
                            onRatingCardYPosition(coordinates.positionInParent().y)
                        },
                    border = if (!isLoggedCurrent) BorderStroke(2.dp, if (isSystemInDarkTheme()) Color.White else Color(0xFF424242)) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha.value)
                    )
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val labelText = when {
                                isLoggedCurrent -> "Already rated:"
                                targetedHourOffset == 0 -> "How was your:"
                                else -> "You also missed rating:"
                            }
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isLoggedCurrent) Color.Gray
                                else if (targetedHourOffset != 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${displayHourInfo.first} (${displayHourInfo.third} hour)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            if (targetedHourOffset == 0) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "You can only rate the immediate past hour and the hour before that, to ensure you are constantly mindful of your day.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = Color.Gray.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                )

                // Rating Dial - 7 is now YELLOW (8-10 GREEN)
                Box(
                    Modifier
                        .padding(vertical = 32.dp)
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
                                color = if (selectedScore == s && s < 5) Color.White else Color.Unspecified
                            )
                        }
                    }

                    // Save Button
                    Button(
                        onClick = {
                            val entry = RatingEntry(
                                System.currentTimeMillis(),
                                selectedScore,
                                Calendar.getInstance().apply {
                                    add(Calendar.HOUR_OF_DAY, -targetedHourOffset)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis,
                                displayHourInfo.third,
                                currentNote,
                                selectedTags.toList()
                            )
                            viewModel.saveRating(context, entry)

                            // Show celebration for 8-10 (GREEN scores)
                            if (selectedScore == 10) {
                                showEpicCelebration = true
                            } else if (selectedScore >= 8) {
                                showCelebration = true
                            }

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
                        enabled = !isLoggedCurrent,
                        modifier = Modifier.size(100.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(32.dp))
                    }
                }
            }

            // Tags Section - ALWAYS EDITABLE
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

            // Notes Section - ONLY visible when there are hours to rate
            if (!bothHoursRated) {
                NotesSection(
                    noteText = currentNote,
                    onNoteChange = { currentNote = it },
                    enabled = true
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        // CELEBRATION OVERLAYS
        if (showCelebration) {
            CelebrationOverlay { showCelebration = false }
        }

        if (showEpicCelebration) {
            EpicCelebrationOverlay { showEpicCelebration = false }
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