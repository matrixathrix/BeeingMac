package com.example.beeing

import android.content.Context
import androidx.core.content.edit

// Deleted hours, so a watch rating saved before the delete can't bring one back
// (see mergeIncoming). Stored in prefs "b" as "<hourStart>:<deletedAt>" records
// joined by ','; anything older than TOMBSTONE_TTL_MS is dropped on every write.

private const val TOMBSTONES_KEY = "tombstones"

fun loadTombstones(ctx: Context): List<Tombstone> =
    (ctx.getSharedPreferences("b", 0).getString(TOMBSTONES_KEY, "") ?: "")
        .split(",").mapNotNull { rec ->
            val (h, d) = rec.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            Tombstone(h.toLongOrNull() ?: return@mapNotNull null, d.toLongOrNull() ?: return@mapNotNull null)
        }

private fun writeTombstones(ctx: Context, list: List<Tombstone>, nowMs: Long) {
    val kept = list.filter { nowMs - it.deletedAtMs < TOMBSTONE_TTL_MS }
    ctx.getSharedPreferences("b", 0).edit {
        putString(TOMBSTONES_KEY, kept.joinToString(",") { "${it.hourStartMs}:${it.deletedAtMs}" })
    }
}

fun recordTombstone(ctx: Context, hourStartMs: Long, nowMs: Long) = synchronized(STORE_LOCK) {
    writeTombstones(ctx, loadTombstones(ctx).filter { it.hourStartMs != hourStartMs } + Tombstone(hourStartMs, nowMs), nowMs)
}

fun clearTombstone(ctx: Context, hourStartMs: Long) = synchronized(STORE_LOCK) {
    val cur = loadTombstones(ctx)
    if (cur.any { it.hourStartMs == hourStartMs }) {
        writeTombstones(ctx, cur.filter { it.hourStartMs != hourStartMs }, System.currentTimeMillis())
    }
}
