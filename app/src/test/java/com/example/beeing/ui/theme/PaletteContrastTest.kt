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

    @Test
    fun white_on_band_is_high_contrast() = extras.forEach { (n, x) ->
        assertContrast(7.0, Color.White, x.bandTop, "$n white/bandTop")
        assertContrast(7.0, Color.White, x.bandBottom, "$n white/bandBottom")
    }

    // The at-risk streak count (flame, number and "!") sits on the band.
    
    fun at_risk_red_reads_on_band() {
        listOf(BandTopLight, BandBottomLight, BandTopDark, BandBottomDark).forEach { band ->
            assertContrast(3.0, StreakAtRiskRed, band, "at-risk red on band $band")
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

    @Test
    fun accent_fill_is_fixed_honey_not_dynamic() = extras.forEach { (n, x) ->
        assertEquals("$n accentFill", Color(0xFFF5A623), x.accentFill)
        assertContrast(4.5, x.onAccentFill, x.accentFill, "$n onAccentFill/accentFill")
    }
}
