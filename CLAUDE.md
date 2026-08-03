# CLAUDE.md

Context for Claude Code when working in this repository. Read this instead of
re-deriving the codebase from scratch.

## What this app is

**Beeing** — an Android mindfulness app (single module `app`, Jetpack Compose,
Material 3 dynamic color). Every clock hour a notification asks the user to
rate the past hour 1–10. Tapping a score opens the app with it pre-selected;
the user must add ≥1 activity tag before saving. The product's core loop:
notification → rate → tag → save → lock phone → back to life.

### Product rules (no currency — 2026-08-03 simplification)

- Rate **8 distinct hours/day** → that day counts toward the streak.
- Only the **last completed hour and the one before it** are ratable
  (2-hour window). Existing ratings are editable up to **10 hours** back
  (`EDIT_WINDOW_MS`).
- **There is no currency.** Flowers, honey pots and savers are all gone; extra
  hours beyond the 8 earn nothing but the ring's overflow head.
- **Forgiveness is automatic**: one fully-missed day becomes a 🌙 **rest day**
  and the streak holds, provided no other rest day falls inside the previous
  `FORGIVE_WINDOW_DAYS - 1` (6) days — so ~1/week, rolling. A second miss
  inside that window resets the streak. A miss while the streak is already 0
  is plain `MISSED`: nothing to forgive, and it does **not** burn the rest day.
- **"Sends a bee back" is free**, capped at `RECLAIM_PER_DAY` (2) per calendar
  day (`reclaimsUsedToday`): reclaim any unrated hour from the last
  **10 clock hours** (`RECLAIM_WINDOW_HOURS`, rolling — may cross midnight
  and retroactively qualify yesterday; the 2 normally-ratable hours are
  excluded). Mandatory note, tagged `RECLAIM_TAG` (stored string is still
  `"💧reclaimed"` — never change it; display is 🐝 in the Hive, 🕐 as the
  badge in history lists). Reclaimed hours count toward the 8.
- Constants live at the top of `StreakEngine.kt`.

### Bee lexicon (used in UI copy)

Reclaim copy is the full phrase "Missed rating a special hour? Send a bee back
to revisit it" 🐝 ("revisit" is the owner-chosen verb) · 🕐 = an hour rated
from memory (a reclaimed entry), so it reads apart from in-the-moment ratings ·
🌙 Rest day = the one free forgiven day per week · Cell/chamber = one
qualifying day; the streak reads as a "cell hive" (⬢ N), no 🔥 · Comb = the
rating widget · Hive = the streak tab · Waggle = celebration (reserved for
rare milestones — never per-save; the one daily celebration is the ring
closing). Honey/🍯 and flowers/🌸 no longer appear anywhere in mechanics —
honey gold survives only as an accent color.

## Verifying changes

Always verify by running:

```
./gradlew installDebug
```

If `./gradlew` fails because Java can't be found, point `JAVA_HOME` at
Android Studio's bundled JDK:

Mac:

```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
```

Windows (Git Bash):

```
JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr" ./gradlew installDebug
```

Note for cloud/CI sessions: Google's artifact hosts (dl.google.com,
maven.google.com) may be blocked, making Gradle builds impossible. In that
case verify by static review and say so explicitly — the user compiles locally.

## File map (all in `app/src/main/java/com/example/beeing/`)

