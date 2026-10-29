package com.example.beeing.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.beeing.R

/**
 * **Plus Jakarta Sans** — the app's one typeface (SIL Open Font License 1.1,
 * static cuts from Google Fonts, bundled so it works offline). Five real
 * weights, so `FontWeight` selects an actual cut rather than faking one.
 *
 * Replaces Baloo 2 and Fredoka (2026-10-27 reskin): hierarchy now comes from
 * size and weight within one family, not from a second "fun" display face.
 */
val JakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_400, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_500, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_600, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_700, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_800, FontWeight.ExtraBold)
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = JakartaFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

// All fifteen M3 roles on Jakarta, so a plain Text() (bodyLarge via
// LocalTextStyle) and every M3 component inherit it. Display/headline are
// ExtraBold with slightly tight tracking; titles Bold; labels SemiBold; body Regular.
val Typography = Typography(
    displayLarge = style(57, 64, FontWeight.ExtraBold, -0.5),
    displayMedium = style(45, 52, FontWeight.ExtraBold, -0.5),
    displaySmall = style(36, 44, FontWeight.ExtraBold, -0.4),
    headlineLarge = style(32, 40, FontWeight.ExtraBold, -0.4),
    headlineMedium = style(28, 36, FontWeight.ExtraBold, -0.3),
    headlineSmall = style(24, 32, FontWeight.ExtraBold, -0.3),
    titleLarge = style(22, 28, FontWeight.Bold, -0.2),
    titleMedium = style(16, 24, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 16, FontWeight.SemiBold)
)
