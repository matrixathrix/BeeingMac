package com.example.beeing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
    // Full height on open — the sheet's content is taller than the default
    // partially-expanded stop, which left it needing a manual swipe-up.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                // Lifted clear of the floating nav pill (which draws on top
                // of this tab's content) and restyled to be easy to read
                // and to actually tap "Undo" on.
                Box(Modifier.fillMaxWidth().padding(bottom = 104.dp, start = 16.dp, end = 16.dp)) {
                    UndoSnackbar(data)
                }
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    // No app header on this tab — clear the status bar here
                    // and keep the reclaimed space for the chart itself.
                    .statusBarsPadding()
                    .padding(horizontal = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rhythm chart + the tag insights it drives, combined into one
                // card (always visible; self-managed Day/Week/Month/Year)
                if (allRatings.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                onChartYPosition(it.positionInParent().y)
                            }
                    ) {
                        PlainSectionCard {
                            ProfessionalChart(ratings = allRatings, refreshKey = viewModel.refreshTrigger)
                        }
                    }
                }

                // Recent history
                CollapsibleSection(
                    title = "Recent History",
                    initiallyExpanded = false,
                    headerActions = {
                        var showEditInfo by remember { mutableStateOf(false) }
                        IconButton(onClick = { showEditInfo = true }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Which entries can be edited",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (showEditInfo) {
                            AlertDialog(
                                onDismissRequest = { showEditInfo = false },
                                title = { Text("Editing entries") },
                                text = {
                                    Text(
                                        "You can edit the score, tags, or note on any of the last 10 hours. Older entries are locked in place.",
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = { showEditInfo = false }) { Text("Got it") }
                                }
                            )
                        }
                    }
                ) {
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
                                duration = SnackbarDuration.Long // more time to react to the undo
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
        colors = CardDefaults.cardColors(containerColor = appCardColor())
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

/** Friendlier, easier-to-tap replacement for the plain Material Snackbar. */
@Composable
private fun UndoSnackbar(data: SnackbarData) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🗑️", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                data.visuals.message,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(
                    onClick = { data.performAction() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// Same red/amber/green bands as scoreBandColor() (Utils.kt) — this one just
// also handles a fractional average and the "no data" gray case.
fun getScoreColor(score: Double): Color {
    return when {
        score >= 8.0 -> Color(0xFF66BB6A)
        score >= 5.0 -> Color(0xFFFFB300)
        score > 0.0 -> Color(0xFFB71C1C)
        else -> Color.Gray
    }
}