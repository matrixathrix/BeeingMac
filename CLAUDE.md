# CLAUDE.md

Context for Claude Code when working in this repository. Read this instead of
re-deriving the codebase from scratch.

## What this app is

**Beeing** — an Android mindfulness app (single module `app`, Jetpack Compose,
Material 3 dynamic color). Every clock hour a notification asks the user to
rate the past hour 1–10. Tapping a score opens the app with it pre-selected;
the user must add ≥1 activity tag before saving. The product's core loop:
notification → rate → tag → save → lock phone → back to life.

### Product rules (the economy)

- Rate **8 distinct hours/day** → that day counts toward the streak.
- Only the **last completed hour and the one before it** are ratable
  (2-hour window). Existing ratings are editable up to **10 hours** back
  (`EDIT_WINDOW_MS`).
- Past the 8-hour mandate, each extra rated hour earns a **🌸 flower**
  (bank cap 20). **10 flowers auto-fill a 🍯 honey pot** (max 3, 1 gifted;
  "honey pot" is the user-facing name for a saver — the code type is still
  `savers`). A missed day silently consumes a honey pot; none left → streak
  resets.
- **5 flowers reclaim** one expired hour *from today only* (mandatory note,
  tagged `RECLAIM_TAG`; reclaimed hours count toward the 8 but earn nothing).
- Constants live at the top of `StreakEngine.kt`.

### Bee lexicon (used in UI copy)

