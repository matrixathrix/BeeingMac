package com.example.beeing

// One stored rating row: `,`-separated fields, tags `;`-joined. Fields 7 and 8
// (modifiedAt, origin P/W) were appended for the watch; rows saved before them
// have six fields and read as phone saves whose last change was their creation.

fun encodeRatingRow(e: RatingEntry): String =
    "${e.id},${e.score},${e.timestamp},${e.hourLabel},${e.note},${e.tags.joinToString(";")}," +
        "${e.modifiedAt},${if (e.origin == Origin.WATCH) "W" else "P"}"

fun decodeRatingRow(line: String): RatingEntry? {
    val parts = line.split(",")
    if (parts.size < 4) return null
    val id = parts[0].toLongOrNull() ?: 0L
    return RatingEntry(
        id = id,
        score = parts[1].toIntOrNull() ?: 0,
        timestamp = parts[2].toLongOrNull() ?: 0L,
        hourLabel = parts[3],
        note = parts.getOrNull(4) ?: "",
        tags = parts.getOrNull(5)?.split(";")?.filter { it.isNotEmpty() } ?: emptyList(),
        modifiedAt = parts.getOrNull(6)?.toLongOrNull() ?: id,
        origin = if (parts.getOrNull(7) == "W") Origin.WATCH else Origin.PHONE
    )
}
