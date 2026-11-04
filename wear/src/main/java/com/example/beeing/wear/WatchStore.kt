package com.example.beeing.wear

import android.content.Context
import androidx.core.content.edit
import com.example.beeing.PhoneState
import com.example.beeing.RatingEntry
import com.example.beeing.decodeRatingRow
import com.example.beeing.decodeState
import com.example.beeing.encodeRatingRow
import com.example.beeing.encodeState

/**
 * The watch's only storage (prefs "w"): the phone's last [PhoneState], the ratings
 * saved here that the phone hasn't processed yet, and a couple of one-time flags.
 * Saves stay until the phone lists them as processed, however long that takes, so
 * the replay resumed from the phone's checkpoint never loses a day.
 */
object WatchStore {
    private const val STATE = "state"
    private const val LOCAL = "local"
    private const val CHIME_TIP = "chime_tip_shown"
    private const val LOCAL_MAX_AGE_MS = 14 * 24 * 3_600_000L // a bound, not a rule: far past any offline stretch

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("w", 0)

    fun state(ctx: Context): PhoneState? =
        prefs(ctx).getString(STATE, null)?.let { decodeState(it.toByteArray(Charsets.UTF_8)) }

    fun saveState(ctx: Context, s: PhoneState) =
        prefs(ctx).edit { putString(STATE, String(encodeState(s), Charsets.UTF_8)) }

    fun local(ctx: Context): List<RatingEntry> =
        (prefs(ctx).getString(LOCAL, "") ?: "").split("|").filter { it.isNotEmpty() }.mapNotNull { decodeRatingRow(it) }

    private fun writeLocal(ctx: Context, list: List<RatingEntry>) =
        prefs(ctx).edit { putString(LOCAL, list.joinToString("|") { encodeRatingRow(it) }) }

    /** One save per hour: a newer save of the same hour replaces the older one. */
    fun saveLocal(ctx: Context, e: RatingEntry) =
        writeLocal(ctx, local(ctx).filter { it.timestamp != e.timestamp } + e)

    /** Drops the saves the phone has processed; returns them so their Data Layer items can go too. */
    fun pruneProcessed(ctx: Context, processedIds: List<Long>, nowMs: Long): List<RatingEntry> {
        val ids = processedIds.toHashSet()
        val (gone, kept) = local(ctx).partition { it.id in ids || nowMs - it.id > LOCAL_MAX_AGE_MS }
        if (gone.isNotEmpty()) writeLocal(ctx, kept)
        return gone
    }

    fun chimeTipShown(ctx: Context) = prefs(ctx).getBoolean(CHIME_TIP, false)
    fun markChimeTipShown(ctx: Context) = prefs(ctx).edit { putBoolean(CHIME_TIP, true) }
}
