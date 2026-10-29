package com.example.beeing

// ============================================================
// WARNINGS  (pure — the bell's items are a filtered view of the streak events)
// ============================================================

data class Warning(
    val id: String,
    val timestamp: Long,
    val type: StreakEventType,
    val title: String,
    val detail: String
)

private const val MAX_WARNINGS = 30

internal val WARNING_TYPES = setOf(
    StreakEventType.SAVER_USED, StreakEventType.SAVER_FADED, StreakEventType.LOW_SAVERS,
    StreakEventType.COMEBACK_GIFT, StreakEventType.STREAK_BROKE,
    StreakEventType.RECOVERY_DAYS_LEFT, StreakEventType.RECOVERED
)

internal fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) one else many

/** Spec copy for the seven warning types. Anything else is not a warning. */
internal fun StreakEvent.warningTitle(): String = when (type) {
    StreakEventType.SAVER_USED -> "A streak saver kept your streak alive"
    StreakEventType.SAVER_FADED -> "A streak saver faded"
    StreakEventType.COMEBACK_GIFT -> "$COMEBACK_GIFT streak savers on the house"
    StreakEventType.LOW_SAVERS -> "Only $savers ${plural(savers, "saver")} left"
    StreakEventType.STREAK_BROKE -> "Your $streak-day streak broke"
    StreakEventType.RECOVERY_DAYS_LEFT ->
        "Only $daysLeft ${plural(daysLeft, "day")} left to win back your $streak-day streak"
    StreakEventType.RECOVERED -> "Streak recovered"
    else -> ""
}

internal fun StreakEvent.warningDetail(dayLabel: String): String = when (type) {
    StreakEventType.SAVER_USED ->
        "You missed $dayLabel and your streak still grew to $streak days. $savers ${plural(savers, "saver")} left."
    StreakEventType.SAVER_FADED -> "No streak was running on $dayLabel." +
        if (savers == 0) " That was your last saver." else " Start one today to keep the $savers you have left."
    StreakEventType.COMEBACK_GIFT -> "Rate a few hours today to start a new streak."
    StreakEventType.LOW_SAVERS -> "Rate $SUPER_DAY_HOURS hours and it's a Super day, which earns another."
    StreakEventType.STREAK_BROKE -> "Rate $RECOVERY_HOURS hours on one of the next $RECOVERY_DAYS days to win it back."
    StreakEventType.RECOVERY_DAYS_LEFT -> "Rate $RECOVERY_HOURS hours on one of them."
    StreakEventType.RECOVERED -> "Your $streak-day streak is back."
    else -> ""
}

/** Warnings disappear from the bell this long after they happened. */
const val WARNING_TTL_MS = 7L * 24 * 3_600_000L

/**
 * Newest first, at most 30, and only from the last 7 days (older ones expire).
 * `dayLabel` formats an event's day-start millis ("Mon 27 Oct").
 */
fun warningsFrom(events: List<StreakEvent>, baselineMs: Long, nowMs: Long, dayLabel: (Long) -> String): List<Warning> =
    events.asSequence()
        .filter { it.type in WARNING_TYPES && nowMs - it.timestamp <= WARNING_TTL_MS }
        .sortedByDescending { it.timestamp }
        .take(MAX_WARNINGS)
        .map { Warning(it.id, it.timestamp, it.type, it.warningTitle(), it.warningDetail(dayLabel(it.dayStartMs))) }
        .toList()

/** Items from before the baseline (first launch / last import) count as already read. */
fun unreadCount(warnings: List<Warning>, readIds: Set<String>, baselineMs: Long): Int =
    warnings.count { it.timestamp >= baselineMs && it.id !in readIds }
