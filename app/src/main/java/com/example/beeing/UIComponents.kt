package com.example.beeing

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
//import androidx.compose.ui.layout.onGloballyPositioned
//import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalChart(ratings: List<RatingEntry>) {
    val isDark = isSystemInDarkTheme()
    val axisTextColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

    var period by remember { mutableStateOf(StatPeriod.DAY) }
    var pageOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(period) { pageOffset = 0 }

    val buckets = remember(period, pageOffset) { buildPeriodBuckets(period, pageOffset, 7) }
    val data = remember(ratings, buckets) {
        buckets.map { b ->
            val avg = ratings.filter { it.timestamp in b.startMillis until b.endMillisExclusive }
                .map { it.score }.average()
            b.label to (if (avg.isNaN()) 0f else avg.toFloat())
        }
    }

    val textPaint = remember(axisTextColor) {
        android.graphics.Paint().apply { color = axisTextColor; textSize = 26f; isAntiAlias = true }
    }
    val goalTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GREEN; textSize = 22f; isFakeBoldText = true; isAntiAlias = true
        }
    }
    val labelPaint = remember(axisTextColor) {
        android.graphics.Paint().apply {
            color = axisTextColor; textSize = 22f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
    }

    Column(Modifier.fillMaxWidth()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = period == p,
                        onClick = { period = p },
                        shape = SegmentedButtonDefaults.itemShape(i, StatPeriod.entries.size)
                    ) { Text(p.label, fontSize = 13.sp) }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { pageOffset++ }) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Earlier")
                }
                Text(
                    "${buckets.first().label} – ${buckets.last().label}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = { if (pageOffset > 0) pageOffset-- }, enabled = pageOffset > 0) {
                    Icon(
                        Icons.Default.KeyboardArrowRight, "Later",
                        tint = if (pageOffset > 0) LocalContentColor.current else Color.Gray
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(230.dp)) {
                Canvas(
                    Modifier.fillMaxSize().padding(top = 18.dp, bottom = 34.dp, start = 26.dp, end = 8.dp)
                ) {
                    val canvasH = size.height
                    val canvasW = size.width
                    val barSpacing = canvasW / data.size
                    val barWidth = barSpacing * 0.55f

                    (0..10 step 2).forEach { i ->
                        val y = canvasH - (i / 10f) * canvasH
                        drawLine(
                            color = Color.Gray.copy(0.45f),
                            start = Offset(0f, y), end = Offset(canvasW, y),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                        drawContext.canvas.nativeCanvas.drawText(i.toString(), -34f, y + 8f, textPaint)
                    }

                    val goalY = canvasH - (7 / 10f) * canvasH
                    drawLine(
                        color = Color(0xFF4CAF50),
                        start = Offset(0f, goalY), end = Offset(canvasW, goalY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f))
                    )
                    drawContext.canvas.nativeCanvas.drawText("Goal", canvasW - 50f, goalY - 10f, goalTextPaint)

                    data.forEachIndexed { i, pair ->
                        val score = pair.second
                        val center = i * barSpacing + barSpacing / 2
                        val left = center - barWidth / 2
                        if (score > 0f) {
                            val barHeight = (score / 10f) * canvasH
                            val barColor = when {
                                score >= 8f -> Color(0xFF66BB6A)
                                score >= 5f -> Color(0xFFFFB300)
                                else -> Color(0xFFB71C1C)
                            }
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(left, canvasH - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                String.format("%.1f", score), center, canvasH - barHeight - 10f, labelPaint
                            )
                        }
                        drawContext.canvas.nativeCanvas.drawText(pair.first, center, canvasH + 34f, labelPaint)
                    }
                }
            }
        }
}

