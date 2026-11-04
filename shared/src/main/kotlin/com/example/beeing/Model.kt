package com.example.beeing

/** Which device saved a rating. The phone never celebrates a watch-saved hour again. */
enum class Origin { PHONE, WATCH }

data class RatingEntry(
    val id: Long,
    val score: Int,
    val timestamp: Long,
    val hourLabel: String,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val modifiedAt: Long = id,          // last save; "the later save wins" when the watch syncs
    val origin: Origin = Origin.PHONE
)

/** A deleted hour, kept long enough that an old watch copy can't bring it back. */
data class Tombstone(val hourStartMs: Long, val deletedAtMs: Long)

const val TOMBSTONE_TTL_MS = 7 * 24 * 3_600_000L