| File | What lives there |
|---|---|
| `MainActivity.kt` | Theme (dynamic M3), notification-tap intent → `PendingRating`, alarm scheduling |
| `HourlyPulseApp.kt` | Root composable: HorizontalPager with 3 tabs (0=Hive, 1=Now default, 2=Past), `FloatingPillNavBar` (slim full-width bar, 20dp card radius, icon+label per tab; highlight pill driven by `currentPage + currentPageOffsetFraction` so it tracks swipes live). **No shared header** — the Scaffold topBar is just a constant status-bar inset for every tab, so nothing pops in/out on a page settle. Each tab owns its own header inside its scroll content. Holds settings/info dialogs (the ⋮ menu now includes "How Beeing works"), import/export, ON_RESUME auto-focus of the pending hour |
| `NowTab.kt` | The rating flow. Header row = a **hamburger** `Icons.Default.Menu` button on the far left (opens the data-options menu — `onMenuClick`; the old ⋮/`MoreVert` is gone), then the large neutral "Beeing" title (`headlineLarge`, `onSurface`), then a weight spacer pushes the **slim** right-aligned `StatusStrip` pill (⬢ cell-hive · ring x/8 — no currency segment — 16sp emojis, 14sp numbers, 18dp ring; **hairline** 0.5dp white-0.22α outline, sized to sit level with the title; taps to Hive) to the right edge. Rating card header is one line: **"How was your"** + a compact `HourWindowPicker` chip (‹ range › carets — each enabled only when that neighbouring hour is still pending; offset 1 = earlier/expiring, offset 0 = latest) + the countdown to its right + the "why these hours" info icon at the far edge; then dial→tags→notes→save (the rating input is `DecagonCombDial` from `DecagonDial.kt`; no section label above the tags — the save button's "Tag it to save" state teaches the rule). The notes box (`NotesSection`) is collapsed to a slim "＋ Add a note" hint row until tapped. `TagPickerSection` shows up to `COLLAPSED_TAG_COUNT` (9, ~3 rows) chips before folding the rest into a neutral-toned `AssistChip`: **"+N more"** just expands to reveal hidden tags; **"edit tags"** (shown when nothing is hidden) and the expanded editor's pencil open **`ManageTagsDialog`** (private in `NowTab.kt`) — one row per tag with long-press-drag reorder (fixed 46dp rows, index math on drag offset), inline rename (✏️ → `BasicTextField` + tick; **picker-only** — past entries keep the old name by owner decision), delete behind a confirm `AlertDialog` (**picker-only** too: old entries keep the tag), and a "New tag" footer that reuses the add-tag `AlertDialog`. The expanded editor's "✓ Show less" pill rides inline in the chip `FlowRow` (no extra vertical line). Caught-up hero with the "🔒 Lock phone and bee mindful🐝" button + `TodayStripCard`. Sends `ACTION_LOCK_PHONE` broadcast if `isLockAccessibilityServiceEnabled`, else shows a dialog that opens Settings → Accessibility |
| `LockAccessibilityService.kt` | `AccessibilityService` that registers a receiver for `ACTION_LOCK_PHONE` and calls `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` — same as the power button, so it never touches keyguard/device-admin policy and biometric unlock keeps working next time. `isLockAccessibilityServiceEnabled(context)` checks `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. User must toggle it on once under Settings → Accessibility (can't be done programmatically) |
| `DecagonDial.kt` | **The rating input now mounted in `NowTab`**: `DecagonCombDial` — a 10-sided readout core with ten hexagonal cells docked on its edges, spun as one unit with 36° detents (drag anywhere, fling with damped snap, tap a cell to seek). Fixed notch on top marks the value; resting state is a theme-aware (`surfaceVariant`/`outline`) dashed core reading "Pick a rating"; rated state floods the core with `dialScoreColor` and shows the numeral plus the Title-Cased score word (`DIAL_WORDS`) inside the core. Haptic+click per detent |
| `CombStrip.kt` | Legacy (unmounted since the dial landed): the previous rating input, **one big flat-top hexagon** (`FlatHexShape`, softly rounded vertices; `aspectRatio(1.1547f)`, `fillMaxWidth(0.86f)`), vertical drag (top = 10, bottom = 1). Two states keyed only on `selectedScore`: **null →** `RestingScaleHex` draws ten filled, soft-tinted (`band × 0.35α`) island bands with gaps (`roundedPolygonPath`, sized to the hex width at each height via `flatHexHalfWidth`), each numbered 1–10, and the whole cell breathes (`pulseScale`); **set →** the hexagon floods one solid `scoreBandColor` with a big centered numeral, staying filled after release (so the notification's pre-selected score lands filled). Haptic tick per boundary; **no loupe** (removed — the big numeral replaces it). `scoreWord()`, `FlatHexShape`, `flatHexHalfWidth`, `roundedPolygonPath` |
| `StreaksTab.kt` | Hive tab: streak hero (`StreakMeter`), "Send a bee back" 🐝 reclaim CTA (shown when reachable hours exist; disabled once the day's 2 bees are spent), calendar with score-tinted dots (🌙 = rest day, hollow red ring = missed), hive log, reclaim dialogs (`reclaimHourLabel` prefixes "Yesterday ·" across midnight; the cap is re-checked in `onReclaim` because the dialog can outlive the tap). No currency card |
| `StreakEngine.kt` | Pure streak logic. **`replayDays` is the single walk over history** — private, returns one `ReplayDay` per calendar day (outcome, streak after, `brokeStreak`, that day's hour timestamps + reclaim spends); `computeStreakState`, `computeDayOutcomes` and `computeStreakLog` are thin readers over it, so they *cannot* disagree. Also `DayOutcome` (QUALIFIED/REST/MISSED), `reclaimsUsedToday`, the `isReclaimed`/`visibleTags()` entry extensions, `StreakMeter`/`StreakRing` composables, constants |
| `PastTab.kt` | Analytics: D/W/M periods. **Charts are non-interactive except in Day view** — there is no tap-to-drill and no chart swipe; zoom levels change only via the `ZoomPicker`, periods only via the ‹ › arrows. In **Day view only**, tapping a day opens its bottom sheet and marks it via `selectedDayStart` (the white outline highlight — `highlightStartMs`); Week/Month bars/cells are inert and carry no highlight. One combined card: a **static control bar** (arrows + range label + `ZoomPicker`) over an **`AnimatedContent` region** keyed by `PastViewKey` — period steps slide L/R, zoom changes fly (`scaleIn/Out` at `zoomOriginX`), driven by `navKind`/`zoomOriginX`. The region stacks summary stats (avg · hours rated · 🌙 rest days) + `ScoreBarChart` + the **collapsed-by-default** hour-by-hour `PatternGrid` (show/hide toggle via `hourByHourExpanded`) + the folded-in **`TagScoresSection`** (short inner scroll ≈5 rows with a `FadingScrollbar`); each slot recomputes its own data from its key. `PatternGrid` is height-capped at the 7-col size (Week stops ballooning; extra width left blank), trimmed 24dp gutter, hour labels on the row seams. `DaySheetContent` (its per-hour rows show a 🕐 badge on reclaimed entries and hide the `RECLAIM_TAG` sentinel via `visibleTags()`), edit sheet. Also `getScoreColor(Double)` |
| `UIComponents.kt` | Shared: `HeaderSection` (legacy, no longer mounted), `EditEntrySheet` (opens fully expanded on one tap via `skipPartiallyExpanded`; grouped SCORE/TAGS/NOTE sections via the `SectionLabel` overline; edits score, note, **and** tags — every available tag is a toggleable `InputChip` (centered, tightened rows). Header = "Edit entry" + date on the left, time range flanked by ‹ › **seek carets** right-aligned. Carets step through that day's rated hours only (`dayEntries`, sorted; disabled at the ends), swapping `currentEntry` in place without closing; edited buffers are keyed on `currentEntry.id` so they reset per hour. Seeking mid-edit raises a save-or-discard `AlertDialog` (`pendingTarget`). Callbacks are split `onPersist`/`onClose` so an in-sheet save doesn't dismiss the sheet), `SoulFuelTagsSection` (display-only tag chips + a pencil that opens `NowTab`'s `ManageTagsDialog`; the old in-place delete mode is gone), `NotesSection` (collapsed "＋ Add a note" row → compact `BasicTextField`; a `hadFocus` flag stops the initial unfocused `onFocusChanged` event from re-collapsing it), `HistoryPanel` (🕐 badge + `visibleTags()`, same as the day sheet), misc format helpers (`formatHour`, `getOrdinalSuffix`), plus legacy `ProfessionalChart`/`InsightPanel` (no longer mounted) |
| `StatsComponents.kt` | Period buckets, weekly report notification, legacy `InsightsContent`/`CollapsibleSection` (no longer mounted) |
| `RatingsViewModel.kt` | Shared state: `allRatings`, `refreshTrigger`, save/delete/load wrappers |
| `Utils.kt` | `RatingEntry`, persistence (SharedPreferences `"b"`), notifications + hourly alarm (`NotificationReceiver`), tags, CSV import/export (HMAC-signed), auto-backup, `computeActiveWindow`, `EDIT_WINDOW_MS`, `scoreBandColor(Int)` |
| `Celebration.kt` | Full-screen ring-closed confetti takeover (`RingClosedCelebration`) |

## Data model & persistence gotchas

- `RatingEntry(id, score, timestamp, hourLabel, note, tags)` — **timestamp is
  the START of the rated hour** (minute/sec zeroed). `id` is the wall-clock
  millis when recorded. `hourLabel` is the ordinal of the hour END ("14th" =
  1–2pm); day membership must be derived from `timestamp`, not the label.
- Storage: SharedPreferences `"b"`, key `"ratings"`, `|`-separated rows,
  `,`-separated fields, tags `;`-joined — so notes/tags must never contain
  `,` `|` `;` (notes are sanitized on save). No database.
- Reclaim spends persist separately (`"reclaim_spends"`) because they are not
  derivable from ratings. Now that reclaims are free they exist only to enforce
  the `RECLAIM_PER_DAY` cap and to date the "bee sent back" log rows — the key
  and format are unchanged, so old data still reads.
- Tags: key `"tags_v2"`, max 30, defaults in `DEFAULT_TAGS`.

## Design system (agreed with the owner — keep these rules)

- **One job per screen, one card per job.** Now = act, Hive = the long game
  (streak, calendar, repair), Past = insight (summary → magnitude → pattern →
  drivers).
- **Color roles:** blue/primary = interactive only. Score bands = data only:
  1–4 red, 5–7 amber, 8–10 green (`scoreBandColor` for ints,
  `getScoreColor` for averages). Honey gold = streak identity. Progress rings
  are never red.
- **Color never travels alone** (amber↔green is at the CVD floor): every band
  color ships with its digit or position.
- **Ceremony budgeted by rarity:** saving = one haptic, ring closing = the
  single daily full-screen celebration, waggle = rare milestones only.
- **Lock phone exists ONLY in the caught-up state** (no pending hours).
  Rating pending hours always outranks leaving.
- Cards: 20dp radius, `surfaceVariant.copy(alpha = 0.4f)` fill, 16dp padding.
  Every scrollable tab ends with a 112dp spacer (floating nav clearance).
- **Active window** (`computeActiveWindow`): stable hour range covering ~90%
  of ratings in the trailing 30 days, min 8h span. The Now today-strip, Past
  pattern grid and day sheet all use it; out-of-window ratings collapse into
  cap pills, never extra rows. (Hysteresis/persistence is a known TODO.)

## Integration seams

- **Lock phone:** `NowTab` sends a package-scoped broadcast
  `com.example.beeing.ACTION_LOCK_PHONE`, received by `LockAccessibilityService`
  (registered in `AndroidManifest.xml`, config at
  `res/xml/lock_accessibility_service_config.xml`), which calls
  `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. If the service isn't
  enabled yet, `NowTab` shows a dialog directing the user to
  Settings → Accessibility instead of sending the broadcast.
- **Notifications:** `NotificationReceiver` posts the hourly RemoteViews
  notification (10 score buttons). Tapping a score deep-links with
  `PENDING_SCORE`/`TARGET_TS` extras → `PendingRating` → pre-selects the comb.
  When both ratable hours are logged, notification ID 1 is cancelled.
- **Hour targeting:** `targetedHourOffset` (0 = latest completed hour,
  1 = the expiring grace hour). With both pending, default is 1 (expiring
  first) unless a notification deep-link chose otherwise — logic split
  between `HourlyPulseApp` ON_RESUME and a guarded effect in `NowTab`.

## Current state / history

- The 2026-07 redesign (comb strip, three-state Now, Hive, Past zoom cascade)
  landed via PR #1 from branch `claude/mindfulness-app-ui-gqjbys`. Design
  rationale lives in that PR's description.
- Known TODOs: active-window hysteresis persistence; legacy chart components
  in `UIComponents.kt`/`StatsComponents.kt` can be deleted once nothing else
  references them.
- 2026-07-20: added the `LockAccessibilityService` (lock now works in-repo,
  no longer an external seam), restored the "Beeing" title to `NowTab`'s
  header, renamed the lock button to "🔒 Lock phone and bee mindful🐝", and
  redesigned `EditEntrySheet` (tighter rating circles and tag spacing, shows
  which hour/date is being edited, notes are editable, tags are read-only).
- 2026-07-21: Past-tab charts are now non-interactive except in Day view —
  removed tap-to-drill and the chart swipe (zoom via `ZoomPicker`, periods via
  arrows). White outline highlight now appears only on a tapped day in Day view
  (`selectedDayStart`/`highlightStartMs`), never on Week/Month bars or cells.
  The hour-by-hour `PatternGrid` is collapsed by default (`hourByHourExpanded`
  show/hide). Gave `NowTab`'s `StatusStrip` a thin white outline.
- 2026-07-21 (later): `NowTab` header reworked — the ⋮/`MoreVert` menu is
  replaced by a 3-line **hamburger** (`Icons.Default.Menu`) on the far left of
  the "Beeing" title (still opens the data-options menu); the `StatusStrip` is
  now slim (16sp emojis, 14sp numbers, 18dp ring, hairline 0.5dp/white-0.22α
  outline) and right-aligned to sit level with the title. Comb hexagons are
  less rounded (`PointyHexShape` radius `0.22f`→`0.12f`); the drag loupe now
  follows the finger (`dragX`) instead of snapping to the cell center.
  `TagPickerSection` shows 15 chips before "+N more"; the "edit tags" chip and
  the editor's tick now open/close edit mode without a redundant pencil step.
  Tag names are capped at 20 chars (30-tag max unchanged).
- 2026-07-31: the rating input is now the **decagon dial** (`DecagonDial.kt`,
  replacing `CombStrip` which is legacy/unmounted): score word Title-Cased
  inside the core, resting core theme-aware via `colorScheme`. Rating card
  tightened: "TAG IT" label removed, `NotesSection` collapses to a "＋ Add a
  note" row, `COLLAPSED_TAG_COUNT` 15→9. Tag editing moved into
  `ManageTagsDialog` (long-press-drag reorder, inline rename that migrates
  history via `renameTagInRatings`, delete, add); `SoulFuelTagsSection` lost
  its delete mode and `isTagDeleteMode` plumbing is gone.
- 2026-08-01: **economy flattened to one currency** — honey pots/savers
  removed (`StreakState.savers`/`bankProgress` → `flowers`). 🌸 cap 50,
  20 gifted, missed day auto-spends 20 (`SAVE_COST`), reclaim = "send a bee
  back" 🐝 for any of the last 10 clock hours (`RECLAIM_WINDOW_HOURS`,
  crosses midnight; can retro-qualify yesterday; hour-start-millis based, no
  longer today-only). GardenCard → `FlowerBankCard`; the two Hive explainers
  merged into one; icon families unified (🐝/🌸/⬢, no 💐🥀💧🍯🛡️). History is
  replayed under the new rules (savers were never persisted, so no stored
  migration was needed). Ripples: Now `StatusStrip` shows 🌸, Past summary
  stat is "🌸 days saved", weekly report line updated.
