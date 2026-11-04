package com.example.beeing

import java.util.Calendar

// How hours and scores are named, the same on the phone and the watch.

fun formatHour(h: Int) = "${if(h%12==0) 12 else h%12}${if(h<12)"am" else "pm"}"

/** "2pm – 3pm" for the hour starting at [hourStartMs]. */
fun hourRangeLabel(hourStartMs: Long): String {
    val h = Calendar.getInstance().apply { timeInMillis = hourStartMs }.get(Calendar.HOUR_OF_DAY)
    return "${formatHour(h)} – ${formatHour((h + 1) % 24)}"
}

/** "14th" for 1-2 PM: the ordinal of the hour's END, 24 for the hour before midnight. */
fun hourLabelFor(targetTs: Long): String {
    val start = Calendar.getInstance().apply { timeInMillis = targetTs }.get(Calendar.HOUR_OF_DAY)
    val end = start + 1
    val suffix = when {
        end in 11..13 -> "th"
        end % 10 == 1 -> "st"
        end % 10 == 2 -> "nd"
        end % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$end$suffix"
}

val DIAL_WORDS = mapOf(
    1 to "terrible", 2 to "bad", 3 to "rough", 4 to "meh", 5 to "okay",
    6 to "fine", 7 to "pretty good", 8 to "great", 9 to "excellent", 10 to "golden"
)
