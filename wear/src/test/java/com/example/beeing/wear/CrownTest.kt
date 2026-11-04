package com.example.beeing.wear

import com.example.beeing.wear.ui.Crown
import com.example.beeing.wear.ui.applyCrown
import org.junit.Assert.assertEquals
import org.junit.Test

class CrownTest {
    private val step = 36f

    @Test fun a_fast_turn_moves_every_detent() =
        assertEquals(Crown(8, 0f), applyCrown(5, 3 * step, step))

    @Test fun the_first_detent_from_unrated_lands_on_five() {
        assertEquals(Crown(5, 0f), applyCrown(null, step, step))
        assertEquals(Crown(5, 0f), applyCrown(null, -step, step))
    }

    @Test fun clamps_at_the_ends_and_keeps_the_remainder() {
        assertEquals(Crown(10, 10f), applyCrown(9, 3 * step + 10f, step))
        assertEquals(Crown(1, 0f), applyCrown(2, -4 * step, step))
    }

    @Test fun under_a_detent_changes_nothing() =
        assertEquals(Crown(5, 20f), applyCrown(5, 20f, step))
}
