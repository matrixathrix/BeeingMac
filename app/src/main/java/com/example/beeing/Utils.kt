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

enum class ChartView { HOURLY, DAY, WEEK, MONTH }

// --- NOTIFICATIONS ---
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val soundUri = Uri.parse(
            "android.resource://${context.packageName}/${R.raw.beeping_android_buzz}"
        )
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            "hourly_bee",
            "Hourly Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
            }
            "ACTION_SELECT_SCORE" -> {
                // Pass existing timestamp/label through to the next stage
                val score = intent.getIntExtra("SCORE", 1)
                val ts = intent.getLongExtra("TARGET_TS", 0L)
                val label = intent.getStringExtra("TARGET_LABEL") ?: ""

                showCustomNotification(context, manager, score, ts, label)
            }
            "ACTION_SUBMIT_SCORE" -> {
                val score = intent.getIntExtra("SCORE", 1)
                val ts = intent.getLongExtra("TARGET_TS", 0L)
                val label = intent.getStringExtra("TARGET_LABEL") ?: ""

                // Double-check we have valid data
                if (ts > 0) {
                    val entry = RatingEntry(
                        id = System.currentTimeMillis(),
                        score = score,
                        timestamp = ts,
                        hourLabel = label,
                        note = "",
                        tags = emptyList()
                    )
                    saveRating(context, entry)
                    Toast.makeText(context, "Saved score $score for $label!", Toast.LENGTH_SHORT).show()
                }

                manager.cancel(1)
            }
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

                showCustomNotification(context, manager, null, targetTimestamp, ordinal)
                scheduleExactHourlyAlarm(context)
            }
        }
    }

    private fun showCustomNotification(
        context: Context,
        manager: NotificationManager,
        selectedScore: Int?,
        targetTs: Long,
        targetLabel: String
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_rating)

        // 1. Format the text
        val cal = Calendar.getInstance().apply { timeInMillis = targetTs }
        val startH = cal.get(Calendar.HOUR_OF_DAY)
        val endH = if (startH == 23) 0 else startH + 1

        val rangeText = "${formatH(startH)}-${formatH(endH)}"
        remoteViews.setTextViewText(R.id.notif_title, "How was your last hour? ($rangeText)")

        // 2. Setup Chips with Color Logic
        val buttonIds = listOf(
            R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5,
            R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_10
        )

        buttonIds.forEachIndexed { index, id ->
            val score = index + 1

            // Logic to colorize the selected button
            if (selectedScore == score) {
                // SELECTED: Solid Color Background, White Text
                val bgRes = when {
                    score >= 8 -> R.drawable.rounded_notif_btn_green
                    score >= 5 -> R.drawable.rounded_notif_btn_yellow
                    else -> R.drawable.rounded_notif_btn_red
                }
                remoteViews.setInt(id, "setBackgroundResource", bgRes)
                remoteViews.setTextColor(id, android.graphics.Color.WHITE)
            } else {
                // UNSELECTED: Neutral Background, Colored Text
                remoteViews.setInt(id, "setBackgroundResource", R.drawable.rounded_notif_btn_neutral)
                val textColor = when {
                    score >= 8 -> android.graphics.Color.parseColor("#2E7D32") // Green
                    score >= 5 -> android.graphics.Color.parseColor("#F57C00") // Orange
                    else -> android.graphics.Color.parseColor("#B71C1C") // Red
                }
                remoteViews.setTextColor(id, textColor)
            }

            // Click Intent
            val selectIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = "ACTION_SELECT_SCORE"
                putExtra("SCORE", score)
                putExtra("TARGET_TS", targetTs)
                putExtra("TARGET_LABEL", targetLabel)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, score, selectIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(id, pendingIntent)
        }

        // Detect system dark mode once — used for all button text colours
        val isDarkMode = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeTextColor = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        // 3. Open App Intent
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        remoteViews.setOnClickPendingIntent(R.id.btn_open_app, pendingIntent)
        remoteViews.setTextColor(R.id.btn_open_app, themeTextColor)

        // 4. Toggle Submit Button
        if (selectedScore != null) {
            remoteViews.setViewVisibility(R.id.btn_open_app, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.btn_submit, android.view.View.VISIBLE)
            remoteViews.setTextViewText(R.id.btn_submit, "Submit score $selectedScore")
            remoteViews.setTextColor(R.id.btn_submit, themeTextColor)

            val submitIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = "ACTION_SUBMIT_SCORE"
                putExtra("SCORE", selectedScore)
                putExtra("TARGET_TS", targetTs)
                putExtra("TARGET_LABEL", targetLabel)
            }
            val submitPendingIntent = PendingIntent.getBroadcast(
                context, 100, submitIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(R.id.btn_submit, submitPendingIntent)
        } else {
            remoteViews.setViewVisibility(R.id.btn_open_app, android.view.View.VISIBLE)
            remoteViews.setViewVisibility(R.id.btn_submit, android.view.View.GONE)
        }

        // 5. Build
        val notification = NotificationCompat.Builder(context, "hourly_bee")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(1, notification)
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
fun loadTags(c: Context): List<String> {
    val prefs = c.getSharedPreferences("b", 0)
    val tagsString = prefs.getString("tags", "Work,Health,Rest,Social") ?: "Work,Health,Rest,Social"
    return tagsString.split(",").filter { it.isNotBlank() }
}

fun saveTags(c: Context, tags: List<String>) {
    val prefs = c.getSharedPreferences("b", 0)
    prefs.edit { putString("tags", tags.joinToString(",")) }
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
fun saveToCsv(c: Context, u: Uri, r: List<RatingEntry>) {
    c.contentResolver.openOutputStream(u)?.use { stream ->
        val writer = stream.bufferedWriter(Charsets.UTF_8)
        val availableTags = loadTags(c)
        writer.write("# AVAILABLE_TAGS: ${availableTags.joinToString("|")}\n")
        writer.write("ID,Score,Timestamp,Recording_Time,Hour_Label,Note,Tags\n")
        r.forEach {
            val recordingTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.id))
            val line = "${it.id},${it.score},${it.timestamp},\"$recordingTime\",\"${it.hourLabel}\",\"${it.note}\",\"${it.tags.joinToString("|")}\"\n"
            writer.write(line)
        }
        writer.flush()
    }
}

fun loadFromCsv(context: Context, uri: Uri): List<RatingEntry> {
    val entries = mutableListOf<RatingEntry>()
    try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            var lineNumber = 0
            reader.forEachLine { line ->
                try {
                    if (lineNumber == 0 && line.startsWith("# AVAILABLE_TAGS:")) {
                        val tagsString = line.substringAfter("# AVAILABLE_TAGS:").trim()
                        if (tagsString.isNotBlank()) {
                            val tags = tagsString.split("|").filter { it.isNotBlank() }
                            saveTags(context, tags)
                        }
                        lineNumber++
                        return@forEachLine
                    }
                    if (line.startsWith("ID,Score,Timestamp")) {
                        lineNumber++
                        return@forEachLine
                    }
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
                    lineNumber++
                } catch (e: Exception) {
                    lineNumber++
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
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