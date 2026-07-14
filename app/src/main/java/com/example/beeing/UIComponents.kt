package com.example.beeing

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The app's one standard translucent card background. Light mode needs a
 * stronger tint than dark mode to read as a distinct card — surfaceVariant
 * at the same alpha nearly disappears into a light page background but
 * already stands out fine against a dark one.
 */
@Composable
fun appCardColor(baseAlpha: Float = 0.4f): Color {
    val alpha = if (isSystemInDarkTheme()) baseAlpha else (baseAlpha + 0.25f).coerceAtMost(1f)
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
}

/**
 * Drives a [SwipeScrubStack]: a live drag offset that either a finger-drag
 * or a prev/next button can move, so both feel like the same one motion
 * system instead of a canned slide bolted on top of real dragging.
 */
@Stable
class SwipeScrubController internal constructor(
    internal val offset: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope,
    private val onEarlier: () -> Unit,
    private val onLater: () -> Unit
) {
    internal var widthPx: Float = 0f

    internal fun dragTo(value: Float) {
        scope.launch { offset.snapTo(value) }
    }

    internal fun release(canEarlier: Boolean, canLater: Boolean) {
        // A short, deliberate flick should be enough — this doesn't need to
        // be a half-screen drag to register.
        val threshold = widthPx * 0.16f
        when {
            offset.value >= threshold && canEarlier -> commit(forward = true)
            offset.value <= -threshold && canLater -> commit(forward = false)
            else -> springBack()
        }
    }

    /** Plays the same commit motion a completed swipe would, for a button tap. */
    fun stepEarlier() = commit(forward = true)
    fun stepLater() = commit(forward = false)

    private fun commit(forward: Boolean) {
        scope.launch {
            val w = widthPx
            if (w > 0f) offset.animateTo(if (forward) w else -w, tween(220, easing = FastOutSlowInEasing))
            if (forward) onEarlier() else onLater()
            offset.snapTo(0f)
        }
    }

    private fun springBack() {
        // Rubber-band release: eases back to rest in one smooth motion —
        // deliberately NOT a bouncy spring, which oscillated back and forth.
        scope.launch {
            offset.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        }
    }
}

@Composable
fun rememberSwipeScrubController(onEarlier: () -> Unit, onLater: () -> Unit): SwipeScrubController {
    val scope = rememberCoroutineScope()
    val earlierState = rememberUpdatedState(onEarlier)
    val laterState = rememberUpdatedState(onLater)
    return remember {
        SwipeScrubController(
            offset = Animatable(0f),
            scope = scope,
            onEarlier = { earlierState.value() },
            onLater = { laterState.value() }
        )
    }
}

/**
 * Standard iOS-style rubber-band resistance: [overshoot] is the raw drag
 * distance past a boundary that can't actually page any further (oldest or
 * newest data) — returns a damped, asymptotically-capped visual distance so
 * the drag still gives a little, instead of feeling like a hard wall.
 */
private fun rubberBand(overshoot: Float, dimension: Float): Float {
    if (dimension <= 0f) return 0f
    val c = 0.55f
    return (overshoot * dimension * c) / (dimension + c * abs(overshoot))
}

/**
 * True finger-tracking swipe for a period/page view: the current content and
 * whichever neighbor is being dragged toward both move with the finger in
 * real time (not a canned slide played only after release), snapping to a
 * full page change past the halfway point or springing back otherwise.
 * Swipe right (revealing content from the left) = Earlier, swipe left = Later
 * — same convention as the prev/next buttons, which drive the identical
 * motion via [SwipeScrubController.stepEarlier]/[stepLater].
 */
@Composable
fun SwipeScrubStack(
    controller: SwipeScrubController,
    canEarlier: Boolean,
    canLater: Boolean,
    modifier: Modifier = Modifier,
    current: @Composable () -> Unit,
    earlierPreview: @Composable () -> Unit,
    laterPreview: @Composable () -> Unit
) {
    val offset = controller.offset
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { controller.widthPx = it.width.toFloat() }
            .pointerInput(controller, canEarlier, canLater) {
                // Tracks the finger's raw, unclamped travel since this drag
                // began — the visual offset is then derived from it, so
                // resistance at a boundary is a function of true drag
                // distance rather than compounding off an already-damped value.
                var rawDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { rawDrag = 0f },
                    onDragEnd = { controller.release(canEarlier, canLater) },
                    onDragCancel = { controller.release(canEarlier = false, canLater = false) },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        rawDrag += dragAmount
                        val visual = when {
                            rawDrag > 0f && !canEarlier -> rubberBand(rawDrag, controller.widthPx)
                            rawDrag < 0f && !canLater -> rubberBand(rawDrag, controller.widthPx)
                            else -> rawDrag.coerceIn(-controller.widthPx, controller.widthPx)
                        }
                        controller.dragTo(visual)
                    }
                )
            }
    ) {
        Box(Modifier.offset { IntOffset((offset.value - controller.widthPx).roundToInt(), 0) }) {
            earlierPreview()
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) {
            current()
        }
        Box(Modifier.offset { IntOffset((offset.value + controller.widthPx).roundToInt(), 0) }) {
            laterPreview()
        }
    }
}

