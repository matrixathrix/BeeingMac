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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
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
    // Deep honey: M3 draws TextButtons, switches and checkboxes in `primary`,
    // and plain honey on white is ~2:1. Big fills use BeeExtras.accentFill.
    primary = HoneyDeep,
    onPrimary = Color.White,
    primaryContainer = Honey.copy(alpha = 0.16f).compositeOn(CardLight),
    onPrimaryContainer = OnHoney,
    secondary = BandTopLight,
    onSecondary = Color.White,
    // Implicit roles (FilledTonalButton, snackbar action) — kept on palette.
    secondaryContainer = MutedLight,
    onSecondaryContainer = InkLight,
    inversePrimary = Honey,
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
    secondaryContainer = MutedDark,
    onSecondaryContainer = InkDark,
    inversePrimary = Honey,
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

/**
 * What a [ColorScheme] can't hold: accent-as-text, the honey fill, and the
 * **night field** roles — the surface under the Now and Streak tabs and
 * everything that sits on it. No composable on the field hard-codes a colour.
 */
data class BeeExtras(
    val accentText: Color,
    // The big "commit" fill (Save, selected chips, active segments) — always
    // honey, even where `primary` is deepened for text legibility.
    val accentFill: Color,
    val onAccentFill: Color,
    val cardShadow: Boolean,

    /** The field itself: a radial glow at the top, 120% of the width across, 70% of the height deep. */
    val fieldBrush: Brush,
    val fieldTop: Color,
    val fieldBottom: Color,
    /** True when the field is dark (status-bar icons go light). */
    val fieldDark: Boolean,
    /** Primary text and icons on the field. */
    val fieldInk: Color,
    /** Secondary text on the field: ink at 60% (dark) / 70% (light). */
    val fieldInkMuted: Color,
    /** Honey as text on the field (countdown, active nav label, bee prompt). */
    val fieldHoney: Color,
    /** Glass: every container on the field (cells, chips, panels, the bell). No shadows. */
    val glassFill: Color,
    val glassLine: Color,
    /** The nav bar while it sits over the field. */
    val navOnField: Color,
    /** At-risk / missed / broken on the field. */
    val fieldRed: Color,
    /** Super day marks (rims, sparks, the outer lap). */
    val fieldSuper: Color,
    /** "Super day" as text on the field. */
    val fieldSuperText: Color,
    /** "Day secured" green as text on the field. */
    val fieldDone: Color
)

/**
 * The field's radial glow: centred at the top, an ellipse 120% of the width
 * across and 70% of the height deep. A gradient's shader can only be circular,
 * so it is drawn as a circle of the horizontal radius squashed vertically by its
 * local matrix. Rebuilt only when the size changes (ShaderBrush caches by size).
 */
private class FieldBrush(private val top: Color, private val mid: Color, private val bottom: Color) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val cx = size.width / 2f
        val rx = (size.width * 1.2f).coerceAtLeast(1f)
        val ry = (size.height * 0.7f).coerceAtLeast(1f)
        return android.graphics.RadialGradient(
            cx, 0f, rx,
            intArrayOf(top.toArgb(), mid.toArgb(), bottom.toArgb()),
            floatArrayOf(0f, 0.45f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        ).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setScale(1f, ry / rx, cx, 0f) })
        }
    }

    override fun equals(other: Any?) =
        other is FieldBrush && other.top == top && other.mid == mid && other.bottom == bottom

    override fun hashCode() = (top.hashCode() * 31 + mid.hashCode()) * 31 + bottom.hashCode()
}

val BeeLightExtras = BeeExtras(
    accentText = AccentTextLight,
    accentFill = Honey,
    onAccentFill = OnHoney,
    cardShadow = true,
    fieldBrush = FieldBrush(FieldTopLight, FieldMidLight, FieldBottomLight),
    fieldTop = FieldTopLight,
    fieldBottom = FieldBottomLight,
    fieldDark = false,
    fieldInk = FieldInkLight,
    fieldInkMuted = FieldInkLight.copy(alpha = 0.70f),
    fieldHoney = FieldHoneyLight,
    glassFill = Color.White.copy(alpha = 0.70f),
    glassLine = FieldInkLight.copy(alpha = 0.07f),
    navOnField = NavOnFieldLight,
    fieldRed = FieldRedLight,
    fieldSuper = FieldSuperLight,
    fieldSuperText = FieldSuperTextLight,
    fieldDone = FieldDoneLight
)

val BeeDarkExtras = BeeExtras(
    accentText = AccentTextDark,
    accentFill = Honey,
    onAccentFill = OnHoney,
    cardShadow = false,
    fieldBrush = FieldBrush(FieldTopDark, FieldMidDark, FieldBottomDark),
    fieldTop = FieldTopDark,
    fieldBottom = FieldBottomDark,
    fieldDark = true,
    fieldInk = Color.White,
    fieldInkMuted = Color.White.copy(alpha = 0.60f),
    fieldHoney = Honey,
    glassFill = Color.White.copy(alpha = 0.065f),
    glassLine = Color.White.copy(alpha = 0.085f),
    navOnField = NavOnFieldDark,
    fieldRed = FieldRedDark,
    fieldSuper = SuperGold,
    fieldSuperText = SuperGold,
    fieldDone = RingQualifiedGreen
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
            // System-bar icons simply follow the theme on every tab: the night field
            // is dark in dark mode and light in light mode, and so is the page.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
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
