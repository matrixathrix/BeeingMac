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
    
    /**
     * Load all ratings from storage
     */
    fun loadRatings(context: Context) {
        allRatings = com.example.beeing.loadRatings(context)
    }
    
    /**
     * Add or update a rating
     */
    fun saveRating(context: Context, rating: RatingEntry) {
        com.example.beeing.saveRating(context, rating)
        loadRatings(context) // Reload to get fresh data
        triggerRefresh()
    }
    
    /**
     * Delete a rating
     */
    fun deleteRating(context: Context, id: Long) {
        com.example.beeing.deleteRating(context, id)
        loadRatings(context)
        triggerRefresh()
    }
    
    /**
     * Trigger UI refresh across all tabs
     */
    fun triggerRefresh() {
        refreshTrigger++
    }
}