- 2026-08-01 (later): tag edits are **picker-only by owner decision** — rename
  no longer rewrites history (`renameTagInRatings` deleted from `Utils.kt`)
  and delete asks for confirmation but never touches old entries. The
  expanded tag editor's "✓ Show less" pill moved inline into the chip
  `FlowRow` (`onCollapse` slot on `SoulFuelTagsSection`) to save vertical
  space under the tags.
- 2026-08-01 (dial polish): the cell parked under the notch now carries a
  white stroke (only once a rating exists) so the selection reads on the ring
  as well as in the core; the first-appearance "this spins" sway went from
  ±4°/1.1 s to ±14°/2 s over two decaying swings (still under the 18°
  half-detent, so the value never flips); detent haptics are now a full-
  bodied `createOneShot(18 ms, 160)` (falling back to `EFFECT_HEAVY_CLICK`
  / `LONG_PRESS`) instead of `EFFECT_TICK`; and `NowTab` trims the dial's
  empty bottom rim (5% of its measured height, via a `layout` modifier) and
  drops the 8dp spacer so the tag chips sit right under the comb.
- 2026-08-01 (dial color): the ring is muted — every cell except the one under
  the notch is drawn in `mutedCellColor` (its `dialScoreColor` hue lerped 60%
  into `surfaceVariant`), with its digit in `onSurfaceVariant` rather than the
  `dialDigitColor` lightness flip. Full saturation now appears only on the
  notched cell and the core, so an unrated dial is a quiet comb. The hue arc
  is unchanged (5°→125°) by owner decision — only intensity was dialled back.
