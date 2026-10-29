# Streak Savers, Recovery, Bell & Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace rest days with streak savers (earned, auto-spent, fading, gifted), add a 3-day streak recovery window, a warnings bell, streak phone alerts, "Add a past hour", a full settings page, silent hours and pause for hourly reminders.

**Architecture:** All streak rules move into one pure, JVM-testable file (`StreakRules.kt`) whose single `replayDays` walk produces day verdicts plus a list of keyed events. The streak meter, calendar, log, bell, celebrations and background alerts are all readers of that list. Notification decisions (silent hours, pause) are pure functions in `NotificationPrefs.kt`. UI work then reads those results.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, SharedPreferences `"b"`, AlarmManager, JUnit 4 (`app/src/test`).

**Spec:** `docs/superpowers/specs/2026-10-28-savers-recovery-settings-design.md`. Read it first. It's the source for every rule and every string below.

## Global Constraints

- Build/verify: `JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr" ./gradlew testDebugUnitTest` (unit tests) and `... ./gradlew installDebug` (device). If no device is attached, `assembleDebug` and say so.
- Constants (spec): `SAVER_START = 2`, `SAVER_CAP = 5`, `SAVER_EARN_HOURS = 12`, `COMEBACK_IDLE_DAYS = 7`, `COMEBACK_GIFT = 2`, `RECOVERY_DAYS = 3`, `RECOVERY_HOURS = 10`, `RECLAIM_WINDOW_HOURS = 12`, `RECLAIM_PER_DAY = 2`. Keep `STREAK_HOURS_REQUIRED = 8` and `BEGINNER_HOURS_REQUIRED = 4`. Delete `FORGIVE_WINDOW_DAYS`.
- `RECLAIM_TAG = "💧reclaimed"` must never change. Backdated = carries it.
- Backdated hours never count toward the ring, the goal, saver earning or recovery. They still show in every list and chart.
- History is replayed, never stored as a running counter.
- Design system per `CLAUDE.md`: `BrandBand`, `BeeCardDefaults`, `OnBandFill`, honey = act here, `Sym` icons (no UI emoji), Plus Jakarta Sans. Run `PaletteContrastTest` after any colour addition.
- Hourly-reminder settings (silent hours, pause, reminders switch) affect **hourly reminders only**. Streak alerts and the weekly report always post, silently during silent hours.
- Copy is fixed by the spec's tables. Use it verbatim, and use plain words (no "bee back", no "revisit").
- End each commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **DST and day boundaries.** Days of 23 or 25 hours must still be one day each. Window and idle counts are calendar days, and `dayEndMs` comes from the next day's start (keep the existing idiom). Test: a window spanning the October DST change.
2. **Silent hours across midnight** (start > end, e.g. 22:00→07:00), and start == end (treat as off). Test both in `NotificationPrefsTest`.
3. **A backdated entry and an in-the-moment entry for the same hour** (possible via import or edits): the hour counts once if *any* in-the-moment entry exists for it. Test in `StreakRulesTest`.
4. **Import or restore re-derives everything.** It must not fire celebrations, bell items or phone alerts for old days. Reset the bell/alert baseline in the import path (Task 6), and test `alertsToPost` with events before the baseline.
5. **Clock or time-zone change while a pause or window is open.** Pause end is an absolute epoch ms. Window days are derived from replay day keys, never from stored day counts. Test that `hourlyDelivery` with `nowMs` past `pauseUntilMs` returns normal delivery.

---

### Task 1: Pure streak rules engine

**Files:**
- Create: `app/src/main/java/com/example/beeing/StreakRules.kt`
- Modify: `app/src/main/java/com/example/beeing/StreakEngine.kt`: remove the constants, `ReplayDay`, `replayDays`, `DayOutcome`, `StreakState`, `computeStreakState`, `computeDayOutcomes` and the log builder (they move). Keep the composables, persistence helpers (`loadReclaimSpends`, mode events) and `visibleTags`.
- Test: `app/src/test/java/com/example/beeing/StreakRulesTest.kt`

