package com.example.beeing.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

val PillShape = RoundedCornerShape(50)

/** The honey "act here" pill (Rate, Next, Save). Disabled = a honey wash with honey text, never grey. */
@Composable
fun HoneyPill(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, height: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        modifier.height(height).clip(PillShape)
            .background(if (enabled) Honey else Honey.copy(alpha = 0.18f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) OnHoney else Honey, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A tag (or "More") on glass; honey once chosen. */
@Composable
fun TagPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(36.dp).clip(PillShape)
            .background(if (selected) Honey else GlassFill)
            .then(if (selected) Modifier else Modifier.border(1.dp, GlassLine, PillShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) OnHoney else Ink, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}
