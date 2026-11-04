package com.example.beeing

// The watch's rules, all pure: which hours wait, which tags show first, and the
// numbers its screens draw. The watch resumes the phone's replay rather than
// approximating it, so after a sync nothing it showed changes.

private const val DAY_MS = 24 * 3_600_000L
private const val TOP_TAG_DAYS = 30

/** Unrated ratable hour starts at [nowMs], the one about to expire first. */
fun waitingHours(nowMs: Long, ratedHourStarts: Set<Long>): List<Long> =
    ratableHourStarts(nowMs).filter { it !in ratedHourStarts }.sorted()

/**
 * The [n] tags used most over the last 30 days (the reclaim sentinel never counts),
 * shown in the user's own order so chips don't move around; when fewer than [n] were
 * used, the rest come from the top of the user's list.
 */
fun topTags(ratings: List<RatingEntry>, ordered: List<String>, nowMs: Long, n: Int = 8): List<String> {
    val since = nowMs - TOP_TAG_DAYS * DAY_MS
    val uses = HashMap<String, Int>()
    for (e in ratings) if (e.timestamp >= since) for (t in e.visibleTags()) uses[t] = (uses[t] ?: 0) + 1
    val rank = ordered.withIndex().filter { (uses[it.value] ?: 0) > 0 }
        .sortedWith(compareByDescending<IndexedValue<String>> { uses[it.value] ?: 0 }.thenBy { it.index })
        .take(n).map { it.value }.toMutableSet()
    for (t in ordered) { if (rank.size >= n) break; rank.add(t) }
    return ordered.filter { it in rank }
}

/** Everything a watch screen, the Tile or a complication draws. */
data class WatchView(
    val setUp: Boolean,
    val waiting: List<Long>,
    val todayHours: Int,
    val goal: Int,
    val streak: Int,
    val atRisk: Boolean,
    val paused: Boolean,
    val beginner: Boolean,
    val savers: Int,
    val confirmed: Boolean   // false while saves made here still wait for the phone
)

/** The phone's copy of each hour, with this watch's own saves on top. */
fun mergedRatings(state: PhoneState, local: List<RatingEntry>): List<RatingEntry> {
    val mine = local.mapTo(HashSet()) { it.timestamp }
    return state.recentRatings.filter { it.timestamp !in mine } + local
}

fun watchView(state: PhoneState?, local: List<RatingEntry>, nowMs: Long): WatchView {
    if (state == null) return WatchView(false, emptyList(), 0, STREAK_HOURS_REQUIRED, 0, false, false, false, 0, true)
    val merged = mergedRatings(state, local)
    val s = computeStreakStateFrom(state.carry, merged, state.modeEvents, nowMs)
    return WatchView(
        setUp = true,
        waiting = waitingHours(nowMs, merged.mapTo(HashSet()) { it.timestamp }),
        todayHours = s.todayHours,
        goal = s.hoursRequired,
        streak = s.displayStreak,
        atRisk = s.recoveryOpen,
        paused = s.streakPaused,
        beginner = s.beginnerMode,
        savers = s.savers,
        confirmed = local.all { it.id in state.mergedWatchIds }
    )
}
