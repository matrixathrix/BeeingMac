package com.example.beeing

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.BeeButtonDefaults
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.theme.BandBottomDark
import com.example.beeing.ui.theme.BandTopLight
import com.example.beeing.ui.theme.HoneyGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SparkColors = listOf(Color(0xFFFFD54F), Color(0xFFFFB300), Color.White)

/**
 * Full-screen moment for a streak won back (a Super day has its own screen,
 * `SuperDayCelebration`). A flame drops in with overshoot, floats on a glowing
 * aubergine field and sparks fly. One haptic on the drop. With system animations
 * off it shows the end state.
 */
@Composable
fun StreakMomentCelebration(moment: StreakMoment.Recovered, onDone: () -> Unit) {
    val context = LocalContext.current
    val animationsOn = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val drop = remember { Animatable(if (animationsOn) 0f else 1f) }
    val float = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (animationsOn) {
            drop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, 200))
            }
            float.animateTo(1f, tween(1400))
        }
    }
    val bob = if (animationsOn) sin(float.value * PI.toFloat() * 2f) * 6f else 0f

    val title = "Your ${moment.streak}-day streak is back"

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(BandTopLight, BandBottomDark), radius = 1400f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.36f)
            drawCircle(
                Brush.radialGradient(listOf(HoneyGold.copy(alpha = 0.40f), Color.Transparent), center = c, radius = size.minDimension * 0.55f),
                radius = size.minDimension * 0.55f, center = c
            )
            val p = drop.value
            if (p > 0.55f) for (i in 0 until 14) {
                val a = i * (2f * PI.toFloat() / 14f)
                val dist = size.minDimension * (0.18f + 0.16f * ((i * 7) % 5) / 4f) * ((p - 0.55f) / 0.45f)
                drawCircle(
                    SparkColors[i % SparkColors.size].copy(alpha = 0.85f),
                    radius = 3.dp.toPx() + (i % 3).dp.toPx(),
                    center = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist)
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BeeIcon(
                Sym.Fire, size = 132.dp, tint = HoneyGold, contentDescription = null,
                modifier = Modifier.graphicsLayer {
                    val s = drop.value
                    scaleX = s; scaleY = s
                    translationY = (1f - s) * -420f + bob
                    alpha = s.coerceIn(0f, 1f)
                }
            )
            Spacer(Modifier.height(24.dp))
            Text("STREAK RECOVERED", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = HoneyGold)
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "You won it back. Keep it going.",
                fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDone, colors = BeeButtonDefaults.colors()) {
                Text("Keep going", fontWeight = FontWeight.Bold)
            }
        }
    }
}
