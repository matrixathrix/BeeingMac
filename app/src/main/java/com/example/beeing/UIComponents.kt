package com.example.beeing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import com.example.beeing.ui.BeeButtonDefaults
import com.example.beeing.ui.beeChipColors
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.icons.tagDisplayText
import com.example.beeing.ui.icons.tagLabelOf
import com.example.beeing.ui.icons.tagSym
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
//import androidx.compose.ui.layout.onGloballyPositioned
//import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryPanel(ratings: List<RatingEntry>, onEdit: (RatingEntry) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        val recentRatings = ratings.toList().take(10)

        recentRatings.forEach { item ->
            key(item.id) {
                val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
                val startH = cal.get(Calendar.HOUR_OF_DAY)
                val endH = if (startH == 23) 0 else startH + 1

                    val displayCal = cal
                    val dateStr = SimpleDateFormat("MMM dd").format(displayCal.time)
                    val range = "${formatHour(startH)} - ${formatHour(endH)}"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(item) }
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$dateStr, $range (${item.hourLabel} hour)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val shownTags = item.visibleTags()
                            if (shownTags.isNotEmpty() || item.isReclaimed) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // clock icon = added later via "Add a past hour"
                                    if (item.isReclaimed) {
                                        BeeIcon(
                                            Sym.FromMemory, size = 12.dp,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            "Added later", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                    }
                                    Text(
                                        shownTags.joinToString(", ") { tagDisplayText(it) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    when {
                                        item.score >= 8 -> Color(0xFF66BB6A)
                                        item.score >= 5 -> Color(0xFFFFB300)
                                        else -> Color(0xFFB71C1C)
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${item.score}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = if (item.score >= 5) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
}

@Composable
fun HeaderSection(
    onImport: () -> Unit,
    onExport: () -> Unit,
    onMenuClick: () -> Unit,
    onStreakClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .padding(top = 48.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.clickable { onStreakClick() }) {
            Text(
                "Beeing",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, "How it works", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Compact overline used to group the edit sheet into Score / Tags / Note.
@Composable
private fun SectionLabel(text: String, top: Dp) {
    Spacer(Modifier.height(top))
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditEntrySheet(
    entry: RatingEntry,
    dayEntries: List<RatingEntry>,
    onPersist: (RatingEntry) -> Unit,
    onClose: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val availableTags = remember { loadTags(context) }

    // Today's rated hours, oldest → newest — what the ‹ › carets step through.
    val sorted = remember(dayEntries) { dayEntries.sortedBy { it.timestamp } }

    // The hour currently on screen; carets swap it without closing the sheet.
    var currentEntry by remember { mutableStateOf(entry) }
    val index = sorted.indexOfFirst { it.id == currentEntry.id }
    val hasPrev = index > 0
    val hasNext = index in 0 until sorted.size - 1

    // Editable only inside the 10h window (EDIT_WINDOW_MS, same rule as the
    // Now tab's today strip). Older entries open as a read-only record —
    // score/tags/note locked, delete still available. Re-evaluated per hour
    // so the ‹ › carets can't sneak an old entry past the gate.
    val editable = remember(currentEntry.timestamp) {
        System.currentTimeMillis() - currentEntry.timestamp <= EDIT_WINDOW_MS
    }

    // Edited buffer + tag selection reset whenever we land on a new hour.
    var editedEntry by remember(currentEntry.id) { mutableStateOf(currentEntry) }
    val selectedTags = remember(currentEntry.id) { currentEntry.tags.toMutableStateList() }

    val hasUnsavedEdits = editedEntry.score != currentEntry.score ||
            editedEntry.note != currentEntry.note ||
            selectedTags.toSet() != currentEntry.tags.toSet()

    // A caret tapped mid-edit stashes the destination and asks save-or-discard.
    var pendingTarget by remember { mutableStateOf<RatingEntry?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun seekTo(target: RatingEntry) {
        if (hasUnsavedEdits) pendingTarget = target else currentEntry = target
    }

    val cal = remember(currentEntry.timestamp) { Calendar.getInstance().apply { timeInMillis = currentEntry.timestamp } }
    val startH = cal.get(Calendar.HOUR_OF_DAY)
    val endH = if (startH == 23) 0 else startH + 1
    val dateLabel = remember(currentEntry.timestamp) { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(cal.time) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (editable) "Edit entry" else "Entry details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(top = 6.dp)
                )
                // Time period + seek carets in a pill, with the date centered below.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { sorted.getOrNull(index - 1)?.let { seekTo(it) } },
                                enabled = hasPrev,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, "Previous rated hour")
                            }
                            Text(
                                text = "${formatHour(startH)} – ${formatHour(endH)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { sorted.getOrNull(index + 1)?.let { seekTo(it) } },
                                enabled = hasNext,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, "Next rated hour")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (editable) {
                SectionLabel("SCORE", top = 18.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    (1..10).forEach { score ->
                        val isSelected = editedEntry.score == score
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 42.dp else 36.dp)
                                .background(
                                    when {
                                        score >= 8 -> Color(0xFF66BB6A).copy(alpha = if (isSelected) 1f else 0.3f)
                                        score >= 5 -> Color(0xFFFFB300).copy(alpha = if (isSelected) 1f else 0.3f)
                                        else -> Color(0xFFB71C1C).copy(alpha = if (isSelected) 1f else 0.3f)
                                    },
                                    shape = CircleShape
                                )
                                .clickable(enabled = editable) {
                                    editedEntry = editedEntry.copy(score = score)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$score",
                                fontSize = if (isSelected) 15.sp else 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (score >= 5) Color.Black else Color.White
                            )
                        }
                    }
                }

                SectionLabel("TAGS", top = 14.dp)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    availableTags.forEach { tag ->
                        InputChip(
                            selected = tag in selectedTags,
                            onClick = {
                                if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                            },
                            enabled = editable,
                            label = { TagChipContent(tag) },
                            shape = TagChipShape,
                            colors = tagChipColors(),
                            border = null
                        )
                    }
                }

                SectionLabel("NOTE", top = 14.dp)
                OutlinedTextField(
                    value = editedEntry.note,
                    onValueChange = { editedEntry = editedEntry.copy(note = it) },
                    enabled = editable,
                    placeholder = { Text("Add a note") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(0.2f)
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    run {
                        Button(
                            colors = BeeButtonDefaults.colors(),
                            onClick = {
                                onPersist(editedEntry.copy(tags = selectedTags.toList()))
                                onClose()
                            },
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text("Save Changes")
                        }
                    }
                }
            } else {
                // Past the 10h window you're here to read, not edit: what you
                // logged and nothing else, then one quiet line saying why it's
                // locked, with delete at its end.
                ReadOnlyEntry(entry = currentEntry, onDelete = { confirmDelete = true })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Deleting is one confirm away, never a single tap. The undo snackbar is
    // shown by the caller, which outlives this sheet.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this hour?") },
            text = { Text("The rating for ${formatHour(startH)} – ${formatHour(endH)}, $dateLabel will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(currentEntry.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    // Save-or-discard before a caret moves us off an edited hour.
    pendingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingTarget = null },
            title = { Text("Save changes?") },
            text = { Text("You have unsaved edits to this hour.") },
            confirmButton = {
                TextButton(onClick = {
                    onPersist(editedEntry.copy(tags = selectedTags.toList()))
                    currentEntry = target
                    pendingTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    currentEntry = target
                    pendingTarget = null
                }) { Text("Discard") }
            }
        )
    }
}

/**
 * An hour past the edit window, as a finished entry: the score as one big
 * coloured number with its word, when it was rated (or "Added later"), only the
 * tags that were picked, the note or "No note", and a footer line that says why
 * it's locked, with delete at its end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadOnlyEntry(entry: RatingEntry, onDelete: () -> Unit) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(60.dp)
                .background(scoreBandColor(entry.score), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${entry.score}",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = dialDigitColor(entry.score)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                (DIAL_WORDS[entry.score] ?: "").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (entry.isReclaimed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BeeIcon(Sym.FromMemory, size = 14.dp, tint = hint, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Added later", fontSize = 12.sp, color = hint)
                }
            } else {
                // id is the wall-clock time it was saved; only trust it when it
                // falls after the hour began (imports may carry other ids).
                val savedAt = entry.id.takeIf { it >= entry.timestamp && it <= System.currentTimeMillis() }
                if (savedAt != null) Text(
                    "Rated " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(savedAt)).lowercase(),
                    fontSize = 12.sp,
                    color = hint
                )
            }
        }
    }

    val tags = entry.visibleTags()
    if (tags.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach { tag ->
                Surface(shape = TagChipShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) { TagChipContent(tag) }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    if (entry.note.isNotBlank()) {
        Text(entry.note, style = MaterialTheme.typography.bodyLarge)
    } else {
        Text("No note", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = hint)
    }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = hint.copy(alpha = 0.15f))
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BeeIcon(Sym.Lock, size = 16.dp, tint = hint, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            "Locked · hours can be edited for 10 hours",
            fontSize = 12.sp,
            color = hint,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDelete) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}

// Shared by every InputChip tag picker (NowTab's collapsed picker, this
// section, EditEntrySheet) so the fixed-palette look can't drift out of
// sync across call sites. Fixed orange/tan, matching the reference
// screenshot's tag chips, in place of Material You's dynamic primary.
/**
 * Fully-rounded tag chips: 50% of the chip's own height, so the sides are
 * semicircular caps at any text length. Shared for the same reason the colors
 * are — four call sites, one look.
 */
internal val TagChipShape = RoundedCornerShape(50)

/**
 * A chip's contents: a built-in tag shows its Material Symbol and its name with
 * the stored emoji stripped; a tag the user wrote is left exactly as typed,
 * because their emoji is their choice, not app chrome. Lives in the `label`
 * slot rather than `leadingIcon` so all four chip call sites stay one-liners.
 */
@Composable
internal fun TagChipContent(tag: String) {
    val sym = tagSym(tag)
    if (sym == null) {
        Text(tag)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BeeIcon(sym, size = 15.dp, contentDescription = null)
            Spacer(Modifier.width(5.dp))
            Text(tagLabelOf(tag))
        }
    }
}

@Composable
internal fun tagChipColors() = beeChipColors()

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SoulFuelTagsSection(
    allRatings: List<RatingEntry>,
    availableTags: List<String>,
    selectedTags: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    isEnabled: Boolean,
    onTagsUpdate: (List<String>) -> Unit,
    onManageTags: () -> Unit,
    onCollapse: (() -> Unit)? = null
) {
    // Centered chips that read as part of the rating dial above; the single
    // pencil opens the manage-tags popup (reorder / rename / delete / add).
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(-8.dp, Alignment.CenterVertically)
        ) {
            availableTags.forEach { tag ->
                InputChip(
                    selected = tag in selectedTags,
                    onClick = {
                        if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                    },
                    label = { TagChipContent(tag) },
                    enabled = isEnabled,
                    shape = TagChipShape,
                    colors = tagChipColors(),
                    border = null
                )
            }

            IconButton(
                onClick = onManageTags,
                enabled = isEnabled,
                modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Manage tags",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // "Show less" rides in the same flow line as the chips so the
            // expanded editor spends no extra vertical space on it.
            if (onCollapse != null) {
                // Same chip type and metrics as the tags (and as "+N more"): a
                // 32dp pill inside the 48dp touch row the FlowRow's -8dp line
                // spacing is tuned for. A 30dp button here got pulled up into the
                // row above by that same -8dp.
                AssistChip(
                    onClick = onCollapse,
                    label = { Text("Show less") },
                    leadingIcon = {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = TagChipShape,
                    border = null,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun NotesSection(
    noteText: String,
    onNoteChange: (String) -> Unit,
    enabled: Boolean = true
) {
    // Deliberately low-contrast and collapsed by default — an optional
    // afterthought, not a call to action. A slim "+ Add a note" row until
    // tapped; then a compact single-row field at hint-text emphasis.
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by remember { mutableStateOf(noteText.isNotEmpty()) }
    var wantFocus by remember { mutableStateOf(false) }
    // onFocusChanged fires once with isFocused=false when the field first
    // attaches, so only collapse after the field has genuinely held focus.
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (!expanded && noteText.isEmpty()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { expanded = true; wantFocus = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add, contentDescription = null,
                tint = hint.copy(alpha = 0.55f), modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text("Add a note", fontSize = 13.sp, color = hint.copy(alpha = 0.55f))
        }
    } else {
        BasicTextField(
            value = noteText,
            onValueChange = onNoteChange,
            enabled = enabled,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = hint),
            cursorBrush = SolidColor(hint),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        hadFocus = true
                    } else if (hadFocus && noteText.isEmpty()) {
                        expanded = false
                        hadFocus = false
                    }
                },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, hint.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (noteText.isEmpty()) {
                        Text("Add a note", fontSize = 14.sp, color = hint.copy(alpha = 0.4f))
                    }
                    inner()
                }
            }
        )
        LaunchedEffect(wantFocus) {
            if (wantFocus) { focusRequester.requestFocus(); wantFocus = false }
        }
    }
}

// ==============================================
// HELPER FUNCTIONS
// ==============================================

fun isSameDay(c1: Calendar, c2: Calendar) =
    c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
            c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)


fun formatHour(h: Int) = "${if(h%12==0) 12 else h%12}${if(h<12)"am" else "pm"}"

fun getOrdinalSuffix(n: Int) = when {
    n in 11..13 -> "th"
    n%10==1 -> "st"
    n%10==2 -> "nd"
    n%10==3 -> "rd"
    else -> "th"
}

fun getPreviousHourLabel(): String {
    val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -1) }
    val endH = cal.get(Calendar.HOUR_OF_DAY)
    return "${if (endH == 0) 24 else endH}${getOrdinalSuffix(if (endH == 0) 24 else endH)}"
}