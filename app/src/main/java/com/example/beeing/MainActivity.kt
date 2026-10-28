package com.example.beeing

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import com.example.beeing.ui.theme.BeeingTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    // Score picked on the hourly notification, to be finished in-app
    private val pendingRating = mutableStateOf<PendingRating?>(null)
    private val openTab = mutableStateOf<Int?>(null)

    private fun consumePendingRating(intent: Intent?) {
        if (intent == null) return
        intent.getIntExtra("OPEN_TAB", -1).let { if (it in 0..2) openTab.value = it }
        val score = intent.getIntExtra("PENDING_SCORE", -1)
        if (score in 1..10) {
            pendingRating.value = PendingRating(
                score = score,
                targetTs = intent.getLongExtra("TARGET_TS", 0L),
                label = intent.getStringExtra("TARGET_LABEL") ?: "",
                tags = (intent.getStringExtra("PENDING_TAGS") ?: "").split(";").filter { it.isNotBlank() }
            )
            // The rating continues in-app; the notification has done its job
            getSystemService(NotificationManager::class.java).cancel(1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumePendingRating(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw under the system bars on every API level (35+ forces it anyway), so
        // each tab's BrandBand really sits under the status bar. Light icons:
        // a band is always behind them.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        createNotificationChannel(this)
        scheduleWeeklyReport(this)
        scheduleStreakAlerts(this)
        // A first-ever launch starts in beginner mode (4-hour days). Runs before
        // any composition so the first frame already knows its goal.
        ensureModeInitialized(this)
        consumePendingRating(intent)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) scheduleExactHourlyAlarm(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleExactHourlyAlarm(this)
        }

        setContent {
            // Beeing's own palette, light/dark by the system setting — never
            // the wallpaper-derived dynamic scheme.
            BeeingTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HourlyPulseApp(
                        pendingRating = pendingRating.value,
                        onPendingRatingConsumed = { pendingRating.value = null },
                        openTab = openTab.value,
                        onOpenTabConsumed = { openTab.value = null }
                    )
                }
            }
        }
    }
}