@Composable
fun InsightPanel(ratings: List<RatingEntry>, view: ChartView) {
    val periodDays = when(view) {
        ChartView.HOURLY -> 1
        ChartView.DAY -> 10
        ChartView.WEEK -> 70
        ChartView.MONTH -> 300
    }

    val relevant = ratings.filter { isWithinDays(it.timestamp, periodDays) }
    if (relevant.isEmpty()) return

    val allTags = relevant.flatMap { it.tags }.distinct()

    data class TagImpact(val tag: String, val impact: Double, val confidence: Int)
    val tagImpacts = allTags.mapNotNull { tag ->
        val withTag = relevant.filter { tag in it.tags }
        if (withTag.size < 2) return@mapNotNull null

        val avgWithTag = withTag.map { it.score }.average()
        val withoutTag = relevant.filter { entry ->
            tag !in entry.tags && entry.tags.any { it in withTag.flatMap { w -> w.tags } }
        }

        if (withoutTag.isEmpty()) {
            val overallAvg = relevant.map { it.score }.average()
            TagImpact(tag, avgWithTag - overallAvg, withTag.size)
        } else {
            val avgWithoutTag = withoutTag.map { it.score }.average()
            TagImpact(tag, avgWithTag - avgWithoutTag, withTag.size)
        }
    }

    val sortedImpacts = tagImpacts.sortedByDescending { it.impact }
    val topBoosters = sortedImpacts.filter { it.impact > 0.5 }.take(3)
    val topDraggers = sortedImpacts.filter { it.impact < -0.5 }.sortedBy { it.impact }.take(3)

    Column(Modifier.padding(horizontal = 16.dp)) {
        if (topBoosters.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(0.1f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "💖🚀 Soul Fuel Tags:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Spacer(Modifier.height(4.dp))
                    topBoosters.forEach { impact ->
                        Text(
                            "• ${impact.tag}: +${String.format("%.1f", impact.impact)} pts on average",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
            }
        }

        if (topDraggers.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(0.1f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "⚠️💔 Vibe Killer Tags:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C)
                    )
                    Spacer(Modifier.height(4.dp))
                    topDraggers.forEach { impact ->
                        Text(
                            "• ${impact.tag}: ${String.format("%.1f", impact.impact)} pts on average",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryPanel(ratings: List<RatingEntry>, onEdit: (RatingEntry) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        val recentRatings = ratings.toList().take(10)

        recentRatings.forEach { item ->
            key(item.id) {
                val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
                val startH = cal.get(Calendar.HOUR_OF_DAY)
                val endH = if (startH == 23) 0 else startH + 1

                    val displayCal = cal
                    val dateStr = SimpleDateFormat("MMM dd").format(displayCal.time)
                    val range = "${formatHour(startH)} - ${formatHour(endH)}"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(item) }
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$dateStr, $range (${item.hourLabel} hour)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (item.tags.isNotEmpty()) {
                                Text(
                                    item.tags.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    when {
                                        item.score >= 8 -> Color(0xFF66BB6A)
                                        item.score >= 5 -> Color(0xFFFFB300)
                                        else -> Color(0xFFB71C1C)
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${item.score}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = if (item.score >= 5) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
}

@Composable
fun HeaderSection(
    onImport: () -> Unit,
    onExport: () -> Unit,
    onMenuClick: () -> Unit,
    onStreakClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .padding(top = 48.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.clickable { onStreakClick() }) {
            Text(
                "Beeing",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, "How it works", tint = Color.Gray)
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, "Menu", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun EditEntrySheet(
    entry: RatingEntry,
    onUpdate: (RatingEntry) -> Unit,
    onDelete: (Long) -> Unit
) {
    var editedEntry by remember { mutableStateOf(entry) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var availableTags by remember { mutableStateOf(loadTags(context)) }
    val selectedTags = remember { mutableStateListOf<String>().apply { addAll(entry.tags) } }
    var showTagDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Edit Entry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))
            Text("Select new score:")
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                (1..10).forEach { score ->
                    val isSelected = editedEntry.score == score
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 56.dp else 48.dp)
                            .background(
                                when {
                                    score >= 8 -> Color(0xFF66BB6A).copy(alpha = if (isSelected) 1f else 0.3f)
                                    score >= 5 -> Color(0xFFFFB300).copy(alpha = if (isSelected) 1f else 0.3f)
                                    else -> Color(0xFFB71C1C).copy(alpha = if (isSelected) 1f else 0.3f)
                                },
                                shape = CircleShape
                            )
                            .clickable {
                                editedEntry = editedEntry.copy(score = score)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$score",
                            fontSize = if (isSelected) 20.sp else 16.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (score >= 5) Color.Black else Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Tags:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                availableTags.forEach { tag ->
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

                IconButton(
                    onClick = { showTagDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add tag",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        val deletedEntry = editedEntry
                        onDelete(entry.id)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Entry deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                saveRating(context, deletedEntry)
                            }
                        }
                    },
                    modifier = Modifier.weight(0.2f)
                ) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        onUpdate(editedEntry.copy(tags = selectedTags.toList()))
                    },
                    modifier = Modifier.weight(0.8f)
                ) {
                    Text("Save Changes")
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showTagDialog) {
        var newTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Add new tag") },
            text = {
                OutlinedTextField(
                    newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Tag name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTag.isNotBlank()) {
                            availableTags = (availableTags + newTag.trim()).distinct()
                            saveTags(context, availableTags)
                            selectedTags.add(newTag.trim())
                        }
                        showTagDialog = false
                    },
                    enabled = newTag.isNotBlank()
                ) {
                    Text("Add")
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SoulFuelTagsSection(
    allRatings: List<RatingEntry>,
    availableTags: List<String>,
    selectedTags: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    isTagDeleteMode: Boolean,
    isEnabled: Boolean,
    onTagDeleteModeChange: (Boolean) -> Unit,
    onTagsUpdate: (List<String>) -> Unit,
    onShowTagDialog: () -> Unit
) {
    val context = LocalContext.current

    // Centered chips that read as part of the rating dial above; a single
    // pencil toggles edit mode (delete existing tags / add new ones).
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(-8.dp, Alignment.CenterVertically)
        ) {
            if (isTagDeleteMode) {
                AssistChip(
                    onClick = onShowTagDialog,
                    enabled = isEnabled && availableTags.size < 30,
                    label = { Text("New tag") },
                    leadingIcon = {
                        Icon(Icons.Default.Add, "Add tag", Modifier.size(16.dp))
                    }
                )
                availableTags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {
                            val newTags = availableTags.filter { it != tag }
                            saveTags(context, newTags)
                            selectedTags.remove(tag)
                            onTagsUpdate(newTags)
                        },
                        label = { Text(tag) },
                        enabled = isEnabled,
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete $tag",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            } else {
                availableTags.forEach { tag ->
                    InputChip(
                        selected = tag in selectedTags,
                        onClick = {
                            if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                        },
                        label = { Text(tag) },
                        enabled = isEnabled,
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            IconButton(
                onClick = { onTagDeleteModeChange(!isTagDeleteMode) },
                enabled = isEnabled,
                modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
            ) {
                Icon(
                    if (isTagDeleteMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (isTagDeleteMode) "Done editing" else "Edit tags",
                    modifier = Modifier.size(18.dp),
                    tint = if (isTagDeleteMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NotesSection(
    noteText: String,
    onNoteChange: (String) -> Unit,
    enabled: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = noteText,
            onValueChange = onNoteChange,
            label = { Text("Add a note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
        )
    }
}

// ==============================================
// HELPER FUNCTIONS
// ==============================================

fun getChartData(ratings: List<RatingEntry>, view: ChartView, selectedDate: Calendar? = null): List<Pair<String, Float>> {
    return when(view) {
        ChartView.HOURLY -> {
            // Show all 24 hours (1st..24th) for the selected date.
            // The selected date defaults to today.
            val targetDay = selectedDate ?: Calendar.getInstance()
            (0..23).map { hourIndex ->
                // hourIndex 0 = 1st hour (12AM-1AM), startH=0, endH=1
                val startH = hourIndex
                val endH = if (startH == 23) 0 else startH + 1
                val label = "${formatHour(startH)}-${formatHour(endH)}"

                // Find a rating whose timestamp falls on this hour of the target day
                val match = ratings.find {
                    val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    c.get(Calendar.HOUR_OF_DAY) == startH &&
                            c.get(Calendar.DAY_OF_YEAR) == targetDay.get(Calendar.DAY_OF_YEAR) &&
                            c.get(Calendar.YEAR) == targetDay.get(Calendar.YEAR)
                }
                label to (match?.score?.toFloat() ?: 0f)
            }
        }
        ChartView.DAY -> {
            (0..9).map { i ->
                val target = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(9 - i)) }
                val avg = ratings.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    isSameDay(cal, target)
                }.map { it.score }.average()
                SimpleDateFormat("d-MMM", Locale.getDefault()).format(target.time) to (if(avg.isNaN()) 0f else avg.toFloat())
            }
        }
        ChartView.WEEK -> {
            (0..9).map { i ->
                val target = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -(9 - i)) }
                val week = target.get(Calendar.WEEK_OF_YEAR)
                val year = target.get(Calendar.YEAR)
                val avg = ratings.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    cal.get(Calendar.WEEK_OF_YEAR) == week && cal.get(Calendar.YEAR) == year
                }.map { it.score }.average()
                "W$week" to (if(avg.isNaN()) 0f else avg.toFloat())
            }
        }
        ChartView.MONTH -> {
            (0..9).map { i ->
                val target = Calendar.getInstance().apply { add(Calendar.MONTH, -(9 - i)) }
                val targetMonth = target.get(Calendar.MONTH)
                val targetYear = target.get(Calendar.YEAR)
                val avg = ratings.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear
                }.map { it.score }.average()
                SimpleDateFormat("MMM").format(target.time) to (if(avg.isNaN()) 0f else avg.toFloat())
            }
        }
    }
}

fun isSameDay(c1: Calendar, c2: Calendar) =
    c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
            c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)

fun isWithinDays(ts: Long, days: Int): Boolean {
    val limit = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
    return ts >= limit
}

fun formatHour(h: Int) = "${if(h%12==0) 12 else h%12}${if(h<12)"am" else "pm"}"

fun getOrdinalSuffix(n: Int) = when {
    n in 11..13 -> "th"
    n%10==1 -> "st"
    n%10==2 -> "nd"
    n%10==3 -> "rd"
    else -> "th"
}

fun getPreviousHourLabel(): String {
    val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -1) }
    val endH = cal.get(Calendar.HOUR_OF_DAY)
    return "${if (endH == 0) 24 else endH}${getOrdinalSuffix(if (endH == 0) 24 else endH)}"
}