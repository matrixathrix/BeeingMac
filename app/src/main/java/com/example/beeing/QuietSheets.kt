package com.example.beeing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.BeeButtonDefaults
import com.example.beeing.ui.theme.LocalBeeExtras
import com.example.beeing.ui.theme.StreakAtRiskRed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================
// SILENT HOURS + PAUSE  (hourly reminders only — see NotificationPrefs.kt)
// ============================================================

/** "10 PM" or "10:30 PM" for minutes after midnight. */
fun formatMinuteOfDay(min: Int): String {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, min / 60); set(Calendar.MINUTE, min % 60) }
    return SimpleDateFormat(if (min % 60 == 0) "h a" else "h:mm a", Locale.getDefault()).format(c.time)
}

private fun dayHour(ms: Long) = SimpleDateFormat("EEE h a", Locale.getDefault()).format(Date(ms))

/** "2 hours", "1 day", "1 day 3 hours" — how long until [untilMs]. */
internal fun pauseDurationLabel(nowMs: Long, untilMs: Long): String {
    val hours = ((untilMs - nowMs + 3_599_999L) / 3_600_000L).toInt().coerceAtLeast(1)
    if (hours < 24) return "$hours ${plural(hours, "hour")}"
    val d = hours / 24
    val r = hours % 24
    return "$d ${plural(d, "day")}" + if (r > 0) " $r ${plural(r, "hour")}" else ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentHoursSheet(prefs: NotificationPrefs, onChange: (NotificationPrefs) -> Unit, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Silent hours", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Hourly reminders still arrive, with no sound or vibration. Streak alerts and the weekly report go quiet too.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Every day", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = prefs.silentOn, onCheckedChange = { onChange(prefs.copy(silentOn = it)) })
            }
            TimeRow("From", prefs.silentStartMin, prefs.silentOn) { editing = true }
            TimeRow("Until", prefs.silentEndMin, prefs.silentOn) { editing = false }
            if (prefs.silentOn && prefs.silentStartMin == prefs.silentEndMin) {
                Text("Start and end are the same, so silent hours are off.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    editing?.let { isStart ->
        val initial = if (isStart) prefs.silentStartMin else prefs.silentEndMin
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isStart) "Silent from" else "Silent until") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val min = state.hour * 60 + state.minute
                    onChange(if (isStart) prefs.copy(silentStartMin = min) else prefs.copy(silentEndMin = min))
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TimeRow(label: String, minute: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMinuteOfDay(minute), fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Pause hourly reminders until a time within 48 hours. Quiet keeps them arriving
 * silently; Off stops them. Off warns when the pause could cost savers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(
    streakState: StreakState,
    onConfirm: (untilMs: Long, mode: PauseMode) -> Unit,
    onDismiss: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val maxUntil = remember { maxPauseUntil(now) }
    var mode by remember { mutableStateOf(PauseMode.OFF) }
    var until by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }

    fun ceilHour(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms
        if (get(Calendar.MINUTE) != 0 || get(Calendar.SECOND) != 0 || get(Calendar.MILLISECOND) != 0) add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val tomorrow7 = remember {
        Calendar.getInstance().apply {
            timeInMillis = now; add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 7); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val presets = listOf(
        "In 1 hour" to ceilHour(now + 3_600_000L),
        "In 3 hours" to ceilHour(now + 3 * 3_600_000L),
        "Tomorrow 7 AM" to tomorrow7
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pause notifications", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("HOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeCard("Quiet", "Still arrive, no sound. You can keep rating.", mode == PauseMode.QUIET, Modifier.weight(1f)) { mode = PauseMode.QUIET }
                ModeCard("Off", "No notifications. A real break from Beeing.", mode == PauseMode.OFF, Modifier.weight(1f)) { mode = PauseMode.OFF }
            }
            Text("UNTIL", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            @OptIn(ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presets.forEach { (label, ms) ->
                    FilterChip(selected = until == ms, onClick = { until = ms }, label = { Text(label) })
                }
                FilterChip(
                    selected = until != null && presets.none { it.second == until },
                    onClick = { picking = true },
                    label = { Text("Pick a time") }
                )
            }
            until?.let { u ->
                val verb = if (mode == PauseMode.OFF) "off" else "silent"
                Text(
                    buildAnnotatedLine(
                        if (mode == PauseMode.OFF) "Notifications are off until " else "Notifications arrive silently until ",
                        dayHour(u), " · ${pauseDurationLabel(now, u)}"
                    ),
                    fontSize = 14.sp
                )
                if (mode == PauseMode.OFF) {
                    pauseRisk(streakState, streakState.todayQualified, streakState.beginnerMode, now, u)?.let { RiskCard(it) }
                }
            }
            Button(
                colors = BeeButtonDefaults.colors(),
                enabled = until != null,
                onClick = { until?.let { onConfirm(it, mode) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(until?.let { "Pause until ${dayHour(it)}" } ?: "Pick when to stop") }
        }
    }
    if (picking) PickTimeDialog(now, maxUntil, onPick = { until = it; picking = false }, onDismiss = { picking = false })
}

@Composable
private fun buildAnnotatedLine(a: String, bold: String, b: String) = androidx.compose.ui.text.buildAnnotatedString {
    append(a)
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold))
    append(bold)
    pop()
    append(b)
}

@Composable
private fun ModeCard(title: String, body: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val honey = LocalBeeExtras.current.accentFill
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (selected) BorderStroke(2.dp, honey) else null
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(body, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RiskCard(risk: PauseRisk) {
    val text = if (risk.breakDayStartMs != null) {
        "This covers ${risk.days} ${plural(risk.days, "day")} and you have ${risk.savers} ${plural(risk.savers, "saver")}. " +
            "If you don't rate on your own, your ${risk.streak}-day streak could break on " +
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(risk.breakDayStartMs)) + "."
    } else {
        "Savers fade on days you don't complete. You could lose ${risk.fadeCount} of your ${risk.savers}."
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StreakAtRiskRed.copy(alpha = 0.16f)),
        border = BorderStroke(1.dp, StreakAtRiskRed.copy(alpha = 0.6f))
    ) {
        Text(text, fontSize = 13.sp, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Day + hour choice capped at [maxUntil]: tap a day, then an hour that falls inside the range. */
@Composable
private fun PickTimeDialog(now: Long, maxUntil: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val dayFmt = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val days = remember {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        buildList { while (c.timeInMillis <= maxUntil) { add(c.timeInMillis); c.add(Calendar.DAY_OF_YEAR, 1) } }
    }
    var dayIndex by remember { mutableStateOf(0) }
    val hours = remember(dayIndex) {
        (0..23).map { h ->
            Calendar.getInstance().apply { timeInMillis = days[dayIndex]; set(Calendar.HOUR_OF_DAY, h) }.timeInMillis
        }.filter { it > now && it <= maxUntil }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a time") },
        text = {
            Column {
                @OptIn(ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEachIndexed { i, d ->
                        FilterChip(selected = i == dayIndex, onClick = { dayIndex = i }, label = { Text(dayFmt.format(Date(d))) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    hours.forEach { ms ->
                        Text(
                            SimpleDateFormat("h a", Locale.getDefault()).format(Date(ms)),
                            modifier = Modifier.fillMaxWidth().clickable { onPick(ms) }.padding(vertical = 10.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (hours.isEmpty()) Text("Nothing left on this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
