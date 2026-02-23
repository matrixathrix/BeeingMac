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
import java.util.*

/**
 * PAST TAB - WITH EXPANDED SCORE TABLE
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

    // Load ratings from ViewModel
    val allRatings = viewModel.allRatings

    // Chart view state
    var currentChartView by remember { mutableStateOf(ChartView.DAY) }

    // Edit state
    var editingEntry by remember { mutableStateOf<RatingEntry?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Calculate time period averages
    val scoreStats = remember(allRatings, viewModel.refreshTrigger) {
        val now = Calendar.getInstance()

        // Yesterday
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayRatings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dayToCheck = if (hour == 0) {
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            } else cal
            dayToCheck.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
                    dayToCheck.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
        }
        val yesterdayAvg = if (yesterdayRatings.isEmpty()) 0.0 else yesterdayRatings.map { it.score }.average()

        // Today
        val todayRatings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dayToCheck = if (hour == 0) {
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            } else cal
            dayToCheck.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                    dayToCheck.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        }
        val todayAvg = if (todayRatings.isEmpty()) 0.0 else todayRatings.map { it.score }.average()

        // Last Month
        val lastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val lastMonthRatings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val monthToCheck = if (hour == 0) {
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            } else cal
            monthToCheck.get(Calendar.MONTH) == lastMonth.get(Calendar.MONTH) &&
                    monthToCheck.get(Calendar.YEAR) == lastMonth.get(Calendar.YEAR)
        }
        val lastMonthAvg = if (lastMonthRatings.isEmpty()) 0.0 else lastMonthRatings.map { it.score }.average()

        // Current Month
        val currentMonthRatings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val monthToCheck = if (hour == 0) {
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            } else cal
            monthToCheck.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                    monthToCheck.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        }
        val currentMonthAvg = if (currentMonthRatings.isEmpty()) 0.0 else currentMonthRatings.map { it.score }.average()

        // Last Year
        val lastYear = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
        val lastYearRatings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val yearToCheck = if (hour == 0) {
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            } else cal
            yearToCheck.get(Calendar.YEAR) == lastYear.get(Calendar.YEAR)
        }
        val lastYearAvg = if (lastYearRatings.isEmpty()) 0.0 else lastYearRatings.map { it.score }.average()

        // Current Year
        val currentYearRatings = allRatings.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val yearToCheck = if (hour == 0) {
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            } else cal
            yearToCheck.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        }
        val currentYearAvg = if (currentYearRatings.isEmpty()) 0.0 else currentYearRatings.map { it.score }.average()

        mapOf(
            "Yesterday" to yesterdayAvg,
            "Today" to todayAvg,
            "Last Month" to lastMonthAvg,
            "Current Month" to currentMonthAvg,
            "Last Year" to lastYearAvg,
            "Current Year" to currentYearAvg
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp)) // DOUBLE the padding for header

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

                    // Row headers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("", modifier = Modifier.weight(1f)) // Empty for label column
                        Text("Previous", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Current", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Divider(Modifier.padding(vertical = 8.dp))

                    // Day comparison
                    ScoreComparisonRow("Day", scoreStats["Yesterday"] ?: 0.0, scoreStats["Today"] ?: 0.0)
                    Divider(Modifier.padding(vertical = 4.dp))

                    // Month comparison
                    ScoreComparisonRow("Month", scoreStats["Last Month"] ?: 0.0, scoreStats["Current Month"] ?: 0.0)
                    Divider(Modifier.padding(vertical = 4.dp))

                    // Year comparison
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
                // Professional Chart (original style with toggle buttons inside)
                ProfessionalChart(
                    ratings = allRatings,
                    view = currentChartView,
                    onToggle = { currentChartView = it }
                )

                // Insight Panel
                InsightPanel(
                    ratings = allRatings,
                    view = currentChartView
                )
            }
        }

        // History Panel
        HistoryPanel(
            ratings = allRatings,
            onEdit = { editingEntry = it }
        )

        Spacer(Modifier.height(32.dp))
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
                    viewModel.deleteRating(context, id)
                    editingEntry = null
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

        // Previous score
        Text(
            if (previousScore > 0) String.format("%.1f", previousScore) else "-",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                previousScore >= 8.0 -> Color(0xFF2E7D32)
                previousScore >= 5.0 -> Color(0xFFF57C00)
                previousScore > 0.0 -> Color(0xFFC62828)
                else -> Color.Gray
            }
        )

        // Current score with trend indicator
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (currentScore > 0) String.format("%.1f", currentScore) else "-",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    currentScore >= 8.0 -> Color(0xFF2E7D32)
                    currentScore >= 5.0 -> Color(0xFFF57C00)
                    currentScore > 0.0 -> Color(0xFFC62828)
                    else -> Color.Gray
                }
            )

            // Trend indicator
            if (previousScore > 0 && currentScore > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        currentScore > previousScore -> "↗"
                        currentScore < previousScore -> "↘"
                        else -> "→"
                    },
                    fontSize = 16.sp,
                    color = when {
                        currentScore > previousScore -> Color(0xFF2E7D32)
                        currentScore < previousScore -> Color(0xFFC62828)
                        else -> Color.Gray
                    }
                )
            }
        }
    }
}