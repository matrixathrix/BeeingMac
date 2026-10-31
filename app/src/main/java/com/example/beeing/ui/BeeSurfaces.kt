package com.example.beeing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.example.beeing.ui.theme.FieldBrush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beeing.ui.theme.BeeExtras
import com.example.beeing.ui.theme.DisplaySerif
import com.example.beeing.ui.theme.LocalBeeExtras

/**
 * The top of a titled page: status-bar inset, an optional back arrow, and a
 * large title in the ambient ink (field ink on Past and Trends, page ink on
 * Settings and Tags), with an optional trailing slot (Past's "Trends ›").
 * [reserveTopRow] leaves room for the fixed app top row that
 * HourlyPulseApp draws over the pager (Past needs it; Trends/Settings hide it).
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    reserveTopRow: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    ) {
        if (reserveTopRow) BandTopRowSpacer()
        Row(
            Modifier.fillMaxWidth().padding(top = if (reserveTopRow) 6.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp).size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = backLabel)
                }
            }
            // Large page titles are reflective lines too: the serif, sized up
            Text(
                title,
                style = DisplaySerif.copy(fontSize = 33.sp, lineHeight = 38.sp),
                maxLines = 1,
                modifier = Modifier.weight(1f).offset(x = if (onBack != null) (-12).dp else 0.dp)
            )
            trailing()
        }
    }
}

// ============================================================
// THE NIGHT FIELD  (Now, Streak and Past: one surface, glass in it)
// ============================================================
//
// The three tabs paint `LocalBeeExtras.current.fieldBrush` edge to edge and put
// everything on it as GLASS — a faint fill with a hairline, never a shadow, so
// nothing reads as a card sitting on the field. One shape family: panels are
// 20dp, everything smaller is a circle or a full pill.

private class PageTheme(val scheme: ColorScheme, val extras: BeeExtras, val content: Color)
private val LocalPageTheme = staticCompositionLocalOf<PageTheme?> { null }

/**
 * [content] sits on the night field but was written against the PAGE's colour
 * roles (the Past tab's journal and Trends, ~60 role reads). They are remapped to
 * the field's here, in one place: ink and muted ink to field ink, `surface` (what
 * BeeCardDefaults fills with) to glass, `primary` and `accentText` to field honey,
 * no card shadow. Score colours are untouched: their text tints clear 4.5:1 on
 * glass over the field in both modes, so score text must always sit on glass.
 * A dialog opened from inside goes back to the page with [PageColors].
 */
@Composable
fun FieldColors(content: @Composable () -> Unit) {
    val page = MaterialTheme.colorScheme
    val x = LocalBeeExtras.current
    val saved = PageTheme(page, x, LocalContentColor.current)
    MaterialTheme(
        colorScheme = page.copy(
            background = x.fieldBottom,
            onBackground = x.fieldInk,
            surface = x.glassFill,
            onSurface = x.fieldInk,
            surfaceVariant = x.fieldInk.copy(alpha = 0.08f),
            onSurfaceVariant = x.fieldInkMuted,
            outlineVariant = x.fieldInk.copy(alpha = 0.10f),
            primary = x.fieldHoney
        )
    ) {
        CompositionLocalProvider(
            LocalPageTheme provides saved,
            LocalBeeExtras provides x.copy(accentText = x.fieldHoney, cardShadow = false),
            LocalContentColor provides x.fieldInk,
            content = content
        )
    }
}

/** The page's own colours again, for a dialog opened from inside [FieldColors]. */
@Composable
fun PageColors(content: @Composable () -> Unit) {
    val saved = LocalPageTheme.current ?: return content()
    MaterialTheme(colorScheme = saved.scheme) {
        CompositionLocalProvider(
            LocalBeeExtras provides saved.extras,
            LocalContentColor provides saved.content,
            content = content
        )
    }
}

/**
 * Paints the field under a piece of a page exactly as the page shows it, so a
 * sticky header hides what scrolls beneath it without a visible band. [page] is
 * the bounds, in the root, of the surface the field fills.
 */
fun Modifier.fieldBackdrop(x: BeeExtras, page: () -> Rect): Modifier = composed {
    var at by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { at = it.positionInRoot() }
        .drawBehind {
            val p = page()
            val brush = (x.fieldBrush as? FieldBrush)?.takeIf { p.width > 0f }
                ?.at(p.size, at - p.topLeft) ?: SolidColor(x.fieldBottom)
            drawRect(brush)
        }
}

/** Every panel on the field. Anything smaller is a circle or a full pill. */
val GlassPanelShape = RoundedCornerShape(20.dp)

/** A full pill: tags, Save, Lock, the status strip. */
val PillShape = RoundedCornerShape(50)

