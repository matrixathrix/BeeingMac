package com.example.beeing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.beeing.ui.theme.LocalBeeExtras

/** Bottom-corner radius of every tab's brand band. */
val BandShape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)

/** Translucent white for chips and pills that sit ON the band. */
val OnBandFill = Color.White.copy(alpha = 0.14f)

/**
 * The aubergine band every tab opens with (Swiggy's coloured header idea):
 * full-bleed gradient, rounded bottom corners, white content. It draws under
 * the status bar and pads itself below it, so callers place it at the very
 * top of their tab with no inset of their own.
 */
@Composable
fun BrandBand(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(BandShape)
                .background(LocalBeeExtras.current.bandBrush)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
            content = content
        )
    }
}

/** One card style for the whole app: `surface` fill, 20dp, shadow in light mode only. */
object BeeCardDefaults {
    val Shape: Shape = RoundedCornerShape(20.dp)

    @Composable
    fun colors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    fun elevation(): CardElevation =
        CardDefaults.cardElevation(defaultElevation = if (LocalBeeExtras.current.cardShadow) 2.dp else 0.dp)
}

/** Chips everywhere: muted fill and no outline; selected = honey with aubergine ink. */
@Composable
fun beeChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = LocalBeeExtras.current.accentFill,
    selectedLabelColor = LocalBeeExtras.current.onAccentFill,
    selectedLeadingIconColor = LocalBeeExtras.current.onAccentFill,
    selectedTrailingIconColor = LocalBeeExtras.current.onAccentFill
)

/** Every filled button: the honey fill with aubergine ink, in both modes. */
object BeeButtonDefaults {
    @Composable
    fun colors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = LocalBeeExtras.current.accentFill,
        contentColor = LocalBeeExtras.current.onAccentFill
    )
}

/**
 * An hour cell with no rating, in the day strips and the pattern grid. Ink at
 * low alpha rather than `surfaceVariant`, which nearly vanishes on a white
 * card; a still-to-come hour is fainter than a passed, unrated one.
 */
@Composable
fun emptyCellColor(future: Boolean): Color =
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (future) 0.07f else 0.16f)

/**
 * A tab frame of three slots: [band] on top, [middle] content, [footer] pinned
 * to the bottom, for screens whose content can be shorter than the screen.
 *
 * Spare height becomes **band growth**, capped at [maxBandGrow]: the band is
 * told how much via `grow` (0f…1f) and spends it on bigger type and spacing,
 * so a taller band reads as a bigger heading, never as an empty patch. The
 * band should animate toward `grow` (e.g. `animateFloatAsState`).
 *
 * [compact] is the caller saying "the middle wants all the room" (the tags are
 * expanded): the band's target drops to 0 and the middle is measured against
 * the band's *current*, animating height, so it grows into exactly the room
 * the band frees and never overlaps the footer. When not compact, the middle is
 * measured once against the band at rest and the growth follows from that.
 *
 * Deliberately no intrinsic measurement of [middle]: asking a FlowRow of chips
 * for its intrinsic height on every animation frame is what made the
 * expand/collapse stutter.
 */
@Composable
fun BandFillLayout(
    band: @Composable (grow: Float) -> Unit,
    middle: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    maxBandGrow: Dp = 160.dp
) {
    // Stable slot contents: a fresh `{ band(x) }` lambda per measure pass would
    // force the band to recompose on every animation frame. The target travels
    // through state instead, written only when it actually changes.
    val targetState = remember { mutableFloatStateOf(0f) }
    val bandAtRest = remember(band) { @Composable { band(0f) } }
    val bandLive = remember(band) { @Composable { band(targetState.floatValue) } }

    SubcomposeLayout(modifier) { c ->
        val width = c.maxWidth
        val total = c.maxHeight
        val loose = Constraints(maxWidth = width, maxHeight = total)

        val foot = subcompose("footer", footer).map { it.measure(loose) }
        val footH = foot.maxOfOrNull { it.height } ?: 0
        val bandMin = subcompose("bandMin", bandAtRest).maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0
        val midMeasurables = subcompose("middle", middle)
        val growCap = maxBandGrow.roundToPx()

        fun measureBand(target: Float): Pair<List<androidx.compose.ui.layout.Placeable>, Int> {
            if (targetState.floatValue != target) targetState.floatValue = target
            val m = subcompose("band", bandLive)
            val h = (m.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0)
                .coerceIn(bandMin, (total - footH).coerceAtLeast(bandMin))
            return m.map { it.measure(Constraints.fixed(width, h)) } to h
        }

        val bandP: List<androidx.compose.ui.layout.Placeable>
        val bandH: Int
        val mid: List<androidx.compose.ui.layout.Placeable>
        if (compact) {
            // Band first (shrinking toward 0), middle takes whatever it leaves.
            val (p, h) = measureBand(0f)
            bandP = p; bandH = h
            mid = midMeasurables.map {
                it.measure(Constraints(maxWidth = width, maxHeight = (total - footH - bandH).coerceAtLeast(0)))
            }
        } else {
            // Middle at its natural size (capped by the band at rest), then the
            // band grows into what's left.
            mid = midMeasurables.map {
                it.measure(Constraints(maxWidth = width, maxHeight = (total - footH - bandMin).coerceAtLeast(0)))
            }
            val midH = mid.maxOfOrNull { it.height } ?: 0
            val spare = (total - footH - midH - bandMin).coerceAtLeast(0)
            val target = if (growCap == 0) 0f else minOf(spare, growCap).toFloat() / growCap
            val (p, h) = measureBand(target)
            bandP = p; bandH = h
        }

        layout(width, total) {
            bandP.forEach { it.place(0, 0) }
            mid.forEach { it.place(0, bandH) }
            foot.forEach { it.place(0, total - footH) }
        }
    }
}

/**
 * Draws its content scaled by [scale] (read at layout time, so animating it
 * never recomposes) and reports the scaled size to its parent. The content is
 * always measured with the same constraints, so a Text keeps its cached layout:
 * animating type size this way costs a relayout of one box, not a text reshape.
 */
fun Modifier.scaledLayout(scale: () -> Float): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val s = scale()
    layout((p.width * s).roundToInt(), (p.height * s).roundToInt()) {
        p.placeWithLayer(0, 0) {
            scaleX = s
            scaleY = s
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** A vertical gap whose height is read at layout time (animate without recomposing). */
fun Modifier.animatedHeight(height: () -> Dp): Modifier = this.layout { measurable, constraints ->
    val h = height().roundToPx()
    val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(p.width, h) { p.place(0, 0) }
}
