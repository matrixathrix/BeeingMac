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
    // Edit state
    var editingEntry by remember { mutableStateOf<RatingEntry?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        // the app-level header already consumed the status bar inset;
        // re-applying it here left a band of dead space below the header
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rhythm chart (untitled, always visible; self-managed Day/Week/Month/Year)
                if (allRatings.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                onChartYPosition(it.positionInParent().y)
                            }
                    ) {
                        PlainSectionCard {
                            ProfessionalChart(ratings = allRatings)
                        }
                    }
                }

                // Insights: single-period tag correlation (feature 2)
                if (allRatings.isNotEmpty()) {
                    CollapsibleSection(title = "Insights", initiallyExpanded = false) {
                        InsightsContent(
                            ratings = allRatings,
                            refreshKey = viewModel.refreshTrigger
                        )
                    }
                }

                // Recent history
                CollapsibleSection(title = "Recent History", initiallyExpanded = false) {
                    HistoryPanel(
                        ratings = allRatings,
                        onEdit = { editingEntry = it }
                    )
                }

                Spacer(Modifier.height(96.dp))
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

/**
 * Non-collapsible, untitled sibling of CollapsibleSection — same card look,
 * no header row.
 */
@Composable
private fun PlainSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
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