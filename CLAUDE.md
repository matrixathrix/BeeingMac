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

- Rate **8 distinct hours/day** (`STREAK_HOURS_REQUIRED`) → that day counts
  toward the streak. In **beginner mode** the goal is
  `BEGINNER_HOURS_REQUIRED` (4) — see below.
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
  badge in history lists). Reclaimed hours count toward the 8 (or the 4).
- Constants live at the top of `StreakEngine.kt`.

### Beginner mode (2026-08-04 — softens the day-one cliff)

- **The goal drops to `BEGINNER_HOURS_REQUIRED` (4) hours/day.** Every daily
  goal in the UI is `state.hoursRequired`, never the raw constant.
- **Mode is replayable state, not a live flag.** An append-only log under
  SharedPreferences `"b"` key `"mode_events"` (`ModeEvent(timestamp,
  enteredBeginner)`), mirroring `reclaim_spends`. A day's mode is the mode in
  effect **at that day's END**, so a mid-day toggle re-scores the whole day
  under the mode it finished in. All three engine readers take the same
  `modeEvents` list, so they cannot disagree.
- **Beginner days are transparent to the streak** — that is what "paused"
  means in replay: ≥4 distinct rated hours → `DayOutcome.BEGINNER_COMPLETE`;
  under 4 → **no outcome at all** (no MISSED, no rest-day consumption, no
  reset). The streak neither extends nor breaks, and resumes at its old count
  on the next Master-mode day.
- **Reclaim stays fully available** in beginner mode (free, 2/day, 🕐 badge,
  counts toward the 4) — it improves the record, which is the point.
- **Fresh installs land in beginner mode**: on startup, if `"mode_events"` is
  absent AND there are zero ratings, a beginner-enter event is written
  (`ensureModeInitialized`, same "key absent ⇒ never configured" idiom as
  `tags_v2`). Existing users with ratings stay in Master, no event written.
- **Graduation nudge**: a one-time dialog (flag `"beginner_graduation_shown"`,
  set the moment it appears) at `BEGINNER_GRADUATION_DAYS` (3) completed
  beginner days — "Level up" / "Not yet". Declining costs nothing and never
  repeats; the toggle in the menu is always there.
- The ring-closed celebration still fires at 4 hours (it's the one daily
  ceremony) but announces "N BEGINNER DAYS", never a day streak.

### Lexicon (used in UI copy — 2026-08-03 copy pass)

**Plain words win.** Bee metaphor survives only where it earns its place; the
forced vocabulary (hive, cell, comb, forage, waggle) is gone from every
user-visible string.

Kept deliberately: the app name **Beeing** · the reclaim phrase "Missed rating
a special hour? Send a bee back to revisit it" 🐝 ("revisit" is the
owner-chosen verb) · the "🔒 Lock phone and bee mindful🐝" button · the ⬢
hexagon as a pure visual motif (no "cell" wording attached to it).

