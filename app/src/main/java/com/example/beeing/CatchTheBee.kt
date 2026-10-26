package com.example.beeing

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.theme.FredokaFontFamily
import com.example.beeing.ui.theme.HoneyGold
import kotlin.math.roundToInt
import kotlin.random.Random

/** Visible bee, and the larger circle that counts as catching it. */
private val BEE_ICON = 38.dp
private val BEE_TOUCH = 64.dp

/** Room kept at the top of the field for the prompt, so the bee never covers it. */
private val HINT_SPACE = 34.dp

/** Cruising speed. Slow on purpose: catching it should take a moment, not a reflex. */
private val BEE_SPEED_PER_S = 70.dp
private const val MIN_LEG_MS = 1200

/**
 * The caught-up screen's earned way out: a bee drifting slowly around this
 * field, with "Catch the bee to lock the phone" above it. Tapping the bee
 * calls [onCatch] (NowTab locks the phone, same as the Lock button).
 *
 * Cheap by construction: every moving value is read inside a layout/draw
 * lambda (`offset {}`, `graphicsLayer {}`), so the flight never recomposes,
 * and everything runs on the frame clock — it stops by itself when the tab is
 * paged away, the app leaves the screen, or the display is off.
 *
 * With system animations turned off ("Remove animations"), the bee sits
 * still in the middle of the field and the prompt doesn't pulse.
 */
@Composable
fun CatchTheBee(onCatch: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxX = with(density) { (maxWidth - BEE_TOUCH).toPx() }.coerceAtLeast(0f)
        val minY = with(density) { HINT_SPACE.toPx() }
        val maxY = with(density) { (maxHeight - BEE_TOUCH).toPx() }.coerceAtLeast(minY)

        val pos = remember {
            Animatable(Offset(maxX * 0.15f, (minY + maxY) / 2f), Offset.VectorConverter)
        }
        var facingLeft by remember { mutableStateOf(false) }

        // Flight: one eased leg at a time to a random point in the field.
        LaunchedEffect(maxX, minY, maxY, reduceMotion) {
            if (reduceMotion) {
                pos.snapTo(Offset(maxX / 2f, (minY + maxY) / 2f))
                return@LaunchedEffect
            }
            val speedPx = with(density) { BEE_SPEED_PER_S.toPx() }
            while (true) {
                val target = Offset(
                    Random.nextFloat() * maxX,
                    minY + Random.nextFloat() * (maxY - minY)
                )
                val legMs = ((target - pos.value).getDistance() / speedPx * 1000f)
                    .roundToInt().coerceAtLeast(MIN_LEG_MS)
                facingLeft = target.x < pos.value.x
                pos.animateTo(target, tween(legMs, easing = FastOutSlowInEasing))
            }
        }

        val loop = rememberInfiniteTransition(label = "bee")
        val pulse by loop.animateFloat(
            1f, 0.55f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "hintPulse"
        )
        val wings by loop.animateFloat(
            -8f, 8f, infiniteRepeatable(tween(240), RepeatMode.Reverse), label = "wings"
        )
        val bob by loop.animateFloat(
            -3f, 3f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bob"
        )

        // The prompt drops in once, then breathes.
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val enter by animateFloatAsState(if (shown) 1f else 0f, tween(900), label = "hintIn")

        Text(
            "Catch the bee to lock the phone",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FredokaFontFamily,
            color = HoneyGold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = enter * if (reduceMotion) 1f else pulse
                    translationY = (1f - enter) * -24.dp.toPx()
                }
        )

        Box(
            Modifier
                .offset {
                    val lift = if (reduceMotion) 0f else bob.dp.toPx()
                    IntOffset(pos.value.x.roundToInt(), (pos.value.y + lift).roundToInt())
                }
                .size(BEE_TOUCH)
                .clip(CircleShape)
                .clickable(onClickLabel = "Lock the phone", onClick = onCatch)
                .semantics { contentDescription = "Bee. Catch it to lock the phone" },
            contentAlignment = Alignment.Center
        ) {
            // The 🐝 emoji, not a Material Symbol: a flying character has to
            // read as a bee at a glance (owner call). Sized in dp so the
            // system font scale can't balloon it past its tap target. The
            // emoji faces left in Noto/One UI, so it mirrors when heading right.
            Text(
                "🐝",
                fontSize = with(density) { BEE_ICON.toSp() },
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (reduceMotion) 0f else wings
                    scaleX = if (facingLeft) 1f else -1f
                }
            )
        }
    }
}
