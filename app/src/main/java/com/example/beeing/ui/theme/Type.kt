package com.example.beeing.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.beeing.R

/**
 * **Instrument** — two faces from one family (2026-10-31, the owner's pick of four
 * mockups; replaced Plus Jakarta Sans). Both SIL Open Font License 1.1 from
 * google/fonts, bundled so they work offline; licences in `assets/licenses`.
 *
 * **Instrument Sans** sets everything. It is one variable file: a `Font` asks for
 * its `FontWeight` and Android dials the `wght` axis to it, so these are real
 * weights, not faked ones. The face stops at 700, so ExtraBold renders at 700.
 */
val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans, FontWeight.Normal),
    Font(R.font.instrument_sans, FontWeight.Medium),
    Font(R.font.instrument_sans, FontWeight.SemiBold),
    Font(R.font.instrument_sans, FontWeight.Bold),
    Font(R.font.instrument_sans, FontWeight.ExtraBold)
)

/** The serif's one cut: Italic, weight 400. Never pair it with a heavier weight. */
val InstrumentSerifItalic = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

/**
 * **Instrument Serif Italic, for the reflective lines only**: the question, "All
 * caught up", large page titles, the celebration titles, the wordmark and the
 * dial's score word. Never numbers, buttons, chips or dialogs, and never under
 * 28sp. A condensed serif reads about 15% smaller than the sans at the same size,
 * so call sites size it up (`DisplaySerif.copy(fontSize = …, lineHeight = …)`).
 */
val DisplaySerif = TextStyle(
    fontFamily = InstrumentSerifItalic,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
    letterSpacing = (-0.3).sp
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = InstrumentSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

// All fifteen M3 roles on Instrument Sans, so a plain Text() (bodyLarge via
// LocalTextStyle) and every M3 component inherit it. Tracking is lighter than
// Jakarta's was: the face is narrower and already set fairly tight.
//
// THE WEIGHT SCALE (2026-10-31): weight is spent where the eye should land
// first, and only there, so nothing shouts at the same volume.
//   800 ExtraBold — hero numerals (the dial's score, the streak count, ring
//                   counts): display roles. Renders at 700, the face's heaviest.
//   700 Bold      — headings in the sans (headline roles) and the primary action (Save)
//   600 SemiBold  — panel titles and values (title roles, the hour range,
//                   selected tags, dial digits, small caps overlines)
//   500 Medium    — controls and supporting labels (label roles: chips, buttons,
//                   legends, captions, nav)
//   400 Regular   — reading text (body roles)
// The reflective headlines aren't on this scale: they are DisplaySerif.
val Typography = Typography(
    displayLarge = style(57, 64, FontWeight.ExtraBold, -0.3),
    displayMedium = style(45, 52, FontWeight.ExtraBold, -0.3),
    displaySmall = style(36, 44, FontWeight.ExtraBold, -0.2),
    headlineLarge = style(32, 40, FontWeight.Bold, -0.3),
    headlineMedium = style(28, 36, FontWeight.Bold, -0.3),
    headlineSmall = style(24, 32, FontWeight.Bold, -0.2),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium)
)