Current vocabulary: **Streak** = the tab (nav label) and the long game;
a qualifying day is a **complete day** ("Day complete", "⬢ Day complete",
calendar legend "complete"), and the count reads "**N-day streak**" /
"Day N of your streak", never a cell count · **History** = the event log on
the Streak tab · 🌙 **Rest day** = the one free forgiven day per week
("the streak held") · 🌱 **Beginner day** = a completed 4-hour day in
**beginner mode** ("Beginner day complete", calendar legend "beginner day");
the opposite mode is **Master mode** (8 hours) and a streak inside beginner
mode is **paused**, never lost ("Streak paused at N", "Resumes in Master
mode") · 🕐 = an hour rated from memory (a reclaimed entry), so
it reads apart from in-the-moment ratings · celebration is reserved for rare
milestones — never per-save; the one daily celebration is the ring closing.
Honey/🍯 and flowers/🌸 no longer appear anywhere in mechanics — honey gold
survives only as an accent color.

**Internal names still use the old words** (`StreaksTab.kt` is the "Hive tab",
`DecagonCombDial`, `CombStrip.kt`, `HoneyGold`, comments): identifiers were
left alone on purpose to keep the copy diff small. When reading this file's
tables below, "Hive" means the Streak tab.

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
| `MainActivity.kt` | Theme (dynamic M3), notification-tap intent → `PendingRating`, alarm scheduling, `ensureModeInitialized` (fresh install ⇒ beginner mode, before `setContent`) |
| `HourlyPulseApp.kt` | Root composable: HorizontalPager with 3 tabs (0=Hive, 1=Now default, 2=Past), `BottomNavBar` (**the Scaffold's `bottomBar`** — permanent, opaque, edge-to-edge; icon+label per tab; highlight pill driven by `currentPage + currentPageOffsetFraction` so it tracks swipes live). **No shared header** — the Scaffold topBar is just a constant status-bar inset for every tab, so nothing pops in/out on a page settle. Each tab owns its own header inside its scroll content. Holds settings/info dialogs (the ⋮ menu now includes "How Beeing works"), import/export, ON_RESUME auto-focus of the pending hour. Also owns the **beginner-mode seam**: it computes the shared `streakState`, renders the "Beginner mode (4-hour days)" checkbox row in the Data-options dialog (a row like auto-backup; entering beginner with `currentStreak > 0` closes the menu and raises the `showBeginnerConfirm` pause dialog instead of toggling), the one-time graduation `AlertDialog` (fired by a `LaunchedEffect` on `beginnerDaysCompleted`, flag written before it shows), and the mode-aware "🌱 Beginner Mode" paragraph in "How Beeing works" |
| `NowTab.kt` | The rating flow. Header row = a **hamburger** `Icons.Default.Menu` button on the far left (opens the data-options menu — `onMenuClick`; the old ⋮/`MoreVert` is gone), then the large neutral "Beeing" title (`headlineLarge`, `onSurface`), then a weight spacer pushes the **slim** right-aligned `StatusStrip` pill (⬢ streak · ring x/goal — no currency segment — 16sp emojis, 14sp numbers, 18dp ring; **hairline** 0.5dp white-0.22α outline, sized to sit level with the title; taps to the Streak tab; merged semantics announce "N-day streak · H of G hours rated today" with the click label "Open Streak tab". In **beginner mode** the ⬢ becomes 🌱, the ring/ratio read x/4 via `state.hoursRequired`, and the description leads with "Beginner mode · streak paused at N") to the right edge. Rating card header is one line: **"How was your"** + a compact `HourWindowPicker` chip (‹ range › carets — each enabled only when that neighbouring hour is still pending; offset 1 = earlier/expiring, offset 0 = latest) + the countdown to its right + the "why these hours" info icon at the far edge; then dial→tags→notes→save (the rating input is `DecagonCombDial` from `DecagonDial.kt`; no section label above the tags — the save button's "Tag it to save" state teaches the rule). The notes box (`NotesSection`) is collapsed to a slim "＋ Add a note" hint row until tapped. `TagPickerSection` shows up to `COLLAPSED_TAG_COUNT` (9, ~3 rows) chips before folding the rest into a neutral-toned `AssistChip`: **"+N more"** just expands to reveal hidden tags; **"edit tags"** (shown when nothing is hidden) and the expanded editor's pencil open **`ManageTagsDialog`** (private in `NowTab.kt`) — one row per tag with long-press-drag reorder (fixed 46dp rows, index math on drag offset), inline rename (✏️ → `BasicTextField` + tick; **picker-only** — past entries keep the old name by owner decision), delete behind a confirm `AlertDialog` (**picker-only** too: old entries keep the tag), and a "New tag" footer that reuses the add-tag `AlertDialog`. The expanded editor's "✓ Show less" pill rides inline in the chip `FlowRow` (no extra vertical line). Caught-up hero with the "🔒 Lock phone and bee mindful🐝" button + `TodayStripCard`. Sends `ACTION_LOCK_PHONE` broadcast if `isLockAccessibilityServiceEnabled`, else shows a dialog that opens Settings → Accessibility |
| `LockAccessibilityService.kt` | `AccessibilityService` that registers a receiver for `ACTION_LOCK_PHONE` and calls `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` — same as the power button, so it never touches keyguard/device-admin policy and biometric unlock keeps working next time. `isLockAccessibilityServiceEnabled(context)` checks `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. User must toggle it on once under Settings → Accessibility (can't be done programmatically) |
| `DecagonDial.kt` | **The rating input now mounted in `NowTab`**: `DecagonCombDial` — a 10-sided readout core with ten **round** cells docked one per edge, spun as one unit with 36° detents (drag anywhere, fling with damped snap, tap a cell to seek). Fixed notch on top marks the value; resting state is a theme-aware (`surfaceVariant`/`outline`) dashed core reading "Pick a rating"; rated state floods the core with `dialScoreColor` and shows the numeral plus the Title-Cased score word (`DIAL_WORDS`) inside the core. Haptic+click per detent |
| `CombStrip.kt` | Legacy (unmounted since the dial landed): the previous rating input, **one big flat-top hexagon** (`FlatHexShape`, softly rounded vertices; `aspectRatio(1.1547f)`, `fillMaxWidth(0.86f)`), vertical drag (top = 10, bottom = 1). Two states keyed only on `selectedScore`: **null →** `RestingScaleHex` draws ten filled, soft-tinted (`band × 0.35α`) island bands with gaps (`roundedPolygonPath`, sized to the hex width at each height via `flatHexHalfWidth`), each numbered 1–10, and the whole cell breathes (`pulseScale`); **set →** the hexagon floods one solid `scoreBandColor` with a big centered numeral, staying filled after release (so the notification's pre-selected score lands filled). Haptic tick per boundary; **no loupe** (removed — the big numeral replaces it). `scoreWord()`, `FlatHexShape`, `flatHexHalfWidth`, `roundedPolygonPath` |
| `StreaksTab.kt` | Hive tab: streak hero (`StreakMeter`), "Send a bee back" 🐝 reclaim CTA (shown when reachable hours exist; disabled once the day's 2 bees are spent), calendar with score-tinted dots (🌱 = completed beginner day, 🌙 = rest day, hollow red ring = missed — 🌱 is a glyph, not a hollow tinted dot, which would collide with the missed ring at 9dp), a `FlowRow` legend (four keys + the info button no longer fit one line), hive log, reclaim dialogs (`reclaimHourLabel` prefixes "Yesterday ·" across midnight; the cap is re-checked in `onReclaim` because the dialog can outlive the tap), and the rules explainer, which swaps to a "🌱 How beginner mode works" body in beginner mode. No currency card |
| `StreakEngine.kt` | Pure streak logic. **`replayDays` is the single walk over history** — private, returns one `ReplayDay` per calendar day (outcome, streak after, `brokeStreak`, `beginner`, that day's hour timestamps + reclaim spends); `computeStreakState`, `computeDayOutcomes` and `computeStreakLog` are thin readers over it and all three take the same `modeEvents` list, so they *cannot* disagree. Also `DayOutcome` (QUALIFIED/REST/MISSED/**BEGINNER_COMPLETE**), the **mode-event layer** (`ModeEvent`, `loadModeEvents`/`recordModeEvent`, `isBeginnerMode`, `hoursRequiredFor`, private `beginnerAt(timeMs, events)` = the mode at an instant, `ensureModeInitialized`, `beginnerGraduationShown`/`markBeginnerGraduationShown`), `reclaimsUsedToday`, the `isReclaimed`/`visibleTags()` entry extensions, `StreakMeter`/`StreakRing` composables (`StreakRing` takes `hoursRequired`, so the ring is 4 segments in beginner mode; `StreakMeter` reads `state.hoursRequired` and shows "Beginner day complete ✓" / "Streak paused at N" + "Resumes in Master mode"), constants |
| `PastTab.kt` | **Journal first, analytics second.** `PastTab` is now a shell: it owns the active window, the shared `EditEntrySheet` + delete-undo snackbar, and an `AnimatedContent` that swaps between two full-screen views — **`JournalView`** (default) and **`TrendsView`** — with a `BackHandler` returning from Trends. **`JournalView`**: a fixed `JournalHeader` (month label of the topmost visible day, derived from `listState.firstVisibleItemIndex`, tapping it opens `DateJumpDialog`; "Trends ›" pill on the right) over a `LazyColumn` of `JournalDayCard`s, newest day first (`buildJournalDays` groups ratings into `JournalDay`; **days with no ratings are not rows**). Collapsed card = date (`journalDayLabel`: Today/Yesterday/`EEE d MMM`) · hour count · avg pill (`getScoreColor`) over a slim tick strip — one 14dp tick per active-window hour, `scoreBandColor` when rated, positioned by hour, with `TickCapPill` ▲N/▼N badges for out-of-window ratings. Tapping the card expands that day inline (single-open accordion, `expandedDay` hoisted in `PastTab`) into `HourEntryRow`s; tapping an hour opens `EditEntrySheet` **with no edit-window gate** (same latitude `HistoryPanel` always had — the journal would be pointless otherwise). `DateJumpDialog` is a month grid with ‹ › month arrows, clamped to the data's first/last month; only days that hold ratings are live and they're tinted by that day's average — picking one expands the day and `animateScrollToItem`s to it (any past hour in 2 taps). **`TrendsView`** is the rare deep-dive, deliberately short: static control bar (arrows + range label + `ZoomPicker`) over the `AnimatedContent` keyed by `PastViewKey` (period steps slide L/R, zoom flies via `scaleIn/Out`, driven by `navKind`), stacking summary stats (avg · hours rated · 🌙 rest days — counted off `computeStreakLog`, now passed `loadModeEvents(context)`, so beginner stretches correctly contribute none) → **one auto-written insight line** (`buildInsight`) → `ScoreBarChart` → `PatternGrid` (**Months/year zoom only, expanded by default**, `hourByHourExpanded`) → `TagScoresSection` (short inner scroll ≈5 rows with a `FadingScrollbar`). **Zoom is Weeks (across a month) / Months (across a year) only** — no Day level, no `DaySheetContent`, and every chart is a pure readout (no taps, no highlight). Its zoom/period state is local, so it opens at Weeks/this-month each time. `buildInsight(entries, ratedHours)` is pure: returns null under `INSIGHT_MIN_HOURS` (20) rated hours in the period, else joins up to two clauses with " · " — the widest gap between `DAY_PARTS` (mornings 5–11 / afternoons 12–16 / evenings 17–23, each needing `INSIGHT_MIN_SAMPLES` = 3 ratings and a gap ≥ `INSIGHT_MIN_GAP` = 0.5) and the lowest-scoring tag (≥3 uses, ≥2 tags, `visibleTags()` so the reclaim sentinel can't be named); null when neither clause qualifies. `HourEntryRow` (time range, score circle, 🕐 badge, `visibleTags()`, note preview) is journal-only now. Also `getScoreColor(Double)` |
| `UIComponents.kt` | Shared: `HeaderSection` (legacy, no longer mounted), `EditEntrySheet` (opens fully expanded on one tap via `skipPartiallyExpanded`; grouped SCORE/TAGS/NOTE sections via the `SectionLabel` overline; edits score, note, **and** tags — every available tag is a toggleable `InputChip` (centered, tightened rows). Header = "Edit entry" + date on the left, time range flanked by ‹ › **seek carets** right-aligned. Carets step through that day's rated hours only (`dayEntries`, sorted; disabled at the ends), swapping `currentEntry` in place without closing; edited buffers are keyed on `currentEntry.id` so they reset per hour. Seeking mid-edit raises a save-or-discard `AlertDialog` (`pendingTarget`). Callbacks are split `onPersist`/`onClose` so an in-sheet save doesn't dismiss the sheet), `SoulFuelTagsSection` (display-only tag chips + a pencil that opens `NowTab`'s `ManageTagsDialog`; the old in-place delete mode is gone), `NotesSection` (collapsed "＋ Add a note" row → compact `BasicTextField`; a `hadFocus` flag stops the initial unfocused `onFocusChanged` event from re-collapsing it), `HistoryPanel` (🕐 badge + `visibleTags()`; legacy — unmounted since the Past tab became a journal, which supersedes it), misc format helpers (`formatHour`, `getOrdinalSuffix`, `isSameDay`) |
| `StatsComponents.kt` | Just the weekly report notification now (`buildWeeklySummaryText` — third line is mode-aware: "streak ⬢N", or "🌱 N beginner days · streak paused at M" — `showWeeklyReportNotification`, `schedule`/`cancelWeeklyReport`) |
| `RatingsViewModel.kt` | Shared state: `allRatings`, `refreshTrigger`, save/delete/load wrappers, plus `modeEvents` / `beginnerMode` / `setBeginnerMode` — the mode lives here (not per-tab like `spendsVersion`) because it is replay input: one toggle has to re-derive the meter, calendar and log in the same composition |
| `Utils.kt` | `RatingEntry`, persistence (SharedPreferences `"b"`), notifications + hourly alarm (`NotificationReceiver`), tags, CSV import/export (HMAC-signed), auto-backup, `computeActiveWindow`, `EDIT_WINDOW_MS`, `scoreBandColor(Int)` |
| `res/drawable/ic_sym_*.xml` | The vendored Material Symbols. Each is a converted SVG: Material Symbols ship `viewBox="0 -960 960 960"`, which a `<vector>` cannot express (there is no viewport origin), so every file wraps its path in `<group android:translateY="960">`. `fillColor` is opaque black and the tint comes from `BeeIcon` |
| `ui/icons/BeeIcons.kt` | **The icon vocabulary.** `Sym` enum (one entry per meaning: Streak/Beginner/Rest/Reclaim/FromMemory/Lock/Hourglass/Reset/Star/Sparkle/Alarm/Chart/Tag/Note/Hive/CaughtUp) → a vector drawable in `res/drawable/ic_sym_*.xml`, plus `BeeIcon(sym, size, tint, contentDescription)` and `SymTitle(sym, text)` for dialog titles. Drawables are **Material Symbols Rounded**, converted from the official SVGs (see below) |
| `Celebration.kt` | Full-screen ring-closed confetti takeover (`RingClosedCelebration`), driven by a `RingCelebration(count, beginner)` payload so a paused streak is never announced as a day streak — beginner reads "N BEGINNER DAYS" + "Ring closed — beginner day complete! 🌱" |

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
- Mode toggles persist under `"mode_events"` for the same reason: a single
  boolean would rewrite history every time it flipped. Format is
  `"<millis>:<0|1>"` records joined by `,` (1 = entered beginner). Absence of
  the key means "never configured" and is what `ensureModeInitialized` tests;
  the graduation flag is the separate boolean `"beginner_graduation_shown"`.
  Neither is exported in the CSV.
- Tags: key `"tags_v2"`, max 30, defaults in `DEFAULT_TAGS`.

## Design system (agreed with the owner — keep these rules)

- **One job per screen, one card per job.** Now = act, Hive = the long game
  (streak, calendar, repair), Past = the record — the journal of days is the
  screen, and insight (summary → magnitude → pattern → drivers) lives one tap
  away in Trends.
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
  Every scrollable tab ends with a 24dp spacer — the nav is a `bottomBar` and
  insets the pager itself, so nothing has to clear a floating pill any more.
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
- Known TODOs: active-window hysteresis persistence; `HeaderSection` and
  `HistoryPanel` in `UIComponents.kt` are still unmounted legacy and can go
  once nothing else references them (the legacy *charts* are gone as of
  2026-08-04).
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
- 2026-08-03 (copy pass): **forced bee vocabulary pruned from every visible
  string** — strings only, no identifiers or filenames touched (see the
  Lexicon section above for the resulting vocabulary). Nav tab "Hive" →
  **"Streak"** (the nav icon's `contentDescription` derives from the same
  label, so it followed); `StreakMeter` title "N-cell hive"/"No hive yet" →
  "N-day streak"/"No streak yet"; calendar legend "hive day" → "complete" and
  its info icon "How the hive works" → "How streaks work"; "Hive log" →
  **"History"**; day-stats "⬢ Cell built" → "⬢ Day complete" and "🌙 Rest day —
  the hive held" → "— the streak held"; log events "Hive started"/"Cell added"
  → "Streak started"/"Day complete" with the detail line "Day N of your streak
  — 8 hours on <date>", "Hive reset 💔" → "Streak reset 💔"; both explainers
  (the ⋮ "How Beeing works" dialog and the Streak-tab rules dialog) rewritten —
  "every rated hour is a foraging trip … builds one cell" became "rate 8 hours
  in a day and that day is complete — complete days are your streak"; weekly
  report line "hive ⬢N" → "streak ⬢N"; the Now and Past pointers "send a bee
  back from the Hive" → "…from the Streak tab". `StatusStrip` had no
  accessibility description to rename (it was an unlabelled `Card` whose
  children announced as the bare glyphs "⬢ 5", "3/8"), so one was **added**:
  `semantics(mergeDescendants = true)` with "N-day streak · H of 8 hours rated
  today" (or "No streak yet · …"), plus `onClickLabel = "Open Streak tab"` on
  the `clickable`. This is the one non-string change in the pass.
- 2026-08-03 (Past goes journal-first): the Past tab **opens on the log, not
  the charts**. A **re-composition, not a redesign** — no chart internals were
  touched. `PastTab` is now a shell around an `AnimatedContent` swapping
  `JournalView` ⇄ `TrendsView`; the entire former tab (control bar, `PastViewKey`
  region, stats, `ScoreBarChart`, `PatternGrid`, `TagScoresSection`,
  `DaySheetContent`) moved verbatim into `TrendsView`, reached by the header's
  "Trends ›" pill and left by system back.
  The journal is a `LazyColumn` of `JournalDayCard`s, newest first, one card per
  day that holds ratings (`buildJournalDays`): date · hour count · avg pill over
  a tick strip of that day's active-window hours (`scoreBandColor`, ▲N/▼N
  `TickCapPill`s for out-of-window ratings — the design system's cap-pill rule,
  now applied per day). Tapping a card expands it inline (single-open accordion,
  state hoisted so a Trends round-trip returns to the same open day and scroll
  position); tapping an hour opens the existing `EditEntrySheet` with its seek
  carets intact. `DateJumpDialog` (month grid, ‹ › clamped to the data's range,
  live cells only on days with ratings, tinted by that day's average) scrolls to
  and opens the picked day — any past hour in **2 taps**.
  Three deliberate calls: (1) the journal's hour rows are **not** gated by
  `EDIT_WINDOW_MS` — `HistoryPanel` never gated either, and a journal you can't
  open is not a journal; (2) days with zero ratings are **not** rows (the Streak
  tab's calendar is where absences read); (3) `TrendsView`'s zoom/period state is
  local, so Trends always opens at Days/this-week. Ripples: the old "RECENT
  HISTORY" card is gone (the journal supersedes it), leaving `HistoryPanel` in
  `UIComponents.kt` unmounted/legacy; the per-hour row was extracted to a shared
  `HourEntryRow` used by both the journal and `DaySheetContent`.
- 2026-08-04 (Trends polish): **fewer, better analytics for the rare visit.**
  (1) **The Day zoom is deleted** — `Zoom` is now `{ W, M }` (Weeks across a
  month, Months across a year). Day-level lookup is the journal's job, and with
  no Day view the whole tap-a-bar-to-drill path went with it: `DaySheetContent`,
  `openDay`, `selectedDayStart`/`sheetDayStart`, the `highlightStartMs` +
  `onUnitClick`/`onColumnClick` params on `ScoreBarChart`/`PatternGrid`,
  `PeriodUnit.drillStartMs`, `weekOffset`, `zoomOriginX` (only ever 0.5f) and
  `TrendsView`'s `onEdit`. **Every chart in Trends is now a pure readout** —
  nothing in it is tappable. `HourEntryRow` lost `showEditIcon` and its nullable
  `onClick` (the journal is its only caller).
  (2) **Order is summary → insight → bar chart → pattern grid → tags**, and the
  grid — the crown jewel — is rendered **only at Months/year zoom, expanded by
  default** (`hourByHourExpanded` starts `true`). It was removed from the Weeks
  view per owner call; the show/hide toggle and the `gridExpanded` full-day cap
  pills are unchanged.
  (3) **One auto-written insight line** under the stats: `buildInsight` is a
  pure function over the period's entries returning at most two clauses joined
  by " · " — "Mornings average 2.1 higher than evenings" and "lowest tag: Work
  (2.8)". It is heavily gated so it can never be confidently wrong: silent under
  `INSIGHT_MIN_HOURS` (20) rated hours in the period, each day-part and tag
  needs `INSIGHT_MIN_SAMPLES` (3) ratings, the day-part gap must clear
  `INSIGHT_MIN_GAP` (0.5), the tag clause needs ≥2 tags to have a "lowest", and
  the whole line is dropped when neither clause qualifies. Tags come from
  `visibleTags()` so `"💧reclaimed"` can never be named.
  (4) **Legacy chart code deleted** after verifying zero references:
  `ProfessionalChart`, `InsightPanel`, `getChartData`, `isWithinDays` from
  `UIComponents.kt`; `InsightsContent`, `CollapsibleSection`,
  `TagCorrelationCard` and the entire period-bucket model (`StatPeriod`,
  `PeriodBucket`, `buildPeriodBuckets` + helpers) from `StatsComponents.kt`,
  which is now just the weekly report; and the orphaned `ChartView` enum from
  `Utils.kt`. `isSameDay` was kept (StreaksTab uses it). ~430 lines gone.
  `HeaderSection`/`HistoryPanel` were left alone — still unmounted, not charts.
- 2026-08-05 (dial cells go round): the ten ring cells are **circles**, not
  hexagons. Ring geometry moved into three shared constants + `cellRadius`/
  `cellDistance` (`DecagonDial.kt`) so the tap hit-test and the draw pass can no
  longer drift apart — they used to duplicate the same five-term expression.
  `CELL_R_FRAC` is 0.0902 (not the old 0.0966 circumradius): a hexagon docked on
  its flat side reached `dn + rr` = `apo + gap + 1.866*rr`, so 0.0902 puts the
  ring's outer extent within a pixel of where it was, leaving the dial's
  footprint and `NowTab`'s 5% rim trim untouched. Digits and the blur annulus
  were re-expressed against the circle radius at their old absolute sizes; the
  hit-test slop went `rr * 1.1` → `rc * 1.18` (a circle has no corners to lean
  on). The decagon core is unchanged — the crisp-vs-round contrast is now the
  point. `roundedPoly` survives for the core only.
- 2026-08-05 (dial color + card inversion), four changes:
  (1) **Smaller core**: `CORE_R_FRAC` 0.2557 → 0.2100. The ring had to stay put,
  so `cellDistance` is now the independent `RING_D_FRAC` (0.3533 = the old
  derived apothem+gap+radius) instead of being computed off the core — a derived
  distance would have dragged the cells inward and shrunk the whole dial. Text
  inside the core is now sized off `rd`, not `s`, so it scales with the shape.
  (2) **The ring is gray, the digits carry the band**: every unselected cell
  fills with one flat `cellGrayColor` (surfaceVariant→onSurfaceVariant 18%) and
  its digit is `scoreBandColor` — so the only *colored fill* on the dial is the
  selection. `mutedCellColor` (per-score wash) is gone. Digits use
  `cellDigitColor`, which lifts the band 38% toward white on dark cells / 18%
  toward black on light ones: the 1–4 red is 0xFFB71C1C and vanished on a dark
  gray otherwise.
  (3) **`dialScoreColor` = `scoreBandColor` again** — it had been flattened to a
  constant `AccentOrange`, so the core said nothing about the rating it showed.
  `dialDigitColor` now flips on the band's own luminance (>0.35 ⇒ near-black),
  because white-on-amber was the reason the flat fill existed.
  (4) **Background/card inversion**: the `Scaffold` `containerColor` in
  `HourlyPulseApp` takes the tinted `surfaceVariant@0.4 over background` blend
  the rating card used to have, and `NowTab`'s rating card takes the plain
  `background` — the hero now reads as a cut-out, not a raised panel. Every
  other card still fills with `surfaceVariant@0.4`, so they composite one step
  off the new tint and keep their edge on all three tabs.
- 2026-08-05 (dial tightened + pill chips):
  (1) **The ring is derived from the core again** — `RING_D_FRAC` is gone;
  `cellDistance` is back to `apothem + CELL_GAP_FRAC + CELL_R_FRAC`, so
  shrinking the core pulls the cells in with it. (The previous round decoupled
  them to hold the ring still, which is exactly what made the cells look no
  closer.) Numbers: core 0.2351, cell radius 0.1011, gap 0.0176 — bigger
  circles, snug against the core and each other (adjacent-cell gap fell from
  0.038*s to 0.009*s). Two constraints pin them: cells must clear their
  neighbours (`CELL_R_FRAC` <= `0.309 * dn`, the 36°-chord half-length; ceiling
  here is 0.1058) and the outer extent stays 0.4435*s so the notch clearance
  and the rim trim still line up.
  (2) **Rim trim now cuts both ends**: the art's real vertical span is 1.3%
  below the box top (notch tip) to 5.6% above its bottom, so `NowTab`'s `layout`
  modifier trims both, placing at `-top`. The spacer above the dial went
  20dp → 4dp. ~39dp of dead space reclaimed inside the rating card.
  (3) **Tag chips are pills**: shared `TagChipShape = RoundedCornerShape(50)` in
  `UIComponents.kt`, applied at all four chip sites (NowTab's picker InputChip +
  its "+N more"/"edit tags" AssistChip, `SoulFuelTagsSection`, `EditEntrySheet`)
  — same reason `tagChipColors()` is shared: four call sites, one look.
- 2026-08-05 (tighten, fix the nav, free the save button):
  (1) **Cell size reverted, ring kept tight.** The previous entry grew the cells
  to 0.1011 to hold the old 0.4435*s footprint — the wrong trade. Cells are back
  to `CELL_R_FRAC` 0.0902 with the core at 0.2100 and the gap at 0.0140, so the
  ring's outer extent is **0.394*s**: the art itself is ~11% smaller and the
  space comes back as layout. The rim trim grew to match (top 6.3% / bottom
  10.6% — the notch and the lowest cell), ~50dp reclaimed inside the card.
  (2) **Nav is a fixture, not an overlay.** `FloatingPillNavBar` →
  `BottomNavBar`, mounted as the Scaffold's `bottomBar`: opaque
  `surfaceContainer`, edge-to-edge, no side inset or 20dp radius,
  `navigationBarsPadding` inside the Surface. Because the Scaffold now insets
  the pager, content stops above the bar instead of scrolling under it, and the
  112dp clearance spacer in all three tabs dropped to 24dp.
  (3) **The save button moved out of the rating card**, into the Now column
  right below it (12dp gap). The card is inputs; committing is its own object,
  so on a short screen the button clears the fold instead of riding at the
  bottom of a tall card. Its state-machine label ("Pick a rating" / "Tag it to
  save" / "Save Nth") is unchanged.
- 2026-08-05 (Manage tags redesign, from an owner mockup): the popup is now a
  panel — grabber pill, 28dp corners, full-width (`DialogProperties(
  usePlatformDefaultWidth = false)`, the platform cap was too narrow for the new
  row), 26sp Baloo title over "Hold and drag to reorder", circular ✕. **Each tag
  is its own card** (60dp, 16dp radius, `surfaceVariant@0.45`, 8dp gap):
  drawn 6-dot `DragDots` · `TagGlyphTile` · name · circular `RowActionButton`
  pencil · circular red trash. Footer is a **dashed** "Add new tag" slot
  (`drawBehind` + `PathEffect.dashPathEffect`) with a filled primary ⊕, so the
  one row that isn't a tag reads as a slot to fill.
  Two things worth knowing: (1) **the emoji is not a new field** — a tag is
  still one string like `"💼 Work"`, and `tagGlyphOf`/`tagLabelOf` split it for
  display only (leading space-delimited token with no letters/digits ⇒ glyph);
  rename still edits the whole raw string, and tags with no emoji get a monogram
  tile so every row keeps its shape. Nothing about storage changed. (2) the drag
  index math now steps by **row height + gap** (`rowStepPx`) — stepping by the
  row height alone would drift by one gap per position moved.
- 2026-08-05 (emoji → Material Symbols): **UI emoji are gone; meaning is
  carried by icons.** 16 Material Symbols Rounded SVGs were fetched from
  `google/material-design-icons` and converted to `res/drawable/ic_sym_*.xml`
  (the `viewBox="0 -960 960 960"` → `<group android:translateY="960">` trick
  noted in the file map), reached through the `Sym` enum in
  `ui/icons/BeeIcons.kt`. Every glyph that used to stand for something —
  ⬢ 🌱 🌙 🐝 🕐 🔒 ⏳ 💔 ⭐ ✨ ⏰ 📊 🏷️ 📝 🌟 — is now a tinted, sized,
  described icon. Where the emoji sat **inside a sentence** the copy simply lost
  it ("it becomes a rest day"), since an inline icon in prose costs
  `InlineTextContent` plumbing for no gain; where it **led a label** it became a
  `Row` of icon + text, or `SymTitle` for the eight dialog titles.
  Deliberately NOT converted: **`RECLAIM_TAG`** ("💧reclaimed") is a persisted
  sentinel; and **notification
  text** (the weekly report) simply dropped its emoji, because a Notification's
  body cannot render a vector drawable inline. The typographic marks ‹ › ⋮ ▲ ▼ ✓
  are not emoji and stayed.
  Two ripples: `StreakEventType.emoji()` became `.sym()` (log rows draw an icon
  and the titles no longer repeat it — "Rest day", not "Rest day 🌙"), and the
  weekly report's streak line reads "N-day streak" instead of "streak ⬢N".
- 2026-08-05 (built-in tag emoji too — **display-only**): the 10 `DEFAULT_TAGS`
  now show Material Symbols, but **their stored strings are untouched**, and
  that constraint is the whole design. A tag's full string *is* its identity:
  it sits in every `RatingEntry.tags`, tag-score aggregation groups by it, and
  CSV export writes it. Editing `DEFAULT_TAGS` to "Work" would orphan every
  historical entry tagged "💼 Work" — the picker and the history would stop
  matching and Trends would split one tag in two — and `loadTags` returns
  `DEFAULT_TAGS` for anyone who never edited their list, so it would hit
  existing users, not just fresh installs.
  So `ui/icons/BeeIcons.kt` gained a display layer: `tagGlyphOf`/`tagLabelOf`
  (moved out of `NowTab.kt`, where `ManageTagsDialog` had them privately),
  `tagSym(tag)` — matched on the **label**, so it survives the emoji being
  stripped — and `tagDisplayText(tag)`. Nine new `ic_sym_tag_*.xml` drawables
  (Rest reuses `bedtime`).
  **User-created tags are left exactly as typed**: no `tagSym` match means the
  chip shows their raw string, emoji and all. Guessing an icon for "Guitar
  practice" would be worse than showing none, and the emoji there is the user's
  choice, not app chrome.
  Applied at: the three chip sites via the shared `TagChipContent` (in the
  `label` slot, not `leadingIcon` — one composable, four one-line call sites),
  `TagGlyphTile` in the manage dialog (icon → user emoji → monogram), Trends'
  `TagStatRow`, and every plain-text tag list (`HistoryPanel`, `HourEntryRow`,
  `BestHourCard`, the day-stats "Top tags", `buildInsight`'s lowest-tag clause,
  and the weekly notification's "most logged").
- 2026-08-05 (fixed rating frame · fire · Fredoka):
  (1) **The Now tab's rating state is a fixed frame, not a scrolling page.**
  `verticalScroll(scrollState)` is now applied only in the caught-up branch; in
  the rating branch the Column is `fillMaxSize` with the header at the top, the
  card in a `Box(Modifier.weight(1f))`, and the save button **pinned** under it,
  directly above the `bottomBar`. The card's own `Column` carries
  `verticalScroll(cardScroll)` with `FadingScrollbar` (made `internal` in
  `PastTab.kt`; the Trends tag list is the other caller) overlaid at its
  `CenterEnd` — the frame stays put, only its contents move. Because the card is
  measured against the weight slot, it wraps its content when short and fills +
  scrolls when tall. `BestHourCard` had to move **inside both branches** (it
  used to sit after the if/else): below the card but above the button in the
  rating state, since anything after the button would push it off the frame.
  (2) **The streak counter is a flame.** New `Sym.Fire`
  (`local_fire_department`). The split is deliberate and worth keeping:
  **`Sym.Fire` = the streak** (Now's `StatusStrip`, "Build Your Streak", "How
  streaks work", the ring-closed celebration) and **`Sym.Streak` (hexagon) = one
  complete day** (log rows, the day-stats popup) — the same distinction the
  Lexicon draws between a complete day and the run of them.
  (3) **Fredoka** for the two big headers ("Beeing", "How was your hour?") and
  the save button; `FredokaFontFamily` in `Type.kt`, three static cuts in
  `res/font/fredoka_{500,600,700}.ttf` (fetched from Google Fonts). Baloo 2
  stays for the rest of the hero text. The reason for the swap is mechanical,
  not taste: `BalooFontFamily` is a single variable file pinned to weight 800,
  so every size renders at the same heavy density — Fredoka ships real static
  weights, so `FontWeight` actually selects one (Medium for the big header,
  SemiBold for the title, Bold for the CTA).
- 2026-08-04 (Beginner mode): **day one is a 4-hour day, not an 8-hour cliff.**
  (1) **Mode is replayed, never a live flag.** A boolean would make history lie
  — yesterday's outcome would change the instant the toggle moved. So the
  toggle appends `ModeEvent(timestamp, enteredBeginner)` records to
  `"mode_events"` (same idiom as `reclaim_spends`), and `replayDays` asks
  `beginnerAt(dayEndMs, events)` per day: **the mode at the day's END wins**, so
  a mid-day toggle re-scores that whole day. `dayEndMs` is derived from the next
  day's start rather than `+24h`, so DST days still end where they end. All
  three readers (`computeStreakState`/`computeDayOutcomes`/`computeStreakLog`)
  take the same `modeEvents` list — the 2026-08-03 "cannot disagree" guarantee
  extends to mode.
  (2) **Beginner days are transparent to the streak**, which is what "paused"
  means in replay: ≥`BEGINNER_HOURS_REQUIRED` (4) hours → the new
  `DayOutcome.BEGINNER_COMPLETE`; under 4 → **no outcome at all**, so no MISSED,
  no rest-day burn, no reset. `streakAfter` carries the old count forward
  untouched and it resumes on the next Master day.
  (3) **The goal is threaded, not re-read.** `StreakState` gained
  `beginnerMode`/`hoursRequired`/`beginnerDaysCompleted` + a `streakPaused`
  helper, and every daily-goal site now reads `state.hoursRequired`:
  `StreakMeter` (both layouts), `StreakRing` (a new `hoursRequired` param — the
  ring is genuinely 4 segments), `StatusStrip`/`MiniHourRing`. Mode-aware copy
  swept through the engine log ("Beginner day complete 🌱 — … streak paused at
  N"), `StreakLogContent`'s empty state, the Streak-tab rules dialog, "How
  Beeing works" (new 🌱 paragraph) and the weekly report line.
  (4) **The mode lives in `RatingsViewModel`**, not per-tab like `spendsVersion`
  — one toggle must re-derive meter, calendar and log in the same composition.
  (5) **Fresh install ⇒ beginner** via `ensureModeInitialized` in
  `MainActivity.onCreate` (key absent AND zero ratings). Existing users get no
  event, so their history replays byte-identically. Note the consequence: a user
  who deletes every rating lands back in beginner mode next launch.
  (6) **Graduation nudge** at 3 completed beginner days — one dialog, ever;
  the flag is written *when it shows*, so "Not yet" never re-asks.
  (7) UI ripples: calendar 🌱 at 11sp in the slot the old 🌸 used (a glyph, not
  a hollow tinted dot, which would collide with the missed ring); the legend
  became a `FlowRow` to fit a fourth key; `RingClosedCelebration` now takes a
  `RingCelebration(count, beginner)` so the one daily ceremony still fires at 4
  hours without claiming a day streak; `StatusStrip` shows 🌱 instead of ⬢ so
  the mode is visible from the main screen. Reclaim is untouched and stays
  available in beginner mode by design.
  (8) One guard worth keeping: **lowering the goal also flips
  `todayQualified`**, so `NowTab`'s celebration effect now also keys on
  `hoursRequired` and re-baselines without firing when the goal itself changed
  — a settings toggle must never trigger the daily takeover.
