package com.example.beeing.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.beeing.wear.ui.CaughtUpScreen
import com.example.beeing.wear.ui.ChimeTipScreen
import com.example.beeing.wear.ui.FieldBottom
import com.example.beeing.wear.ui.Ink
import com.example.beeing.wear.ui.WearTheme
import java.util.Calendar

/** The launcher: straight into rating when an hour waits, otherwise the caught-up picture. */
class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createWatchChannels(this)
        scheduleWatchHour(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)

        val setUp = WatchStore.state(this) != null
        if (setUp && WatchStore.chimeTipShown(this) && WatchSurfaces.view(this).waiting.isNotEmpty()) {
            startActivity(RatingActivity.intent(this)); finish(); return
        }
        setContent {
            WearTheme {
                var tip by remember { mutableStateOf(setUp && !WatchStore.chimeTipShown(this)) }
                when {
                    !setUp -> Box(Modifier.fillMaxSize().background(FieldBottom).padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("Open Beeing on your phone to set up", color = Ink, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                    tip -> ChimeTipScreen {
                        WatchStore.markChimeTipShown(this)
                        tip = false
                        if (WatchSurfaces.view(this).waiting.isNotEmpty()) { startActivity(RatingActivity.intent(this)); finish() }
                    }
                    else -> CaughtUpScreen(WatchSurfaces.view(this), 60 - Calendar.getInstance().get(Calendar.MINUTE))
                }
            }
        }
    }
}
