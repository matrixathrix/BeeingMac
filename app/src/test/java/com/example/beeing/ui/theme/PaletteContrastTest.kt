package com.example.beeing.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text-on-colour pairing the reskin relies on stays readable, in both
 * modes. WCAG 2.x thresholds: 4.5 for body text, 3.0 for large/bold UI text.
 */
class PaletteContrastTest {

    private val schemes = mapOf("light" to BeeingLightColors, "dark" to BeeingDarkColors)
    private val extras = mapOf("light" to BeeLightExtras, "dark" to BeeDarkExtras)

    private fun assertContrast(min: Double, fg: Color, bg: Color, what: String) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$what: contrast $ratio < $min", ratio >= min)
    }

    private fun eachScheme(block: (String, ColorScheme) -> Unit) = schemes.forEach { (n, s) -> block(n, s) }

    @Test
    fun onPrimary_on_primary_is_readable() = eachScheme { n, s ->
        assertContrast(4.5, s.onPrimary, s.primary, "$n onPrimary/primary")
    }

    @Test
    fun body_text_on_card_and_page_is_high_contrast() = eachScheme { n, s ->
        assertContrast(7.0, s.onSurface, s.surface, "$n onSurface/surface")
        assertContrast(7.0, s.onBackground, s.background, "$n onBackground/background")
    }

    @Test
    fun secondary_text_on_card_is_readable() = eachScheme { n, s ->
        assertContrast(4.5, s.onSurfaceVariant, s.surface, "$n onSurfaceVariant/surface")
    }

    @Test
    fun accent_text_on_page_meets_ui_contrast() = extras.forEach { (n, x) ->
        // Used for 12–15sp labels (nav, bee prompt): normal-size text, so 4.5.
        assertContrast(4.5, x.accentText, schemes.getValue(n).background, "$n accentText/background")
    }

    // The full-screen celebrations still sit on the aubergine band colours.
    @Test
    fun white_on_band_is_high_contrast() {
        listOf(BandTopLight, BandBottomLight, BandTopDark, BandBottomDark).forEach { band ->
            assertContrast(7.0, Color.White, band, "white/band $band")
        }
    }

    // The journal's "Super day" label (12sp SemiBold) sits on a day card: small text, so 4.5.
    @Test
    fun accent_text_on_card_is_readable() = extras.forEach { (n, x) ->
        assertContrast(4.5, x.accentText, schemes.getValue(n).surface, "$n accentText/surface")
    }

    // The at-risk streak count (flame, number and "!") sits on the band.
    @Test
    fun at_risk_red_reads_on_band() {
        listOf(BandTopLight, BandBottomLight, BandTopDark, BandBottomDark).forEach { band ->
            assertContrast(3.0, StreakAtRiskRed, band, "at-risk red on band $band")
        }
    }

    // The Super day outer lap, its numerals and overlines sit on the aubergine field.
    @Test
    fun super_gold_reads_on_the_band() {
        listOf(BandTopLight, BandBottomLight, BandTopDark, BandBottomDark).forEach { band ->
            assertContrast(4.5, SuperGold, band, "SuperGold on band $band")
        }
    }

    // M3 draws TextButtons (every dialog's Cancel/OK), switches and checkboxes
    // in `primary`, so primary must read as text on a card.
    @Test
    fun primary_as_text_on_card_is_readable() = eachScheme { n, s ->
        assertContrast(4.5, s.primary, s.surface, "$n primary/surface")
    }

    // Roles M3 components read implicitly (FilledTonalButton → secondaryContainer,
    // snackbar action → inversePrimary) must not fall back to baseline purple.
    @Test
    fun implicit_roles_stay_on_palette() = eachScheme { n, s ->
        assertEquals("$n secondaryContainer", s.surfaceVariant, s.secondaryContainer)
        assertEquals("$n onSecondaryContainer", s.onSurface, s.onSecondaryContainer)
        assertEquals("$n inversePrimary", Color(0xFFF5A623), s.inversePrimary)
    }

    // ---- The night field (Now and Streak) ----
    // Text sits on the field itself and on glass panels in it. The glow (top) is the
    // worst case for dark ink in light mode and the best in dark mode, so every
    // pairing is checked at both ends of the field and on glass over the glow.

    private fun Color.over(bg: Color): Color = Color(
        red = red * alpha + bg.red * (1 - alpha),
        green = green * alpha + bg.green * (1 - alpha),
        blue = blue * alpha + bg.blue * (1 - alpha)
    )

    /** Every opaque background text meets on the field: the glow, the bottom, glass over each. */
    private fun fieldGrounds(x: BeeExtras) = listOf(
        x.fieldTop, x.fieldBottom, x.glassFill.over(x.fieldTop), x.glassFill.over(x.fieldBottom)
    )

    @Test
    fun field_ink_is_high_contrast() = extras.forEach { (n, x) ->
        fieldGrounds(x).forEach { bg -> assertContrast(7.0, x.fieldInk, bg, "$n fieldInk/$bg") }
    }

    @Test
    fun field_muted_ink_is_readable() = extras.forEach { (n, x) ->
        fieldGrounds(x).forEach { bg -> assertContrast(4.5, x.fieldInkMuted.over(bg), bg, "$n fieldInkMuted/$bg") }
    }

    // The countdown, the bee prompt and the active nav label: 13-17sp, so 4.5.
    @Test
    fun field_honey_text_is_readable() = extras.forEach { (n, x) ->
        fieldGrounds(x).forEach { bg -> assertContrast(4.5, x.fieldHoney, bg, "$n fieldHoney/$bg") }
        assertContrast(4.5, x.fieldHoney, x.navOnField, "$n fieldHoney/navOnField")
        assertContrast(4.5, x.fieldInkMuted.over(x.navOnField), x.navOnField, "$n nav idle")
    }

    // Captions on the field: at-risk / missed red, "Super day", "Day secured".
    @Test
    fun field_status_text_is_readable() = extras.forEach { (n, x) ->
        fieldGrounds(x).forEach { bg ->
            assertContrast(4.5, x.fieldRed, bg, "$n fieldRed/$bg")
            assertContrast(4.5, x.fieldSuperText, bg, "$n fieldSuperText/$bg")
            assertContrast(4.5, x.fieldDone, bg, "$n fieldDone/$bg")
        }
    }

    // Disabled Save: its honey-as-text label on the low-opacity honey wash.
    @Test
    fun disabled_save_label_is_readable() = extras.forEach { (n, x) ->
        listOf(x.fieldTop, x.fieldBottom).forEach { bg ->
            val wash = x.accentFill.copy(alpha = 0.18f).over(bg)
            assertContrast(4.5, x.fieldHoney, wash, "$n disabled Save on $bg")
        }
    }

    // The selected dial cell and the rated core: score bands with the digit flipped on lightness.
    @Test
    fun dial_digits_read_on_their_score_band() {
        (1..10).forEach { score ->
            assertContrast(4.5, com.example.beeing.dialDigitColor(score), com.example.beeing.scoreBandColor(score), "dial $score")
        }
    }

    @Test
    fun accent_fill_is_fixed_honey_not_dynamic() = extras.forEach { (n, x) ->
        assertEquals("$n accentFill", Color(0xFFF5A623), x.accentFill)
        assertContrast(4.5, x.onAccentFill, x.accentFill, "$n onAccentFill/accentFill")
    }
}
