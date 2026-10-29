package com.example.beeing

import java.util.Calendar

// ============================================================
// STREAK RULES  (pure logic — no Context, no Compose, JVM-testable)
// ============================================================
//
// `replay` is the single walk over history. The streak meter, calendar, log,
// warnings bell, celebrations and background alerts are all readers of what it
// returns, so none of them can disagree. Savers, breaks and recoveries are
// always replayed from ratings, never kept as a running counter.

const val STREAK_HOURS_REQUIRED = 8   // counted hours for a day to be complete
const val BEGINNER_HOURS_REQUIRED = 4 // ...and the softer day-one goal in beginner mode
const val BEGINNER_GRADUATION_DAYS = 3 // completed beginner days before the one-time nudge
const val SAVER_START = 2             // every history begins with this many savers
const val SAVER_CAP = 5
const val SAVER_EARN_HOURS = 12       // a Master day this long earns one saver
const val COMEBACK_IDLE_DAYS = 7      // days in a row with no complete day => gift
const val COMEBACK_GIFT = 2
const val RECOVERY_DAYS = 3           // calendar days after a break
const val RECOVERY_HOURS = 10         // hours on one of them that win the streak back
const val RECLAIM_WINDOW_HOURS = 12   // how far back a past hour can be added (clock hours)
const val RECLAIM_PER_DAY = 2         // past hours a calendar day may add
// Stored sentinel inside persisted entries — the UI shows a badge instead, but the
// stored string must never change or old backdated entries stop being recognized.
const val RECLAIM_TAG = "💧reclaimed"

/** True when this entry was added later ("Add a past hour"). It never counts toward a streak. */
val RatingEntry.isReclaimed: Boolean get() = RECLAIM_TAG in tags

/** One flip of the mode toggle. `enteredBeginner == false` means "back to Master". */
data class ModeEvent(val timestamp: Long, val enteredBeginner: Boolean)

/** The mode in effect right now — i.e. the latest event. No events = Master. */
fun isBeginnerMode(events: List<ModeEvent>): Boolean =
    events.lastOrNull()?.enteredBeginner == true

/** The daily goal a mode asks for. */
fun hoursRequiredFor(beginner: Boolean): Int =
    if (beginner) BEGINNER_HOURS_REQUIRED else STREAK_HOURS_REQUIRED

/** The mode in effect at a given instant — Master before the first event. */
internal fun beginnerAt(timeMs: Long, events: List<ModeEvent>): Boolean =
    events.lastOrNull { it.timestamp <= timeMs }?.enteredBeginner ?: false

internal fun Calendar.dayKey(): Long = get(Calendar.YEAR) * 1000L + get(Calendar.DAY_OF_YEAR)

enum class DayOutcome { QUALIFIED, SAVED, FADED, BROKEN, MISSED, RECOVERED, BEGINNER_COMPLETE }

enum class StreakEventType {
    COMPLETED, BEGINNER, SAVER_EARNED, SAVER_USED, SAVER_FADED, LOW_SAVERS,
    COMEBACK_GIFT, STREAK_BROKE, RECOVERY_DAYS_LEFT, RECOVERED, PAST_HOUR_ADDED
}

/**
 * One thing that happened to the streak. `id` is stable across replays so the bell
 * and the phone alerts can remember what they've already shown. `streak` is the
 * count the event is about (the protected or broken count, or the new one).
 */
data class StreakEvent(
    val id: String,
    val timestamp: Long,
    val type: StreakEventType,
    val streak: Int,
    val savers: Int,
    val daysLeft: Int = 0,
    val dayStartMs: Long
)

