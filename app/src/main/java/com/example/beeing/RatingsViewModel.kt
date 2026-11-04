package com.example.beeing

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel

/**
 * Shared ViewModel to manage ratings state across Now and Past tabs.
 * This ensures both tabs observe the same data and stay synchronized.
 */
class RatingsViewModel : ViewModel() {
    // Shared state for all ratings
    var allRatings by mutableStateOf<List<RatingEntry>>(emptyList())
        private set
    
    // Refresh trigger to force UI updates
    var refreshTrigger by mutableStateOf(0)
        private set

    // Beginner/Master toggle history. Lives here rather than in each tab because
    // it is replay input: change it and the meter, calendar and log must all
    // re-derive from the same list, in the same composition.
    var modeEvents by mutableStateOf<List<ModeEvent>>(emptyList())
        private set

    /** Today's mode — the latest event. */
    val beginnerMode: Boolean get() = isBeginnerMode(modeEvents)

    /**
     * Load all ratings from storage
     */
    fun loadRatings(context: Context) {
        allRatings = com.example.beeing.loadRatings(context)
        watchStorage(context)
    }

    // A watch rating merged in the background must show up in the open UI at once, or
    // a save made from the stale copy would act on an hour that is already rated.
    private var storageListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    private fun watchStorage(context: Context) {
        if (storageListener != null) return
        val app = context.applicationContext
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ratings") {
                allRatings = com.example.beeing.loadRatings(app)
                triggerRefresh()
            }
        }
        storageListener = listener // the prefs hold listeners weakly
        app.getSharedPreferences("b", 0).registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Load the beginner/Master toggle history
     */
    fun loadModeEvents(context: Context) {
        modeEvents = com.example.beeing.loadModeEvents(context)
    }

    /**
     * Flip the mode. Appends an event rather than overwriting a flag, so past
     * days keep replaying under the mode they were actually lived in.
     */
    fun setBeginnerMode(context: Context, beginner: Boolean) {
        if (beginnerMode == beginner) return
        recordModeEvent(context, beginner)
        loadModeEvents(context)
        triggerRefresh()
    }

    /**
     * Add or update a rating
     */
    fun saveRating(context: Context, rating: RatingEntry) {
        com.example.beeing.saveRating(context, rating)
        loadRatings(context) // Reload to get fresh data
        withdrawStaleAlerts(context)
        triggerRefresh()
    }
    
    /**
     * Delete a rating
     */
    fun deleteRating(context: Context, id: Long) {
        com.example.beeing.deleteRating(context, id)
        loadRatings(context)
        withdrawStaleAlerts(context)
        triggerRefresh()
    }
    
    /**
     * Trigger UI refresh across all tabs
     */
    fun triggerRefresh() {
        refreshTrigger++
    }
}
