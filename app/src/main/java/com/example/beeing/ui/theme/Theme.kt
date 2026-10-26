package com.example.beeing.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Beeing's fixed light/dark schemes. Roles are mapped so the whole app reads
 * as one surface system: `background` = the page, `surface` and every
 * `surfaceContainer*` = a card (so M3 sheets, menus and dialogs come out as
 * cards too), `surfaceVariant` = muted fills (chips, dial cells, tracks),
 * `primary` = the honey accent.
 */
val BeeingLightColors: ColorScheme = lightColorScheme(
    primary = Honey,
    onPrimary = OnHoney,
    primaryContainer = Honey.copy(alpha = 0.16f).compositeOn(CardLight),
    onPrimaryContainer = OnHoney,
    secondary = BandTopLight,
    onSecondary = Color.White,
    tertiary = RingQualifiedGreen,
    background = PageLight,
    onBackground = InkLight,
    surface = CardLight,
    onSurface = InkLight,
    surfaceVariant = MutedLight,
    onSurfaceVariant = InkSoftLight,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = CardLight,
    outline = InkSoftLight,
    outlineVariant = HairlineLight,
    error = Color(0xFFD93A3F),
    onError = Color.White
)

val BeeingDarkColors: ColorScheme = darkColorScheme(
    primary = Honey,
    onPrimary = OnHoney,
    primaryContainer = Honey.copy(alpha = 0.18f).compositeOn(CardDark),
    onPrimaryContainer = AccentTextDark,
    secondary = BandTopDark,
    onSecondary = Color.White,
    tertiary = RingQualifiedGreen,
    background = PageDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = MutedDark,
    onSurfaceVariant = InkSoftDark,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = CardDark,
    outline = InkSoftDark,
    outlineVariant = HairlineDark,
    error = Color(0xFFFF6B6E),
    onError = Color(0xFF2A0A0B)
)

/** What a [ColorScheme] can't hold: the band gradient and accent-as-text. */
data class BeeExtras(
    val bandBrush: Brush,
    val bandTop: Color,
    val bandBottom: Color,
    val accentText: Color,
    val cardShadow: Boolean
)

private fun bandBrush(top: Color, bottom: Color) =
    // ~160°: top-left lighter, bottom-right deeper.
    Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(600f, 1650f))

val BeeLightExtras = BeeExtras(
    bandBrush = bandBrush(BandTopLight, BandBottomLight),
    bandTop = BandTopLight,
    bandBottom = BandBottomLight,
    accentText = AccentTextLight,
    cardShadow = true
)

val BeeDarkExtras = BeeExtras(
    bandBrush = bandBrush(BandTopDark, BandBottomDark),
    bandTop = BandTopDark,
    bandBottom = BandBottomDark,
    accentText = AccentTextDark,
    cardShadow = false
)

val LocalBeeExtras = staticCompositionLocalOf { BeeLightExtras }

private fun Color.compositeOn(bg: Color): Color {
    val a = alpha
    return Color(
        red = red * a + bg.red * (1 - a),
        green = green * a + bg.green * (1 - a),
        blue = blue * a + bg.blue * (1 - a)
    )
}

@Composable
fun BeeingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // A brand band always sits under the status bar, in both modes.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalBeeExtras provides if (darkTheme) BeeDarkExtras else BeeLightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) BeeingDarkColors else BeeingLightColors,
            typography = Typography,
            content = content
        )
    }
}