data class StreakState(
    val currentStreak: Int,        // consecutive complete days (the in-window run while recovery is open)
    val displayStreak: Int,        // the pre-break count while recoveryOpen, else currentStreak
    val todayHours: Int,           // COUNTED (in-the-moment) hours today; may exceed the goal
    val todayQualified: Boolean,   // todayHours >= hoursRequired
    val extraToday: Int,           // counted hours today beyond the goal
    val beginnerMode: Boolean,     // today's mode
    val hoursRequired: Int,        // today's goal: 4 in beginner mode, else 8
    val beginnerDaysCompleted: Int,
    val savers: Int,
    val recoveryOpen: Boolean,
    val recoveryDaysLeft: Int,     // including today; 0 when no window is open
    val todayEarnedSaver: Boolean,
    val todayRecovered: Boolean = false // today's hours are what won the streak back
) {
    /** In beginner mode the streak is frozen, not lost — say so, don't hide it. */
    val streakPaused: Boolean get() = beginnerMode && currentStreak > 0
}

internal class ReplayDay(
    val key: Long,                  // year * 1000 + dayOfYear
    val dayStartMs: Long,
    val countedHours: Int,
    val outcome: DayOutcome?,       // null = today in progress, or a beginner day under the goal
    val streakAfter: Int,
    val saversAfter: Int,
    val earnedSaver: Boolean,
    val beginner: Boolean,          // the mode in effect at this day's END
    val isToday: Boolean
)

internal class Replay(
    val days: List<ReplayDay>,
    val events: List<StreakEvent>,   // unsorted
    val savers: Int,
    val streak: Int,
    val recoveryOpen: Boolean,
    val recoverableStreak: Int,
    val recoveryDaysLeft: Int
)

private class Window(val breakIndex: Int, val prevStreak: Int) {
    var qualified = 0   // qualifying (>= goal) days inside the window so far
    var trailing = 0    // the run of qualifying days ending at the latest window day
}

/**
 * Walks every calendar day from the first rating through today.
 *
 *  - Only in-the-moment hours COUNT; entries carrying RECLAIM_TAG never do. An hour
 *    counts once if any non-backdated entry covers it.
 *  - A Master day with >= 8 counted hours is complete and extends the streak; >= 12
 *    also earns a saver (cap 5).
 *  - A finished Master day under 8 spends a saver automatically: it SAVES a running
 *    streak, or FADES uselessly with no streak. With no savers, a running streak
 *    BREAKS and opens a 3-day recovery window; no streak just MISSES.
 *  - Inside an open window nothing is spent. A day with >= 10 counted hours RECOVERS
 *    the streak (old count + qualifying days up to it); when the window closes
 *    without that, the streak is the run of qualifying days ending at the close.
 *  - 7 idle days in a row (no complete day) gift +2 savers at the end of the 7th.
 *  - Beginner days are transparent: they use up window days and count as activity,
 *    but never earn, spend, break or recover.
 *  - Today is in progress: it can qualify or recover, never miss.
 */
