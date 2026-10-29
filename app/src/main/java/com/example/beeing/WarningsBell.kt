package com.example.beeing

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.theme.HoneyGold
import com.example.beeing.ui.theme.LocalBeeExtras
import com.example.beeing.ui.theme.StreakAtRiskRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// WARNINGS STORE  (read ids + baseline + the on/off switch, in SharedPreferences "b")
// ============================================================

private const val READ_IDS_KEY = "warn_read_ids"
private const val BASELINE_KEY = "warn_baseline_ms"
private const val ENABLED_KEY = "streak_warnings"

fun loadReadWarningIds(ctx: Context): Set<String> =
    (ctx.getSharedPreferences("b", 0).getString(READ_IDS_KEY, "") ?: "")
        .split(";").filter { it.isNotBlank() }.toSet()

fun markWarningsRead(ctx: Context, ids: Collection<String>) {
    // Only the newest 30 can ever be shown, so older ids are dead weight: keep a bounded set.
    val merged = (loadReadWarningIds(ctx) + ids).toList().takeLast(200)
    ctx.getSharedPreferences("b", 0).edit().putString(READ_IDS_KEY, merged.joinToString(";")).apply()
}

/** Events before this instant show as already read. Set to "now" on the first call ever. */
fun warningsBaseline(ctx: Context): Long {
    val prefs = ctx.getSharedPreferences("b", 0)
    if (!prefs.contains(BASELINE_KEY)) prefs.edit().putLong(BASELINE_KEY, System.currentTimeMillis()).apply()
    return prefs.getLong(BASELINE_KEY, 0L)
}

/** After an import the whole history re-derives; nothing in it may badge the bell. */
fun resetWarningsBaseline(ctx: Context) {
    ctx.getSharedPreferences("b", 0).edit().putLong(BASELINE_KEY, System.currentTimeMillis()).apply()
}

fun streakWarningsEnabled(ctx: Context): Boolean =
    ctx.getSharedPreferences("b", 0).getBoolean(ENABLED_KEY, true)

fun setStreakWarningsEnabled(ctx: Context, enabled: Boolean) {
    ctx.getSharedPreferences("b", 0).edit().putBoolean(ENABLED_KEY, enabled).apply()
}

// ============================================================
// "HEADS UP" SHEET
// ============================================================

/**
 * `readIds` is the snapshot taken when the sheet opened, so the honey dots show
 * what was new; the host marks everything read on open (clearing the badge).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarningsSheet(
    warnings: List<Warning>,
    readIds: Set<String>,
    baselineMs: Long,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit
) {
    var dotsCleared by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Heads up", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (warnings.isNotEmpty()) {
                    TextButton(onClick = { dotsCleared = true; onMarkAllRead() }) { Text("Mark all read") }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (warnings.isEmpty()) {
                Text(
                    "Nothing to worry about.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(warnings, key = { it.id }) { w ->
                        val unread = !dotsCleared && w.timestamp >= baselineMs && w.id !in readIds
                        WarningRow(w, fmt.format(Date(w.timestamp)), unread)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WarningRow(w: Warning, time: String, unread: Boolean) {
    val (sym, tint) = when (w.type) {
        // Red only for a loss. A saver stepping in kept the streak: that is
        // good news, so it wears honey like a gift does.
        StreakEventType.SAVER_FADED, StreakEventType.STREAK_BROKE -> Sym.Saver to StreakAtRiskRed
        StreakEventType.SAVER_USED, StreakEventType.LOW_SAVERS, StreakEventType.COMEBACK_GIFT ->
            Sym.Saver to HoneyGold
        else -> Sym.Fire to HoneyGold
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { BeeIcon(sym, size = 20.dp, tint = tint, contentDescription = null) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(w.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(w.detail, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        if (unread) {
            Box(
                Modifier.padding(start = 8.dp, top = 6.dp).size(8.dp)
                    .background(LocalBeeExtras.current.accentFill, CircleShape)
            )
        }
    }
}