**Interfaces:**
- Produces (all in `StreakRules.kt`, no `Context`, no Compose):
  - `enum class DayOutcome { QUALIFIED, SAVED, FADED, BROKEN, MISSED, RECOVERED, BEGINNER_COMPLETE }`
  - `enum class StreakEventType { COMPLETED, BEGINNER, SAVER_EARNED, SAVER_USED, SAVER_FADED, LOW_SAVERS, COMEBACK_GIFT, STREAK_BROKE, RECOVERY_DAYS_LEFT, RECOVERED, PAST_HOUR_ADDED }`
  - `data class StreakEvent(val id: String, val timestamp: Long, val type: StreakEventType, val streak: Int, val savers: Int, val daysLeft: Int = 0, val dayStartMs: Long)`. `id` is stable: `"${type.name}:${dayKey}"`, plus `":${daysLeft}"` for `RECOVERY_DAYS_LEFT` and `":${spendMs}"` for `PAST_HOUR_ADDED`.
  - `data class StreakState(currentStreak: Int, displayStreak: Int, todayHours: Int, todayQualified: Boolean, extraToday: Int, beginnerMode: Boolean, hoursRequired: Int, beginnerDaysCompleted: Int, savers: Int, recoveryOpen: Boolean, recoveryDaysLeft: Int, todayEarnedSaver: Boolean)` with `val streakPaused` as today. `todayHours` = **counted** (in-the-moment) hours. `displayStreak` = the pre-break count while `recoveryOpen`, else `currentStreak`.
  - `fun computeStreakState(ratings: List<RatingEntry>, modeEvents: List<ModeEvent>, nowMs: Long = System.currentTimeMillis()): StreakState`
  - `fun computeDayOutcomes(ratings, modeEvents, nowMs = …): Map<Long, DayOutcome>` (key = `year*1000+dayOfYear`, as today)
  - `fun computeStreakEvents(ratings, reclaimSpends: List<Long>, modeEvents, nowMs = …): List<StreakEvent>` (newest first)
  - `fun Calendar.dayKey(): Long` (made `internal`)