/**
 * Scrubber center label that slides sideways with the live drag offset —
 * clipped to its own box so it never crosses the chevrons flanking it.
 */
@Composable
fun ScrubLabel(
    controller: SwipeScrubController,
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    Box(modifier.clipToBounds()) {
        Text(
            text,
            Modifier.graphicsLayer { translationX = controller.offset.value * 0.35f },
            fontWeight = fontWeight,
            fontSize = fontSize,
            maxLines = 1
        )
    }
}

/**
 * A floating pointer-tip: a small bubble with a caret, hovering just above
 * whatever composable it's placed inside. Auto-dismisses after a moment.
 */
@Composable
fun TapTip(
    text: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                    .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
                val y = (anchorBounds.top - popupContentSize.height - 10).coerceAtLeast(8)
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = positionProvider, onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bubbleColor,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp
            ) {
                Text(
                    text,
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            Canvas(Modifier.size(16.dp, 7.dp)) {
                val caret = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(caret, bubbleColor)
            }
        }
    }
    LaunchedEffect(text) {
        delay(2600)
        onDismiss()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalChart(ratings: List<RatingEntry>) {
    val isDark = isSystemInDarkTheme()
    val axisTextColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

    var period by remember { mutableStateOf(StatPeriod.DAY) }
    var pageOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(period) { pageOffset = 0 }

    val buckets = remember(period, pageOffset) { buildPeriodBuckets(period, pageOffset, 7) }
    // Nothing before the earliest rating — don't let "Earlier" scroll into empty history
    val earliestRatingTs = remember(ratings) { ratings.minOfOrNull { it.timestamp } }
    val canGoEarlier = earliestRatingTs != null && buckets.first().startMillis > earliestRatingTs

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

    val controller = rememberSwipeScrubController(
        onEarlier = { if (canGoEarlier) pageOffset++ },
        onLater = { if (pageOffset > 0) pageOffset-- }
    )

    Column(Modifier.fillMaxWidth()) {
            // Prev/range/next scrubber + period picker share one line
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.stepEarlier() }, enabled = canGoEarlier) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft, "Earlier",
                        tint = if (canGoEarlier) LocalContentColor.current else Color.Gray
                    )
                }
                ScrubLabel(
                    controller,
                    "${buckets.first().label} – ${buckets.last().label}",
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = { controller.stepLater() }, enabled = pageOffset > 0) {
                    Icon(
                        Icons.Default.KeyboardArrowRight, "Later",
                        tint = if (pageOffset > 0) LocalContentColor.current else Color.Gray
                    )
                }
                Spacer(Modifier.weight(1f))
                PeriodDropdown(selected = period, onSelect = { period = it })
            }

            // Switching granularity still crossfades; scrubbing through pageOffset
            // (either by button or by drag) is handled by the stack below, which
            // tracks the finger live instead of only animating after release.
            Crossfade(targetState = period, animationSpec = tween(200), label = "chartPeriodFade") { animPeriod ->
                SwipeScrubStack(
                    controller = controller,
                    canEarlier = canGoEarlier,
                    canLater = pageOffset > 0,
                    current = {
                        ChartCanvas(buildPeriodBuckets(animPeriod, pageOffset, 7), ratings, textPaint, goalTextPaint, labelPaint)
                    },
                    earlierPreview = {
                        if (canGoEarlier) {
                            ChartCanvas(buildPeriodBuckets(animPeriod, pageOffset + 1, 7), ratings, textPaint, goalTextPaint, labelPaint)
                        } else {
                            Box(Modifier.fillMaxWidth().height(230.dp))
                        }
                    },
                    laterPreview = {
                        if (pageOffset > 0) {
                            ChartCanvas(buildPeriodBuckets(animPeriod, pageOffset - 1, 7), ratings, textPaint, goalTextPaint, labelPaint)
                        } else {
                            Box(Modifier.fillMaxWidth().height(230.dp))
                        }
                    }
                )
            }
        }
}