/** Glass on the field: [BeeExtras.glassFill] inside a 1dp [BeeExtras.glassLine]. */
fun Modifier.glass(x: BeeExtras, shape: Shape = GlassPanelShape): Modifier =
    clip(shape).background(x.glassFill, shape).border(1.dp, x.glassLine, shape)

/**
 * A glass panel on the field, its content inked in field ink. [onClick] makes
 * the whole panel the tap target.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val x = LocalBeeExtras.current
    CompositionLocalProvider(LocalContentColor provides x.fieldInk) {
        Column(
            modifier
                .fillMaxWidth()
                .glass(x)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content
        )
    }
}

/** Tag chips on the field: glass, honey with aubergine ink when selected. */
@Composable
fun fieldChipColors(): SelectableChipColors {
    val x = LocalBeeExtras.current
    return FilterChipDefaults.filterChipColors(
        containerColor = x.glassFill,
        // Softened ink: the tags are a field of options, not headlines
        labelColor = x.fieldInk.copy(alpha = 0.88f),
        iconColor = x.fieldInkMuted,
        selectedContainerColor = x.accentFill,
        selectedLabelColor = x.onAccentFill,
        selectedLeadingIconColor = x.onAccentFill,
        selectedTrailingIconColor = x.onAccentFill
    )
}

/** The glass hairline round an unselected field chip; a selected one is solid honey. */
@Composable
fun fieldChipBorder(selected: Boolean): BorderStroke? =
    if (selected) null else BorderStroke(1.dp, LocalBeeExtras.current.glassLine)

/**
 * The width, in dp, every phone's screen is treated as for the three tabs. It is
 * the owner's phone (1440px at density 512) at the moment the layout was tuned,
 * so that phone looks exactly as approved, and every other screen or zoom level
 * is the same picture scaled up or down.
 */
private const val DESIGN_WIDTH_DP = 450f

/**
 * The density for the three tabs' content: fixed PROPORTIONS whatever the
 * phone's DPI, screen zoom or font size.
 *
 *  - dp are rescaled so the screen's short side is always [DESIGN_WIDTH_DP]
 *    wide: Android's display size ("screen zoom") changes how many dp a screen
 *    has, which would otherwise make every fixed size (pills, panels, padding,
 *    type) bigger or smaller against the width-driven parts like the dial.
 *  - the font size never goes below 100%, since it shrinks text but not the
 *    shapes around it; larger font sizes still apply, so accessibility holds.
 *
 * Provided around Now, Streak, the journal/Trends and the shared top row only;
 * the nav bar, dialogs, sheets, Settings and Tags stay on the system setting.
 */
@Composable
fun designDensity(): Density {
    val d = LocalDensity.current
    val config = LocalConfiguration.current
    val shortSideDp = minOf(config.screenWidthDp, config.screenHeightDp)
    return remember(d, shortSideDp) {
        if (shortSideDp <= 0) d
        else Density(d.density * shortSideDp / DESIGN_WIDTH_DP, maxOf(1f, d.fontScale))
    }
}

/** An hour cell with no rating, drawn on glass (the Now tab's today strip). */
fun fieldEmptyCellColor(x: BeeExtras, future: Boolean): Color =
    x.fieldInk.copy(alpha = if (future) 0.07f else 0.16f)

// ============================================================
// THE PLAIN PAGE  (Settings, Tags, dialogs, sheets)
// ============================================================

/** One card style for the whole app: `surface` fill, 20dp, shadow in light mode only. */
object BeeCardDefaults {
    val Shape: Shape = RoundedCornerShape(20.dp)

    @Composable
    fun colors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    fun elevation(): CardElevation =
        CardDefaults.cardElevation(defaultElevation = if (LocalBeeExtras.current.cardShadow) 2.dp else 0.dp)
}

/** Chips everywhere: muted fill and no outline; selected = honey with aubergine ink. */
@Composable
fun beeChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = LocalBeeExtras.current.accentFill,
    selectedLabelColor = LocalBeeExtras.current.onAccentFill,
    selectedLeadingIconColor = LocalBeeExtras.current.onAccentFill,
    selectedTrailingIconColor = LocalBeeExtras.current.onAccentFill
)

/** Every filled button: the honey fill with aubergine ink, in both modes. */
object BeeButtonDefaults {
    @Composable
    fun colors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = LocalBeeExtras.current.accentFill,
        contentColor = LocalBeeExtras.current.onAccentFill
    )
}

/**
 * An hour cell with no rating, in the day strips and the pattern grid. Ink at
 * low alpha rather than `surfaceVariant`, which nearly vanishes on a white
 * card; a still-to-come hour is fainter than a passed, unrated one.
 */
@Composable
fun emptyCellColor(future: Boolean): Color =
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (future) 0.07f else 0.16f)
