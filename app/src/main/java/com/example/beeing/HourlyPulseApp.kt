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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    // Entering beginner mode with a live streak pauses it — that needs saying
    // out loud before it happens, not after.
    var showBeginnerConfirm by remember { mutableStateOf(false) }
    var showGraduation by remember { mutableStateOf(false) }

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
    var ringCelebration by remember { mutableStateOf<RingCelebration?>(null) }

    // Mode + streak, shared by the menu copy, the explainer and the nudge
    val streakState = remember(viewModel.allRatings, viewModel.modeEvents, viewModel.refreshTrigger) {
        computeStreakState(viewModel.allRatings, loadReclaimSpends(context), viewModel.modeEvents)
    }

    // Graduation nudge: once, ever, on the 3rd completed beginner day. Marked
    // as shown the moment it appears, so declining is not re-asked.
    LaunchedEffect(streakState.beginnerMode, streakState.beginnerDaysCompleted) {
        if (streakState.beginnerMode &&
            streakState.beginnerDaysCompleted >= BEGINNER_GRADUATION_DAYS &&
            !beginnerGraduationShown(context)
        ) {
            markBeginnerGraduationShown(context)
            showGraduation = true
        }
    }

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
        viewModel.loadModeEvents(context)
    }

    // Lifecycle observer for app resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadRatings(context)
                viewModel.loadModeEvents(context)
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
                } else if (isPreviousHourLogged) {
                    targetedHourOffset = 0
                }
                // both pending: leave the target alone — NowTab defaults to
                // the expiring hour unless a notification chose otherwise
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            // A constant status-bar inset for every tab — no per-tab header in
            // the Scaffold, so nothing pops in or out when a page settles. Each
            // tab now owns its own header inside its scroll content (Now shows
            // the status card + ⋮ menu; Hive and Past show none), so the header
            // slides away with the page instead of jumping.
            Spacer(Modifier.fillMaxWidth().statusBarsPadding())
        },
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
                    onMenuClick = { showMenu = true },
                    pendingScore = pendingScore,
                    onPendingScoreConsumed = { pendingScore = null },
                    onRingClosed = { ringCelebration = it }
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
                onTabSelected = { selectedTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ORIGINAL SETTINGS MENU
        if (showMenu) {
            AlertDialog(
                onDismissRequest = { showMenu = false },
                title = { Text("Data options") },
                text = {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    importLauncher.launch("text/*")
                                    showMenu = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("IMPORT")
                            }

                            Button(
                                onClick = {
                                    exportLauncher.launch("bee_data.csv")
                                    showMenu = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("EXPORT")
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        Row(
                            Modifier.fillMaxWidth().clickable {
                                autoBackupEnabled = !autoBackupEnabled
                                context.getSharedPreferences("b", 0).edit().putBoolean("auto_backup", autoBackupEnabled).apply()
                                if (autoBackupEnabled) {
                                    scheduleAutoBackup(context)
                                } else {
                                    cancelAutoBackup(context)
                                }
                            }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-backup (Daily 12:05 AM)")
                            Checkbox(
                                checked = autoBackupEnabled,
                                onCheckedChange = {
                                    autoBackupEnabled = it
                                    context.getSharedPreferences("b", 0).edit().putBoolean("auto_backup", it).apply()
                                    if (it) {
                                        scheduleAutoBackup(context)
                                    } else {
                                        cancelAutoBackup(context)
                                    }
                                }
                            )
                        }

                        if (autoBackupEnabled) {
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Set Backup Location", fontSize = 12.sp)
                            }

                            if (lastBackupTime > 0) {
                                val backupDate = SimpleDateFormat("hh:mma, dd-MMM-yyyy", Locale.getDefault())
                                    .format(Date(lastBackupTime))
                                Text(
                                    "Last backed up: $backupDate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        // Beginner mode: a softer daily goal while the habit is
                        // young. Leaving it needs no ceremony; entering it with
                        // a live streak does, so that path routes via a confirm.
                        val toggleBeginner: (Boolean) -> Unit = { wantBeginner ->
                            if (wantBeginner && streakState.currentStreak > 0) {
                                showMenu = false
                                showBeginnerConfirm = true
                            } else {
                                viewModel.setBeginnerMode(context, wantBeginner)
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { toggleBeginner(!streakState.beginnerMode) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Beginner mode ($BEGINNER_HOURS_REQUIRED-hour days)")
                                Text(
                                    if (streakState.streakPaused)
                                        "Streak paused at ${streakState.currentStreak}"
                                    else "Streaks pause while it's on",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = streakState.beginnerMode,
                                onCheckedChange = { toggleBeginner(it) }
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        // How it works — moved here now that the header's info
                        // button is gone
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMenu = false
                                    showInfoDialog = true
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("How Beeing works")
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        // Author information
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "🐝 Beeing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Developed by AthrixMatrix",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Text(
                                "Version 1.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMenu = false }) {
                        Text("Close")
                    }
                }
            )
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
                    Column(
                        Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Welcome to your journey of intentional living!",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "⏰ The Rating System",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Every hour, Beeing asks you to rate how well you spent your time on a scale of 1-10. This simple practice brings awareness to each hour of your day.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "⬢ Build Your Streak",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Rate $STREAK_HOURS_REQUIRED hours in a day and that day is complete — complete days are your streak. Come up short one day and it becomes a 🌙 rest day: the streak holds, once a week. Miss an hour instead? Send a bee back to revisit any of the last $RECLAIM_WINDOW_HOURS — free, twice a day.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "🌱 Beginner Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "New here? Beginner mode asks for $BEGINNER_HOURS_REQUIRED hours a day instead of $STREAK_HOURS_REQUIRED, and those days sit outside the streak entirely — nothing to break, nothing to lose. Any streak you already have is paused and waiting when you switch back. Send a bee back still works. Turn it on or off any time in the menu." +
                                    if (streakState.beginnerMode) "\n\nIt's on right now — today's goal is $BEGINNER_HOURS_REQUIRED hours." else "",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "🏷️ Tag Your Activities",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add tags to track what you're doing—work, exercise, reading, family time.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "📊 Discover Patterns",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your charts reveal trends. Soul Fuel Tags show what boosts your score, while Vibe Killer Tags highlight what brings you down. Use these insights to design better days.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "✨ The Impact",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "By tracking each hour, you're not just logging time—you're taking ownership of how you live. Small adjustments compound into a more intentional, fulfilling life.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = buildAnnotatedString {
                                append("Remember: To live is to pass through hours. To ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                                    append("Bee")
                                }
                                append(" is to stretch your being into them. 🌟")
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            style = MaterialTheme.typography.bodyMedium,
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

        // Entering beginner mode with a streak running: say what "paused" means
        if (showBeginnerConfirm) {
            AlertDialog(
                onDismissRequest = { showBeginnerConfirm = false },
                title = { Text("🌱 Turn on beginner mode?") },
                text = {
                    Text(
                        "Your ${streakState.currentStreak}-day streak pauses — it won't grow and it " +
                                "can't break while beginner mode is on. Days need only " +
                                "$BEGINNER_HOURS_REQUIRED rated hours and are marked 🌱 on the " +
                                "calendar. Switch back any time and the streak picks up at " +
                                "${streakState.currentStreak} again.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.setBeginnerMode(context, true)
                        showBeginnerConfirm = false
                    }) { Text("Pause and switch") }
                },
                dismissButton = {
                    TextButton(onClick = { showBeginnerConfirm = false }) { Text("Cancel") }
                }
            )
        }

        // The one-time graduation nudge — declining costs nothing and never repeats
        if (showGraduation) {
            AlertDialog(
                onDismissRequest = { showGraduation = false },
                title = { Text("🌱 → ⬢ Ready for more?") },
                text = {
                    Text(
                        "You've completed $BEGINNER_GRADUATION_DAYS beginner days. " +
                                "Level up to Master mode ($STREAK_HOURS_REQUIRED hours)? " +
                                "Complete days start counting toward a streak again. " +
                                "No rush — you can switch whenever you like from the menu.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.setBeginnerMode(context, false)
                        showGraduation = false
                    }) { Text("Level up") }
                },
                dismissButton = {
                    TextButton(onClick = { showGraduation = false }) { Text("Not yet") }
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
    }

    // Duolingo-style full-screen takeover when today's ring closes —
    // drawn above everything, header and nav pill included
    ringCelebration?.let { celebration ->
        RingClosedCelebration(
            celebration = celebration,
            onDone = { ringCelebration = null }
        )
    }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    NavItem("Streak", Icons.Default.Star),
    NavItem("Now", Icons.Default.Home),
    NavItem("Past", Icons.Default.DateRange)
)

/**
 * Slim floating bottom navigation bar spanning the screen width, matching the
 * 20dp card radius used across the app. Each tab is icon + label side by side.
 * The filled highlight pill is driven directly by the pager's continuous
 * scroll position, so it tracks finger swipes in real time instead of
 * snapping after the swipe settles.
 * Translucent — it floats over the content, which scrolls beneath it.
 */
@Composable
private fun FloatingPillNavBar(
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val slotWidth = maxWidth / bottomNavItems.size
                val highlightInset = 12.dp
                // Continuous pager position: whole part = page, fraction = swipe progress
                val progress = pagerState.currentPage + pagerState.currentPageOffsetFraction

                Box(
                    Modifier
                        .offset(x = slotWidth * progress + highlightInset)
                        .size(slotWidth - highlightInset * 2, 36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )

                Row {
                    bottomNavItems.forEachIndexed { index, item ->
                        val focus = (1f - abs(progress - index)).coerceIn(0f, 1f)
                        val contentColor = lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.onPrimary,
                            focus
                        )

                        Row(
                            modifier = Modifier
                                .size(slotWidth, 36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTabSelected(index) },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                item.label,
                                color = contentColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}