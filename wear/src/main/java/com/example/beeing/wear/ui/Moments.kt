package com.example.beeing.wear.ui

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.beeing.WatchView
import com.example.beeing.wear.R
import kotlinx.coroutines.delay

/** The day's ring: honey on a glass track. [filled] is 0..1 of the goal. */
@Composable
fun DayRing(filled: Float, modifier: Modifier = Modifier, color: Color = Honey, stroke: Float = 10f) {
    Canvas(modifier) {
        val w = stroke.dp.toPx()
        val inset = w / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(GlassFill, -90f, 360f, false, topLeft, arcSize, style = Stroke(w))
        drawArc(color, -90f, 360f * filled.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/**
 * The ring-closed moment: one strong haptic, the ring sweeps once, then it sits still.
 * Closes on a tap or after 4 s. With system animations off the ring is simply full.
 */
@Composable
fun RingClosedScreen(title: String, caption: String?, count: String, superDay: Boolean, onClose: () -> Unit) {
    val view = LocalView.current
    val ctx = LocalContext.current
    val still = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val sweep = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (!still) sweep.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        delay(4000)
        onClose()
    }
    Box(Modifier.fillMaxSize().background(FieldBottom).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        DayRing(sweep.value, Modifier.fillMaxSize().padding(10.dp), if (superDay) SuperGold else Honey)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(title, color = if (superDay) SuperGold else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            caption?.let { Text(it, color = InkMuted, fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
    }
}

/** Flame + streak, shield + savers: the same states as the phone's status strip. */
@Composable
fun StreakLine(v: WatchView) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val ink = if (v.atRisk) AtRiskRed else Ink
        Image(painterResource(if (v.paused || v.beginner) R.drawable.ic_sym_beginner else R.drawable.ic_sym_fire), null,
            Modifier.size(14.dp), colorFilter = ColorFilter.tint(if (v.streak == 0) InkMuted else if (v.atRisk) AtRiskRed else Honey))
        Text(
            when { v.paused -> " Paused ${v.streak}"; v.atRisk -> " ${v.streak}!"; else -> " ${v.streak}" },
            color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(10.dp))
        Image(painterResource(R.drawable.ic_sym_saver), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(Honey))
        Text(" ${v.savers}/5", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (!v.confirmed) {
            Spacer(Modifier.width(6.dp))
            Image(painterResource(R.drawable.ic_sym_reset), "waiting to sync", Modifier.size(12.dp), colorFilter = ColorFilter.tint(InkMuted))
        }
    }
}

/** Nothing waits: the same picture as the Tile, still. */
@Composable
fun CaughtUpScreen(v: WatchView, nextInMin: Int) {
    Box(Modifier.fillMaxSize().background(FieldBottom), contentAlignment = Alignment.Center) {
        DayRing(v.todayHours / v.goal.toFloat(), Modifier.fillMaxSize().padding(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${v.todayHours}/${v.goal}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("All caught up", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Next hour opens in ${nextInMin}m", color = InkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            StreakLine(v)
        }
    }
}

/** Shown once, the first time the watch is set up. */
@Composable
fun ChimeTipScreen(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(FieldBottom).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Beeing already marks every hour. If Galaxy's hourly chime is on, you'll feel both — you can turn it off under Sounds and vibration.",
                color = Ink, fontSize = 13.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            HoneyPill("Got it", height = 34.dp, onClick = onDone)
        }
    }
}
