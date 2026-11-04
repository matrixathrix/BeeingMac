package com.example.beeing

data class RatingEntry(
    val id: Long,
    val score: Int,
    val timestamp: Long,
    val hourLabel: String,
    val note: String = "",
    val tags: List<String> = emptyList()
)
