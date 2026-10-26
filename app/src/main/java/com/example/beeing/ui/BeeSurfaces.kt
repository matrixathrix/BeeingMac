package com.example.beeing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
