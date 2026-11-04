package com.example.beeing.wear

import com.example.beeing.waitingHours

/**
 * Where the rating screens are: which hour, what's picked so far, and which step
 * (the crown turns the score on SCORE and scrolls the tags on TAGS). Pure, so the
 * clock-turning cases are tested rather than hoped for.
 */
data class RatingFlow(
    val hour: Long,
    val waiting: List<Long>,
    val score: Int?,
    val tags: Set<String>,
    val step: Step,
    val notice: String? = null
) {
    enum class Step { SCORE, TAGS }
}

/** The flow on [preferHour] if it waits, else the hour about to expire; null when nothing waits. */
fun startFlow(nowMs: Long, rated: Set<Long>, preferHour: Long? = null): RatingFlow? {
    val w = waitingHours(nowMs, rated)
    if (w.isEmpty()) return null
    return RatingFlow(preferHour?.takeIf { it in w } ?: w.first(), w, null, emptySet(), RatingFlow.Step.SCORE)
}

/**
 * The clock moved. If this hour stopped being ratable, the choices made so far move to
 * the newest waiting hour (one tap on Save finishes); with nothing waiting, null closes.
 */
fun RatingFlow.onClock(nowMs: Long, rated: Set<Long>): RatingFlow? {
    val w = waitingHours(nowMs, rated)
    return when {
        hour in w -> if (w == waiting) this else copy(waiting = w)
        w.isEmpty() -> null
        else -> copy(hour = w.last(), waiting = w, notice = "That hour just closed")
    }
}

/** Saved: on to the next waiting hour from a clean start, or null to close. */
fun RatingFlow.afterSave(nowMs: Long, rated: Set<Long>): RatingFlow? {
    val w = waitingHours(nowMs, rated)
    if (w.isEmpty()) return null
    return RatingFlow(w.first(), w, null, emptySet(), RatingFlow.Step.SCORE)
}

/** The other waiting hour, starting over; a single waiting hour stays put. */
fun RatingFlow.switchHour(): RatingFlow {
    val other = waiting.firstOrNull { it != hour } ?: return this
    return copy(hour = other, score = null, tags = emptySet(), step = RatingFlow.Step.SCORE, notice = null)
}
