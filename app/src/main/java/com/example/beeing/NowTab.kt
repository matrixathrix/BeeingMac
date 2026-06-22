package com.example.beeing

import android.app.NotificationManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
    var showStreakInfo by remember { mutableStateOf(false) }

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
    val streakState = remember(allRatings, viewModel.refreshTrigger) { computeStreakState(allRatings) }

    // Dismiss notification when both hours are rated
    LaunchedEffect(bothHoursRated) {
        if (bothHoursRated) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1) // Cancel the hourly notification (ID = 1)
        }
    }

    // Yesterday's & today's average scores, and today's single best hour
    val yesterdayAvg = remember(allRatings, viewModel.refreshTrigger) {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val ratings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
        }
        if (ratings.isEmpty()) 0.0 else ratings.map { it.score }.average()
    }

    val todayRatings = remember(allRatings, viewModel.refreshTrigger) {
        val today = Calendar.getInstance()
        allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        }
    }
    val todayAvg = remember(todayRatings) {
        if (todayRatings.isEmpty()) 0.0 else todayRatings.map { it.score }.average()
    }
    val todayBest = remember(todayRatings) {
        todayRatings.maxWithOrNull(compareBy<RatingEntry> { it.score }.thenBy { it.timestamp })
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
            // Prominent streak meter (core feature)
            StreakMeter(
                state = streakState,
                modifier = Modifier.padding(bottom = 16.dp),
                onClick = { showStreakInfo = true }
            )

            // Cohesive "Today at a glance": yesterday vs today averages + today's best hour
            if (todayRatings.isNotEmpty() || yesterdayAvg > 0) {
                TodayGlanceCard(
                    yesterdayAvg = yesterdayAvg,
                    todayAvg = todayAvg,
                    bestHour = todayBest,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
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
                        .then(
                            if (!isLoggedCurrent) Modifier.clickable {
                                onTargetedHourOffsetChange(if (targetedHourOffset == 0) 1 else 0)
                            } else Modifier
                        )
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
                        if (!isLoggedCurrent) {
                            Icon(
                                if (targetedHourOffset == 0) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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

            Spacer(Modifier.height(16.dp))

            // Goals section (set per-tag target averages)
            GoalsSection(
                context = context,
                ratings = allRatings,
                refreshKey = viewModel.refreshTrigger
            )

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

        // STREAK RULES DIALOG (tap the meter)
        if (showStreakInfo) {
            AlertDialog(
                onDismissRequest = { showStreakInfo = false },
                title = { Text("🔥 How streaks work") },
                text = {
                    Column {
                        Text("• Rate at least 8 hours in a day to keep your streak going.", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("• Every extra hour beyond 8 grows 🌸 Flowers. Collect 10 Flowers for 1 Streak Saver (hold up to 3).", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("• Miss a day? A saver is spent automatically to keep your streak alive. No savers left means the streak resets.", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("• The ring fills as you log today's first 8 hours. The shields show your savers.", fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "See every streak event in the Streak Log on the Past tab.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStreakInfo = false }) { Text("Got it!") }
                }
            )
        }
    }
}

/**
 * Cohesive "Today at a glance" card: yesterday vs today averages side by side,
 * with today's single best hour highlighted beneath.
 */
@Composable
fun TodayGlanceCard(
    yesterdayAvg: Double,
    todayAvg: Double,
    bestHour: RatingEntry?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvgStat("Yesterday", yesterdayAvg, Modifier.weight(1f))
                Box(
                    Modifier
                        .height(44.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                )
                AvgStat("Today", todayAvg, Modifier.weight(1f))
            }

            if (bestHour != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                val cal = Calendar.getInstance().apply { timeInMillis = bestHour.timestamp }
                val startH = cal.get(Calendar.HOUR_OF_DAY)
                val endH = if (startH == 23) 0 else startH + 1
                Row(verticalAlignment = Alignment.CenterVertically) {
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
    }
}

@Composable
private fun AvgStat(label: String, avg: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (avg > 0) String.format("%.1f", avg) else "–",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = getScoreColor(avg)
        )
    }
}