- 2026-08-01 (dial sound): the per-detent click no longer uses
  `View.playSoundEffect` — that is the system TOUCH sound, silent unless the
  user enabled "Touch sounds" (off by default on One UI). `DialTicker`
  (private in `DecagonDial.kt`) synthesizes a ~9 ms damped 2.6 kHz ping with a
  noise transient into a `MODE_STATIC` `AudioTrack` and replays it per detent;
  `USAGE_ASSISTANCE_SONIFICATION` keeps it on the system/ring volume and
  silent in silent mode. Knobs: `TICK_MS`, `TICK_VOLUME` (0.45), the 2600 Hz
  and `exp(-t * 520)` terms. The dial's `tickSound` flag still gates it.
- 2026-08-01 (dial fling): a flung wheel now coasts about twice as long —
  `TAU_MS` 120→240 with the travel cap lifted in step (new
  `FLING_MAX_TRAVEL` = 400°, ~11 detents, was 200°/~5). Both had to move
  together: the cap is expressed as `FLING_MAX_TRAVEL / TAU_MS`, so raising
  tau alone would have kept the same distance at half the speed.
- 2026-08-01 (dial fling, again): 3× faster and 3× longer again — `TAU_MS`
  240→720, `FLING_MAX_TRAVEL` 400°→3600° (ten full turns, ~100 detents), so
  peak speed is 5°/ms and a max fling coasts ~4 s. Because that crosses a
  detent every ~7 ms, `tick()` now drops haptic+click when the previous one
  was under `MIN_TICK_GAP_MS` (55 ms) ago — otherwise the 18 ms vibration and
  9 ms click queue into a single buzz. Value changes are NOT thinned: every
  detent still fires `onRatingChange`.
