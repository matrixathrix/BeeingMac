package com.example.beeing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.*

// --- DATA MODELS ---
data class RatingEntry(
    val id: Long,
    val score: Int,
    val timestamp: Long,
    val hourLabel: String,
    val note: String = "",
    val tags: List<String> = emptyList()
)

/** A score chosen on the hourly notification, waiting to be finished in-app. */
data class PendingRating(val score: Int, val targetTs: Long, val label: String, val tags: List<String> = emptyList())

// Score band colors shared by the dial, history dots and reclaim dialog
fun scoreBandColor(score: Int): androidx.compose.ui.graphics.Color = when {
    score >= 8 -> androidx.compose.ui.graphics.Color(0xFF66BB6A)
    score >= 5 -> androidx.compose.ui.graphics.Color(0xFFFFB300)
    else -> androidx.compose.ui.graphics.Color(0xFFB71C1C)
}

// --- NOTIFICATIONS ---
const val ACTION_NOTIF_SCORE = "com.example.beeing.NOTIF_SCORE"
const val ACTION_NOTIF_TAG = "com.example.beeing.NOTIF_TAG"
const val ACTION_NOTIF_SUBMIT = "com.example.beeing.NOTIF_SUBMIT"
const val CHANNEL_STREAK_ALERTS = "streak_alerts"
const val CHANNEL_STREAK_ALERTS_SILENT = "streak_alerts_silent"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val soundUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            "hourly_bee",
            "Hourly Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(soundUri, audioAttributes)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val weeklyChannel = NotificationChannel(
            "weekly_report",
            "Weekly Report",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(soundUri, audioAttributes)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(weeklyChannel)

        // Silent twins: a channel's sound is fixed at creation, so "no sound" needs its own.
        val manager = context.getSystemService(NotificationManager::class.java)
        fun silent(id: String, name: String, importance: Int) = NotificationChannel(id, name, importance).apply {
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(silent("hourly_bee_silent", "Hourly Reminders (silent)", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(silent("weekly_report_silent", "Weekly Report (silent)", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STREAK_ALERTS, "Streak alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(soundUri, audioAttributes) }
        )
        manager.createNotificationChannel(
            silent(CHANNEL_STREAK_ALERTS_SILENT, "Streak alerts (silent)", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

fun scheduleExactHourlyAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        return
    }

    val intent = Intent(context, NotificationReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val calendar = Calendar.getInstance().apply {
        // Trigger at the start of the next hour
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    alarmManager.cancel(pendingIntent)
    alarmManager.setExactAndAllowWhileIdle(
        android.app.AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent
    )
}

class NotificationReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            "android.intent.action.BOOT_COMPLETED" -> {
                scheduleExactHourlyAlarm(context)
                scheduleWeeklyReport(context)
                scheduleStreakAlerts(context)
            }
            ACTION_WEEKLY_REPORT -> {
                showWeeklyReportNotification(context)
                scheduleWeeklyReport(context) // reschedule for next week
            }
            ACTION_NOTIF_SCORE, ACTION_NOTIF_TAG -> onDraftTap(context, manager, intent)
            ACTION_NOTIF_SUBMIT -> onSubmit(context, manager, intent)
            else -> {
                // STANDARD TRIGGER (Start of a new hour)
                // Calculate the "Previous Hour" NOW and freeze it
                val now = Calendar.getInstance()
                val endHour = now.get(Calendar.HOUR_OF_DAY)

                // If it's 5:00 PM, we are rating 4:00 PM - 5:00 PM
                val startHour = if (endHour == 0) 23 else endHour - 1

                // Calculate Timestamp (Start of the rated hour)
                val targetTimestamp = Calendar.getInstance().apply {
                    if (endHour == 0) add(Calendar.DAY_OF_YEAR, -1) // Handle midnight wrap-around
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                // Calculate Label (e.g., "5th")
                val ordinal = "${if (endHour == 0) 24 else endHour}${getSuffix(if (endHour == 0) 24 else endHour)}"

                // Hourly reminders obey silent hours, pause and the reminders switch;
                // the alarm keeps rescheduling either way, so a pause ends on its own.
                when (hourlyDelivery(loadNotificationPrefs(context), System.currentTimeMillis())) {
                    Delivery.SOUND -> showCustomNotification(context, manager, targetTimestamp, ordinal, "hourly_bee")
                    Delivery.SILENT -> showCustomNotification(context, manager, targetTimestamp, ordinal, "hourly_bee_silent")
                    Delivery.NONE -> {}
                }
                scheduleExactHourlyAlarm(context)
            }
        }
    }

    /**
     * Posts (or re-posts) the rating notification. Taps on a score or tag are
     * broadcasts back to this receiver that update the [NotifDraft] and call this
     * again; `alertOnce` keeps those re-posts from ringing a second time.
     */
    private fun showCustomNotification(
        context: Context,
        manager: NotificationManager,
        targetTs: Long,
        targetLabel: String,
        channelId: String,
        alertOnce: Boolean = false
    ) {
        val draft = draftFor(loadNotifDraft(context), targetTs)
        val tags = quickTags(loadRatings(context), loadTags(context), System.currentTimeMillis(), 6)
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_rating)

        // 1. Format the text
        val cal = Calendar.getInstance().apply { timeInMillis = targetTs }
        val startH = cal.get(Calendar.HOUR_OF_DAY)
        val endH = if (startH == 23) 0 else startH + 1

        val rangeText = "${formatH(startH)}-${formatH(endH)}"
        remoteViews.setTextViewText(R.id.notif_title, "How was your last hour? ($rangeText)")

        // Detect system dark mode once — used for all button text colours
        val isDarkMode = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeTextColor = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val selectedText = android.graphics.Color.BLACK

        fun broadcast(code: Int, action: String, fill: Intent.() -> Unit = {}) = PendingIntent.getBroadcast(
            context, code,
            Intent(context, NotificationReceiver::class.java).apply {
                this.action = action
                putExtra("TARGET_TS", targetTs)
                putExtra("TARGET_LABEL", targetLabel)
                fill()
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. Score chips: a tap selects (and highlights) instead of leaving the notification
        val buttonIds = listOf(
            R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5,
            R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_10
        )
        buttonIds.forEachIndexed { index, id ->
            val score = index + 1
            val selected = draft.score == score
            remoteViews.setInt(
                id, "setBackgroundResource",
                if (selected) R.drawable.rounded_notif_btn_selected else R.drawable.rounded_notif_btn_neutral
            )
            remoteViews.setTextColor(
                id,
                if (selected) selectedText else when {
                    score >= 8 -> android.graphics.Color.parseColor("#2E7D32") // Green
                    score >= 5 -> android.graphics.Color.parseColor("#F57C00") // Orange
                    else -> android.graphics.Color.parseColor("#B71C1C") // Red
                }
            )
            remoteViews.setOnClickPendingIntent(
                id, broadcast(100 + score, ACTION_NOTIF_SCORE) { putExtra("SCORE", score) }
            )
        }

        // 3. Tag chips (the most used lately); more tags live in the app
        val tagIds = listOf(R.id.btn_tag_1, R.id.btn_tag_2, R.id.btn_tag_3, R.id.btn_tag_4, R.id.btn_tag_5, R.id.btn_tag_6)
        tagIds.forEachIndexed { i, id ->
            val tag = tags.getOrNull(i)
            if (tag == null) {
                remoteViews.setViewVisibility(id, android.view.View.INVISIBLE)
                return@forEachIndexed
            }
            val selected = tag in draft.tags
            remoteViews.setViewVisibility(id, android.view.View.VISIBLE)
            remoteViews.setTextViewText(id, com.example.beeing.ui.icons.tagLabelOf(tag))
            remoteViews.setInt(
                id, "setBackgroundResource",
                if (selected) R.drawable.rounded_notif_btn_selected else R.drawable.rounded_notif_btn_neutral
            )
            remoteViews.setTextColor(id, if (selected) selectedText else themeTextColor)
            remoteViews.setOnClickPendingIntent(id, broadcast(200 + i, ACTION_NOTIF_TAG) { putExtra("TAG", tag) })
        }

        // 4. Open the app, carrying whatever has been picked so far
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            draft.score?.let {
                putExtra("PENDING_SCORE", it)
                putExtra("TARGET_TS", targetTs)
                putExtra("TARGET_LABEL", targetLabel)
                putExtra("PENDING_TAGS", draft.tags.joinToString(";"))
            }
        }
        val openIntent = PendingIntent.getActivity(
            context, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        remoteViews.setOnClickPendingIntent(R.id.btn_open_app, openIntent)
        remoteViews.setTextColor(R.id.btn_open_app, themeTextColor)

        // 5. Submit stays dimmed until there is a score and a tag
        remoteViews.setViewVisibility(R.id.btn_submit, android.view.View.VISIBLE)
        remoteViews.setInt(
            R.id.btn_submit, "setBackgroundResource",
            if (draft.canSubmit) R.drawable.rounded_notif_btn_selected else R.drawable.rounded_notif_btn_neutral
        )
        remoteViews.setTextColor(R.id.btn_submit, if (draft.canSubmit) selectedText else themeTextColor)
        remoteViews.setTextViewText(
            R.id.btn_submit,
            when {
                draft.score == null -> "Pick a score"
                draft.tags.isEmpty() -> "Pick a tag"
                else -> "Submit"
            }
        )
        remoteViews.setOnClickPendingIntent(R.id.btn_submit, broadcast(300, ACTION_NOTIF_SUBMIT))

        // 6. Build
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(alertOnce)
            .setAutoCancel(true) // body tap only; the chips are broadcasts and never cancel
            .setContentIntent(openIntent)
            .build()

        manager.notify(1, notification)
    }

    /** A tap on the notification: update the draft, then re-post it with the selection shown. */
    private fun onDraftTap(context: Context, manager: NotificationManager, intent: Intent) {
        val targetTs = intent.getLongExtra("TARGET_TS", 0L)
        if (!hourStillRatable(targetTs, System.currentTimeMillis())) {
            clearNotifDraft(context); manager.cancel(1); return
        }
        val label = intent.getStringExtra("TARGET_LABEL") ?: hourLabelFor(targetTs)
        var draft = draftFor(loadNotifDraft(context), targetTs)
        when (intent.action) {
            ACTION_NOTIF_SCORE -> draft = draft.copy(score = intent.getIntExtra("SCORE", -1).takeIf { it in 1..10 } ?: draft.score)
            ACTION_NOTIF_TAG -> intent.getStringExtra("TAG")?.let { draft = draft.toggleTag(it) }
        }
        saveNotifDraft(context, draft)
        // Quiet channel on re-posts: the selection must never ring again.
        showCustomNotification(context, manager, targetTs, label, "hourly_bee_silent", alertOnce = true)
    }

    /** Saves the draft as an ordinary in-the-moment rating, then dismisses the notification. */
    private fun onSubmit(context: Context, manager: NotificationManager, intent: Intent) {
        val targetTs = intent.getLongExtra("TARGET_TS", 0L)
        val draft = draftFor(loadNotifDraft(context), targetTs)
        val now = System.currentTimeMillis()
        if (!hourStillRatable(targetTs, now)) {
            clearNotifDraft(context); manager.cancel(1); return
        }
        if (!draft.canSubmit) return // dimmed button: nothing to save yet
        val alreadyRated = loadRatings(context).any { it.timestamp == targetTs }
        if (!alreadyRated) {
            saveRating(
                context,
                RatingEntry(
                    id = now, score = draft.score!!, timestamp = targetTs,
                    hourLabel = hourLabelFor(targetTs), note = "", tags = draft.tags
                )
            )
        }
        clearNotifDraft(context)
        manager.cancel(1)
    }


    // Private helpers to ensure Utils.kt is self-contained
    private fun formatH(h: Int) = "${if(h%12==0) 12 else h%12}${if(h<12)"am" else "pm"}"
    private fun getSuffix(n: Int) = when {
        n in 11..13 -> "th"
        n%10==1 -> "st"
        n%10==2 -> "nd"
        n%10==3 -> "rd"
        else -> "th"
    }
}

// --- TAGS MANAGEMENT ---
// The 10 default tags. Stored under a fresh key ("tags_v2") so tag lists
// clobbered by old backup imports are abandoned and everyone starts from these.
val DEFAULT_TAGS = listOf(
    "💼 Work", "📚 Learning", "🏋️ Exercise", "🍽️ Food", "😴 Rest",
    "👨‍👩‍👧 Family", "🤝 Social", "📱 Scrolling", "🏠 Chores", "🎨 Hobby"
)

fun loadTags(c: Context): List<String> {
    val prefs = c.getSharedPreferences("b", 0)
    val tagsString = prefs.getString("tags_v2", null) ?: return DEFAULT_TAGS
    return tagsString.split(",").filter { it.isNotBlank() }
}

fun saveTags(c: Context, tags: List<String>) {
    val prefs = c.getSharedPreferences("b", 0)
    prefs.edit { putString("tags_v2", tags.joinToString(",")) }
}

// --- DATA PERSISTENCE ---
fun saveRating(context: Context, entry: RatingEntry) {
    val prefs = context.getSharedPreferences("b", 0)
    val existing = loadRatings(context).toMutableList()
    existing.removeAll { it.id == entry.id }

    // Safety: Sanitize note to prevent CSV corruption
    val safeNote = entry.note.replace(",", " ").replace("\n", " ")
    val safeEntry = entry.copy(note = safeNote)

    existing.add(safeEntry)

    val serialized = existing.joinToString("|") { e ->
        "${e.id},${e.score},${e.timestamp},${e.hourLabel},${e.note},${e.tags.joinToString(";")}"
    }
    prefs.edit { putString("ratings", serialized) }
}

fun loadRatings(context: Context): List<RatingEntry> {
    val prefs = context.getSharedPreferences("b", 0)
    val data = prefs.getString("ratings", "") ?: return emptyList()
    if (data.isEmpty()) return emptyList()

    return data.split("|").mapNotNull { line ->
        val parts = line.split(",")
        if (parts.size >= 4) {
            RatingEntry(
                id = parts[0].toLongOrNull() ?: 0L,
                score = parts[1].toIntOrNull() ?: 0,
                timestamp = parts[2].toLongOrNull() ?: 0L,
                hourLabel = parts[3],
                note = parts.getOrNull(4) ?: "",
                tags = parts.getOrNull(5)?.split(";")?.filter { it.isNotEmpty() } ?: emptyList()
            )
        } else null
    }.sortedByDescending { it.timestamp }
}

fun deleteRating(context: Context, id: Long) {
    val prefs = context.getSharedPreferences("b", 0)
    val existing = loadRatings(context).filter { it.id != id }
    val serialized = existing.joinToString("|") { e ->
        "${e.id},${e.score},${e.timestamp},${e.hourLabel},${e.note},${e.tags.joinToString(";")}"
    }
    prefs.edit { putString("ratings", serialized) }
}

// --- BACKUP/RESTORE ---
// Exports stay plain CSV (opens directly in Excel / Sheets) but carry an HMAC
// signature over the data rows. Import rejects files whose signature is
// missing or wrong — except in debuggable builds, so fabricated test data can
// still be imported during development.
private const val EXPORT_SIGN_KEY = "beeing-export-hmac-v1-7c4a1f"

private fun signRows(rows: List<String>): String {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(EXPORT_SIGN_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(rows.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private fun isDebuggableBuild(c: Context): Boolean =
    (c.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

fun saveToCsv(c: Context, u: Uri, r: List<RatingEntry>) {
    c.contentResolver.openOutputStream(u)?.use { stream ->
        val writer = stream.bufferedWriter(Charsets.UTF_8)
        val availableTags = loadTags(c)
        writer.write("# AVAILABLE_TAGS: ${availableTags.joinToString("|")}\n")
        writer.write("ID,Score,Timestamp,Recording_Time,Hour_Label,Note,Tags\n")
        val rows = r.map {
            val recordingTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.id))
            "${it.id},${it.score},${it.timestamp},\"$recordingTime\",\"${it.hourLabel}\",\"${it.note}\",\"${it.tags.joinToString("|")}\""
        }
        rows.forEach { row ->
            writer.write(row)
            writer.write("\n")
        }
        writer.write("# SIG: ${signRows(rows)}\n")
        writer.flush()
    }
}

fun loadFromCsv(context: Context, uri: Uri): List<RatingEntry> {
    val entries = mutableListOf<RatingEntry>()
    val dataRows = mutableListOf<String>()
    var signature: String? = null
    try {
        val lines = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)?.readLines() ?: return emptyList()

        for (line in lines) {
            try {
                when {
                    // Importing data must never replace the user's own
                    // selectable tag list — skip the header.
                    line.startsWith("# AVAILABLE_TAGS:") -> continue
                    line.startsWith("ID,Score,Timestamp") -> continue
                    line.startsWith("# SIG:") -> {
                        signature = line.substringAfter("# SIG:").trim()
                        continue
                    }
                    line.isBlank() -> continue
                }
                dataRows.add(line)
                val parts = mutableListOf<String>()
                var currentPart = StringBuilder()
                var inQuotes = false
                for (char in line) {
                    when {
                        char == '"' -> inQuotes = !inQuotes
                        char == ',' && !inQuotes -> {
                            parts.add(currentPart.toString())
                            currentPart = StringBuilder()
                        }
                        else -> currentPart.append(char)
                    }
                }
                parts.add(currentPart.toString())
                if (parts.size >= 7) {
                    entries.add(
                        RatingEntry(
                            id = parts[0].toLongOrNull() ?: 0L,
                            score = parts[1].toIntOrNull() ?: 0,
                            timestamp = parts[2].toLongOrNull() ?: 0L,
                            hourLabel = parts[4],
                            note = parts[5],
                            tags = if (parts[6].isNotBlank()) parts[6].split("|").filter { it.isNotEmpty() } else emptyList()
                        )
                    )
                }
            } catch (e: Exception) {
                // skip malformed line
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return emptyList()
    }

    // Tamper check (release builds only)
    if (!isDebuggableBuild(context) && signature != signRows(dataRows)) {
        Toast.makeText(
            context,
            "Import rejected: this file was modified or wasn't exported by Beeing.",
            Toast.LENGTH_LONG
        ).show()
        return emptyList()
    }
    return entries
}

fun scheduleAutoBackup(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        return
    }
    val intent = Intent(context, BackupReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 5)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (before(Calendar.getInstance())) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    alarmManager.cancel(pendingIntent)
    alarmManager.setRepeating(
        android.app.AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        24 * 60 * 60 * 1000L,
        pendingIntent
    )
}

fun cancelAutoBackup(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    val intent = Intent(context, BackupReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    alarmManager.cancel(pendingIntent)
}

class BackupReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("b", 0)
        val backupUriString = prefs.getString("backup_uri", null) ?: return
        try {
            val backupUri = Uri.parse(backupUriString)
            val ratings = loadRatings(context)
            val fileName = "bee_backup.csv"
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                backupUri,
                android.provider.DocumentsContract.getTreeDocumentId(backupUri)
            )
            val resolver = context.contentResolver
            val childUri = android.provider.DocumentsContract.createDocument(
                resolver, docUri, "text/csv", fileName
            )
            childUri?.let {
                saveToCsv(context, it, ratings)
                prefs.edit { putLong("last_backup", System.currentTimeMillis()) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        scheduleAutoBackup(context)
    }
}

// --- ACTIVE WINDOW ---
// The stable hour range the day-strip and pattern grid render. Smallest
// contiguous range covering ~90% of the hours rated in the trailing 30 days,
// never narrower than 8 hours. Ratings outside it still count everywhere —
// the UI shows them via compact "early/late" cap rows instead of dead rows.
const val EDIT_WINDOW_MS = 10L * 3600_000L // ratings editable up to 10h back

fun computeActiveWindow(ratings: List<RatingEntry>): IntRange {
    val cutoff = System.currentTimeMillis() - 30L * 24 * 3600_000L
    val counts = IntArray(24)
    var total = 0
    val cal = Calendar.getInstance()
    for (e in ratings) {
        if (e.timestamp < cutoff) continue
        cal.timeInMillis = e.timestamp
        counts[cal.get(Calendar.HOUR_OF_DAY)]++
        total++
    }
    if (total == 0) return 7..22

    var lo = 0
    var hi = 23
    while (lo < hi && counts[lo] == 0) lo++
    while (hi > lo && counts[hi] == 0) hi--

    var kept = 0
    for (h in lo..hi) kept += counts[h]
    val minKeep = Math.ceil(total * 0.9).toInt()
    while (hi - lo >= 8) {
        val trimLow = counts[lo] <= counts[hi]
        val edgeCount = if (trimLow) counts[lo] else counts[hi]
        if (kept - edgeCount < minKeep) break
        kept -= edgeCount
        if (trimLow) lo++ else hi--
    }
    // A strip narrower than 8 hours looks broken; widen symmetrically
    while (hi - lo < 7) {
        if (lo > 0) lo-- else if (hi < 23) hi++ else break
    }
    return lo..hi
}

fun calculateStreak(r: List<RatingEntry>): Int {
    if (r.isEmpty()) return 0
    fun hasRatingForHour(offset: Int): Boolean {
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -offset) }
        val targetHour = cal.get(Calendar.HOUR_OF_DAY)
        val targetDay = cal.get(Calendar.DAY_OF_YEAR)
        val targetYear = cal.get(Calendar.YEAR)
        return r.any { entry ->
            val c = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            c.get(Calendar.HOUR_OF_DAY) == targetHour &&
                    c.get(Calendar.DAY_OF_YEAR) == targetDay &&
                    c.get(Calendar.YEAR) == targetYear
        }
    }
    val startOffset = when {
        hasRatingForHour(0) -> 0
        hasRatingForHour(1) -> 1
        hasRatingForHour(2) -> 2
        else -> return 0
    }
    var streak = 1
    var currentOffset = startOffset + 1
    while (hasRatingForHour(currentOffset)) {
        streak++
        currentOffset++
    }
    return streak
}