package com.example.beeing

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    // Score picked on the hourly notification, to be finished in-app
    private val pendingRating = mutableStateOf<PendingRating?>(null)

    private fun consumePendingRating(intent: Intent?) {
        if (intent == null) return
        val score = intent.getIntExtra("PENDING_SCORE", -1)
        if (score in 1..10) {
            pendingRating.value = PendingRating(
                score = score,
                targetTs = intent.getLongExtra("TARGET_TS", 0L),
                label = intent.getStringExtra("TARGET_LABEL") ?: ""
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
        createNotificationChannel(this)
        scheduleWeeklyReport(this)
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
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (isSystemInDarkTheme()) {
                        dynamicDarkColorScheme(context)
                    } else {
                        dynamicLightColorScheme(context)
                    }
                }
                else -> {
                    if (isSystemInDarkTheme()) {
                        darkColorScheme()
                    } else {
                        lightColorScheme()
                    }
                }
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HourlyPulseApp(
                        pendingRating = pendingRating.value,
                        onPendingRatingConsumed = { pendingRating.value = null }
                    )
                }
            }
        }
    }
}