- [ ] **Step 1: Write the failing tests.** Add a helper `day(offset: Int, organic: Int, backdated: Int = 0): List<RatingEntry>` that builds entries at distinct hours 0.. on the day `offset` days before a fixed `NOW` (e.g. 2026-10-28 20:00 local). Backdated entries carry `RECLAIM_TAG`. Tests (each asserts on `computeStreakState` or outcomes or events):
  - `starts_with_two_savers`: no ratings → `savers == 2`, `currentStreak == 0`.
  - `twelve_organic_hours_earns_a_saver`: one finished day with 12 organic → `savers == 3`, event `SAVER_EARNED`.
  - `backdated_hours_do_not_count`: a day with 8 organic + 4 backdated → no saver earned. A day with 6 organic + 2 backdated → not QUALIFIED. Today with 3 organic + 2 backdated → `todayHours == 3`.
  - `same_hour_backdated_and_organic_counts_once_as_organic`.
  - `savers_cap_at_five`: four 12-hour days from 2 → `savers == 5`, the 4th emits no `SAVER_EARNED`.
  - `miss_with_streak_uses_saver`: 3 qualifying days then 1 miss → outcome `SAVED`, `currentStreak == 3`, `savers == 1`, events `SAVER_USED` and `LOW_SAVERS` (savers ≤ 2).
  - `miss_at_streak_zero_fades_saver`: 1 miss with no streak → `FADED`, `savers == 1`.
  - `miss_with_no_savers_breaks_and_opens_window`: drain savers, then a miss → `BROKEN`, `recoveryOpen`, `displayStreak == oldCount`, `currentStreak == 0`, event `STREAK_BROKE`.
  - `ten_hours_on_window_day_1_2_3_recovers`: three parameterized cases. On recovery: `RECOVERED`, `currentStreak == old + qualifyingWindowDays` (including the recovery day), `recoveryOpen == false`.
  - `recovery_counts_qualifying_days_and_ignores_short_ones`: window 8,3,10 → `currentStreak == old + 2`.
  - `day_four_does_not_recover`: window 3,3,3 then a 10-hour day 4 → that day is QUALIFIED, `currentStreak == 1`.
  - `closed_window_keeps_trailing_run`: window 8,3,8 → after close `currentStreak == 1`. Window 8,8,3 → `0`.
  - `no_burn_inside_open_window`: break with 0 savers. Window day 1 is short and emits no `SAVER_USED`/`SAVER_FADED`. If day 1 is also idle day 7, the `COMEBACK_GIFT` savers survive the rest of the window (`savers == 2` while it's open).
  - `today_ten_hours_recovers_immediately`: break yesterday, today 10 organic in progress → `RECOVERED` today.
  - `recovery_days_left_events_at_two_and_one`: window days 2 and 3 each emit `RECOVERY_DAYS_LEFT` with `daysLeft` 2 then 1, one each, stable ids.
  - `beginner_days_use_window_days_and_cannot_recover`: break, then a beginner day with 10 hours → window still counts it, no recovery.
  - `beginner_days_neither_earn_nor_spend`: a beginner day with 12 hours → no saver. A beginner day under 4 → savers unchanged.
  - `comeback_gift_after_seven_idle_days`: from 0 savers, 7 finished days with no complete day → +2 after day 7 (`COMEBACK_GIFT`), day 8 miss → `FADED` (1 left), day 9 miss → `FADED` (0). 14 idle days from 2 → gifts at 7 and 14, capped at 5.
  - `beginner_completion_resets_idle_count`.
  - `today_in_progress_has_no_verdict`.
  - `window_across_dst_change` (Review Focus 1): break on 2026-10-24, window over the DST weekend for the default zone. Use `TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))` in the test and restore it after.
- [ ] **Step 2: Run them to see them fail.** Run: `JAVA_HOME=… ./gradlew testDebugUnitTest --tests com.example.beeing.StreakRulesTest`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement `StreakRules.kt`.** Move the constants (with the new values) and keep `replayDays` as the single walk, now parameterized by `nowMs`. Walk state: `streak`, `savers = SAVER_START`, `idleRun`, and a nullable window `(breakIndex, prevStreak, qualifiedInWindow, trailingRun)`. Per finished day, in this order: beginner → transparent (counts toward the window's calendar days and resets `idleRun` if ≥4). Open window and inside it → ≥10 recover / ≥8 qualify-in-window / short (no burn). Window just expired → `streak = trailingRun`, then evaluate the day normally. Normal → ≥8 qualify (+saver if ≥12 and < cap) / miss → spend (SAVED or FADED) / break / MISSED. Then update `idleRun`, and gift at multiples of `COMEBACK_IDLE_DAYS`. Today: may qualify or recover (≥8 / ≥10), never misses. Hour counting: an hour counts if any non-backdated entry covers it. Event timestamps: completion = the Nth counted hour's timestamp (as today), misses/gifts = end of day, `RECOVERY_DAYS_LEFT` = that day's start.
- [ ] **Step 4: Repoint callers.** Callers are `HourlyPulseApp.kt:128`, `NowTab.kt:221`, `StreaksTab.kt:74,77`, `StatsComponents.kt:31`, `PastTab.kt:1201`, and `StreakLogContent` (`StreakEngine.kt:742`). Drop the `reclaimSpends` argument where it was only for replay. Remove every `DayOutcome.REST`/`Sym.Rest` calendar branch for now; Task 3 restyles them. Map log rows to the new event types with interim titles. Task 3 finalizes copy.
- [ ] **Step 5: Run the tests to see them pass**, plus `assembleDebug`. Expected: all `StreakRulesTest` PASS, build OK.
- [ ] **Step 6: Commit**: `git commit -m "Streak engine: savers, fading, comeback gift, recovery window; backdated hours no longer count"`

### Task 2: "Add a past hour" (replaces "send a bee back")

**Files:**
- Modify: `StreaksTab.kt` (card at 136–198, pick dialog 303–345, `ReclaimHourDialog` ~700–776, rules copy 423), `NowTab.kt:471`, `HourlyPulseApp.kt:504,513`, `ui/icons/BeeIcons.kt` (add `Sym.AddPast`, a history/schedule-add glyph, plus its drawable), `UIComponents.kt:72-75` and `PastTab.kt:861` (badge label).
- Create: `app/src/main/res/drawable/ic_sym_history.xml` (Material Symbols Rounded `history`, same `translateY="960"` wrapping as the others).

**Interfaces:**
- Consumes: `RECLAIM_WINDOW_HOURS` (12) and `RECLAIM_PER_DAY` from Task 1.
- Produces: `ReclaimHourDialog(startMillis, availableTags: List<String>, onDismiss, onReclaim: (score: Int, tags: List<String>, note: String) -> Unit)`.

- [ ] **Step 1: Card + pick dialog copy.** Card: "Missed an hour?" / "Add a past hour · {n} available". Spent state: "You've added 2 past hours today. More tomorrow." Pick dialog title `SymTitle(Sym.AddPast, "Which hour?")` and helper "You'll add a score and tags. {k} left today." `Sym.AddPast` replaces `Sym.Reclaim` here.
- [ ] **Step 2: Entry dialog takes tags.** Score row as now, then a toggleable `InputChip` `FlowRow` over `loadTags(context)` using `TagChipShape`/`tagChipColors()`/`TagChipContent` (the same look as `EditEntrySheet`), then an **optional** note. The confirm button "Add hour" is enabled when score != null and ≥1 tag. The entry saves `tags = selected + RECLAIM_TAG`.
- [ ] **Step 3: Badge label.** Wherever `isReclaimed` draws `Sym.FromMemory`, add the text "Added later" (content description too).
- [ ] **Step 4: Copy sweep.** `grep -rniE "bee back|revisit|bees? (left|sent|are all)" app/src/main` returns nothing except identifiers and comments you update. `NowTab.kt:471` becomes "{n} hour(s) slipped past · add a past hour from the Streak tab ›".
- [ ] **Step 5: Verify.** `assembleDebug` OK. On device: add a past hour with 2 tags and no note. It appears in the journal with "Added later" and does not move the ring.
- [ ] **Step 6: Commit**: `"Add a past hour: rename, 12-hour reach, tags required, note optional"`

### Task 3: Savers and recovery on screen

**Files:**
- Modify: `NowTab.kt` `StatusStrip` (849–914), `StreakEngine.kt` `StreakMeter` + `StreakLogContent`, `StreaksTab.kt` calendar marks (247, 564, 605–621) and the rules dialog, `HourlyPulseApp.kt` "How Beeing works" text, `ui/theme/Color.kt` (`StreakAtRiskRed`), `ui/icons/BeeIcons.kt` (`Sym.Saver`, shield-with-flame drawable `ic_sym_saver.xml`; delete `Sym.Rest` if unused), `PaletteContrastTest.kt`.

**Interfaces:**
- Consumes: `StreakState.displayStreak/recoveryOpen/recoveryDaysLeft/savers`, `StreakEventType` from Task 1.
- Produces: `@Composable fun SaverSlots(savers: Int, modifier: Modifier = Modifier, highlightIndex: Int? = null)` (5 shields, filled to `savers`, empty ones dashed outline), used by Tasks 4 and 5.

- [ ] **Step 1: Contrast test first.** Add `StreakAtRiskRed` (start at `0xFFFF6B6B`) and a test `at_risk_red_reads_on_band` that asserts ≥ 3.0 against `BandTopLight`, `BandBottomLight`, `BandTopDark` and `BandBottomDark` (large/bold UI text). Run, adjust the value until it passes, commit with the next steps.
- [ ] **Step 2: At-risk strip.** While `recoveryOpen`, `StatusStrip` shows `displayStreak`, with the flame and number in `StreakAtRiskRed` and a `Icons.Default.Error`-style "!" (14dp, same red) after the number. Semantics: "{N}-day streak at risk · {D} day(s) left to win it back". Same treatment in `StreakMeter`'s count.
- [ ] **Step 3: Savers on the Streak tab.** Under the `StreakMeter` title: `SaverSlots(state.savers)` + "{S} of 5 savers". Tapping it opens the saver explainer dialog (the same content as the settings row in Task 6: earn 12 / auto-use / fade at no streak / comeback gift / recovery at 10 within 3 days).
- [ ] **Step 4: Calendar + day stats.** `SAVED` → small `Sym.Saver` (honey) where REST used to sit. `FADED` → no mark (it isn't a streak event on the calendar). `BROKEN`/`MISSED` → the existing hollow ring. `RECOVERED` → normal complete tint. Legend: replace "rest day" with "saver used". Day-stats line for SAVED: "Saver used — the streak held".
- [ ] **Step 5: Log titles/details** for each `StreakEventType`, using the spec's bell-table copy where it applies. "Day complete" and "Beginner day complete" stay as today. `PAST_HOUR_ADDED` → "Past hour added". Icons: `Sym.Saver` for saver types, `Sym.Fire` for recovered, `Sym.Reset` for broke.
- [ ] **Step 6: Explainers.** Rewrite the Streak-tab rules dialog, "How Beeing works" and the beginner paragraph for savers, fading, the comeback gift, recovery and "Add a past hour". Remove all rest-day copy.
- [ ] **Step 7: Verify.** `testDebugUnitTest` PASS, `assembleDebug` OK. On a device, check the strip at 360dp width with a 3-digit streak and the "!".
- [ ] **Step 8: Commit**: `"Show savers, at-risk streak and new log events; rest-day copy removed"`

### Task 4: Saver-earned and recovered celebrations

**Files:**
- Create: `app/src/main/java/com/example/beeing/SaverCelebration.kt`
- Modify: `HourlyPulseApp.kt` (celebration host near 124/653), `NowTab.kt` (detection next to the ring effect at 224–240).

**Interfaces:**
- Consumes: `SaverSlots`, `StreakState.todayEarnedSaver/savers/recoveryOpen`.
- Produces: `sealed interface StreakMoment { data class SaverEarned(val savers: Int); data class Recovered(val streak: Int) }`, `@Composable fun StreakMomentCelebration(moment: StreakMoment, onDone: () -> Unit)`, and `NowTab` param `onStreakMoment: (StreakMoment) -> Unit = {}`.

- [ ] **Step 1: Build the screen** to the approved mockup (`.superpowers/brainstorm/884-1793206120/content/bell-and-saver-v2.html`, third phone). Radial aubergine background, honey glow, the shield drops in (overshoot) then floats, sparks, the label "12-HOUR DAY", the title "Streak saver earned", the sub line "It will step in automatically the next time you miss a day.", `SaverSlots(savers, highlightIndex = savers-1)` with the new slot popping, and a "Keep going" `accentFill` button. One haptic on the drop. With `ANIMATOR_DURATION_SCALE == 0`, show the end state. `Recovered` uses the same frame with the flame, label "STREAK RECOVERED" and title "Your {N}-day streak is back".
- [ ] **Step 2: Trigger.** In `NowTab`, keep a `previous` state like the ring guard. Fire `SaverEarned` only when `todayEarnedSaver` flips false→true. Fire `Recovered` only when `recoveryOpen` flips true→false **and** today's outcome is `RECOVERED`. Never fire on the first composition, after an import (`refreshTrigger` bump from import → re-baseline), or on mode/goal changes. If `ringCelebration` is showing, queue the moment until `onDone`.
- [ ] **Step 3: Verify on device.** With test data seeded to 11 organic hours today, saving the 12th shows the screen once. Re-opening the app does not show it again. Saving a past hour never does.
- [ ] **Step 4: Commit**: `"Full-screen saver-earned and streak-recovered moments"`

### Task 5: Warnings bell and the shared top row

**Files:**
- Create: `app/src/main/java/com/example/beeing/WarningsBell.kt` (store + sheet), `app/src/main/java/com/example/beeing/ui/BandTopRow.kt`
- Modify: `NowTab.kt` (replace the hamburger row 371–386, move `StatusStrip`/`MiniHourRing` to `BandTopRow.kt` as `internal`), `StreaksTab.kt:118`, `PastTab.kt:633,1073` (journal band; Trends keeps its back row), `HourlyPulseApp.kt` (pass state and callbacks; import path calls `resetWarningsBaseline`).
- Test: `app/src/test/java/com/example/beeing/WarningsTest.kt`

**Interfaces:**
- Consumes: `computeStreakEvents`, `StreakEvent`.
- Produces:
  - `data class Warning(val id: String, val timestamp: Long, val type: StreakEventType, val title: String, val detail: String)`
  - `fun warningsFrom(events: List<StreakEvent>, baselineMs: Long, dayLabel: (Long) -> String): List<Warning>`. Keeps only `SAVER_USED, SAVER_FADED, LOW_SAVERS, COMEBACK_GIFT, STREAK_BROKE, RECOVERY_DAYS_LEFT, RECOVERED`, newest 30, with spec copy filled in.
  - `fun unreadCount(warnings: List<Warning>, readIds: Set<String>, baselineMs: Long): Int`
  - prefs: `loadReadWarningIds(ctx)`, `markWarningsRead(ctx, ids)`, `warningsBaseline(ctx): Long` (set to now on first call if absent), `resetWarningsBaseline(ctx)`, `streakWarningsEnabled(ctx)`/`setStreakWarningsEnabled`.
  - `@Composable fun BandTopRow(state: StreakState, unread: Int, onStreakClick: () -> Unit, onBellClick: () -> Unit, onMenuClick: () -> Unit, titleModifier: Modifier = Modifier)`
  - `@Composable fun WarningsSheet(warnings: List<Warning>, readIds: Set<String>, onMarkAllRead: () -> Unit, onDismiss: () -> Unit)`

- [ ] **Step 1: Failing tests.** `copy_matches_spec` (each type's title/detail, with singular and plural "saver(s)", "day(s)", and "That was your last saver." at 0). `events_before_baseline_are_read`. `days_left_items_are_one_per_day` (ids are distinct, no duplicates across calls). `unread_count_ignores_read_ids`. `non_warning_types_are_excluded`.
- [ ] **Step 2: Run and see them fail**, then **implement** `warningsFrom`/`unreadCount` and the prefs keys (`"warn_read_ids"` as a `;`-joined string set, `"warn_baseline_ms"`, `"streak_warnings"`, default true). Run and see them pass.
- [ ] **Step 3: `BandTopRow`.** "Beeing" (`headlineSmall`, left, keeps the Now tab's `scaledLayout` growth via `titleModifier`) · `StatusStrip` · a 36dp `OnBandFill` circle bell with a honey badge (`accentFill`/`onAccentFill`, 2dp ring in the band colour, hidden at 0 or when warnings are disabled) · a 28dp-wide plain ⋮ (`Icons.Default.MoreVert`, content description "Settings"). It must fit 360dp with a 3-digit streak and the at-risk "!". Check with a `@Preview(widthDp = 360)`.
- [ ] **Step 4: `WarningsSheet`.** A `ModalBottomSheet` (`skipPartiallyExpanded`): title "Heads up", a "Mark all read" `TextButton`, then rows of a 34dp tinted icon tile (`Sym.Saver` red for used/faded/broke, honey for low/gift, `Sym.Fire` for recovered), title, detail, time and a honey unread dot. An empty state: "Nothing to worry about." Opening the sheet marks everything shown as read.
- [ ] **Step 5: Mount on all three tabs** in place of each band's title row. The bell opens the sheet, which is hoisted to `HourlyPulseApp` so it's shared. ⋮ calls `onOpenSettings` (wired in Task 6; for now it opens the existing dialog).
- [ ] **Step 6: Verify.** Tests PASS, `assembleDebug` OK, and on device the badge clears after opening the sheet.
- [ ] **Step 7: Commit**: `"Warnings bell with unread badge, shared band top row on every tab"`

### Task 6: Full settings page

**Files:**
- Create: `app/src/main/java/com/example/beeing/SettingsScreen.kt`
- Modify: `HourlyPulseApp.kt`: delete the `showMenu` `AlertDialog` (299–474). Move import/export/backup/beginner/how-it-works wiring into callbacks for `SettingsScreen`, and add `showSettings` with a `BackHandler`. The import success path calls `resetWarningsBaseline` and `resetAlertBaseline` (Task 8).

**Interfaces:**
- Consumes: Task 5 prefs, the existing launchers, `ManageTagsDialog` (make it `internal` in `NowTab.kt`), Task 7's `NotificationPrefs` load/save and its two sheets (Task 7 adds those rows. This task leaves the Notifications section with only the "Hourly reminders" switch).
- Produces: `@Composable fun SettingsScreen(state: StreakState, callbacks: SettingsCallbacks, onBack: () -> Unit)` and `data class SettingsCallbacks(...)`, one lambda per row action.

- [ ] **Step 1: Layout** to the approved mockup (`settings.html`, right phone). A `BrandBand` with a back arrow and "Settings", over a scrolling page of grouped `BeeCardDefaults` cards with section overlines. A private `SettingsRow(icon: Sym or ImageVector, tint, title, subtitle?, trailing: @Composable)` with a 34dp tile, 14sp title and 11.5sp subtitle. Sections/rows exactly as the spec's list. The footer reads `BuildConfig.VERSION_NAME`.
- [ ] **Step 2: Wire** every row to the existing behaviour (beginner pause-confirm stays). "Streak savers" opens the saver explainer from Task 3. "Streak warnings" is the Task 5 switch. "Hourly reminders" stores `"hourly_reminders"` (default true), checked in Task 7.
- [ ] **Step 3: Navigation.** `showSettings` draws `SettingsScreen` full-screen above the Scaffold (it covers the nav bar). System back closes it.
- [ ] **Step 4: Verify on device.** Every old dialog function still works from settings (export, import + confirm, auto-backup toggle + folder, beginner toggle + confirm, how it works, manage tags). The old dialog is gone.
- [ ] **Step 5: Commit**: `"Full settings page replaces the Data options dialog"`

### Task 7: Silent hours and pause for hourly reminders

**Files:**
- Create: `app/src/main/java/com/example/beeing/NotificationPrefs.kt`, `app/src/main/java/com/example/beeing/QuietSheets.kt` (silent-hours sheet, pause sheet)
- Modify: `Utils.kt` (`createNotificationChannel`: add `hourly_bee_silent`, `streak_alerts`, `streak_alerts_silent` and `weekly_report_silent`, the silent ones with `setSound(null, null)` and `enableVibration(false)`). `NotificationReceiver` hourly branch: ask `hourlyDelivery` and pick the channel or skip, always rescheduling. `StatsComponents.kt` weekly report: pick the silent channel inside silent hours. `SettingsScreen.kt` (two rows + the running-pause row state).
- Test: `app/src/test/java/com/example/beeing/NotificationPrefsTest.kt`

**Interfaces:**
- Produces:
  - `enum class PauseMode { QUIET, OFF }`, `enum class Delivery { SOUND, SILENT, NONE }`
  - `data class NotificationPrefs(val remindersOn: Boolean, val silentOn: Boolean, val silentStartMin: Int, val silentEndMin: Int, val pauseUntilMs: Long, val pauseMode: PauseMode)`, `fun loadNotificationPrefs(ctx)`, `fun saveNotificationPrefs(ctx, p)`
  - `fun inSilentHours(p: NotificationPrefs, nowMs: Long): Boolean`
  - `fun hourlyDelivery(p: NotificationPrefs, nowMs: Long): Delivery` (reminders off or pause OFF → NONE; pause QUIET or silent hours → SILENT; else SOUND)
  - `fun otherDelivery(p: NotificationPrefs, nowMs: Long): Delivery` (SILENT in silent hours, else SOUND; never NONE)
  - `fun maxPauseUntil(nowMs: Long): Long` = now + 48h, floored to the hour
  - `data class PauseRisk(val days: Int, val savers: Int, val streak: Int, val breakDayStartMs: Long?, val fadeCount: Int)` and `fun pauseRisk(state: StreakState, todayQualified: Boolean, beginnerMode: Boolean, nowMs: Long, untilMs: Long): PauseRisk?` (null = no warning)

- [ ] **Step 1: Failing tests.** `silent_hours_cross_midnight` (22:00–07:00: 23:30 in, 06:59 in, 07:00 out, 12:00 out). `equal_start_end_means_off`. `pause_off_suppresses_hourly_only` (`hourlyDelivery == NONE`, `otherDelivery == SOUND`). `pause_quiet_is_silent`. `expired_pause_is_ignored` (Review Focus 5). `reminders_off_is_none`. `max_pause_is_48h`. `pause_risk_counts_overlapped_days_minus_qualified_today` (now Wed 20:00, until Fri 21:00 → 3 days, or 2 if today qualified). `pause_risk_null_when_savers_cover`. `pause_risk_fade_version_at_streak_zero`. `pause_risk_ignores_beginner`.
- [ ] **Step 2: Run, see them fail, implement, see them pass.** Prefs keys: `"hourly_reminders"`, `"silent_on"`, `"silent_start_min"` (default 1320), `"silent_end_min"` (420), `"pause_until_ms"`, `"pause_mode"`.
- [ ] **Step 3: Receiver + channels.** The hourly post uses `hourly_bee` / `hourly_bee_silent` / skip from `hourlyDelivery`. The weekly report and Task 8 alerts use `otherDelivery`. Rescheduling is unconditional.
- [ ] **Step 4: Sheets**, per the approved `silence.html`:
  - **Silent hours:** an on/off switch plus two time pickers (M3 `TimePicker` in a dialog). The row reads "Every day, {start} to {end} · no sound" or "Off".
  - **Pause:** "How" as two side-by-side choice cards with the exact copy: "Quiet": "Still arrive, no sound. You can keep rating."; "Off": "No notifications. A real break from Beeing." "Until" has chips "In 1 hour" · "In 3 hours" · "Tomorrow 7 AM" · "Pick a time". The last opens a day + hour wheel capped by `maxPauseUntil`. Then the read-back "Notifications are off until **{EEE h a}** · {duration}" (or "…arrive silently until…" for Quiet), the red risk card from `pauseRisk` (Off only, spec copy), and the confirm "Pause until {EEE h a}".
  - **Running pause row:** honey, "Paused until {EEE h a} · Quiet/Off", with an "End now" button that sets `pauseUntilMs = 0`.
- [ ] **Step 5: Verify.** Tests PASS. On device: set silent hours around the current time and wait for the hour (or trigger the receiver via `adb shell am broadcast -n com.example.beeing/.NotificationReceiver`). The notification arrives without sound. Pause Off: none arrives. End now: the next one rings.
- [ ] **Step 6: Commit**: `"Silent hours and pause for hourly reminders; silent channels"`

### Task 8: Streak phone alerts

**Files:**
- Create: `app/src/main/java/com/example/beeing/StreakAlerts.kt` (receiver, scheduling, posting, pure selection)
- Modify: `AndroidManifest.xml` (register `StreakAlertReceiver`), `NotificationReceiver` BOOT branch + `MainActivity` (schedule it), `HourlyPulseApp.kt` (tapping the alert intent extra `OPEN_TAB = 0` opens the Streak tab).
- Test: `app/src/test/java/com/example/beeing/StreakAlertsTest.kt`

**Interfaces:**
- Consumes: `computeStreakEvents`, `otherDelivery`, Task 5's baseline idea (own key).
- Produces: `fun alertsToPost(events: List<StreakEvent>, notifiedIds: Set<String>, baselineMs: Long): List<StreakEvent>` (types `COMEBACK_GIFT`, `SAVER_USED`, `SAVER_FADED` only; after baseline; not yet notified; oldest first), `fun alertCopy(e: StreakEvent, giftedFade: Boolean): Pair<String, String>`, `fun scheduleStreakAlerts(ctx)` (daily 00:01 exact alarm, same pattern as `scheduleExactHourlyAlarm`), `resetAlertBaseline(ctx)`.

- [ ] **Step 1: Failing tests.** `only_gift_used_faded_are_alerted`. `nothing_before_baseline`. `each_event_once` (ids in `notifiedIds` are skipped). `copy_matches_spec`: gift "2 streak savers on the house" / "Rate a few hours today to start a new streak.". Used "A streak saver kept your {N}-day streak alive" / "{S} left.". Gifted fade, day 8: "One of your gifted savers faded. Rate today to keep the last one."; at 0: "Your last saver faded.". The general fade uses the bell copy. `gifted_fade_detection`: a fade within 2 days after a `COMEBACK_GIFT` uses the gifted wording.
- [ ] **Step 2: Run, see them fail, implement, see them pass.** Prefs: `"alert_notified_ids"`, `"alert_baseline_ms"` (set on first run).
- [ ] **Step 3: Receiver.** On the alarm: load ratings, mode events and spends; `computeStreakEvents(nowMs)`; `alertsToPost`; post each on `streak_alerts` or `streak_alerts_silent` per `otherDelivery` (pause and reminders-off do **not** apply), with a tap → `MainActivity` + `OPEN_TAB = 0`. Record the ids. Reschedule. Hourly-reminder settings are never consulted.
- [ ] **Step 4: Verify on device.** Seed a miss for yesterday, then `adb shell am broadcast -n com.example.beeing/.StreakAlertReceiver`. One "saver kept your streak alive" alert appears. A second broadcast posts nothing.
- [ ] **Step 5: Commit**: `"Streak phone alerts for gifts and saver spends, once per event"`

### Task 9: Docs and final pass

**Files:** Modify `CLAUDE.md`.

- [ ] **Step 1:** Update the Product rules (savers, fading, comeback gift, recovery, backdated hours don't count, 12-hour reach), Lexicon (saver, "Add a past hour", "Added later", streak at risk; remove rest day and "send a bee back"), File map (`StreakRules.kt`, `WarningsBell.kt`, `ui/BandTopRow.kt`, `SettingsScreen.kt`, `NotificationPrefs.kt`, `QuietSheets.kt`, `StreakAlerts.kt`, `SaverCelebration.kt`), Integration seams (channels, alert alarm), and a 2026-10-28 history entry.
- [ ] **Step 2:** Full `testDebugUnitTest` + `installDebug`, then walk the spec section by section on the device.
- [ ] **Step 3: Commit**: `"CLAUDE.md: savers, recovery, bell, settings, quiet reminders"`
