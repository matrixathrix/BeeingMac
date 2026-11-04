package com.example.beeing

// What the phone tells the watch. Replaced as one Data Layer item on every change.

data class PhoneState(
    val syncedAtMs: Long,
    val tags: List<String>,              // the user's full list, in their order
    val topTags: List<String>,           // the 8 the watch shows first (see topTags)
    val modeEvents: List<ModeEvent>,
    val prefs: NotificationPrefs,        // silent hours and pause, for the watch's own reminders
    val carry: ReplayCarry,              // the replay at the end of the day before yesterday
    val recentRatings: List<RatingEntry>, // yesterday + today, every origin
    val mergedWatchIds: List<Long>       // watch ratings the phone has applied (last 2 days)
)
