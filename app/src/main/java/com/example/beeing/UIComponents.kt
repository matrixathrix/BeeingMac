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
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun ProfessionalChart(ratings: List<RatingEntry>, view: ChartView, onToggle: (ChartView) -> Unit) {
    val isDark = isSystemInDarkTheme()
    val axisTextColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val data = remember(ratings, view) { getChartData(ratings, view) }

    val textPaint = remember(axisTextColor) {
        android.graphics.Paint().apply {
            color = axisTextColor
            textSize = 26f
            isAntiAlias = true
        }
    }

    val goalTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GREEN
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    val labelPaint = remember(axisTextColor) {
        android.graphics.Paint().apply {
            color = axisTextColor
            textSize = 24f
            textAlign = android.graphics.Paint.Align.LEFT
            isAntiAlias = true
        }
    }

    Card(Modifier.padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Trend Matrix", fontWeight = FontWeight.Bold)
                Row { ChartView.entries.forEach { v -> TextButton(onClick = { onToggle(v) }) { Text(v.name, color = if(view==v) MaterialTheme.colorScheme.primary else Color.Gray, fontSize = 11.sp) } } }
            }

            val scrollState = rememberScrollState()
            val density = LocalDensity.current

            LaunchedEffect(data, view) {
                val lastEntryIndex = data.indexOfLast { it.second > 0f }
                if (lastEntryIndex != -1) {
                    val chartWidthDp = if(view == ChartView.HOURLY) 1000.dp else 550.dp
                    val totalWidthPx = with(density) { chartWidthDp.toPx() }
                    val offsetPx = (lastEntryIndex.toFloat() / data.size) * totalWidthPx
                    scrollState.animateScrollTo(offsetPx.toInt())
                } else {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .horizontalScroll(scrollState)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(if(view == ChartView.HOURLY) 1000.dp else 550.dp)
                        .fillMaxHeight()
                        .padding(top = 40.dp, bottom = 70.dp, start = 50.dp, end = 50.dp)
                ) {
                    val canvasH = size.height
                    val canvasW = size.width
                    val barSpacing = canvasW / data.size
                    val barWidth = barSpacing * 0.8f

                    (0..10 step 2).forEach { i ->
                        val y = canvasH - (i / 10f) * canvasH
                        drawLine(
                            color = Color.Gray.copy(0.45f),
                            start = Offset(0f, y),
                            end = Offset(canvasW, y),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            i.toString(),
                            -40f,
                            y + 8f,
                            textPaint
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            i.toString(),
                            canvasW + 15f,
                            y + 8f,
                            textPaint
                        )
                    }

                    val goalY = canvasH - (7 / 10f) * canvasH
                    drawLine(
                        color = Color(0xFF4CAF50),
                        start = Offset(0f, goalY),
                        end = Offset(canvasW, goalY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f))
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "Goal",
                        canvasW - 60f,
                        goalY - 10f,
                        goalTextPaint
                    )

                    data.forEachIndexed { i, pair ->
                        val score = pair.second
                        val leftPos = i * barSpacing + (barSpacing * 0.1f)

                        if (score > 0f) {
                            val barHeight = (score / 10f) * canvasH
                            val barColor = when {
                                score >= 8f -> Color(0xFF66BB6A)
                                score >= 5f -> Color(0xFFFFB300)
                                else -> Color(0xFFB71C1C)
                            }
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(leftPos, canvasH - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                        }

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.rotate(90f, leftPos + (barWidth / 2), canvasH + 20f)
                        drawContext.canvas.nativeCanvas.drawText(
                            pair.first,
                            leftPos + (barWidth / 2),
                            canvasH + 20f,
                            labelPaint
                        )
                        drawContext.canvas.nativeCanvas.restore()
                    }
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
    Card(
        Modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Recent History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

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
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$dateStr, $range (${item.hourLabel} hour)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (item.tags.isNotEmpty()) {
                                Text(
                                    item.tags.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
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
                                fontSize = 20.sp,
                                color = if (item.score >= 5) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    streak: Int,
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
            Text(
                "🔥 $streak hour streak",
                color = MaterialTheme.colorScheme.tertiary
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
fun CelebrationOverlay(onDismiss: () -> Unit)
{
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 150, 100, 200, 100, 150, 100)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 150, 100, 200, 100, 150, 100), -1)
        }

        delay(3000)
        onDismiss()
    }

    data class ConfettiEmoji(val emoji: String, val angle: Float, val speed: Float, val delay: Long)
    val confettiEmojis = remember {
        listOf(
            "🎊", "⭐", "🚀", "✨", "🎉", "⭐", "🚀", "💫", "🎊", "⭐",
            "🚀", "✨", "🎉", "⭐", "🚀", "💫", "🎊", "⭐", "🚀", "✨"
        ).mapIndexed { index, emoji ->
            ConfettiEmoji(
                emoji = emoji,
                angle = (index * 18f) - 90f,
                speed = (200..300).random().toFloat(),
                delay = (0..300).random().toLong()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        confettiEmojis.forEach { confetti ->
            val offsetX = remember { Animatable(0f) }
            val offsetY = remember { Animatable(0f) }
            val alpha = remember { Animatable(1f) }
            val rotation = remember { Animatable(0f) }

            LaunchedEffect(Unit) {
                delay(confetti.delay)
                launch {
                    val angleRad = Math.toRadians(confetti.angle.toDouble())
                    val targetX = (cos(angleRad) * confetti.speed).toFloat()
                    offsetX.animateTo(
                        targetValue = targetX,
                        animationSpec = tween(1500, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    val angleRad = Math.toRadians(confetti.angle.toDouble())
                    val targetY = (sin(angleRad) * confetti.speed).toFloat()
                    offsetY.animateTo(
                        targetValue = targetY,
                        animationSpec = tween(1500, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    delay(1000)
                    alpha.animateTo(0f, animationSpec = tween(500))
                }
                launch {
                    rotation.animateTo(
                        360f,
                        animationSpec = tween(1500, easing = LinearEasing)
                    )
                }
            }

            Text(
                text = confetti.emoji,
                fontSize = 32.sp,
                modifier = Modifier
                    .offset(offsetX.value.dp, offsetY.value.dp)
                    .alpha(alpha.value)
                    .rotate(rotation.value)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "🚀",
                fontSize = 100.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Way to go!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Keep living in the now!",
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
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
fun EpicCelebrationOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 200, 150, 250, 150, 300, 150, 200, 100)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 200, 150, 250, 150, 300, 150, 200, 100), -1)
        }
        delay(4000)
        onDismiss()
    }

    data class ConfettiEmoji(
        val emoji: String,
        val angle: Float,
        val speed: Float,
        val delay: Long,
        val size: Float
    )

    val confettiEmojis = remember {
        val emojis = listOf(
            "🌟", "✨", "💫", "⭐", "🎆", "🎇", "🎉", "🎊", "🏆", "👑",
            "🌟", "✨", "💫", "⭐", "🎆", "🎇", "🎉", "🎊", "🏆", "👑",
            "🌟", "✨", "💫", "⭐", "🎆", "🎇", "🎉", "🎊", "🏆", "👑",
            "💎", "💰", "🔥", "⚡", "🌈", "🦄", "🚀", "🌠", "💥", "✨"
        )
        emojis.mapIndexed { index, emoji ->
            ConfettiEmoji(
                emoji = emoji,
                angle = (index * 9f) - 90f,
                speed = (250..400).random().toFloat(),
                delay = (0..400).random().toLong(),
                size = (28..40).random().toFloat()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        confettiEmojis.forEach { confetti ->
            val offsetX = remember { Animatable(0f) }
            val offsetY = remember { Animatable(0f) }
            val alpha = remember { Animatable(1f) }
            val rotation = remember { Animatable(0f) }
            val scale = remember { Animatable(0.5f) }

            LaunchedEffect(Unit) {
                delay(confetti.delay)
                launch {
                    scale.animateTo(
                        1.2f,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    val angleRad = Math.toRadians(confetti.angle.toDouble())
                    val targetX = (cos(angleRad) * confetti.speed).toFloat()
                    offsetX.animateTo(
                        targetValue = targetX,
                        animationSpec = tween(2000, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    val angleRad = Math.toRadians(confetti.angle.toDouble())
                    val targetY = (sin(angleRad) * confetti.speed).toFloat()
                    offsetY.animateTo(
                        targetValue = targetY,
                        animationSpec = tween(2000, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    delay(1500)
                    alpha.animateTo(0f, animationSpec = tween(500))
                }
                launch {
                    rotation.animateTo(
                        720f,
                        animationSpec = tween(2000, easing = LinearEasing)
                    )
                }
            }

            Text(
                text = confetti.emoji,
                fontSize = confetti.size.sp,
                modifier = Modifier
                    .offset(offsetX.value.dp, offsetY.value.dp)
                    .alpha(alpha.value)
                    .rotate(rotation.value)
                    .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "👑",
                fontSize = 120.sp
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "PERFECT!",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFD700)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "You just lived a very special moment of your life, congratulations!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
        }
    }
}

@Composable
fun RatingCard(
    hourOffset: Int,
    allRatings: List<RatingEntry>,
    onSave: (score: Int, hourLabel: String, tags: List<String>, note: String) -> Unit,
    isTargeted: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var selectedScore by remember { mutableIntStateOf(1) }
    val selectedTags = remember { mutableStateListOf<String>() }
    var currentNote by remember { mutableStateOf("") }
    var availableTags by remember { mutableStateOf(loadTags(context)) }
    var isTagDeleteMode by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }

    val displayHourInfo = remember(hourOffset) {
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -hourOffset) }
        val startH = cal.get(Calendar.HOUR_OF_DAY)

        // FIX: Range is Start -> Start+1 (e.g. 5:00 is 5-6)
        val endH = if (startH == 23) 0 else startH + 1

        val range = "${formatHour(startH)} - ${formatHour(endH)}"
        val label = "${if (endH == 0) 24 else endH}${getOrdinalSuffix(if (endH == 0) 24 else endH)}"
        Triple(range, endH, label)
    }

    val isLogged = remember(allRatings, hourOffset) {
        val targetDayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
            Date(System.currentTimeMillis() - (hourOffset * 3600000L))
        )
        allRatings.any {
            it.hourLabel == displayHourInfo.third &&
                    SimpleDateFormat(
                        "yyyyMMdd",
                        Locale.getDefault()
                    ).format(Date(it.timestamp)) == targetDayKey
        }
    }

    val pulseAlpha = remember { Animatable(0.3f) }
    LaunchedEffect(isLogged) {
        if (!isLogged) {
            repeat(3) {
                pulseAlpha.animateTo(0.8f, animationSpec = tween(300))
                pulseAlpha.animateTo(0.3f, animationSpec = tween(300))
            }
        } else {
            pulseAlpha.snapTo(0.15f)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = if (!isLogged) BorderStroke(
                2.dp,
                if (isSystemInDarkTheme()) Color.White else Color(0xFF424242)
            ) else null,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha.value)
            )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val labelText = when {
                        isLogged -> "Already rated:"
                        hourOffset == 0 -> "How was your:"
                        else -> "You also missed rating:"
                    }
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isLogged) Color.Gray
                        else if (hourOffset != 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${displayHourInfo.first} (${displayHourInfo.third} hour)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

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
                    enabled = !isLogged,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selectedScore == s) {
                            when {
                                s >= 8 -> Color(0xFF66BB6A)
                                s >= 5 -> Color(0xFFFFB300)
                                else -> Color(0xFFB71C1C)
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

            Button(
                onClick = {
                    onSave(selectedScore, displayHourInfo.third, selectedTags.toList(), currentNote)
                    selectedScore = 1
                    selectedTags.clear()
                    currentNote = ""
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                enabled = !isLogged,
                modifier = Modifier.size(100.dp),
                shape = CircleShape
            ) {
                Text(
                    if (isLogged) "Rated" else "Rate",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLogged) Color.Gray else Color.White
                )
            }
        }

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

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tags",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(-8.dp, Alignment.CenterVertically)
        ) {
            if (isTagDeleteMode) {
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

            if (availableTags.isNotEmpty()) {
                IconButton(
                    onClick = { onTagDeleteModeChange(!isTagDeleteMode) },
                    enabled = isEnabled,
                    modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
                ) {
                    Icon(
                        if (isTagDeleteMode) Icons.Default.Check else Icons.Default.Delete,
                        contentDescription = if (isTagDeleteMode) "Done deleting" else "Delete tags",
                        modifier = Modifier.size(18.dp),
                        tint = if (isTagDeleteMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onShowTagDialog,
                enabled = isEnabled && availableTags.size < 30,
                modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add tag",
                    modifier = Modifier.size(18.dp),
                    tint = if (availableTags.size >= 30)
                        Color.Gray.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.primary
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
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}

// ==============================================
// HELPER FUNCTIONS
// ==============================================

fun getChartData(ratings: List<RatingEntry>, view: ChartView): List<Pair<String, Float>> {
    return when(view) {
        ChartView.HOURLY -> {
            // FIX: Chart range shifted by -1 to exclude the current incomplete hour
            // Map 0..23 to offsets 24 downTo 1.
            (0..23).map { i ->
                val offset = 24 - i
                val target = Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, -offset)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val hr = target.get(Calendar.HOUR_OF_DAY)
                val day = target.get(Calendar.DAY_OF_YEAR)

                val match = ratings.find {
                    val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    c.get(Calendar.HOUR_OF_DAY) == hr && c.get(Calendar.DAY_OF_YEAR) == day
                }

                // FIX: Label range is Hour -> Hour+1
                val endHour = if (hr == 23) 0 else hr + 1
                val label = "${formatHour(hr)}-${formatHour(endHour)}"
                label to (match?.score?.toFloat() ?: 0f)
            }
        }
        ChartView.DAY -> {
            (0..9).map { i ->
                val target = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(9 - i)) }
                val avg = ratings.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)

                    val dayToCheck = if (hour == 0) {
                        Calendar.getInstance().apply {
                            timeInMillis = it.timestamp
                            add(Calendar.DAY_OF_YEAR, -1)
                        }
                    } else {
                        cal
                    }

                    isSameDay(dayToCheck, target)
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
                    val hour = cal.get(Calendar.HOUR_OF_DAY)

                    val weekToCheck = if (hour == 0) {
                        Calendar.getInstance().apply {
                            timeInMillis = it.timestamp
                            add(Calendar.DAY_OF_YEAR, -1)
                        }
                    } else {
                        cal
                    }

                    weekToCheck.get(Calendar.WEEK_OF_YEAR) == week && weekToCheck.get(Calendar.YEAR) == year
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
                    val hour = cal.get(Calendar.HOUR_OF_DAY)

                    val monthToCheck = if (hour == 0) {
                        Calendar.getInstance().apply {
                            timeInMillis = it.timestamp
                            add(Calendar.DAY_OF_YEAR, -1)
                        }
                    } else {
                        cal
                    }

                    monthToCheck.get(Calendar.MONTH) == targetMonth && monthToCheck.get(Calendar.YEAR) == targetYear
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