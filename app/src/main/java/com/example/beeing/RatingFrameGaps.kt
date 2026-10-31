package com.example.beeing

/**
 * The rating frame's four stretchable gaps, in dp and in order: hour line → dial,
 * dial → tags, tags → note, note → Save. [RATING_GAP_BASE] is the rhythm the
 * owner approved (exactly what the owner's phone gets at density 512);
 * [RATING_GAP_MIN] is where a gap stops shrinking and a tag row folds instead.
 */
internal val RATING_GAP_BASE = listOf(24f, 24f, 12f, 24f)
internal val RATING_GAP_MIN = listOf(16f, 16f, 8f, 16f)

/**
 * Shares [spare] dp (the frame's height minus the dial, tags and note) between
 * the four gaps in proportion to their base sizes, never below their minimums.
 *
 * Tags come in 40dp rows, so the leftover after the last row that fits can be
 * anything from 0 to 40dp, and it changes with screen zoom (the status and nav
 * bars are in system dp; see designDensity) and aspect ratio. Spread like this
 * the gaps keep their ratios on every screen and none moves more than ~11dp,
 * instead of one gap swallowing the whole 40.
 */
internal fun shareRatingGaps(spare: Float): List<Float> {
    val extra = (spare - RATING_GAP_MIN.sum()).coerceAtLeast(0f)
    val weight = RATING_GAP_BASE.sum()
    return RATING_GAP_MIN.zip(RATING_GAP_BASE) { min, base -> min + extra * base / weight }
}