Flower = extra rated hour · Honey pot 🍯 = streak insurance (the saver, renamed)
· Cell/chamber = one qualifying day; the streak reads as a "cell hive" (⬢ N),
no 🔥 · Comb = the rating widget · Hive = the streak tab · Waggle = celebration
(reserved for rare milestones — never per-save; the one daily celebration is
the ring closing).

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
| `NowTab.kt` | The rating flow. Header row = a **hamburger** `Icons.Default.Menu` button on the far left (opens the data-options menu — `onMenuClick`; the old ⋮/`MoreVert` is gone), then the large neutral "Beeing" title (`headlineLarge`, `onSurface`), then a weight spacer pushes the **slim** right-aligned `StatusStrip` pill (⬢ cell-hive · ring x/8 · 🍯 honey pots — 16sp emojis, 14sp numbers, 18dp ring; **hairline** 0.5dp white-0.22α outline, sized to sit level with the title; taps to Hive) to the right edge. Rating card header is one line: **"How was your"** + a compact `HourWindowPicker` chip (‹ range › carets — each enabled only when that neighbouring hour is still pending; offset 1 = earlier/expiring, offset 0 = latest) + the countdown to its right + the "why these hours" info icon at the far edge; then comb→tags→notes→save. The notes box (`NotesSection`) is deliberately low-contrast (hint-level border/label/text). `TagPickerSection` shows up to `COLLAPSED_TAG_COUNT` (15) chips before folding the rest into a neutral-toned `AssistChip`: **"+N more"** just expands to reveal hidden tags; **"edit tags"** (shown when nothing is hidden) opens the editor already in edit mode. In the expanded editor the tick (done) both exits edit mode **and** collapses back to the tag row. Caught-up hero with the "🔒 Lock phone and bee mindful🐝" button + `TodayStripCard`. Sends `ACTION_LOCK_PHONE` broadcast if `isLockAccessibilityServiceEnabled`, else shows a dialog that opens Settings → Accessibility |
| `LockAccessibilityService.kt` | `AccessibilityService` that registers a receiver for `ACTION_LOCK_PHONE` and calls `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` — same as the power button, so it never touches keyguard/device-admin policy and biometric unlock keeps working next time. `isLockAccessibilityServiceEnabled(context)` checks `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. User must toggle it on once under Settings → Accessibility (can't be done programmatically) |
| `CombStrip.kt` | The rating input, now **one big flat-top hexagon** (`FlatHexShape`, softly rounded vertices; `aspectRatio(1.1547f)`, `fillMaxWidth(0.86f)`), vertical drag (top = 10, bottom = 1). Two states keyed only on `selectedScore`: **null →** `RestingScaleHex` draws ten filled, soft-tinted (`band × 0.35α`) island bands with gaps (`roundedPolygonPath`, sized to the hex width at each height via `flatHexHalfWidth`), each numbered 1–10, and the whole cell breathes (`pulseScale`); **set →** the hexagon floods one solid `scoreBandColor` with a big centered numeral, staying filled after release (so the notification's pre-selected score lands filled). Haptic tick per boundary; **no loupe** (removed — the big numeral replaces it). `scoreWord()`, `FlatHexShape`, `flatHexHalfWidth`, `roundedPolygonPath` |
| `StreaksTab.kt` | Hive tab: streak hero (`StreakMeter`), reclaim CTA (only when recoverable hours exist), `GardenCard` (flowers→savers), calendar with score-tinted dots, streak log, reclaim dialogs |
| `StreakEngine.kt` | Pure streak logic: `computeStreakState`, `computeDayOutcomes`, `computeStreakLog` (all replay full history deterministically — they must never disagree), `StreakMeter`/`StreakRing` composables, constants |
| `PastTab.kt` | Analytics: D/W/M periods. **Charts are non-interactive except in Day view** — there is no tap-to-drill and no chart swipe; zoom levels change only via the `ZoomPicker`, periods only via the ‹ › arrows. In **Day view only**, tapping a day opens its bottom sheet and marks it via `selectedDayStart` (the white outline highlight — `highlightStartMs`); Week/Month bars/cells are inert and carry no highlight. One combined card: a **static control bar** (arrows + range label + `ZoomPicker`) over an **`AnimatedContent` region** keyed by `PastViewKey` — period steps slide L/R, zoom changes fly (`scaleIn/Out` at `zoomOriginX`), driven by `navKind`/`zoomOriginX`. The region stacks summary stats (avg · hours rated · 🍯 honey used) + `ScoreBarChart` + the **collapsed-by-default** hour-by-hour `PatternGrid` (show/hide toggle via `hourByHourExpanded`) + the folded-in **`TagScoresSection`** (short inner scroll ≈5 rows with a `FadingScrollbar`); each slot recomputes its own data from its key. `PatternGrid` is height-capped at the 7-col size (Week stops ballooning; extra width left blank), trimmed 24dp gutter, hour labels on the row seams. `DaySheetContent`, edit sheet. Also `getScoreColor(Double)` |
| `UIComponents.kt` | Shared: `HeaderSection` (legacy, no longer mounted), `EditEntrySheet` (opens fully expanded on one tap via `skipPartiallyExpanded`; grouped SCORE/TAGS/NOTE sections via the `SectionLabel` overline; edits score, note, **and** tags — every available tag is a toggleable `InputChip` (centered, tightened rows). Header = "Edit entry" + date on the left, time range flanked by ‹ › **seek carets** right-aligned. Carets step through that day's rated hours only (`dayEntries`, sorted; disabled at the ends), swapping `currentEntry` in place without closing; edited buffers are keyed on `currentEntry.id` so they reset per hour. Seeking mid-edit raises a save-or-discard `AlertDialog` (`pendingTarget`). Callbacks are split `onPersist`/`onClose` so an in-sheet save doesn't dismiss the sheet), `SoulFuelTagsSection` (full tag editor, used by `NowTab`'s live rating card; in edit mode the neutral-toned "New tag" `AssistChip` comes **last**, after the existing tags), `NotesSection`, `HistoryPanel`, misc format helpers (`formatHour`, `getOrdinalSuffix`), plus legacy `ProfessionalChart`/`InsightPanel` (no longer mounted) |
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
  derivable from ratings.
- Tags: key `"tags_v2"`, max 30, defaults in `DEFAULT_TAGS`.

## Design system (agreed with the owner — keep these rules)

- **One job per screen, one card per job.** Now = act, Hive = economy,
  Past = insight (summary → magnitude → pattern → drivers).
- **Color roles:** blue/primary = interactive only. Score bands = data only:
  1–4 red, 5–7 amber, 8–10 green (`scoreBandColor` for ints,
  `getScoreColor` for averages). Honey gold = streak identity. Progress rings
  are never red.
- **Color never travels alone** (amber↔green is at the CVD floor): every band
  color ships with its digit or position.
- **Ceremony budgeted by rarity:** saving = one haptic, flower = counter tick,
  ring closing = the single daily full-screen celebration, waggle = rare
  milestones only.
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
