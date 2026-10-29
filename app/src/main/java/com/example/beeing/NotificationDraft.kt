package com.example.beeing

import android.content.Context
import java.util.Calendar

// ============================================================
// NOTIFICATION DRAFT  (the score + tags picked on the hourly notification)
// ============================================================
//
// RemoteViews can't hold state, so each tap on the notification is a broadcast
// that updates this draft and re-posts the notification with the selection
// highlighted. Submit saves it as an ordinary in-the-moment rating.

private const val QUICK_TAG_WINDOW_MS = 30L * 24 * 3_600_000L
private const val HOUR_MS = 3_600_000L
private const val DRAFT_KEY = "notif_draft"

/** A draft belongs to one rated hour (its start timestamp). */
data class NotifDraft(val targetTs: Long, val score: Int?, val tags: List<String>) {
    /** Same rule as in-app: a score and at least one tag. */
    val canSubmit: Boolean get() = score != null && tags.isNotEmpty()

    fun toggleTag(tag: String) = copy(tags = if (tag in tags) tags - tag else tags + tag)

    fun serialize(): String = "$targetTs|${score ?: ""}|${tags.joinToString(";")}"
}

fun parseDraft(s: String): NotifDraft? {
    val parts = s.split("|")
    if (parts.size != 3) return null
    val ts = parts[0].toLongOrNull() ?: return null
    return NotifDraft(ts, parts[1].toIntOrNull(), parts[2].split(";").filter { it.isNotBlank() })
}

/** The stored draft if it is for this hour, otherwise a fresh one. */
fun draftFor(stored: NotifDraft?, targetTs: Long): NotifDraft =
    if (stored != null && stored.targetTs == targetTs) stored else NotifDraft(targetTs, null, emptyList())

fun loadNotifDraft(ctx: Context): NotifDraft? =
    parseDraft(ctx.getSharedPreferences("b", 0).getString(DRAFT_KEY, "") ?: "")

fun saveNotifDraft(ctx: Context, d: NotifDraft) {
    ctx.getSharedPreferences("b", 0).edit().putString(DRAFT_KEY, d.serialize()).apply()
}

fun clearNotifDraft(ctx: Context) {
    ctx.getSharedPreferences("b", 0).edit().remove(DRAFT_KEY).apply()
}

/**
 * The tags offered on the notification: the [n] most used in the last 30 days
 * (ties in picker order), among tags that still exist. With no history it is just
 * the first [n] of the picker. The reclaim sentinel can never appear.
 */
fun quickTags(ratings: List<RatingEntry>, allTags: List<String>, nowMs: Long, n: Int = 6): List<String> {
    val counts = HashMap<String, Int>()
    for (e in ratings) {
        if (nowMs - e.timestamp > QUICK_TAG_WINDOW_MS) continue
        for (t in e.visibleTags()) counts[t] = (counts[t] ?: 0) + 1
    }
    return allTags.withIndex()
        .sortedWith(compareByDescending<IndexedValue<String>> { counts[it.value] ?: 0 }.thenBy { it.index })
        .map { it.value }
        .take(n)
}

/** Only the last two completed hours can be rated; a stale notification must not save. */
fun hourStillRatable(targetTs: Long, nowMs: Long): Boolean {
    val currentHourStart = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return targetTs in (currentHourStart - 2 * HOUR_MS)..(currentHourStart - HOUR_MS)
}

/** "14th" for 1-2 PM: the ordinal of the hour's END, 24 for the hour before midnight. */
fun hourLabelFor(targetTs: Long): String {
    val start = Calendar.getInstance().apply { timeInMillis = targetTs }.get(Calendar.HOUR_OF_DAY)
    val end = start + 1
    val suffix = when {
        end in 11..13 -> "th"
        end % 10 == 1 -> "st"
        end % 10 == 2 -> "nd"
        end % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$end$suffix"
}
