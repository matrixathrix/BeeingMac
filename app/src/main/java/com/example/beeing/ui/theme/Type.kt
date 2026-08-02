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