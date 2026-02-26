package com.example.beeing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.*

/**
 * PAST TAB - WITH UNDO FEATURE
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastTab(
    viewModel: RatingsViewModel,
    scrollState: ScrollState,
    chartYPosition: Float,
    onChartYPosition: (Float) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load ratings from ViewModel
    val allRatings = viewModel.allRatings

    // Chart view state
    var currentChartView by remember { mutableStateOf(ChartView.HOURLY) }

    // Edit state
    var editingEntry by remember { mutableStateOf<RatingEntry?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Calculate time period averages
    val scoreStats = remember(allRatings, viewModel.refreshTrigger) {
        val now = Calendar.getInstance()

        // Helper to filter safely
        fun getAvgFor(field: Int, amount: Int): Double {
            val target = Calendar.getInstance().apply { add(field, amount) }
            val relevant = allRatings.filter { entry ->
                val c = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                when (field) {
                    Calendar.DAY_OF_YEAR -> isSameDay(c, target)
                    Calendar.MONTH -> c.get(Calendar.MONTH) == target.get(Calendar.MONTH) && c.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                    Calendar.YEAR -> c.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                    else -> false
                }
            }
            return if (relevant.isEmpty()) 0.0 else relevant.map { it.score }.average()
        }

        mapOf(
            "Yesterday" to getAvgFor(Calendar.DAY_OF_YEAR, -1),
            "Today" to getAvgFor(Calendar.DAY_OF_YEAR, 0),
            "Last Month" to getAvgFor(Calendar.MONTH, -1),
            "Current Month" to getAvgFor(Calendar.MONTH, 0),
            "Last Year" to getAvgFor(Calendar.YEAR, -1),
            "Current Year" to getAvgFor(Calendar.YEAR, 0)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                // EXPANDED SCORE COMPARISON TABLE
                if (allRatings.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Score Comparison",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Text("", modifier = Modifier.weight(1f))
                                Text("Previous", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Current", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            ScoreComparisonRow("Day", scoreStats["Yesterday"] ?: 0.0, scoreStats["Today"] ?: 0.0)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            ScoreComparisonRow("Month", scoreStats["Last Month"] ?: 0.0, scoreStats["Current Month"] ?: 0.0)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            ScoreComparisonRow("Year", scoreStats["Last Year"] ?: 0.0, scoreStats["Current Year"] ?: 0.0)
                        }
                    }
                }

                // Chart and Insights Section
                if (allRatings.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                onChartYPosition(it.positionInParent().y)
                            }
                    ) {
                        ProfessionalChart(
                            ratings = allRatings,
                            view = currentChartView,
                            onToggle = { currentChartView = it }
                        )

                        InsightPanel(
                            ratings = allRatings,
                            view = currentChartView
                        )
                    }
                }

                // History Panel with UNDO Logic
                HistoryPanel(
                    ratings = allRatings,
                    onEdit = { editingEntry = it }
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Edit Bottom Sheet
    if (editingEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { editingEntry = null },
            sheetState = sheetState
        ) {
            EditEntrySheet(
                entry = editingEntry!!,
                onUpdate = { updated ->
                    viewModel.saveRating(context, updated)
                    editingEntry = null
                },
                onDelete = { id ->
                    // 1. Capture the entry to delete for potential Undo
                    val entryToDelete = allRatings.find { it.id == id }

                    // 2. Perform deletion
                    viewModel.deleteRating(context, id)
                    editingEntry = null // Close sheet immediately

                    // 3. Show Snackbar with Undo
                    if (entryToDelete != null) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Rating deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short // Persists for ~4 seconds
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                // 4. Restore if Undo clicked
                                viewModel.saveRating(context, entryToDelete)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ScoreComparisonRow(label: String, previousScore: Double, currentScore: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            if (previousScore > 0) String.format("%.1f", previousScore) else "-",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = getScoreColor(previousScore)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (currentScore > 0) String.format("%.1f", currentScore) else "-",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = getScoreColor(currentScore)
            )
            if (previousScore > 0 && currentScore > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        currentScore > previousScore -> "↗"
                        currentScore < previousScore -> "↘"
                        else -> "→"
                    },
                    fontSize = 16.sp,
                    color = getScoreColor(currentScore)
                )
            }
        }
    }
}

fun getScoreColor(score: Double): Color {
    return when {
        score >= 8.0 -> Color(0xFF2E7D32)
        score >= 5.0 -> Color(0xFFF57C00)
        score > 0.0 -> Color(0xFFC62828)
        else -> Color.Gray
    }
}