package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Test

class RatingFrameGapsTest {

    private fun assertGaps(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 0.01f) }
    }

    @Test
    fun theNormalSpareHeightGivesTheNormalGaps() =
        assertGaps(listOf(24f, 24f, 12f, 24f), shareRatingGaps(84f))

    @Test
    fun theMinimumSpareHeightGivesTheMinimumGaps() =
        assertGaps(listOf(16f, 16f, 8f, 16f), shareRatingGaps(56f))

    @Test
    fun gapsNeverGoBelowTheirMinimums() =
        assertGaps(listOf(16f, 16f, 8f, 16f), shareRatingGaps(20f))

    @Test
    fun extraHeightKeepsTheRatioBetweenTheGaps() =
        assertGaps(listOf(32f, 32f, 16f, 32f), shareRatingGaps(112f))

    @Test
    fun theGapsUseExactlyTheSpareHeight() =
        assertEquals(70.6f, shareRatingGaps(70.6f).sum(), 0.01f)
}
