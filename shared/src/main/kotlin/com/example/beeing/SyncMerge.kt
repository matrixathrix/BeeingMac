package com.example.beeing

// How the phone takes in a rating saved on the watch. Pure, so both the rules and
// their edge cases live in tests; the phone only persists the result.

/** Watch and phone clocks can drift a little; this much is forgiven at the window's edges. */
const val WATCH_CLOCK_SLACK_MS = 5 * 60_000L

private const val HOUR_MS = 3_600_000L

/**
 * True when [e] was saved (its `id`) while its hour was ratable: from the hour's end
 * until two hours after it, give or take [WATCH_CLOCK_SLACK_MS]. Whether a rating counts
 * is decided by when it was saved, never by when it reached the phone.
 */
fun savedInTheMoment(e: RatingEntry): Boolean =
    e.id >= e.timestamp + HOUR_MS - WATCH_CLOCK_SLACK_MS && e.id < e.timestamp + 3 * HOUR_MS + WATCH_CLOCK_SLACK_MS

sealed interface MergeResult {
    /** Store the incoming rating, removing [replaces]; refund the reclaim spend nearest [refundReclaimNear]. */
    data class Apply(val replaces: RatingEntry?, val refundReclaimNear: Long?) : MergeResult
    data object Drop : MergeResult
}

/**
 * The later save wins (owner decision); a tie goes to the phone. A live rating always
 * beats an "Added later" one for the same hour, and gives back that day's reclaim. A
 * deletion newer than the save keeps the hour deleted.
 */
fun mergeIncoming(incoming: RatingEntry, local: List<RatingEntry>, tombstones: List<Tombstone>): MergeResult {
    if (!savedInTheMoment(incoming)) return MergeResult.Drop
    if (tombstones.any { it.hourStartMs == incoming.timestamp && it.deletedAtMs >= incoming.modifiedAt }) {
        return MergeResult.Drop
    }
    val existing = local.firstOrNull { it.timestamp == incoming.timestamp } ?: return MergeResult.Apply(null, null)
    return when {
        existing.isReclaimed -> MergeResult.Apply(existing, existing.id)
        existing.modifiedAt >= incoming.modifiedAt -> MergeResult.Drop
        else -> MergeResult.Apply(existing, null)
    }
}
