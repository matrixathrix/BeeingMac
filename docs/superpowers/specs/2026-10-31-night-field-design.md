# Night field: a coherent Now and Streak tab

Date: 2026-10-31 · Status: design approved by the owner, not yet planned or built.

Mockups (open in a browser; the owner approved these): `docs/superpowers/specs/2026-10-31-night-field/`

- `0-before-now-rating-dark.png`: the Now tab as it is today (rating state, dark).
- `1-directions.html`: the three directions considered. **A, "Night field", was chosen.**
- `2-now-dark-and-light.html`: the Now tab, both states, dark and light. Light mode uses the
  **soft violet** row; the deep violet row is the rejected alternative.
- `3-streak.html`: the Streak tab with the Super day legend.
- `4-streak-history.html`: Streak tab History, collapsed and expanded.

The mockups are HTML approximations: their geometry and copy are a guide, not pixel specs. Where
this document and a mockup disagree, this document wins.

## Intent

The owner's verdict on today's Now tab: "overall not coherent, in both states." Specifically, it
reads as pieces from three different apps. There's an aubergine band on top, a charcoal or white
card under it, and a grey Save slab (or the bee and Lock footer) at the bottom. On top of that, six
unrelated rounded shapes compete: the status pill, the round bell, the hour-picker pill, the
10-sided core, the round cells, the tag pills and the Save bar.

The fix is one surface. The aubergine stops being a header and becomes the whole tab, a "night
field" with a soft glow at the top. With no band edge and no card edge, nothing can look pasted on.
Everything on the field shares one shape family (circles and pills) and one material (frosted
glass). Honey stays reserved for "act here" and for the streak.

The Streak tab moves onto the same field, so swiping between the two "you, right now" tabs feels
like one place. Past, Trends and Settings stay on the plain page.

## Goals

- The Now tab, in both states (rating and caught up), reads as one designed surface.
- The Now and Streak tabs read as the same family.
- Light mode is the same design with lifted values: a **soft violet** field, not pink and not
  the plain page.
- The dial keeps every one of its mechanics.

## Non-goals

- No change to any rule, number, stored value or notification. This is a visual and layout pass.
- No change to Past, Trends, Settings, the Tags page, the edit sheet or the dialogs, beyond what
  the shared top row and nav bar need (below).
- No change to the full-screen celebrations, the Super day invite or the Super day celebration.
  They are already full-screen aubergine and sit naturally next to the field.
- No new features.

## Anti-goals (a failure even if it works)

- Any part of the field tabs that looks like a card or band sitting *on* the field, rather than
  glass *in* it.
- A third shape family creeping back in (a squircle button, a sharp-cornered panel, a
  different pill height).
- Honey used for decoration. It stays "act here" (Save, selected tags) and "streak identity"
  (medallion, rings, savers, complete tiles).
- Any change to how the dial feels in the hand: detents, haptics, the click sound, fling and
  fidget spin, tap-to-seek, the intro sway, the notification pre-select snap.
- Text on the field that fails contrast. Every new text-on-field pairing goes into
  `PaletteContrastTest`.

## Constraints

- Jetpack Compose, Material 3, Plus Jakarta Sans, Beeing's fixed palette (no dynamic colour).
  These stay.
- The CLAUDE.md design-system rules still hold, except the ones this document deliberately
  replaces (listed under "Rules this replaces" below).
- Performance: CLAUDE.md's 2026-10-27 lesson stands. Keep animated values out of composition;
  read them in layout and draw lambdas. The field's gradient is static per theme.
- The dial's `DecagonCombDial` mechanics code is not to be rewritten, only its drawing.

## The field

One background for the Now and Streak tabs: a radial gradient centred at the top, about 120% of
the width across and 70% of the height deep.

| | top (glow) | 45% | bottom |
|---|---|---|---|
| Dark | `#4A1D53` | `#2E1135` | `#1A0A1E` |
| Light (soft violet) | `#DCCDF6` | `#ECE4FA` | `#F8F5FD` |

**Field ink** (primary text and icons on the field):
- Dark: white. Secondary text is white at ≥ 60% (5.7:1 at the glow).
- Light: violet-black `#24123A` (11.5:1 at the glow). Secondary text is that ink at **≥ 70%**
  (5.3:1). The mockups' 55% fails, so don't copy it.

**Honey on the field:**
- The fill (Save, selected tags, medallion, ring progress, complete tiles) is `#F5A623` in both
  modes, with aubergine ink `#2A0F31` on it (8.6:1).
- Honey *text* on the field (the countdown, the active nav label):
  - Dark: `#F5A623` (6.5:1 at the glow).
  - Light: **`#7A4A00`** (5.0:1 at the glow). The app's existing `#9A6000` reaches only 3.5:1 on
    the violet glow, so it can't be reused here.

**Glass** (every container on the field: cells, chips, panels, the bell button):
- Dark: white at 6–7% fill, with a 1px hairline of white at 7–10%.
- Light: white at ~70% fill, with a 1px hairline of field ink at 6–8%.