- 2026-08-01 (dial as fidget spinner): owner wants the wheel to be spinnable
  for its own sake. Three things were capping it, and all three are fixed:
  (1) the release-velocity filter was `0.75*old + 0.25*new`, so a hard flick
  reported a fraction of the finger's real speed — now `0.35/0.65` plus a
  `FLING_GAIN` of 1.9; (2) the cap tripled again to 15°/ms
  (`FLING_MAX_TRAVEL` 10800°, ~30 turns, ~42 turns/s); (3) **the ring aliased**
  — 10-fold symmetry means the silhouette repeats every 36°, so past 18°/frame
  the wagon-wheel effect made a fast spin look like a slow backwards crawl.
  `drawDial` now takes the signed speed and crossfades the ten cells into a
  smeared annulus above ~10°/frame (`blur`), dropping digits and the selected
  outline as it goes. Feedback: ticks stay rate-limited (`MIN_TICK_GAP_MS`)
  and go short/light above `WHIRR_V`; `onRatingChange` is not pushed per detent
  above `VALUE_PUSH_MAX_V` (~500/s would recompose the rating card) — the
  landed value is always published by `animateThetaTo`. `spin` must be zeroed
  anywhere `settleJob` is cancelled or the blur sticks on screen.
- 2026-08-01 (fling gate): the fidget spin is now behind a firmer flick —
  `settleFrom` picks one of two regimes off the raw release speed. Under
  `FIDGET_FLICK_V` (3°/ms) an ordinary rating drag gets no gain,
  `TAU_SETTLE_MS` (240) and `SETTLE_MAX_TRAVEL` (600°, ~1.7 turns); at or above
  it, the `FLING_GAIN` boost, `TAU_FIDGET_MS` (720) and `FIDGET_MAX_TRAVEL`
  (10800°) unlock. Rating precision lives below the gate, fidgeting above it.
  Retuned same day to widen the gap between the two regimes: gate down to
  2°/ms and `FLING_GAIN` up to 3.5 (so ~4.3°/ms of finger already tops out the
  15°/ms cap), while the calm side got *slower* — `SETTLE_GAIN` 0.7 (it now
  coasts slower than the finger threw it), `TAU_SETTLE_MS` 180,
  `SETTLE_MAX_TRAVEL` 300° (<1 turn).
