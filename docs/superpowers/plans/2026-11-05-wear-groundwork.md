# Wear Groundwork (phone + shared engine) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Everything the watch app needs from the phone side, shippable on its own: a pure `:shared` engine module that can resume a replay from a checkpoint, ratings that know when and where they were saved, the merge rules, no double celebrations, streak alerts at 02:01, the Beeing tone, and no "Hourly reminders" switch.

**Architecture:** A new plain Kotlin/JVM module `:shared` (package `com.example.beeing`, unchanged, so imports don't move) takes the pure engine files out of `:app`. The replay walk gains an explicit carry so it can stop at a day and resume later with identical results. Merge rules are a pure function in `:shared`; `:app` only persists their result.

**Tech Stack:** Kotlin 2.2.10, AGP 9.1.1, JUnit 4.13.2, `org.jetbrains.kotlin.jvm` for `:shared`.

**Spec:** `docs/superpowers/specs/2026-11-05-wear-companion-design.md` (read it first). Plan 2 (`2026-11-05-wear-app.md`) builds the watch app on top of this one.

## Global Constraints

- Package stays `com.example.beeing` in every module; `applicationId` stays `com.example.beeing`.
- `RECLAIM_TAG` stays `"💧reclaimed"`; tag strings are never rewritten.
- Stored ratings stay in SharedPreferences `"b"`, key `"ratings"`, `|` rows, `,` fields, `;` tags. New fields are **appended**; a row with 6 fields must still load.
- CSV export/import format is unchanged. Imported rows get `modifiedAt = id`, `origin = PHONE`.
- No streak rule changes: every existing `StreakRulesTest` / `StreakMomentTest` / `SuperDayTest` assertion passes untouched.
- Verify every task with `./gradlew test` and, for tasks touching `:app`, `./gradlew installDebug` (Windows: `JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"`).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A row saved before this change** (6 fields) must load with `modifiedAt = id` and `origin = PHONE`, and re-save with 8 fields. → test in Task 3.
2. **Checkpoint on a day inside an open recovery window, or mid comeback-gift idle run** must resume to the same state as a full replay. → covered by Task 4's every-split test over the histories listed there.
3. **DST day as the checkpoint day** (23 h or 25 h): resume must start at the next local midnight, not `+24h`. → test in Task 4.
4. **A watch rating for an hour with a phone "Added later" entry** must win regardless of `modifiedAt` and refund one reclaim spend. → test in Task 5.
5. **A Delete + Undo of a watch-origin hour on the phone** must not celebrate (existing ratable-hour guard) and must not be blocked by its own tombstone. → tests in Tasks 5 and 6.

---

### Task 1: `:shared` module with the pure engine

**Files:**
- Modify: `settings.gradle.kts` (add `include(":shared")`), `gradle/libs.versions.toml` (plugin `kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }`), root `build.gradle.kts` (`alias(libs.plugins.kotlin.jvm) apply false`), `app/build.gradle.kts` (`implementation(project(":shared"))`)
- Create: `shared/build.gradle.kts` (kotlin-jvm, `testImplementation(libs.junit)`, JVM target matching `:app`'s `kotlinOptions.jvmTarget`)
- Move (git mv): `app/src/main/java/com/example/beeing/StreakRules.kt` → `shared/src/main/kotlin/com/example/beeing/StreakRules.kt`; `SuperDay.kt` likewise
- Create: `shared/src/main/kotlin/com/example/beeing/Model.kt` — `RatingEntry` moves here from `Utils.kt:18-25`
- Create: `shared/src/main/kotlin/com/example/beeing/NotificationRules.kt` — `PauseMode`, `Delivery`, `NotificationPrefs`, `inSilentHours`, `pauseActive`, `hourlyDelivery`, `otherDelivery`, `maxPauseUntil`, `PauseRisk`, `pauseRisk` move here from `NotificationPrefs.kt`; `NotificationPrefs.kt` keeps only the prefs keys and `loadNotificationPrefs` / `saveNotificationPrefs`
- Move tests: `StreakRulesTest.kt`, `StreakMomentTest.kt`, `SuperDayTest.kt`, `NotificationPrefsTest.kt` → `shared/src/test/kotlin/com/example/beeing/` (any test there that needs `Context` stays in `:app`)

**Interfaces:**
- Produces: module `:shared` exposing everything above unchanged. Visibility changes only where `:app` calls across the module line: `Calendar.dayKey()` becomes public (used by `PastTab.kt:709`, `StreakEngine.kt:157-159`). `replay`, `ReplayDay`, `Replay`, `beginnerAt`, `singleNewHourDay` stay `internal` (only `:shared` and its tests use them).

- [ ] **Step 1:** Create the module, move the files and tests as listed; no code changes beyond `dayKey` visibility and the `NotificationPrefs.kt` split.
- [ ] **Step 2:** Run `./gradlew :shared:test` — Expected: PASS, same test count as these four classes had in `:app`.
- [ ] **Step 3:** Run `./gradlew test installDebug` — Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 4:** Commit `"Move the pure streak engine into a :shared module"`.

### Task 2: Remove the "Hourly reminders" switch

**Files:**
- Modify: `shared/.../NotificationRules.kt` (drop `remindersOn` and the `!p.remindersOn` branch), `app/.../NotificationPrefs.kt` (drop `REMINDERS_KEY` read/write), `app/.../SettingsScreen.kt:104-109` (drop the row and its following `Divider()`), `app/.../Utils.kt:153` (comment: "obey silent hours and pause")
- Test: `shared/src/test/.../NotificationPrefsTest.kt`

**Interfaces:**
- Produces: `NotificationPrefs(silentOn, silentStartMin, silentEndMin, pauseUntilMs, pauseMode)`.

- [ ] **Step 1:** Delete tests that construct `remindersOn = false`; update constructors in remaining tests.
- [ ] **Step 2:** Make the code change; run `./gradlew test installDebug` — Expected: PASS. The old `"hourly_reminders"` pref key is simply left unread (no migration — no installed base).
- [ ] **Step 3:** Commit `"Remove the Hourly reminders switch; pause and silent hours remain"`.

### Task 3: Ratings know when and where they were saved; tombstones

**Files:**
- Modify: `shared/.../Model.kt`, `app/.../Utils.kt:372-418` (`saveRating`, `loadRatings`, `deleteRating`), `app/.../Utils.kt` CSV import (set defaults), every `RatingEntry(` construction site that saves a new or edited rating (NowTab save, `EditEntrySheet` persist, reclaim save, notification-draft submit) — they set `modifiedAt = System.currentTimeMillis()`
- Create: `app/src/main/java/com/example/beeing/Tombstones.kt`
- Create: `shared/src/main/kotlin/com/example/beeing/RatingRow.kt` (pure encode/decode so it is JVM-testable)
- Test: `shared/src/test/kotlin/com/example/beeing/RatingRowTest.kt`

**Interfaces:**
- Produces:
  - `enum class Origin { PHONE, WATCH }`
  - `RatingEntry(id, score, timestamp, hourLabel, note = "", tags = emptyList(), modifiedAt: Long = id, origin: Origin = Origin.PHONE)`
  - `fun encodeRatingRow(e: RatingEntry): String` → `"id,score,timestamp,hourLabel,note,tags;joined,modifiedAt,P|W"`
  - `fun decodeRatingRow(line: String): RatingEntry?` (null when < 4 fields, as today)
  - `data class Tombstone(val hourStartMs: Long, val deletedAtMs: Long)`; `fun loadTombstones(ctx): List<Tombstone>`; `fun recordTombstone(ctx, hourStartMs, nowMs)`; `fun clearTombstone(ctx, hourStartMs)`; key `"tombstones"`, format `"<hourStart>:<deletedAt>"` joined by `,`, entries older than 7 days dropped on every write (`TOMBSTONE_TTL_MS = 7 * 24 * 3_600_000L`, in `:shared`).
  - `deleteRating` records a tombstone for the deleted entry's `timestamp`; the undo path (re-save of the same entry) calls `clearTombstone`.

- [ ] **Step 1: Write the failing tests** in `RatingRowTest`:
  - `old_six_field_row_defaults_modifiedAt_to_id_and_origin_to_phone`: decode `"1730000000000,7,1729990800000,14th,,💼 Work;Rest"` → `modifiedAt == 1730000000000`, `origin == PHONE`, tags `["💼 Work","Rest"]`.
  - `round_trips_eight_fields`: encode then decode an entry with `origin = WATCH`, `modifiedAt = id + 5` → equal.
  - `reclaim_tag_survives_round_trip`: tags `[RECLAIM_TAG, "Work"]` → equal after round trip.
- [ ] **Step 2:** Run `./gradlew :shared:test --tests "*RatingRowTest*"` — Expected: FAIL (unresolved `encodeRatingRow`).
- [ ] **Step 3:** Implement `RatingRow.kt`; switch `saveRating`/`loadRatings`/`deleteRating` to it; add `Tombstones.kt`; set `modifiedAt` at the save sites.
- [ ] **Step 4:** Run `./gradlew test installDebug` — Expected: PASS.
- [ ] **Step 5:** Commit `"Ratings record modifiedAt and origin; deletes leave a 7-day tombstone"`.

### Task 4: Resumable replay (the checkpoint)

**Files:**
- Modify: `shared/src/main/kotlin/com/example/beeing/StreakRules.kt` (`replay` at line 162, `computeStreakState` at 301)
- Test: `shared/src/test/kotlin/com/example/beeing/ReplayCarryTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  /** Everything the day walk carries from one day to the next, at the END of day `lastDayStartMs`. */
  data class ReplayCarry(
      val lastDayStartMs: Long,
      val streak: Int, val savers: Int, val idleRun: Int,
      val windowDaysIn: Int?,        // index - breakIndex of the open window, null = none
      val windowPrevStreak: Int, val windowQualified: Int, val windowTrailing: Int,
      val beginnerDaysCompleted: Int
  )
  fun carryAt(ratings: List<RatingEntry>, modeEvents: List<ModeEvent>, lastDayStartMs: Long): ReplayCarry
  fun computeStreakStateFrom(
      carry: ReplayCarry, ratings: List<RatingEntry>, modeEvents: List<ModeEvent>, nowMs: Long
  ): StreakState
  ```
  `ratings` passed to `computeStreakStateFrom` need only cover days after `carry.lastDayStartMs`; older ones are ignored.
- Approach: refactor the `while (true)` body of `replay` into a private `stepDay(...)` over a mutable carry so `replay`, `carryAt` and `computeStreakStateFrom` share it — one walk, not two. The resumed walk starts at the local midnight after `lastDayStartMs` (`Calendar.add(DAY_OF_YEAR, 1)`, never `+24h`). `computeStreakStateFrom` fills `beginnerDaysCompleted` from the carry plus resumed days; `recentDays` as today.

- [ ] **Step 1: Write the failing tests** in `ReplayCarryTest`, reusing `StreakRulesTest`'s helpers (copy `now()`, `day()`, `hist()`, `beginnerOn()`):
  - `resume_equals_full_replay_at_every_split`: for each history below and each checkpoint day from the first day to the day before yesterday, `computeStreakStateFrom(carryAt(r, m, d), r, m, now())` == `computeStreakState(r, m, now())`, comparing `currentStreak, displayStreak, savers, recoveryOpen, recoveryDaysLeft, beginnerDaysCompleted, todayHours, recentDays`. Histories: 10 complete days; a break then recovery on day 2 of the window; a break whose window closes unrecovered with 2 qualifying days; 8 idle days (comeback gift); savers spent down to 0 then broken; a beginner stretch across a running streak (`beginnerOn`); a Super day at the saver cap.
  - `resume_starts_at_local_midnight_across_dst`: with `TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))` and a checkpoint on 2026-10-25 (25 h day), the resumed state equals the full replay. Restore the default zone in `@After`.
- [ ] **Step 2:** Run `./gradlew :shared:test --tests "*ReplayCarryTest*"` — Expected: FAIL (unresolved `carryAt`).
- [ ] **Step 3:** Implement as described above.
- [ ] **Step 4:** Run `./gradlew :shared:test` — Expected: PASS, including every pre-existing engine test.
- [ ] **Step 5:** Commit `"Replay can stop at a day and resume with identical results"`.

### Task 5: Merge rules for incoming watch ratings

**Files:**
- Create: `shared/src/main/kotlin/com/example/beeing/SyncMerge.kt`
- Test: `shared/src/test/kotlin/com/example/beeing/SyncMergeTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  const val WATCH_CLOCK_SLACK_MS = 5 * 60_000L
  /** True when `e.id` (its save time) falls inside the 2-hour window of its hour, ± slack. */
  fun savedInTheMoment(e: RatingEntry): Boolean
  sealed interface MergeResult {
      data class Apply(val replaces: RatingEntry?, val refundReclaimNear: Long?) : MergeResult
      data object Drop : MergeResult
  }
  fun mergeIncoming(incoming: RatingEntry, local: List<RatingEntry>, tombstones: List<Tombstone>): MergeResult
  ```
  Window for `savedInTheMoment`: `hourStart + 1h - slack <= id < hourStart + 3h + slack` (the hour is ratable from its end until two hours later). The phone stores `Apply` by removing `replaces` (if any) and saving `incoming`; when `refundReclaimNear != null`, it removes the reclaim spend closest to that millis (`StreakEngine.kt:85-91` storage).
- Rules, in order: (1) not `savedInTheMoment` → `Drop`; (2) tombstone for its hour with `deletedAtMs >= incoming.modifiedAt` → `Drop`; (3) no local entry for its hour → `Apply(null, null)`; (4) local entry is reclaimed (`isReclaimed`) → `Apply(local, local.id)`; (5) local `modifiedAt > incoming.modifiedAt` → `Drop`; equal → `Drop` (tie goes to the phone); else `Apply(local, null)`.

- [ ] **Step 1: Write the failing tests**: `drops_a_rating_saved_outside_its_window` (id = hourStart + 4h), `accepts_within_clock_slack` (id = hourStart + 3h + 4min), `later_save_wins` (watch modifiedAt +60s over phone → Apply replaces phone), `earlier_watch_save_loses`, `tie_goes_to_phone`, `live_watch_rating_beats_added_later_and_refunds` (local tagged `RECLAIM_TAG` with a later modifiedAt → `Apply(local, local.id)`), `tombstone_newer_than_save_drops`, `tombstone_older_than_save_applies`.
- [ ] **Step 2:** Run `./gradlew :shared:test --tests "*SyncMergeTest*"` — Expected: FAIL.
- [ ] **Step 3:** Implement `SyncMerge.kt`.
- [ ] **Step 4:** Run `./gradlew :shared:test` — Expected: PASS.
- [ ] **Step 5:** Commit `"Merge rules for watch ratings: later save wins, live beats added-later"`.

### Task 6: No phone celebration for a watch-closed day

**Files:**
- Modify: `shared/.../StreakRules.kt` (`singleNewHourDay` at 414, `ringClosed` at 443, `streakMoment` at 457), `app/.../NowTab.kt:252-263`
- Test: `shared/src/test/kotlin/com/example/beeing/StreakMomentTest.kt` (extend)

**Interfaces:**
- Produces: `singleNewHourDay(prev, now, nowMs = …, watchHourStarts: Set<Long> = emptySet())`, and the same trailing parameter on `ringClosed` and `streakMoment`. The day is null when the one added hour is in `watchHourStarts`.
- `NowTab` passes `allRatings.filter { it.origin == Origin.WATCH && !it.isReclaimed }.map { it.timestamp }.toSet()`.

- [ ] **Step 1: Write the failing tests**: `ring_closed_by_a_watch_hour_does_not_fire` (8th hour's start in `watchHourStarts` → `ringClosed` null), `super_day_from_a_watch_hour_does_not_fire`, `phone_hour_after_watch_hours_still_fires` (hours 1–7 from the watch, 8th from the phone → fires).
- [ ] **Step 2:** Run — Expected: FAIL (no such parameter).
- [ ] **Step 3:** Implement; wire `NowTab`.
- [ ] **Step 4:** Run `./gradlew test installDebug` — Expected: PASS.
- [ ] **Step 5:** Commit `"Don't replay a celebration the watch already showed"`.

### Task 7: Streak alerts at 02:01, and withdraw alerts that stopped being true

**Files:**
- Modify: `app/.../StreakAlerts.kt` (`scheduleStreakAlerts` line 78: `HOUR_OF_DAY` 0→2; `postPendingAlerts`)
- Test: `app/src/test/java/com/example/beeing/StreakAlertsTest.kt` (extend)

**Interfaces:**
- Produces: `fun alertsToWithdraw(events: List<StreakEvent>, notifiedIds: Set<String>): Set<String>` (pure, in `StreakAlerts.kt`) — notified ids of `ALERT_TYPES` that no longer appear in `events`. `postPendingAlerts` cancels `1000 + (id.hashCode() and 0xFFFF)` for each and removes them from the notified set. It runs at 02:01 and also after every phone save/merge (call it from where `RatingsViewModel.saveRating` persists and from the merge path in Plan 2).

- [ ] **Step 1: Write the failing test**: `withdraws_a_saver_alert_once_the_day_completes` — events after a late rating lack `"SAVER_USED:<key>"`, notified contains it → returned.
- [ ] **Step 2:** Run — Expected: FAIL.
- [ ] **Step 3:** Implement; move the alarm to 02:01; update the 00:01 mentions in comments and `CLAUDE.md`'s "Streak alerts" seam line.
- [ ] **Step 4:** Run `./gradlew test installDebug` — Expected: PASS.
- [ ] **Step 5:** Commit `"Streak alerts wait until 2am and withdraw ones a late hour disproved"`.

### Task 8: The Beeing tone on every sounding channel

**Files:**
- Modify: `app/.../Utils.kt:44-86` (`createNotificationChannel`), every `"hourly_bee"`, `CHANNEL_STREAK_ALERTS`, `"weekly_report"` reference

**Interfaces:**
- Produces: channel ids `hourly_bee_v2`, `streak_alerts_v2`, `weekly_report_v2`, sound `Uri.parse("android.resource://${context.packageName}/${R.raw.beepingandroidbuzz}")` with the existing `audioAttributes`. Old ids `hourly_bee`, `streak_alerts`, `weekly_report` are removed with `deleteNotificationChannel`. Silent twins unchanged. Constants `CHANNEL_HOURLY`, `CHANNEL_STREAK_ALERTS`, `CHANNEL_WEEKLY` hold the new ids so no raw string remains.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Run `./gradlew installDebug` — Expected: BUILD SUCCESSFUL. Add to the owner's device checklist: "Settings → Apps → Beeing → Notifications shows one 'Hourly Reminders' channel and it plays the Beeing tone."
- [ ] **Step 3:** Commit `"Use the Beeing tone for reminders, alerts and the weekly report"`.

### Task 9: Docs

- [ ] **Step 1:** Update `CLAUDE.md`: file map rows for `:shared` (`StreakRules.kt`, `SuperDay.kt`, `Model.kt`, `RatingRow.kt`, `NotificationRules.kt`, `SyncMerge.kt`), `Tombstones.kt`; persistence gotchas (8-field rows, `"tombstones"`); the 02:01 alert; the removed switch; a dated history entry.
- [ ] **Step 2:** Commit `"Document the wear groundwork"`.
