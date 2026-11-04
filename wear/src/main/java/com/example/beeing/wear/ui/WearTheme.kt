package com.example.beeing.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.example.beeing.wear.R

// The phone's dark "night field", for a screen that is always dark. Honey is
// "act here"; score colours are data and always ship with their digit.

val FieldTop = Color(0xFF4A1D53)
val FieldBottom = Color(0xFF1A0A1E)
val Ink = Color(0xFFF3ECF6)
val InkMuted = Color(0xFFB9A9CC)
val Honey = Color(0xFFF5A623)
val OnHoney = Color(0xFF2E0F33)
val GlassFill = Color(0x1AF3ECF6)
val GlassLine = Color(0x38F3ECF6)
val AtRiskRed = Color(0xFFFF8A80)
val SuperGold = Color(0xFFFFD54F)

/** The phone's score bands: 1–4 red, 5–7 amber, 8–10 green. */
fun scoreColor(score: Int): Color = when {
    score >= 8 -> Color(0xFF66BB6A)
    score >= 5 -> Color(0xFFFFB300)
    else -> Color(0xFFB71C1C)
}

/** A score as text on the dark field (the phone's ScoreText*Dark): the fill red is too dark to read. */
fun scoreTextColor(score: Int): Color = when {
    score >= 8 -> Color(0xFF81C784)
    score >= 5 -> Color(0xFFFFCA28)
    else -> Color(0xFFFF8A80)
}

/** Readable ink on a score fill (white on the dark red, near-black on amber and green). */
fun onScoreColor(score: Int): Color = if (score <= 4) Color.White else Color(0xFF1A0A1E)

@OptIn(ExperimentalTextApi::class)
private fun instrument(weight: Int) = Font(
    R.font.instrument_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Instrument = FontFamily(instrument(400), instrument(500), instrument(600), instrument(700))

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = Honey, onPrimary = OnHoney,
            background = FieldBottom, onBackground = Ink,
            surfaceContainer = GlassFill, onSurface = Ink, onSurfaceVariant = InkMuted,
            error = AtRiskRed
        ),
        typography = Typography(defaultFontFamily = Instrument),
        content = content
    )
}
