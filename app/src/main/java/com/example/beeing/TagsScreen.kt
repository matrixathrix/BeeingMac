package com.example.beeing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.beeing.ui.BeeButtonDefaults
import com.example.beeing.ui.BeeCardDefaults
import com.example.beeing.ui.PageHeader
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.tagGlyphOf
import com.example.beeing.ui.icons.tagLabelOf
import com.example.beeing.ui.icons.tagSym
import kotlin.math.roundToInt

private const val MAX_TAGS = 30
private const val TAG_NAME_MAX = 20

/**
 * A tag split for this list: (glyph, name). Built-ins and leading emoji come from
 * tagGlyphOf/tagLabelOf; a TRAILING emoji ("FeelGood😇") is lifted into the tile
 * too, so every row reads tile · name. Display only: the stored string is the
 * tag's identity and is never rewritten.
 */
private fun tagParts(tag: String): Pair<String?, String> {
    val label = tagLabelOf(tag)
    tagGlyphOf(tag)?.let { return it to label }
    val lastWord = label.indexOfLast { it.isLetterOrDigit() }
    val tail = if (lastWord >= 0) label.substring(lastWord + 1).trim() else ""
    return if (tail.isNotEmpty() && tail.none { it.isLetterOrDigit() })
        tail to label.substring(0, lastWord + 1).trim()
    else null to label
}

/** Tags must never carry the storage separators (`,` `|` `;`). */
private fun sanitizeTag(raw: String) = raw.replace(Regex("[,;|]"), " ").trim()

/**
 * The tag list as its own page, pushed from Settings like Trends is from the
 * journal. One grouped card of rows in the Settings row language (neutral tile,
 * name, quiet grip): tap a row to rename or delete it, hold and drag to reorder,
 * "New" in the header adds one. Saves on every change; edits are picker-only, so
 * old entries keep the name they were rated with.
 */
