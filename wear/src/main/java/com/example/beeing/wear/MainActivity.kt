package com.example.beeing.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Text
import com.example.beeing.wear.ui.WearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = WatchSurfaces.view(this)
        setContent {
            WearTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (v.setUp) "${v.todayHours}/${v.goal} · ${v.streak}" else "Open Beeing on your phone to set up")
                }
            }
        }
    }
}
