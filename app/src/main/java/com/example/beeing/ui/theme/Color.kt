package com.example.beeing.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// "NIGHT BLOOM" — Beeing's own palette (no Material You dynamic colour).
// Spec: docs/superpowers/specs/2026-10-27-reskin-design.md
// ============================================================

// The one accent: "do this" — Save, selected chips, the active nav tab.
val Honey = Color(0xFFF5A623)
val OnHoney = Color(0xFF2E0F33)          // aubergine ink: white on honey fails contrast
val HoneyDeep = Color(0xFF9A6000)        // light-mode `primary`: honey that reads as text on white

// Brand band gradient (top → bottom), per mode.
val BandTopLight = Color(0xFF4A1D4F)
val BandBottomLight = Color(0xFF2E0F33)
val BandTopDark = Color(0xFF3F1947)     // lifted 2026-10-29: the old #2C1030 read as a muddy blob on the near-black page
val BandBottomDark = Color(0xFF2A0F31)

// Accent used as text/icon on the page (darker in light mode to read on white).
val AccentTextLight = Color(0xFF9A6000)   // = HoneyDeep: 4.5:1+ on the page for 12–15sp labels
val AccentTextDark = Color(0xFFF7B733)

// Light mode
val PageLight = Color(0xFFF7F5F2)
val CardLight = Color(0xFFFFFFFF)
val MutedLight = Color(0xFFF1EEEA)
val InkLight = Color(0xFF1B1A1C)
val InkSoftLight = Color(0xFF76717A)
val HairlineLight = Color(0xFFEEE9E4)

// Dark mode
val PageDark = Color(0xFF110E12)
val CardDark = Color(0xFF1D1A1F)
val MutedDark = Color(0xFF2A2630)
val InkDark = Color(0xFFECE7EA)
val InkSoftDark = Color(0xFF9C969B)
val HairlineDark = Color(0xFF2A2630)

// Hive identity — streak/ring accents, not score bands (see scoreBandColor).
val HoneyGold = Color(0xFFFFB300)
val RingQualifiedGreen = Color(0xFF66BB6A)

// The streak count while a recovery window is open: light enough to read on the
// aubergine band (the 1-4 score red is too dark there).
val StreakAtRiskRed = Color(0xFFFF6B6B)


// Super day: the outer lap (hours 9-12) and its accents. Lighter than HoneyGold so
// the two laps read apart; the inner ring stays HoneyGold.
val SuperGold = Color(0xFFFFD54F)

// ============================================================
// THE NIGHT FIELD — the one surface under the Now and Streak tabs.
// Spec: docs/superpowers/specs/2026-10-31-night-field-design.md
// A radial glow at the top fading down; light mode is the same field lifted to a
// soft violet. Text and accents on it get their own values: the page's ones fail
// contrast on the violet glow (PaletteContrastTest pins every pairing).
// ============================================================

// Field stops: glow (top) · 45% · bottom
val FieldTopDark = Color(0xFF4A1D53)
val FieldMidDark = Color(0xFF2E1135)
val FieldBottomDark = Color(0xFF1A0A1E)
val FieldTopLight = Color(0xFFDCCDF6)
val FieldMidLight = Color(0xFFECE4FA)
val FieldBottomLight = Color(0xFFF8F5FD)

// Primary ink on the light field (violet-black); dark mode uses white.
val FieldInkLight = Color(0xFF24123A)

// Honey as TEXT on the field. Light needs #7A4A00: the page's #9A6000 is 3.5:1 on the glow.
val FieldHoneyLight = Color(0xFF7A4A00)

// Red / gold / green as text and marks on the light field (the dark-mode values,
// StreakAtRiskRed / SuperGold / RingQualifiedGreen, all wash out on violet).
val FieldRedLight = Color(0xFFA4161A)
val FieldSuperLight = Color(0xFFC99A00)       // rims, sparks, the lap: a mark, not text
val FieldSuperTextLight = Color(0xFF6E5300)   // "Super day" captions
val FieldDoneLight = Color(0xFF1B5E20)

// At-risk / missed red on the dark field: StreakAtRiskRed lifted, since #FF6B6B
// reaches only 3.9:1 on glass over the glow.
val FieldRedDark = Color(0xFFFF8A80)

// The nav bar over the field: its bottom colour with the mockup's tint baked in
// (dark: aubergine-black at 35%, light: white at 55%), so the bar is opaque but
// continuous with the field above it.
val NavOnFieldDark = Color(0xFF140818)
val NavOnFieldLight = Color(0xFFFCFAFE)