- 2026-08-03: **the economy is gone entirely.** `StreakState.flowers`,
  `FLOWER_CAP`, `GIFT_FLOWERS`, `SAVE_COST` and `RECLAIM_COST` are deleted —
  no currency anywhere in the app. Replacing it:
  (1) **Automatic forgiveness** — a fully missed day is a 🌙 `DayOutcome.REST`
  and the streak holds, gated by `FORGIVE_WINDOW_DAYS` (7) on a rolling
  calendar-day index; a second miss inside the window resets. A miss with the
  streak already at 0 is plain `MISSED` and does not consume the rest day.
  (2) **Reclaim is free**, capped at `RECLAIM_PER_DAY` (2) per calendar day via
  the still-persisted `reclaim_spends` list (`reclaimsUsedToday`).
  (3) `DayOutcome.SAVED` → `REST`; `StreakEventType` slimmed from six to four
  (`COMPLETED`, `REST`, `RESET`, `RECLAIMED` — `BANKED` and the STARTED/EXTENDED
  split are gone).
  (4) The three history readers were collapsed onto **one private `replayDays`**
  so the meter, calendar and log physically cannot diverge — that guarantee used
  to be three parallel loops maintained by hand.
  (5) Reclaimed entries now carry a 🕐 badge in `HistoryPanel` and the Past day
  sheet, and `visibleTags()` hides the raw `"💧reclaimed"` sentinel from every
  tag list (`EditEntrySheet` still round-trips it, since `selectedTags` seeds
  from `entry.tags` and the sentinel simply has no chip).
  Ripples: `FlowerBankCard` deleted, `StatusStrip` lost its 🌸 segment, the save
  button lost "+1 🌸", the Past summary's third stat is now "🌙 rest days", the
  weekly report line is "hive ⬢N", and both explainers were rewritten.
  History is **replayed** under the new rules, so days that used to read
  "saved" may now read as rest days or resets — accepted by the owner, same
  precedent as the 2026-08-01 change.