Panels use **20dp** corners. Everything smaller is a circle or a full pill. There are no shadows
on glass. The Save pill and the streak medallion get a soft honey glow (the mockups' shadow).

Name these as theme roles (for example on `BeeExtras`: `fieldBrush`, `fieldInk`,
`fieldInkMuted`, `fieldHoney`, `glassFill`, `glassLine`), so no composable hard-codes a value. How
they're named and grouped is the builder's call.

## Now tab: rating state

From the top (see `2-now-dark-and-light.html`, rating phones):

1. **Shared top row**, unchanged in content: "Beeing" · `StatusStrip` · bell · ⋮. The
   `StatusStrip` and bell become glass on the field.
2. **"How was your hour?"** as the heading. The time line that used to sit above it is removed
   (owner: it was redundant).
3. **One hour line:** ‹ `12am – 1am` › then `· 1h 46m left` in field honey. The carets keep
   today's behaviour. The ⓘ "why these hours" button is removed; its explainer now opens from a
   tap on the countdown text. The hour line has no container.
4. **The dial** (below).
5. **Tags** as glass pills, keeping all of today's `TagPickerSection` behaviour: as many as fit,
   the fold into "+N more", "Edit tags" last, and expanding to scroll inside the frame. Selected
   tags fill honey. "+N more" is a pill with a dashed glass outline and no fill, so it never
   reads as a tag.
6. **"＋ Add a note"** as plain field-muted text. Tapping it opens the note field as today, now as
   a glass panel.
7. **Save:** a full-width honey pill above the nav bar. Its label logic is unchanged ("Rate and
   tag to save", "Save 12am – 1am", the late-hour hints). Disabled is honey at low opacity, never
   a grey slab.

The rating card (`BeeCardDefaults`) is gone. Its content sits directly on the field. The layout
still behaves as a fixed frame: Save stays pinned, and only the tag region scrolls when it can't
fit. Whether `BandFillLayout` survives, shrinks or is replaced is the builder's call. Its job was
to grow a band into spare height, and there is no band now.

### The dial

Mechanics are untouched: the 36° detents, drag, fling and fidget regimes, haptics,
`DialTicker`, tap-to-seek, the intro sway, the external-rating snap and the fast-spin blur.

Drawing changes:
- The core is a **circle**, no longer the decagon. The owner allowed reshaping the dial's
  elements. Circles everywhere is the shape family.
- Each unselected cell is a **glass circle** with its digit in field ink at ~85%. It no longer
  uses the score-band digit colour or the grey fill.
- The selected cell (under the notch) fills with its score colour, with a 2dp white stroke and a
  dark digit.
- When rated, the core floods with the score colour, shows the numeral and the score word in dark
  ink, and gets a faint halo ring in the same colour (about 25% alpha, a few dp outside the core).
- **Unrated:** the core is glass and reads "Pick a rating" in field ink. No cell is selected.
- The notch is a small triangle in field ink.
- The fast-spin blur annulus is drawn in the glass tone rather than grey.
- Score colours are unchanged in both modes (`scoreBandColor`). The light mockup's slightly
  different reds and greens are not a decision.

The dial is the hero, so give it the width it has today. Keep the existing top and bottom rim
trims or recompute them for the new geometry.

## Now tab: caught-up state

See `2-now-dark-and-light.html`, caught-up phones. From the top:

1. Shared top row, as above.
2. A large **closed ring** (today's hours against the goal, honey on a glass track) with
   `H/G` inside, then **"All caught up"** and **"Next hour opens in Nm"**. This replaces the
   tick-and-title row. The ring follows `hoursRequired` (4 segments' worth in beginner mode) and
   draws the Super day lap the same way `StreakRing` does.
3. The **today strip** in a glass panel, labelled `TODAY` with `avg N.N` on the right. Its cells
   and window logic are unchanged.
4. The "slipped hours" link, if present today, as field-muted text under the strip.
5. The **bee field**: `CatchTheBee` flies in the remaining height with its prompt above the
   footer. Its behaviour is unchanged.
6. **Lock:** a full-width pill with a glass outline (not honey: leaving is not the "act here"
   action), labelled "🔒 Lock phone and bee mindful".

**"Today's best hour" (`BestHourCard`) is removed from the Now tab.** That's one job per screen.

The band-growth animation (`grow`, scaled title) has no band to grow. The caught-up layout is a
plain column: hero, strip, then the bee taking the rest.

## Streak tab

See `3-streak.html` and `4-streak-history.html`.

1. **No `BrandBand`.** The tab sits on the same field, with the same shared top row.
2. **Hero:** `StreakMeter`'s two columns float directly on the field, split by the existing
   0.5dp hairline in field ink at ~12%. On the left are the honey medallion with the flame, the
   "N-day streak" label and the five saver shields. On the right are today's ring and its
   caption. The tap targets (Streak and Savers, Today's goal) and all states (at risk, paused,
   beginner, Super day caption) are unchanged; only colours move to field roles.
3. **"Missed an hour?"** is a glass panel. Its icon is the past-hour clock (`Sym.AddPast`) on a
   honey-wash circle. The subtitle is just **"Add a past hour"** (the "· N available" count is
   removed), and **there's no trailing caret**. The whole panel is the tap target. The spent
   state ("You've added 2 past hours today. More tomorrow.") stays, faded and not tappable, with
   no lock icon.
4. **Calendar** in a glass panel, with today's month header, swipe and tile meanings:
   - Complete: a honey tile.
   - **Super day: a honey tile with a thin `SuperGold` rim and a small ✦ spark in the top-right
     corner** (with a soft gold glow in dark mode). Today it's a honey tile with a `Sym.Sparkle`;
     the rim makes it read as "complete, and then some".
   - Saved: a honey wash.
   - Missed: a hollow red outline.
   - Today: an outline in field ink.
   - Other days: faint glass.
   - Beginner: the leaf glyph, as today.
5. **Legend:** a 2×2 grid of **mini tiles that match the calendar exactly** (Complete,
   Super day, Saved, Missed), under a hairline divider. In beginner mode a fifth key, "Beginner
   day" with the leaf, joins the grid. The "How streaks work" info entry stays, placed by the
   builder.
6. **History**, collapsed by default: a glass panel with "History", a chevron, and a **peek at
   the latest event** (its tile, title and date, for example "✦ Super day · 30 Oct"). If there are
   no events, the peek is today's empty-state line. The whole panel toggles it.
7. **History, expanded:** it opens in place and the chevron flips. Rows are grouped under small
   caps headers by recency ("THIS WEEK", "EARLIER IN OCTOBER", then month names). It's still the
   last 3 months, capped at 60 rows with the existing "Showing latest 60" note. Each row has a
   **30dp round tile** (colour and icon together), the title with the detail line under it
   (ellipsised to one line), and the date on the right in small muted type. The date replaces
   today's full timestamp line under each row. Tile colours:
   - Complete: honey with the hexagon.
   - Super day: `SuperGold` with the sparkle.
   - Saver used: honey wash with the shield.
   - Past hour added: glass with the clock.
   - Broken: red wash with a cross.
   - Any other event type (beginner day, gift, faded, recovery and so on): the builder picks its
     tile from the same palette, keeping today's `sym()` icon.

   `logRows` folding (one row for a Super day and its saver) is unchanged.

## Shared chrome

- **Top row (`BandTopRow`):** it already lerps on `bandUnder`. Over the field it uses field ink
  and glass; over the plain page, today's colours. The wordmark fading over Past stays.
- **Nav bar (`BottomNavBar`):** over the field it becomes a translucent panel tinted toward the
  field's bottom colour, with a hairline top in field ink. Over Past it stays as today. Lerp
  between the two on the pager position the same way the top row does. Active is honey (field
  honey on the field), inactive is field-muted.
- **Status bar icons:** the field is dark in dark mode and light in light mode, so the icons are
  simply light in dark mode and dark in light mode on every tab. The current
  `overPlainPage`/settings special-casing can collapse into that.
- **Swiping Streak ↔ Now:** draw the field as each page's background. Two identical glows
  sliding past each other during a swipe is acceptable. Drawing one shared field behind both
  pages is fine too if it's cheap. That's the builder's call.

## Rules this replaces (update CLAUDE.md when built)

- "A `BrandBand` is a stage … only Now and Streak have one" becomes: **Now and Streak sit on the
  night field; there is no band.** `BrandBand` may become unused.
- "Background/card inversion" and "Cards: `BeeCardDefaults`" no longer apply on the field tabs.
  There, containers are glass panels.
- The "Lock phone exists ONLY in the caught-up state" rule is unchanged.
- The `CatchTheBee` emoji exception is unchanged.

## Left to the builder

- Composable structure, naming of the new theme roles, whether `BandFillLayout`/`BrandBand`
  are deleted or kept for something else.
- Exact spacing, type sizes and dial proportions within the mockups' spirit. The heading is
  around `headlineSmall`/25sp ExtraBold; panel padding is 12–16dp.
- The History group headers' exact bucketing and copy.
- Where the "How streaks work" info entry lives once the legend is a grid.
- Tile choices for the History event types not listed above.
- How to verify. The owner's standing preference is build + install, then hand them a list of
  things to check on the device.

## Calls made without the owner (check these)

1. The ⓘ "why these hours" button moves to a tap on the countdown text.
2. "Today's best hour" leaves the Now tab entirely.
3. Lock is an outlined glass pill, not honey.
4. Unrated dial digits are neutral field ink (today they carry their score-band colour).
5. Light-mode honey text is the deeper `#7A4A00` and light secondary text is ≥ 70% ink, because
   the mockups' values fail contrast on the violet glow.
6. The spent "Missed an hour?" state drops its lock icon.
7. History rows show just the date, not the full timestamp.
