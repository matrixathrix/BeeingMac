package com.example.beeing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main app with ORIGINAL info dialog and menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyPulseApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    LaunchedEffect(selectedTab) {
        pagerState.animateScrollToPage(selectedTab)
    }
    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    // Shared ViewModel
    val viewModel: RatingsViewModel = viewModel()

    // Tab state


    // Separate scroll states for each tab
    val nowScrollState = rememberScrollState()
    val pastScrollState = rememberScrollState()

    // Menu and dialogs state
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var autoBackupEnabled by remember { mutableStateOf(context.getSharedPreferences("b", 0).getBoolean("auto_backup", false)) }
    var lastBackupTime by remember { mutableStateOf(context.getSharedPreferences("b", 0).getLong("last_backup", 0L)) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // Notification handling state
    var targetedHourOffset by remember { mutableIntStateOf(0) }
    var ratingCardYPosition by remember { mutableFloatStateOf(0f) }
    var chartYPosition by remember { mutableFloatStateOf(0f) }

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
                    selectedTab = 0
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

    Scaffold(
        topBar = {
            // Original HeaderSection
            HeaderSection(
                streak = calculateStreak(viewModel.allRatings),
                onImport = { importLauncher.launch("text/*") },
                onExport = { exportLauncher.launch("bee_data.csv") },
                onMenuClick = { showMenu = true },
                onStreakClick = {
                    selectedTab = 1  // Switch to Past tab
                    scope.launch {
                        delay(100)
                        pastScrollState.animateScrollTo(chartYPosition.toInt())
                    }
                },
                onInfoClick = { showInfoDialog = true }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Now") },
                    label = { Text("Now") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Past") },
                    label = { Text("Past") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
        { HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ){ page ->
            when (page) {
                0 -> NowTab(
                    viewModel = viewModel,
                    scrollState = nowScrollState,
                    targetedHourOffset = targetedHourOffset,
                    onTargetedHourOffsetChange = { targetedHourOffset = it },
                    ratingCardYPosition = ratingCardYPosition,
                    onRatingCardYPosition = { ratingCardYPosition = it }
                )

                1 -> PastTab(
                    viewModel = viewModel,
                    scrollState = pastScrollState,
                    chartYPosition = chartYPosition,
                    onChartYPosition = { chartYPosition = it }
                )
            }
            }
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
                            "🏆 Build Your Streak",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Rate consistently and watch your streak grow! Missing ratings for more than 2 hours resets your streak, encouraging you to stay mindful.",
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
}