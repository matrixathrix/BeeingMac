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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.SubcomposeLayout
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
 * band should animate toward `grow` (e.g. `animateFloatAsState`); the middle
 * is always measured against the band's *current* height, so while the band
 * animates down (say the tags expand) the middle grows into exactly the room
 * it frees — no overlap with the footer. [middle] sits right under the band;
 * anything still left falls between it and the pinned footer.
 */
@Composable
fun BandFillLayout(
    band: @Composable (grow: Float) -> Unit,
    middle: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    maxBandGrow: Dp = 160.dp
) {
    SubcomposeLayout(modifier) { c ->
        val width = c.maxWidth
        val total = c.maxHeight
        val loose = Constraints(maxWidth = width, maxHeight = total)

        val foot = subcompose("footer", footer).map { it.measure(loose) }
        val footH = foot.maxOfOrNull { it.height } ?: 0

        // The band at rest, and how tall the middle would like to be.
        val bandMin = subcompose("bandMin") { band(0f) }.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0
        val midMeasurables = subcompose("middle", middle)
        val midWants = midMeasurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0

        val growCap = maxBandGrow.roundToPx()
        val spare = (total - footH - midWants - bandMin).coerceAtLeast(0)
        val target = if (growCap == 0) 0f else minOf(spare, growCap).toFloat() / growCap

        // The band at its current (possibly animating) size…
        val bandMeasurables = subcompose("band") { band(target) }
        val bandH = (bandMeasurables.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0)
            .coerceIn(bandMin, (total - footH).coerceAtLeast(bandMin))
        val bandP = bandMeasurables.map { it.measure(Constraints.fixed(width, bandH)) }

        // …and the middle gets exactly what it leaves.
        val midMax = (total - footH - bandH).coerceAtLeast(0)
        val mid = midMeasurables.map { it.measure(Constraints(maxWidth = width, maxHeight = midMax)) }

        layout(width, total) {
            bandP.forEach { it.place(0, 0) }
            mid.forEach { it.place(0, bandH) }
            foot.forEach { it.place(0, total - footH) }
        }
    }
}
