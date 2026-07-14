package com.example.beeing

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Confetto(
    val angle: Float,      // launch direction (radians)
    val speed: Float,      // 0..1 relative burst distance
    val size: Float,       // px
    val spin: Float,       // total rotation over the flight (degrees)
    val color: Color,
    val isRect: Boolean
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFFFB300), // honey amber
    Color(0xFFFFD54F), // light gold
    Color(0xFF66BB6A), // green
    Color(0xFFFF7043), // coral
    Color.White
)

/**
 * Full-screen ~2s takeover when the daily ring closes, Duolingo style:
 * rising haptic ticks into a strong buzz, a golden ring slams shut around the
 * streak number, a glow flash, and a confetti burst with gravity.
 */
@Composable
fun RingClosedCelebration(streakDays: Int, onDone: () -> Unit) {
    val context = LocalContext.current

    // Master timeline 0..1 over 4.2s; every visual is keyed off it
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // accelerating tick cascade rising in strength, one deep buzz,
            // then a delayed double-thump as the confetti settles
            val timings = longArrayOf(0, 20, 75, 20, 68, 25, 60, 30, 52, 35, 45, 45, 40, 60, 150, 110, 600, 70, 120, 130)
            val amplitudes = intArrayOf(0, 50, 0, 70, 0, 95, 0, 125, 0, 160, 0, 200, 0, 255, 0, 190, 0, 150, 0, 220)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
        t.animateTo(1f, tween(4200, easing = LinearEasing))
        onDone()
    }

    val confetti = remember {
        List(130) {
            Confetto(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                speed = 0.35f + Random.nextFloat() * 0.65f,
                size = 10f + Random.nextFloat() * 18f,
                spin = Random.nextFloat() * 720f - 360f,
                color = CONFETTI_COLORS.random(),
                isRect = Random.nextBoolean()
            )
        }
    }
    val overshoot = remember { OvershootInterpolator(2.5f) }

    val progress = t.value
    // Phases: 0–0.18 ring slams shut · 0.18 flash + burst · 0.85–1 fade out
    val fadeOut = ((1f - progress) / 0.15f).coerceIn(0f, 1f)
    val scrimAlpha = (progress / 0.08f).coerceIn(0f, 1f) * 0.96f * fadeOut

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* swallow taps while it plays */ },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val radius = size.minDimension * 0.24f

            // 1. Golden ring sweeping closed
            val ringP = FastOutSlowInEasing.transform((progress / 0.18f).coerceIn(0f, 1f))
            if (ringP > 0f) {
                drawArc(
                    color = Color(0xFFFFB300),
                    startAngle = -90f,
                    sweepAngle = 360f * ringP,
                    useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = radius * 0.2f, cap = StrokeCap.Round),
                    alpha = fadeOut
                )
            }

            // 2. Glow flash expanding outward the instant it closes
            if (progress > 0.18f && progress < 0.5f) {
                val flash = 1f - (progress - 0.18f) / 0.32f
                drawCircle(
                    color = Color(0xFFFFD54F).copy(alpha = 0.35f * flash * fadeOut),
                    radius = radius * (1f + (1f - flash) * 1.8f),
                    center = c
                )
            }

            // 3. Confetti burst with gravity + spin
            if (progress > 0.18f) {
                val p = ((progress - 0.18f) / 0.72f).coerceIn(0f, 1f)
                val burst = 1f - (1f - p) * (1f - p) * (1f - p) // ease-out cubic
                confetti.forEach { cf ->
                    val dist = burst * cf.speed * size.minDimension * 0.55f
                    val x = c.x + cos(cf.angle) * dist
                    val y = c.y + sin(cf.angle) * dist + p * p * size.height * 0.22f
                    val alpha = (1f - p * p) * fadeOut
                    if (alpha > 0.01f) {
                        rotate(cf.spin * p, pivot = Offset(x, y)) {
                            if (cf.isRect) {
                                drawRect(
                                    color = cf.color,
                                    topLeft = Offset(x - cf.size / 2f, y - cf.size / 3f),
                                    size = Size(cf.size, cf.size * 0.66f),
                                    alpha = alpha
                                )
                            } else {
                                drawCircle(cf.color, cf.size / 2f, Offset(x, y), alpha = alpha)
                            }
                        }
                    }
                }
            }
        }

        // Streak number pops in with overshoot as the ring closes
        val pop = overshoot.getInterpolation(((progress - 0.14f) / 0.22f).coerceIn(0f, 1f))
        Column(
            Modifier.graphicsLayer {
                scaleX = pop
                scaleY = pop
                alpha = pop.coerceIn(0f, 1f) * fadeOut
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$streakDays",
                fontSize = 88.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFD54F)
            )
            Text(
                "DAY STREAK",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp,
                color = Color.White
            )
        }

        // Caption slides up beneath the ring once everything has landed
        val captionIn = ((progress - 0.35f) / 0.2f).coerceIn(0f, 1f)
        Text(
            "Ring closed — streak extended! 🐝",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 170.dp)
                .graphicsLayer {
                    translationY = (1f - captionIn) * 40f
                    alpha = captionIn * fadeOut
                }
        )
    }
}
