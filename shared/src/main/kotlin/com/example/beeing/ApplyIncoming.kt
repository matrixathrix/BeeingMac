package com.example.beeing

import kotlin.math.abs

/** The phone's stores after taking in one watch rating. */
data class Applied(val ratings: List<RatingEntry>, val spends: List<Long>, val accepted: Boolean)

/**
 * [mergeIncoming] applied to the phone's ratings and reclaim spends. A Data Layer item
 * can arrive twice, so a rating the phone already holds (same `id`) changes nothing
 * and refunds nothing the second time.
 */
fun applyIncoming(
    incoming: RatingEntry, local: List<RatingEntry>, tombstones: List<Tombstone>, spends: List<Long>
): Applied {
    if (local.any { it.id == incoming.id }) return Applied(local, spends, true)
    return when (val m = mergeIncoming(incoming, local, tombstones)) {
        MergeResult.Drop -> Applied(local, spends, false)
        is MergeResult.Apply -> {
            val ratings = local.filter { it != m.replaces } + incoming
            val near = m.refundReclaimNear
            val refunded = if (near == null) spends else {
                val nearest = spends.minByOrNull { abs(it - near) }
                if (nearest == null) spends else spends - nearest
            }
            Applied(ratings, refunded, true)
        }
    }
}
