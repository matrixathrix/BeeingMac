package com.example.beeing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
        val currentMinute = get(Calendar.MINUTE)
        val currentSecond = get(Calendar.SECOND)

        if (currentMinute == 0 && currentSecond < 5) {
            add(Calendar.HOUR_OF_DAY, 1)
        } else {
            add(Calendar.HOUR_OF_DAY, 1)
        }
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
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            scheduleExactHourlyAlarm(context)
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, "hourly_bee")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to check in 🐝")
            .setContentText("How was your last hour?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(1, notification)
        scheduleExactHourlyAlarm(context)
    }
}

// --- TAGS MANAGEMENT (defined early to avoid forward reference issues) ---
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
    existing.add(entry)

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

        // Write available tags list as first line (special metadata)
        val availableTags = loadTags(c)
        writer.write("# AVAILABLE_TAGS: ${availableTags.joinToString("|")}\n")

        // Write header
        writer.write("ID,Score,Timestamp,Recording_Time,Hour_Label,Note,Tags\n")

        // Write each rating entry
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
                    // First line: check for available tags metadata
                    if (lineNumber == 0 && line.startsWith("# AVAILABLE_TAGS:")) {
                        val tagsString = line.substringAfter("# AVAILABLE_TAGS:").trim()
                        if (tagsString.isNotBlank()) {
                            val tags = tagsString.split("|").filter { it.isNotBlank() }
                            saveTags(context, tags)
                        }
                        lineNumber++
                        return@forEachLine
                    }

                    // Skip header row
                    if (line.startsWith("ID,Score,Timestamp")) {
                        lineNumber++
                        return@forEachLine
                    }

                    // Parse CSV with quoted fields
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

                    // Parse fields: ID, Score, Timestamp, Recording_Time, Hour_Label, Note, Tags
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
                    // Skip malformed lines
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
                resolver,
                docUri,
                "text/csv",
                fileName
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

// --- STREAK CALCULATION ---
fun calculateStreak(r: List<RatingEntry>): Int {
    if (r.isEmpty()) return 0

    // Helper: Checks if there's a rating for a specific hour offset
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

    // Determine starting point based on the rules:
    // 1. If current hour (offset 0) or previous hour (offset 1) has a rating, start from that hour
    // 2. If neither has a rating but the hour before that (offset 2) has one, start from offset 2
    // 3. Otherwise return 0

    val startOffset = when {
        hasRatingForHour(0) -> 0  // Current hour has rating
        hasRatingForHour(1) -> 1  // Previous hour has rating
        hasRatingForHour(2) -> 2  // Hour before the previous has rating
        else -> return 0  // None of the relevant hours have ratings
    }

    // Count streak starting from the determined hour
    var streak = 1
    var currentOffset = startOffset + 1

    // Look for ratings in each preceding hour
    while (hasRatingForHour(currentOffset)) {
        streak++
        currentOffset++
    }

    return streak
}