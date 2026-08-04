package com.example.beeing.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.beeing.R

// Rounded, friendly display face for NowTab's hero text (headline, "Beeing"
// title, Save/Lock CTA labels) — a deliberate, scoped accent, not a global
// typography swap. Baloo 2 ships as a single variable font (one file, a
// 'wght' axis) rather than separate static weight files, so the resource
// is pinned to one heavy instance; Text() calls using it should request
// FontWeight.Black/ExtraBold to match.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val BalooFontFamily = FontFamily(
    Font(
        R.font.baloo2,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

/**
 * **Fredoka** — the display face for the app's two big headers ("Beeing" and
 * "How was your hour?") and the save CTA.
 *
 * Chosen over Baloo 2 for those three because Baloo's only instance here is a
 * single 800-weight variable pin: every size renders at the same heavy weight,
 * so a 26sp question and a 15sp button label carry identical visual density.
 * Fredoka ships **real static cuts**, so this family holds three of them and
 * `FontWeight` actually selects one — Medium for the big quiet header, SemiBold
 * and Bold where the type needs to push. Its rounder terminals and wider
 * counters also stay legible at button size, where Baloo starts to close up.
 */
val FredokaFontFamily = FontFamily(
    Font(R.font.fredoka_500, FontWeight.Medium),
    Font(R.font.fredoka_600, FontWeight.SemiBold),
    Font(R.font.fredoka_700, FontWeight.Bold)
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)