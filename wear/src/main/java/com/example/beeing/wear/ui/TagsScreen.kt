package com.example.beeing.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Text

/**
 * Step 2: tags. The top 8 two to a row, then "More" for the rest of the list (one per
 * row), then Save, enabled at one tag. No cap on how many. The crown scrolls here.
 */
@Composable
fun TagsScreen(
    header: String, topTags: List<String>, allTags: List<String>, selected: Set<String>, notice: String?,
    onToggle: (String) -> Unit, onSave: () -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    val rest = allTags.filter { it !in topTags }
    ScalingLazyColumn(
        Modifier.fillMaxSize().background(FieldBottom),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        notice?.let { n -> item { Text(n, color = Honey, fontSize = 11.sp) } }
        item { Text(header, color = InkMuted, fontSize = 12.sp) }
        topTags.chunked(2).forEach { pair ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { t -> TagPill(t, t in selected, Modifier.weight(1f)) { onToggle(t) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (rest.isNotEmpty()) {
            if (!showAll) {
                item { TagPill("More", false, Modifier.padding(horizontal = 40.dp)) { showAll = true } }
            } else {
                rest.forEach { t -> item { TagPill(t, t in selected, Modifier.padding(horizontal = 20.dp)) { onToggle(t) } } }
            }
        }
        item {
            val n = selected.size
            HoneyPill(
                if (n == 0) "Save" else "Save · $n ${if (n == 1) "tag" else "tags"}",
                enabled = n > 0, modifier = Modifier.width(140.dp), onClick = onSave
            )
        }
    }
}
