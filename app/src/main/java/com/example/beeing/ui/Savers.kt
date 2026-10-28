package com.example.beeing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.RECOVERY_DAYS
import com.example.beeing.RECOVERY_HOURS
import com.example.beeing.COMEBACK_GIFT
import com.example.beeing.COMEBACK_IDLE_DAYS
import com.example.beeing.SAVER_CAP
import com.example.beeing.SAVER_EARN_HOURS
import com.example.beeing.ui.icons.BeeIcon
import com.example.beeing.ui.icons.Sym
import com.example.beeing.ui.icons.SymTitle
import com.example.beeing.ui.theme.HoneyGold

/**
 * [SAVER_CAP] shield slots: honey shields up to [savers], the rest the same shield
 * faded back, so earning one visibly "lights it up". Drawn for dark surfaces (the
 * brand band, the celebration); [highlightIndex] pops one slot.
 */
@Composable
fun SaverSlots(
    savers: Int,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    emptyColor: Color = Color.White.copy(alpha = 0.22f)
) {
    Row(
        modifier.semantics { contentDescription = "$savers of $SAVER_CAP streak savers" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(SAVER_CAP) { i ->
            BeeIcon(
                Sym.Saver, size = 26.dp,
                tint = if (i < savers) HoneyGold else emptyColor,
                contentDescription = null,
                modifier = Modifier.scale(if (highlightIndex == i) 1.25f else 1f)
            )
        }
    }
}

/** How savers, fading, the comeback gift and recovery work — shared by the Streak tab and Settings. */
@Composable
fun SaverExplainerDialog(savers: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { SymTitle(Sym.Saver, "Streak savers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You have $savers of $SAVER_CAP.", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(0.dp))
                listOf(
                    "Rate $SAVER_EARN_HOURS hours in one day to earn a saver. You can hold $SAVER_CAP.",
                    "Miss a day and a saver steps in on its own, so your streak holds. It doesn't grow.",
                    "Savers fade when you miss a day with no streak running. Start one to keep them.",
                    "$COMEBACK_IDLE_DAYS days in a row with no complete day? $COMEBACK_GIFT savers on the house.",
                    "Out of savers and miss a day? The streak breaks, but you get $RECOVERY_DAYS days to win it back. Rate $RECOVERY_HOURS hours on any one of them.",
                    "Hours added later never count toward any of this."
                ).forEach { Text("• $it", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it!") } }
    )
}
