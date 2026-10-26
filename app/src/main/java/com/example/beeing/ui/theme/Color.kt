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
val BandTopDark = Color(0xFF2C1030)
val BandBottomDark = Color(0xFF1C0920)

// Accent used as text/icon on the page (darker in light mode to read on white).
val AccentTextLight = Color(0xFFBB7A09)   // spec's #C7820A was 2.9:1 on the page; nudged to clear 3:1
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

