package com.example.beeing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main app with ORIGINAL info dialog and menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyPulseApp(
    pendingRating: PendingRating? = null,
    onPendingRatingConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    // 0 = Streaks, 1 = Now (default), 2 = Past
    var selectedTab by remember { mutableIntStateOf(1) }
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(
                selectedTab,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
    }
    // settledPage (not currentPage): currentPage updates at every intermediate
    // page during a multi-tab jump, which would re-target the animation there
    LaunchedEffect(pagerState.settledPage) {
        selectedTab = pagerState.settledPage
    }

    // Shared ViewModel
    val viewModel: RatingsViewModel = viewModel()

    // Tab state


    // Separate scroll states for each tab
    val nowScrollState = rememberScrollState()
    val pastScrollState = rememberScrollState()
    val streaksScrollState = rememberScrollState()

    // Menu and dialogs state
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var autoBackupEnabled by remember { mutableStateOf(context.getSharedPreferences("b", 0).getBoolean("auto_backup", false)) }
    var lastBackupTime by remember { mutableStateOf(context.getSharedPreferences("b", 0).getLong("last_backup", 0L)) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var missingTagsAfterImport by remember { mutableStateOf<List<String>?>(null) }

    // Notification handling state
    var targetedHourOffset by remember { mutableIntStateOf(0) }
    var ratingCardYPosition by remember { mutableFloatStateOf(0f) }
    var chartYPosition by remember { mutableFloatStateOf(0f) }

    // Score picked on the notification: jump to Now with it pre-selected
    var pendingScore by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingRating) {
        pendingRating?.let { pr ->
            val currentHourStart = Calendar.getInstance().apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            // targetTs is the START of the rated hour; offset 0 = most recent
            // completed hour, 1 = the grace-period hour before it
            val offset = ((currentHourStart - pr.targetTs) / 3_600_000L).toInt() - 1
            if (offset in 0..1) {
                targetedHourOffset = offset
                pendingScore = pr.score
                selectedTab = 1
            }
            onPendingRatingConsumed()
        }
    }

    // Full-screen celebration when the daily ring closes (streak day secured)
    var ringCelebrationDays by remember { mutableStateOf<Int?>(null) }

    // Back press handling (double tap to exit)
    var lastBackPress by remember { mutableLongStateOf(0L) }
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPress < 2000) {
            (context as? Activity)?.finish()
        } else {
            lastBackPress = currentTime
            android.widget.Toast.makeText(context, "Press back again to exit the app", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Import/Export launchers
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        pendingImportUri = it
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let { uri -> saveToCsv(context, uri, viewModel.allRatings) }
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            context.getSharedPreferences("b", 0).edit().putString("backup_uri", it.toString()).apply()
        }
    }

    // Initial data load
    LaunchedEffect(Unit) {
        viewModel.loadRatings(context)
    }

    // Hourly alarm fires while the app is on screen → refresh immediately so
    // the new hour's rating dial animates into existence, rather than waiting
    // for the user to background/foreground the app.
    DisposableEffect(Unit) {
        val hourTickReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: Intent?) {
                viewModel.loadRatings(context)
                viewModel.triggerRefresh()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            hourTickReceiver,
            android.content.IntentFilter(ACTION_HOUR_TICKED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(hourTickReceiver) }
    }

    // Lifecycle observer for app resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadRatings(context)
                viewModel.triggerRefresh()

                // Auto-focus logic
                val now = java.util.Calendar.getInstance()
                val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)

                val isLatestHourLogged = viewModel.allRatings.any { entry ->
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                    cal.get(java.util.Calendar.HOUR_OF_DAY) == currentHour &&
                            cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) &&
                            cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
                }

                val previousHour = if (currentHour == 0) 23 else currentHour - 1
                val isPreviousHourLogged = viewModel.allRatings.any { entry ->
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                    val entryHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    val entryDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
                    val entryYear = cal.get(java.util.Calendar.YEAR)

                    if (currentHour == 0) {
                        val yesterday = java.util.Calendar.getInstance().apply {
                            add(java.util.Calendar.DAY_OF_YEAR, -1)
                        }
                        entryHour == 23 && entryDay == yesterday.get(java.util.Calendar.DAY_OF_YEAR) &&
                                entryYear == yesterday.get(java.util.Calendar.YEAR)
                    } else {
                        entryHour == previousHour && entryDay == now.get(java.util.Calendar.DAY_OF_YEAR) &&
                                entryYear == now.get(java.util.Calendar.YEAR)
                    }
                }

                if (isLatestHourLogged && !isPreviousHourLogged) {
                    targetedHourOffset = 1
                    selectedTab = 1
                    scope.launch {
                        delay(100)
                        nowScrollState.animateScrollTo(ratingCardYPosition.toInt())
                    }
                } else {
                    targetedHourOffset = 0
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
        { HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // keep all three tabs composed so page switches never stutter on
            // composing an intermediate/destination page mid-animation
            beyondViewportPageCount = 2
        ){ page ->
            when (page) {
                0 -> StreaksTab(
                    viewModel = viewModel,
                    scrollState = streaksScrollState
                )

                1 -> NowTab(
                    viewModel = viewModel,
                    scrollState = nowScrollState,
                    targetedHourOffset = targetedHourOffset,
                    onTargetedHourOffsetChange = { targetedHourOffset = it },
                    ratingCardYPosition = ratingCardYPosition,
                    onRatingCardYPosition = { ratingCardYPosition = it },
                    onOpenStreaks = { selectedTab = 0 },
                    pendingScore = pendingScore,
                    onPendingScoreConsumed = { pendingScore = null },
                    onRingClosed = { ringCelebrationDays = it },
                    // The header is part of the Now PAGE, so it slides
                    // horizontally with it during swipes — the side tabs sit
                    // at full height the whole time instead of drifting
                    // diagonally up into reclaimed header space.
                    header = {
                        HeaderSection(
                            onImport = { importLauncher.launch("text/*") },
                            onExport = { exportLauncher.launch("bee_data.csv") },
                            onMenuClick = { showMenu = true },
                            onStreakClick = { selectedTab = 0 },
                            onInfoClick = { showInfoDialog = true }
                        )
                    }
                )

                2 -> PastTab(
                    viewModel = viewModel,
                    scrollState = pastScrollState,
                    chartYPosition = chartYPosition,
                    onChartYPosition = { chartYPosition = it }
                )
            }
            }

            // Floating translucent nav pill — content scrolls beneath it
            FloatingPillNavBar(
                pagerState = pagerState,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Data & Backup sheet
        if (showMenu) {
            val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            // The last-backup time can change in the background (auto-backup
            // fires whether or not this sheet is open) — refresh it on open
            // rather than trusting whatever was true back at app launch.
            LaunchedEffect(Unit) {
                lastBackupTime = context.getSharedPreferences("b", 0).getLong("last_backup", 0L)
            }
            ModalBottomSheet(
                onDismissRequest = { showMenu = false },
                sheetState = menuSheetState
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text("Data & Backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DataActionCard(
                            emoji = "📥",
                            label = "Import",
                            caption = "Restore from a CSV file",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                importLauncher.launch("text/*")
                                showMenu = false
                            }
                        )
                        DataActionCard(
                            emoji = "📤",
                            label = "Export",
                            caption = "Save a CSV file",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                exportLauncher.launch("bee_data.csv")
                                showMenu = false
                            }
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = appCardColor()
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Auto-backup", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Daily at 12:05 AM",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoBackupEnabled,
                                    onCheckedChange = {
                                        autoBackupEnabled = it
                                        context.getSharedPreferences("b", 0).edit().putBoolean("auto_backup", it).apply()
                                        if (it) scheduleAutoBackup(context) else cancelAutoBackup(context)
                                    }
                                )
                            }

                            AnimatedVisibility(visible = autoBackupEnabled) {
                                Column {
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { folderPickerLauncher.launch(null) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("📁", fontSize = 15.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Set backup location", fontSize = 13.sp)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    val statusText = if (lastBackupTime > 0) {
                                        "Last backed up " + SimpleDateFormat("h:mm a, d MMM", Locale.getDefault())
                                            .format(Date(lastBackupTime))
                                    } else {
                                        "Not backed up yet — pick a folder above"
                                    }
                                    Text(
                                        statusText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🐝 Beeing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Developed by AthrixMatrix · v1.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // ORIGINAL INFO DIALOG
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Text(
                        "🐝 How Beeing Works",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    // Kept deliberately short — the deeper explanations (savers,
                    // flowers, tag scoring, ...) live next to the features
                    // themselves via their own ⓘ buttons, where the context
                    // that makes them worth reading actually is.
                    Column {
                        Text(
                            "⏰ Rate the last hour, 1–10 — a quick, honest check-in.",
                            fontSize = 14.sp, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "🏆 Do it consistently to build a streak — miss too long and it resets.",
                            fontSize = 14.sp, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "🏷️ Tag what you were doing, and Insights will show what actually boosts or drags your score.",
                            fontSize = 14.sp, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = buildAnnotatedString {
                                append("To live is to pass through hours. To ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                    append("Bee")
                                }
                                append(" is to stretch your being into them. 🌟")
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Got it!")
                    }
                }
            )
        }

        // Import confirmation
        if (pendingImportUri != null) {
            AlertDialog(
                onDismissRequest = { pendingImportUri = null },
                title = { Text("Confirm Import") },
                text = { Text("This will replace all current data. Continue?") },
                confirmButton = {
                    Button(onClick = {
                        val imported = loadFromCsv(context, pendingImportUri!!)
                        if (imported.isNotEmpty()) {
                            imported.forEach { viewModel.saveRating(context, it) }
                            // Tags on the imported ratings are never used to modify
                            // the user's own selectable tag list — flag any that
                            // aren't already in it, once, instead of silently
                            // leaving them un-selectable for future ratings.
                            val currentTags = loadTags(context).toSet()
                            val missing = imported.flatMap { it.tags }
                                .filter { it != RECLAIM_TAG }
                                .distinct()
                                .filterNot { it in currentTags }
                            if (missing.isNotEmpty()) {
                                missingTagsAfterImport = missing
                            }
                        }
                        pendingImportUri = null
                    }) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImportUri = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // One-time heads-up: custom tags on the import that aren't selectable yet
        missingTagsAfterImport?.let { missing ->
            AlertDialog(
                onDismissRequest = { missingTagsAfterImport = null },
                title = { Text("Heads up about tags") },
                text = {
                    Text(
                        "${missing.size} tag${if (missing.size == 1) "" else "s"} on the imported ratings " +
                                "${if (missing.size == 1) "isn't" else "aren't"} in your current tag list: " +
                                "${missing.joinToString(", ")}.\n\n" +
                                "They'll still show correctly on those entries — but add them again from " +
                                "the + button if you want to select them for new ratings.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { missingTagsAfterImport = null }) { Text("Got it") }
                }
            )
        }
    }

    // Duolingo-style full-screen takeover when today's ring closes —
    // drawn above everything, header and nav pill included
    ringCelebrationDays?.let { days ->
        RingClosedCelebration(
            streakDays = days,
            onDone = { ringCelebrationDays = null }
        )
    }
    }
}

/** Import/Export tile: emoji + label + short caption, whole card tappable. */
@Composable
private fun DataActionCard(
    emoji: String,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = appCardColor()
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                caption,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    NavItem("Streaks", Icons.Default.Star),
    NavItem("Now", Icons.Default.Home),
    NavItem("Past", Icons.Default.DateRange)
)

/**
 * Floating pill-shaped bottom navigation bar with a fixed width, centered.
 * Every item keeps a fixed slot (icon + label); a filled highlight tracks
 * the pager's live scroll position — including mid-swipe drag — instead of
 * animating separately after the fact once a page settles.
 * Translucent — it floats over the content, which scrolls beneath it.
 */
@Composable
private fun FloatingPillNavBar(
    pagerState: PagerState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val slotWidth = 92.dp
    val slotHeight = 52.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                // Sliding highlight behind the selected slot — a direct function
                // of the pager's own (already-smooth, whether dragged or
                // programmatically animated) scroll position, not a separate
                // animation chasing the settled page after the swipe lands.
                val highlightX = slotWidth * (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                Box(
                    Modifier
                        .offset(x = highlightX)
                        .size(slotWidth, slotHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )

                Row {
                    bottomNavItems.forEachIndexed { index, item ->
                        val selected = selectedTab == index
                        val contentColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(250),
                            label = "navItemContent"
                        )

                        Column(
                            modifier = Modifier
                                .size(slotWidth, slotHeight)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTabSelected(index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.label,
                                color = contentColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}