@Composable
private fun ChartCanvas(
    buckets: List<PeriodBucket>,
    ratings: List<RatingEntry>,
    textPaint: android.graphics.Paint,
    goalTextPaint: android.graphics.Paint,
    labelPaint: android.graphics.Paint
) {
    val data = remember(ratings, buckets) {
        buckets.map { b ->
            val avg = ratings.filter { it.timestamp in b.startMillis until b.endMillisExclusive }
                .map { it.score }.average()
            b.label to (if (avg.isNaN()) 0f else avg.toFloat())
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
                    drawRoundRect(
                        color = getScoreColor(score.toDouble()),
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

/** Compact rounded period picker (Day/Week/Month/Year) — replaces the old
 * full-width segmented row so it can share a line with the prev/next scrubber. */
@Composable
fun PeriodDropdown(selected: StatPeriod, onSelect: (StatPeriod) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Change period",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StatPeriod.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label) },
                    onClick = {
                        onSelect(p)
                        expanded = false
                    }
                )
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

/**
 * One rated hour, Recent-History style: time range + tags + note on the left,
 * score dot on the right. Shared by the Recent History list and the
 * calendar's tapped-day breakdown so a day always reads the same everywhere.
 */
@Composable
fun RatedHourRow(
    item: RatingEntry,
    showDate: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
    val startH = cal.get(Calendar.HOUR_OF_DAY)
    val endH = if (startH == 23) 0 else startH + 1
    val range = "${formatHour(startH)} - ${formatHour(endH)}"
    // Derived fresh from the timestamp — never trust the stored hourLabel
    // string, which can be stale on entries saved before this was fixed.
    val title = if (showDate)
        "${SimpleDateFormat("MMM dd").format(cal.time)}, $range (${ordinalHourLabel(startH)} hour)"
    else "$range (${ordinalHourLabel(startH)} hour)"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (item.tags.isNotEmpty()) {
                Text(
                    item.tags.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (item.note.isNotBlank()) {
                Text(
                    "“${item.note}”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(scoreBandColor(item.score), shape = CircleShape),
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

@Composable
fun HistoryPanel(ratings: List<RatingEntry>, onEdit: (RatingEntry) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        val recentRatings = ratings.toList().take(10)

        recentRatings.forEachIndexed { index, item ->
            key(item.id) {
                RatedHourRow(item, showDate = true, onClick = { onEdit(item) })
                if (index < recentRatings.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
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
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 8.dp),
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
    val context = LocalContext.current

    var availableTags by remember { mutableStateOf(loadTags(context)) }
    val selectedTags = remember { mutableStateListOf<String>().apply { addAll(entry.tags) } }
    var showTagDialog by remember { mutableStateOf(false) }

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

        // Auto-centers the selected score in the viewport, clamped so "1"
        // and "10" never overshoot past their natural start/end edges — each
        // score gets a fixed-width slot so the scroll math stays exact
        // regardless of the selected circle growing larger.
        val scoreScrollState = rememberScrollState()
        val density = LocalDensity.current
        var scoreViewportWidthPx by remember { mutableIntStateOf(0) }
        val scoreSlotWidth = 64.dp
        val scoreSlotWidthPx = with(density) { scoreSlotWidth.toPx() }

        LaunchedEffect(editedEntry.score, scoreViewportWidthPx) {
            if (scoreViewportWidthPx == 0) return@LaunchedEffect
            val index = editedEntry.score - 1
            val totalContentPx = scoreSlotWidthPx * 10
            val target = (index * scoreSlotWidthPx + scoreSlotWidthPx / 2f - scoreViewportWidthPx / 2f)
                .coerceIn(0f, (totalContentPx - scoreViewportWidthPx).coerceAtLeast(0f))
            scoreScrollState.animateScrollTo(target.toInt())
        }

        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { scoreViewportWidthPx = it.width }
                .horizontalScroll(scoreScrollState)
        ) {
            (1..10).forEach { score ->
                val isSelected = editedEntry.score == score
                Box(
                    modifier = Modifier.width(scoreSlotWidth),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 56.dp else 48.dp)
                            .background(
                                scoreBandColor(score).copy(alpha = if (isSelected) 1f else 0.3f),
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
                onClick = { onDelete(entry.id) },
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
                // After the existing tags, right next to the done-editing
                // tick — adding sits at the end of the flow, not before it.
                AssistChip(
                    onClick = onShowTagDialog,
                    enabled = isEnabled && availableTags.size < 30,
                    label = { Text("New tag") },
                    leadingIcon = {
                        Icon(Icons.Default.Add, "Add tag", Modifier.size(16.dp))
                    }
                )
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