internal fun replay(ratings: List<RatingEntry>, modeEvents: List<ModeEvent>, nowMs: Long): Replay {
    if (ratings.isEmpty()) return Replay(emptyList(), emptyList(), SAVER_START, 0, false, 0, 0)

    val countedTs = HashMap<Long, HashMap<Int, Long>>()
    var earliest = Long.MAX_VALUE
    val tmp = Calendar.getInstance()
    for (e in ratings) {
        if (e.timestamp < earliest) earliest = e.timestamp
        if (e.isReclaimed) continue
        tmp.timeInMillis = e.timestamp
        val map = countedTs.getOrPut(tmp.dayKey()) { HashMap() }
        val hour = tmp.get(Calendar.HOUR_OF_DAY)
        val existing = map[hour]
        if (existing == null || e.timestamp < existing) map[hour] = e.timestamp
    }

    val todayKey = Calendar.getInstance().apply { timeInMillis = nowMs }.dayKey()
    val cursor = Calendar.getInstance().apply {
        timeInMillis = earliest
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    val days = ArrayList<ReplayDay>()
    val events = ArrayList<StreakEvent>()
    var streak = 0
    var savers = SAVER_START
    var idleRun = 0
    var window: Window? = null
    var index = 0
    var lastDaysLeft = 0

    while (true) {
        val key = cursor.dayKey()
        val isToday = key == todayKey
        val hourTs = countedTs[key]?.values?.sorted() ?: emptyList()
        val n = hourTs.size
        val dayStartMs = cursor.timeInMillis
        // The last instant of this calendar day — derived from the next day's start
        // so DST-shortened/lengthened days still end where they end.
        val dayEndMs = (cursor.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis - 1
        val beginner = beginnerAt(dayEndMs, modeEvents)

        fun ev(type: StreakEventType, ts: Long, eventStreak: Int, daysLeft: Int = 0, suffix: String = "") {
            events.add(StreakEvent("${type.name}:$key$suffix", ts, type, eventStreak, savers, daysLeft, dayStartMs))
        }

        // The window closes the day after its last day: the run of qualifying days stands.
        window?.let { if (index - it.breakIndex > RECOVERY_DAYS) { streak = it.trailing; window = null } }
        window?.let {
            val left = RECOVERY_DAYS + 1 - (index - it.breakIndex)
            if (left <= 2) ev(StreakEventType.RECOVERY_DAYS_LEFT, dayStartMs, it.prevStreak, left, ":$left")
        }

        var outcome: DayOutcome? = null
        var earned = false
        fun maybeEarn() {
            if (n >= SAVER_EARN_HOURS && savers < SAVER_CAP) {
                savers++; earned = true
                ev(StreakEventType.SAVER_EARNED, hourTs[SAVER_EARN_HOURS - 1], streak)
            }
        }

        val w = window
        when {
            beginner -> if (n >= BEGINNER_HOURS_REQUIRED) {
                outcome = DayOutcome.BEGINNER_COMPLETE
                ev(StreakEventType.BEGINNER, hourTs[BEGINNER_HOURS_REQUIRED - 1], streak)
            }
            w != null -> when {
                n >= RECOVERY_HOURS -> {
                    w.qualified++
                    streak = w.prevStreak + w.qualified
                    window = null
                    outcome = DayOutcome.RECOVERED
                    ev(StreakEventType.RECOVERED, hourTs[RECOVERY_HOURS - 1], streak)
                    maybeEarn()
                }
                n >= STREAK_HOURS_REQUIRED -> {
                    w.qualified++; w.trailing++; streak = w.trailing
                    outcome = DayOutcome.QUALIFIED
                    ev(StreakEventType.COMPLETED, hourTs[STREAK_HOURS_REQUIRED - 1], streak)
                    maybeEarn()
                }
                !isToday -> { w.trailing = 0; streak = 0; outcome = DayOutcome.MISSED }
            }
            n >= STREAK_HOURS_REQUIRED -> {
                streak++
                outcome = DayOutcome.QUALIFIED
                ev(StreakEventType.COMPLETED, hourTs[STREAK_HOURS_REQUIRED - 1], streak)
                maybeEarn()
            }
            isToday -> {}
            savers > 0 -> {
                savers--
                if (streak > 0) {
                    outcome = DayOutcome.SAVED
                    ev(StreakEventType.SAVER_USED, dayEndMs, streak)
                    if (savers <= 2) ev(StreakEventType.LOW_SAVERS, dayEndMs, streak)
                } else {
                    outcome = DayOutcome.FADED
                    ev(StreakEventType.SAVER_FADED, dayEndMs, 0)
                }
            }
            streak > 0 -> {
                outcome = DayOutcome.BROKEN
                ev(StreakEventType.STREAK_BROKE, dayEndMs, streak)
                window = Window(index, streak)
                streak = 0
            }
            else -> outcome = DayOutcome.MISSED
        }

        if (!isToday) {
            val complete = outcome == DayOutcome.QUALIFIED || outcome == DayOutcome.RECOVERED ||
                outcome == DayOutcome.BEGINNER_COMPLETE
            idleRun = if (complete) 0 else idleRun + 1
            if (idleRun > 0 && idleRun % COMEBACK_IDLE_DAYS == 0) {
                savers = minOf(SAVER_CAP, savers + COMEBACK_GIFT)
                ev(StreakEventType.COMEBACK_GIFT, dayEndMs, streak)
            }
        }

        days.add(ReplayDay(key, dayStartMs, n, outcome, streak, savers, earned, beginner, isToday))
        lastDaysLeft = window?.let { RECOVERY_DAYS + 1 - (index - it.breakIndex) } ?: 0

        if (isToday) break
        cursor.add(Calendar.DAY_OF_YEAR, 1)
        index++
    }
    return Replay(days, events, savers, streak, window != null, window?.prevStreak ?: 0, lastDaysLeft)
}

/** Current streak, savers and today's progress, read off the shared replay. */
fun computeStreakState(
    ratings: List<RatingEntry>,
    modeEvents: List<ModeEvent>,
    nowMs: Long = System.currentTimeMillis()
): StreakState {
    val r = replay(ratings, modeEvents, nowMs)
    val today = r.days.lastOrNull()?.takeIf { it.isToday }
    val todayHours = today?.countedHours ?: 0
    // Today's mode comes from the events, not the replay: with no ratings at
    // all the replay is empty, and a brand-new install is exactly that case.
    val beginner = isBeginnerMode(modeEvents)
    val required = hoursRequiredFor(beginner)
    return StreakState(
        currentStreak = r.streak,
        displayStreak = if (r.recoveryOpen) r.recoverableStreak else r.streak,
        todayHours = todayHours,
        todayQualified = todayHours >= required,
        extraToday = maxOf(0, todayHours - required),
        beginnerMode = beginner,
        hoursRequired = required,
        beginnerDaysCompleted = r.days.count { it.outcome == DayOutcome.BEGINNER_COMPLETE },
        savers = r.savers,
        recoveryOpen = r.recoveryOpen,
        recoveryDaysLeft = r.recoveryDaysLeft,
        todayEarnedSaver = today?.earnedSaver == true,
        todayRecovered = today?.outcome == DayOutcome.RECOVERED
    )
}

/**
 * Per-day outcome for every day from the first rating through yesterday.
 * Today appears only once it has qualified or recovered. Key = year * 1000 + dayOfYear.
 */
fun computeDayOutcomes(
    ratings: List<RatingEntry>,
    modeEvents: List<ModeEvent>,
    nowMs: Long = System.currentTimeMillis()
): Map<Long, DayOutcome> =
    replay(ratings, modeEvents, nowMs).days
        .mapNotNull { day -> day.outcome?.let { day.key to it } }
        .toMap()

/** Every streak event, newest first. `reclaimSpends` adds the "past hour added" rows. */
fun computeStreakEvents(
    ratings: List<RatingEntry>,
    reclaimSpends: List<Long>,
    modeEvents: List<ModeEvent>,
    nowMs: Long = System.currentTimeMillis()
): List<StreakEvent> {
    val r = replay(ratings, modeEvents, nowMs)
    val byKey = r.days.associateBy { it.key }
    val cal = Calendar.getInstance()
    val added = reclaimSpends.map { ms ->
        cal.timeInMillis = ms
        val day = byKey[cal.dayKey()]
        StreakEvent(
            id = "${StreakEventType.PAST_HOUR_ADDED.name}:${cal.dayKey()}:$ms",
            timestamp = ms, type = StreakEventType.PAST_HOUR_ADDED,
            streak = day?.streakAfter ?: 0, savers = day?.saversAfter ?: 0,
            dayStartMs = day?.dayStartMs ?: ms
        )
    }
    return (r.events + added).sortedByDescending { it.timestamp }
}

/** A full-screen streak moment: a saver just earned, or a broken streak just won back. */
sealed interface StreakMoment {
    data class SaverEarned(val savers: Int) : StreakMoment
    data class Recovered(val streak: Int) : StreakMoment
}

/**
 * Compares the state before and after a change and says whether it was a moment.
 * It only fires when exactly one new counted hour landed, so a replay (first
 * composition), an import, a backdated save or a mode/goal toggle can never
 * celebrate: none of them add a single counted hour.
 */
fun streakMoment(prev: StreakState?, now: StreakState): StreakMoment? {
    if (prev == null || now.todayHours != prev.todayHours + 1) return null
    if (!prev.todayEarnedSaver && now.todayEarnedSaver) return StreakMoment.SaverEarned(now.savers)
    if (prev.recoveryOpen && !now.recoveryOpen && now.todayRecovered) return StreakMoment.Recovered(now.currentStreak)
    return null
}
