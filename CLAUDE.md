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
  (bank cap 20). **10 flowers auto-forge a 🛡️ saver** (max 3, 1 gifted).
  A missed day silently consumes a saver; no savers → streak resets.
- **5 flowers reclaim** one expired hour *from today only* (mandatory note,
  tagged `RECLAIM_TAG`; reclaimed hours count toward the 8 but earn nothing).
- Constants live at the top of `StreakEngine.kt`.

### Bee lexicon (used in UI copy)

Flower = extra rated hour · Honey/saver = streak insurance · Comb = the rating
widget · Hive = the streak tab · Waggle = celebration (reserved for rare
milestones — never per-save; the one daily celebration is the ring closing).

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
| `HourlyPulseApp.kt` | Root composable: HorizontalPager with 3 tabs (0=Hive, 1=Now default, 2=Past), `FloatingPillNavBar`, header, settings/info dialogs, import/export, ON_RESUME auto-focus of the pending hour |
| `NowTab.kt` | The rating flow. Three queue states (2/1/0 pending hours), `StatusStrip` (🔥·ring·🌸), rating card (chips→comb→tags→notes→save), caught-up hero with Lock button + `TodayStripCard`. Exposes `ACTION_LOCK_PHONE` broadcast |
| `CombStrip.kt` | The rating input: 10 pointy-top hexagons, zoned band tints at rest, hybrid honey fill, drag loupe, haptics. `scoreWord()`, `PointyHexShape` |
| `StreaksTab.kt` | Hive tab: streak hero (`StreakMeter`), reclaim CTA (only when recoverable hours exist), `GardenCard` (flowers→savers), calendar with score-tinted dots, streak log, reclaim dialogs |
| `StreakEngine.kt` | Pure streak logic: `computeStreakState`, `computeDayOutcomes`, `computeStreakLog` (all replay full history deterministically — they must never disagree), `StreakMeter`/`StreakRing` composables, constants |
| `PastTab.kt` | Analytics: D/W/M zoom cascade (page always == control-bar period; taps narrow one level; day = terminal → bottom sheet), stats row, bar chart, hour-of-day `PatternGrid` pinned to the active window with early/late cap pills, tag scores with prev-period deltas, `DaySheetContent`, edit sheet. Also `getScoreColor(Double)` |
| `UIComponents.kt` | Shared: `HeaderSection`, `EditEntrySheet`, `SoulFuelTagsSection` (full tag editor), `NotesSection`, `HistoryPanel`, misc format helpers (`formatHour`, `getOrdinalSuffix`), plus legacy `ProfessionalChart`/`InsightPanel` (no longer mounted) |
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
  `com.example.beeing.ACTION_LOCK_PHONE`. The accessibility service that
  performs the lock lives in the owner's local build (not this repo yet);
  it should receive that action and call
  `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`.
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
- Known TODOs: active-window hysteresis persistence; accessibility-service
  receiver for `ACTION_LOCK_PHONE`; legacy chart components in
  `UIComponents.kt`/`StatsComponents.kt` can be deleted once nothing else
  references them.
