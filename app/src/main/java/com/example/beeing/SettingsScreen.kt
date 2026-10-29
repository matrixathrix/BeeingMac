package com.example.beeing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.BeeButtonDefaults
import com.example.beeing.ui.BeeCardDefaults
import com.example.beeing.ui.BrandBand
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the settings page shows and does; the host owns the state. */
data class SettingsCallbacks(
    val beginnerMode: Boolean,
    val streakPaused: Boolean,
    val pausedAt: Int,
    val streakState: StreakState,
    val notifPrefs: NotificationPrefs,
    val tagCount: Int,
    val autoBackup: Boolean,
    val lastBackupMs: Long,
    val versionName: String,
    val onBeginnerChange: (Boolean) -> Unit,
    val onNotifPrefsChange: (NotificationPrefs) -> Unit,
    val onManageTags: () -> Unit,
    val onAutoBackupChange: (Boolean) -> Unit,
    val onPickBackupFolder: () -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onHowItWorks: () -> Unit
)

@Composable
fun SettingsScreen(callbacks: SettingsCallbacks, onBack: () -> Unit) {
    val c = callbacks
    var showSilent by remember { mutableStateOf(false) }
    var showPause by remember { mutableStateOf(false) }
    val prefs = c.notifPrefs
    val nowMs = System.currentTimeMillis()
    val paused = pauseActive(prefs, nowMs)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow touches so the tabs and the fixed top row underneath stay inert
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
    ) {
        BrandBand {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
        ) {
            Section("YOUR GOAL") {
                SettingsRow(
                    sym = Sym.Beginner, title = "Beginner mode",
                    subtitle = if (c.streakPaused) "Streak paused at ${c.pausedAt}"
                    else "$BEGINNER_HOURS_REQUIRED-hour days · streaks pause while it's on",
                    onClick = { c.onBeginnerChange(!c.beginnerMode) }
                ) { Switch(checked = c.beginnerMode, onCheckedChange = { c.onBeginnerChange(it) }) }
            }
            Section("NOTIFICATIONS") {
                SettingsRow(
                    sym = Sym.Alarm, title = "Hourly reminders",
                    subtitle = "A nudge each hour to rate the last one",
                    onClick = { c.onNotifPrefsChange(prefs.copy(remindersOn = !prefs.remindersOn)) }
                ) { Switch(checked = prefs.remindersOn, onCheckedChange = { c.onNotifPrefsChange(prefs.copy(remindersOn = it)) }) }
                Divider()
                SettingsRow(
                    sym = Sym.Lock, title = "Silent hours",
                    subtitle = if (prefs.silentOn && prefs.silentStartMin != prefs.silentEndMin)
                        "Every day, ${formatMinuteOfDay(prefs.silentStartMin)} to ${formatMinuteOfDay(prefs.silentEndMin)} · no sound"
                    else "Off",
                    onClick = { showSilent = true }
                ) { Chevron() }
                Divider()
                if (paused) {
                    SettingsRow(
                        icon = Icons.Default.Notifications, title = "Paused until ${SimpleDateFormat("EEE h a", Locale.getDefault()).format(Date(prefs.pauseUntilMs))} · ${if (prefs.pauseMode == PauseMode.QUIET) "Quiet" else "Off"}",
                        subtitle = "Streak alerts and the weekly report still arrive",
                        onClick = {}, highlight = true
                    ) { TextButton(onClick = { c.onNotifPrefsChange(prefs.copy(pauseUntilMs = 0L)) }) { Text("End now") } }
                } else {
                    SettingsRow(
                        icon = Icons.Default.Notifications, title = "Pause notifications",
                        subtitle = "Until a time you choose, up to 2 days",
                        onClick = { showPause = true }
                    ) { Chevron() }
                }
            }
            Section("TAGS") {
                SettingsRow(
                    sym = Sym.Tag, title = "Manage tags",
                    subtitle = "Reorder, rename, delete or add",
                    onClick = c.onManageTags
                ) { ValueChevron("${c.tagCount}") }
            }
            Section("BACKUP & DATA") {
                SettingsRow(
                    sym = Sym.Hive, title = "Daily auto-backup",
                    subtitle = if (c.autoBackup && c.lastBackupMs > 0)
                        "Last backed up " + SimpleDateFormat("hh:mma, dd-MMM-yyyy", Locale.getDefault()).format(Date(c.lastBackupMs))
                    else "Every day at 12:05 AM",
                    onClick = { c.onAutoBackupChange(!c.autoBackup) }
                ) { Switch(checked = c.autoBackup, onCheckedChange = { c.onAutoBackupChange(it) }) }
                if (c.autoBackup) {
                    Divider()
                    SettingsRow(
                        icon = Icons.Default.Settings, title = "Backup folder",
                        subtitle = "Where the daily copy is saved",
                        onClick = c.onPickBackupFolder
                    ) { Chevron() }
                }
                Divider()
                SettingsRow(
                    sym = Sym.Note, title = "Export data", subtitle = "Save your ratings as a CSV file",
                    onClick = c.onExport
                ) { Chevron() }
                Divider()
                SettingsRow(
                    sym = Sym.Reset, title = "Import data", subtitle = "Replace everything with a backup file",
                    onClick = c.onImport
                ) { Chevron() }
            }
            Section("HELP") {
                SettingsRow(
                    icon = Icons.Default.Info, title = "How Beeing works", subtitle = null,
                    onClick = c.onHowItWorks
                ) { Chevron() }
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Beeing · Version ${c.versionName}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Developed by AthrixMatrix", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showSilent) SilentHoursSheet(prefs, onChange = { c.onNotifPrefsChange(it) }, onDismiss = { showSilent = false })
    if (showPause) {
        PauseSheet(
            streakState = c.streakState,
            onConfirm = { untilMs, mode ->
                c.onNotifPrefsChange(prefs.copy(pauseUntilMs = untilMs, pauseMode = mode))
                showPause = false
            },
            onDismiss = { showPause = false }
        )
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Text(
        label,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
    )
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = BeeCardDefaults.colors(),
        elevation = BeeCardDefaults.elevation(),
        modifier = Modifier.fillMaxWidth()
    ) { Column { content() } }
}

@Composable
private fun Divider() = HorizontalDivider(
    Modifier.padding(start = 62.dp),
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
)

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    sym: Sym? = null,
    icon: ImageVector? = null,
    highlight: Boolean = false,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) com.example.beeing.ui.theme.LocalBeeExtras.current.accentFill.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            val tint = MaterialTheme.colorScheme.primary
            if (sym != null) BeeIcon(sym, size = 20.dp, tint = tint, contentDescription = null)
            else if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun Chevron() = Icon(
    Icons.Default.KeyboardArrowRight, contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun ValueChevron(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Chevron()
    }
}

/**
 * The tag list editor, opened from Settings. Owns its own copy of the tags and
 * saves on every change, like the Now tab's editor (tag edits are picker-only:
 * old entries keep the name they were rated with).
 */
@Composable
fun SettingsManageTags(context: android.content.Context, onChanged: () -> Unit, onDismiss: () -> Unit) {
    var tags by remember { mutableStateOf(loadTags(context)) }
    var adding by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }
    fun update(next: List<String>) { saveTags(context, next); tags = next; onChanged() }

    ManageTagsDialog(
        tags = tags,
        onReorder = { update(it) },
        onRename = { old, new -> update(tags.map { if (it == old) new else it }) },
        onDelete = { tag -> update(tags.filter { it != tag }) },
        onAddNew = { newTag = ""; adding = true },
        onDismiss = onDismiss
    )
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("New tag") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newTag,
                    onValueChange = { v -> newTag = v.replace(Regex("[,;|]"), " ").take(20) },
                    singleLine = true,
                    supportingText = { Text("${tags.size}/30 tags") }
                )
            },
            confirmButton = {
                Button(
                    colors = BeeButtonDefaults.colors(),
                    enabled = newTag.isNotBlank() && tags.size < 30,
                    onClick = { update((tags + newTag.trim()).distinct()); adding = false }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } }
        )
    }
}