@Composable
fun TagsScreen(context: android.content.Context, onChanged: () -> Unit, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    val tags = remember { loadTags(context).toMutableStateList() }
    fun persist() { saveTags(context, tags.toList()); onChanged() }

    var draggingTag by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var editingTag by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    // Every row is the same fixed height (its divider is drawn inside it), so
    // one row height is exactly one drag step.
    val rowHeight = 58.dp
    val rowStepPx = with(LocalDensity.current) { rowHeight.toPx() }
    val canAdd = tags.size < MAX_TAGS

    // Edge auto-scroll: while a row is held within EDGE of the list's top or
    // bottom, the list scrolls (faster the closer to the edge) and the held row
    // rides along, so a tag can travel the whole list in one drag.
    val scroll = rememberScrollState()
    var viewportHeight by remember { mutableIntStateOf(0) }
    var fingerY by remember { mutableFloatStateOf(0f) } // in the list viewport
    val edgePx = with(LocalDensity.current) { 72.dp.toPx() }
    val maxStepPx = with(LocalDensity.current) { 14.dp.toPx() } // per frame

    // Moves the held tag to whichever slot its row now sits over.
    fun reorder() {
        val tag = draggingTag ?: return
        val from = tags.indexOf(tag)
        val to = (from + (dragOffset / rowStepPx).roundToInt()).coerceIn(0, tags.lastIndex)
        if (to != from) {
            tags.removeAt(from)
            tags.add(to, tag)
            dragOffset -= (to - from) * rowStepPx
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(draggingTag) {
        if (draggingTag == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val h = viewportHeight.toFloat()
            val v = when {
                fingerY < edgePx -> -maxStepPx * ((edgePx - fingerY) / edgePx).coerceAtMost(1f)
                fingerY > h - edgePx -> maxStepPx * ((fingerY - (h - edgePx)) / edgePx).coerceAtMost(1f)
                else -> 0f
            }
            if (v != 0f) {
                // Content moved under a still finger: the row follows it.
                dragOffset += scroll.scrollBy(v)
                reorder()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow touches so Settings underneath stays inert
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        PageHeader("Tags", onBack = onBack, backLabel = "Back to settings") {
            TextButton(onClick = { adding = true }, enabled = canAdd) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New", fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "${tags.size} of $MAX_TAGS · tap to rename, hold to reorder",
            fontSize = 12.5.sp,
            color = hint,
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .onSizeChanged { viewportHeight = it.height }
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = BeeCardDefaults.colors(),
                elevation = BeeCardDefaults.elevation(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    tags.forEachIndexed { index, tag ->
                        key(tag) {
                            val isDragging = draggingTag == tag
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            translationY = dragOffset
                                            shadowElevation = 16f
                                        }
                                    }
                                    .background(
                                        if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent
                                    )
                                    .pointerInput(tag) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { at ->
                                                draggingTag = tag
                                                dragOffset = 0f
                                                // The list's card starts at content y 0.
                                                fingerY = tags.indexOf(tag) * rowStepPx - scroll.value + at.y
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                fingerY += amount.y
                                                reorder()
                                            },
                                            onDragEnd = {
                                                draggingTag = null
                                                dragOffset = 0f
                                                persist()
                                            },
                                            onDragCancel = {
                                                draggingTag = null
                                                dragOffset = 0f
                                            }
                                        )
                                    }
                                    .clickable { editingTag = tag }
                            ) {
                                if (index > 0 && !isDragging) HorizontalDivider(
                                    Modifier.padding(start = 62.dp),
                                    color = hint.copy(alpha = 0.12f)
                                )
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TagTile(tag)
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        tagParts(tag).second,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Grip(hint.copy(alpha = 0.45f))
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Renaming or deleting changes the picker only. Past hours keep their tags.",
                fontSize = 12.sp,
                color = hint,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp)
            )
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (adding) {
        TagNameDialog(
            title = "New tag",
            initial = "",
            confirmLabel = "Add",
            supporting = "${tags.size}/$MAX_TAGS tags",
            isTaken = { it in tags },
            onConfirm = { name ->
                tags.add(name)
                persist()
                adding = false
            },
            onDismiss = { adding = false }
        )
    }

    editingTag?.let { tag ->
        TagNameDialog(
            title = "Rename tag",
            initial = tag,
            confirmLabel = "Save",
            supporting = null,
            isTaken = { it != tag && it in tags },
            onConfirm = { name ->
                val i = tags.indexOf(tag)
                if (i >= 0 && name != tag) { tags[i] = name; persist() }
                editingTag = null
            },
            onDismiss = { editingTag = null },
            onDelete = { editingTag = null; confirmDelete = tag }
        )
    }

    // Confirm before deleting: it can't be undone from here.
    confirmDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${tagParts(tag).second}\"?") },
            text = { Text("Removed from the picker. Past hours keep it.", fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = {
                    tags.remove(tag)
                    persist()
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

/**
 * Add and rename share one dialog: title, a full-width field with its counter (or
 * the duplicate warning) under it, then ONE action row. Rename puts "Delete" at
 * that row's start, away from Save, so the destructive action never floats on
 * its own line between the field and the buttons.
 */
@Composable
private fun TagNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    supporting: String?,
    isTaken: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val name = sanitizeTag(text.text)
    val taken = name.isNotBlank() && isTaken(name)
    val canSave = name.isNotBlank() && !taken
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.text.length <= TAG_NAME_MAX) text = it },
                    singleLine = true,
                    isError = taken,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(name) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp)) {
                    Text(
                        if (taken) "You already have this tag" else supporting ?: "",
                        fontSize = 12.sp,
                        color = if (taken) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${text.text.length}/$TAG_NAME_MAX",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        colors = BeeButtonDefaults.colors(),
                        enabled = canSave,
                        onClick = { onConfirm(name) }
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}

/** The Settings tile, holding the tag's icon, the user's own emoji, or a monogram. */
@Composable
private fun TagTile(tag: String) {
    val sym = tagSym(tag)
    val (glyph, name) = tagParts(tag)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            sym != null -> BeeIcon(sym, size = 20.dp, tint = tint, contentDescription = null)
            glyph != null -> Text(glyph, fontSize = 17.sp)
            else -> Text(
                name.take(1).uppercase(),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint
            )
        }
    }
}

/** A quiet 3×2 dot grip: says "this moves" without looking like a button. */
@Composable
private fun Grip(color: Color) {
    Canvas(Modifier.size(20.dp, 14.dp)) {
        val r = 1.5.dp.toPx()
        for (x in listOf(0.2f, 0.5f, 0.8f)) for (y in listOf(0.3f, 0.7f)) {
            drawCircle(color, r, Offset(size.width * x, size.height * y))
        }
    }
}
