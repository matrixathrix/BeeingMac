package com.example.beeing

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The phone <-> watch sync over the Wear Data Layer (DataClient only: it holds items
// until the other side is reachable, for days if need be).
//  - Watch -> phone: one item per save at PATH_RATING_PREFIX + hourStartMs, and the
//    charging flag at PATH_CHARGING.
//  - Phone -> watch: one PhoneState at PATH_STATE, replaced on every change.

const val PATH_STATE = "/state"
const val PATH_RATING_PREFIX = "/rating/"
const val PATH_CHARGING = "/charging"
const val CAPABILITY_WATCH = "beeing_watch"

/** What the phone tells the watch. */
@Serializable
data class PhoneState(
    val syncedAtMs: Long,
    val tags: List<String>,              // the user's full list, in their order
    val topTags: List<String>,           // the 8 the watch shows first (see topTags)
    val modeEvents: List<ModeEvent>,
    val prefs: NotificationPrefs,        // silent hours and pause, for the watch's own reminders
    val carry: ReplayCarry,              // the replay at the end of the day before yesterday
    val recentRatings: List<RatingEntry>, // yesterday + today, every origin
    val mergedWatchIds: List<Long>       // watch ratings the phone has applied (last 2 days)
)

// Lenient on purpose: a newer app on one side may send fields the other doesn't know.
private val json = Json { ignoreUnknownKeys = true }

fun encodeState(s: PhoneState): ByteArray = json.encodeToString(PhoneState.serializer(), s).toByteArray(Charsets.UTF_8)

fun decodeState(b: ByteArray): PhoneState? = orNull { json.decodeFromString(PhoneState.serializer(), String(b, Charsets.UTF_8)) }

fun encodeRating(e: RatingEntry): ByteArray = json.encodeToString(RatingEntry.serializer(), e).toByteArray(Charsets.UTF_8)

fun decodeRating(b: ByteArray): RatingEntry? = orNull { json.decodeFromString(RatingEntry.serializer(), String(b, Charsets.UTF_8)) }

/** Malformed input is dropped, never thrown at a listener service. */
private inline fun <T> orNull(parse: () -> T): T? =
    try { parse() } catch (_: SerializationException) { null } catch (_: IllegalArgumentException) { null }
