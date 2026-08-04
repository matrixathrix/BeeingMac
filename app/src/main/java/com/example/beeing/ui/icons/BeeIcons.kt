package com.example.beeing.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.beeing.R

/**
 * The app's icon vocabulary — Google **Material Symbols Rounded**, vendored as
 * vector drawables in `res/drawable/ic_sym_*.xml` (converted from the official
 * SVGs; the source symbol and its licence are named in each file's header).
 *
 * These replace the emoji that used to carry meaning in UI copy. Emoji were
 * never a design system: they render differently on every OEM skin, they can't
 * be tinted, they don't scale with the text they sit beside, and screen readers
 * announce them as prose. A [Sym] is one tintable, size-controlled, described
 * glyph — so a rest day looks the same on every device and announces as
 * "rest day", not "crescent moon".
 *
 * Two things deliberately keep their emoji and are NOT in this enum:
 *  - **tag names** (`DEFAULT_TAGS`, e.g. "💼 Work") — those are user-editable
 *    data, not chrome; the user picks the glyph and it round-trips through
 *    storage and CSV export.
 *  - the reclaim sentinel `RECLAIM_TAG` ("💧reclaimed") — a persisted string
 *    that must never change.
 */
enum class Sym(@DrawableRes val res: Int, val label: String) {
    /**
     * ⬢ — a **complete day**: the unit, not the run of them. Log rows and the
     * day-stats popup use this; anything counting the streak itself uses [Fire].
     */
    Streak(R.drawable.ic_sym_hexagon, "complete day"),

    /** The streak itself — the run of complete days, wherever it is counted. */
    Fire(R.drawable.ic_sym_fire, "streak"),

    /** 🌱 — beginner mode / a completed 4-hour beginner day. */
    Beginner(R.drawable.ic_sym_beginner, "beginner day"),

    /** 🌙 — the one forgiven day a week. */
    Rest(R.drawable.ic_sym_rest, "rest day"),

    /** 🐝 — sending a bee back: reclaiming a missed hour. */
    Reclaim(R.drawable.ic_sym_reclaim, "send back"),

    /** 🕐 — this hour was rated from memory, not in the moment. */
    FromMemory(R.drawable.ic_sym_from_memory, "rated from memory"),

    Lock(R.drawable.ic_sym_lock, "lock"),
    Hourglass(R.drawable.ic_sym_hourglass, "time left"),
    Reset(R.drawable.ic_sym_reset, "streak reset"),
    Star(R.drawable.ic_sym_star, "best"),
    Sparkle(R.drawable.ic_sym_sparkle, "highlight"),
    Alarm(R.drawable.ic_sym_alarm, "hourly reminder"),
    Chart(R.drawable.ic_sym_chart, "patterns"),
    Tag(R.drawable.ic_sym_tag, "tags"),
    Note(R.drawable.ic_sym_note, "note"),

    /** The app's own mark, used where the copy is about Beeing itself. */
    Hive(R.drawable.ic_sym_hive, "Beeing"),

    /** Nothing left to rate. */
    CaughtUp(R.drawable.ic_sym_check_circle, "all caught up"),

    // ---- the built-in activity tags (see tagSym) ----
    TagWork(R.drawable.ic_sym_tag_work, "work"),
    TagLearning(R.drawable.ic_sym_tag_learning, "learning"),
    TagExercise(R.drawable.ic_sym_tag_exercise, "exercise"),
    TagFood(R.drawable.ic_sym_tag_food, "food"),
    TagFamily(R.drawable.ic_sym_tag_family, "family"),
    TagSocial(R.drawable.ic_sym_tag_social, "social"),
    TagScrolling(R.drawable.ic_sym_tag_scrolling, "scrolling"),
    TagChores(R.drawable.ic_sym_tag_chores, "chores"),
    TagHobby(R.drawable.ic_sym_tag_hobby, "hobby")
}

// ============================================================
// TAG GLYPHS
// ============================================================
//
// A tag is ONE stored string — "💼 Work" — and that whole string is its
// identity: it is what sits in every RatingEntry.tags, what tag-score
// aggregation groups by, and what CSV export writes. So the built-in tags keep
// their emoji **in storage** and lose it **in display only**. Editing
// DEFAULT_TAGS to "Work" instead would silently orphan every historical entry
// tagged "💼 Work" — the picker and the history would stop matching, and Trends
// would split one tag into two.

/** The leading emoji of a tag name, or null if it doesn't start with one. */
fun tagGlyphOf(tag: String): String? {
    val t = tag.trim()
    val head = t.substringBefore(' ')
    return if (head.isNotEmpty() && head != t && head.none { it.isLetterOrDigit() }) head else null
}

/** A tag with its leading emoji stripped — what the UI shows. */
fun tagLabelOf(tag: String): String {
    val t = tag.trim()
    return if (tagGlyphOf(tag) != null) t.substringAfter(' ').trim() else t
}

/**
 * The icon for a built-in tag, matched on its **label** so it survives the
 * emoji being stripped. Null for anything the user made up — those keep their
 * own emoji (or a monogram), because guessing an icon for "Guitar practice"
 * would be worse than showing nothing.
 */
/**
 * How a tag reads in plain text (a joined list, a notification): the built-ins
 * drop their now-redundant emoji, a user's own tag keeps whatever they typed.
 */
fun tagDisplayText(tag: String): String =
    if (tagSym(tag) != null) tagLabelOf(tag) else tag.trim()

fun tagSym(tag: String): Sym? = when (tagLabelOf(tag).lowercase()) {
    "work" -> Sym.TagWork
    "learning" -> Sym.TagLearning
    "exercise" -> Sym.TagExercise
    "food" -> Sym.TagFood
    "rest" -> Sym.Rest
    "family" -> Sym.TagFamily
    "social" -> Sym.TagSocial
    "scrolling" -> Sym.TagScrolling
    "chores" -> Sym.TagChores
    "hobby" -> Sym.TagHobby
    else -> null
}

/**
 * A [Sym] at a given size and tint. Defaults to the ambient content color, so
 * inside a Button or a colored Row it picks up the right color for free.
 *
 * Pass `contentDescription = null` when the neighbouring text already says it —
 * otherwise a screen reader reads the same thing twice.
 */
/**
 * A dialog/section title led by its icon — the shape "🌱 Beginner mode" used to
 * have, without the emoji. The icon is decorative here: the title text beside it
 * already carries the meaning, so it is not announced twice.
 */
@Composable
fun SymTitle(
    sym: Sym,
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        BeeIcon(sym, size = iconSize, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun BeeIcon(
    sym: Sym,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = sym.label
) {
    Icon(
        painter = painterResource(sym.res),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}
