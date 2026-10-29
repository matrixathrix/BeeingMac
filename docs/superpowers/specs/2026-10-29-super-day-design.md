# Super days — design

Date: 2026-10-29 · Status: draft, awaiting owner approval

## Intent

A Master-mode day with **12 counted hours** already earns a streak saver
(`SAVER_EARN_HOURS`), but the day itself has no name and its only ceremony is the
"Streak saver earned" screen. **Super day** names that day and makes it something
to aim for: when the daily ring closes at 8, a second beat invites the user to go
on to 12, but only when 12 is still realistic.

No new rule and no new reward. A Super day is a name and a look for a day that
already exists in the engine.

## Goals

1. Keep the ring-closed celebration (`RingClosedCelebration`) exactly as it is.
2. Right after it, when 12 is realistic, show a second full-screen beat:
   "Make it a Super day".
3. Reaching 12 shows a **Super day** screen, which replaces today's
   "Streak saver earned" moment.
4. A Super day stays visible in the record: the Streak tab calendar, the
   journal and History.
5. The ring itself teaches the goal: hours 9–12 fill a visible **outer lap**.

## Non-goals

- No change to saver earning, the cap, recovery, the comeback gift or any streak
  outcome. Replayed history is byte-identical.
- No Super days in beginner mode. Savers are Master-only, so the beginner ring
  closing at 4 never shows the invite.
- No notification, no weekly-report line and no Trends stat for Super days.
- Super-ness is never persisted: it's derived from counted hours on replay.
  The only new stored value is the invite's once-a-day flag (below).

## Anti-goals (failure even if it works)

- Inviting the user to a Super day they can't reach (silent hours, a pause, or
  too late in the day).
- A nag: the invite must never come back later in the day, repeat, or read as a
  scold.
- A choice that isn't a choice: the invite has **one** button.
- The Super day screen firing on replay, import, a backdated save or a mode
  toggle (the existing `streakMoment` "exactly one new counted hour" guard has to
  hold).

## The rules, precisely

**Super day** = a day whose mode at day end is Master, with
`countedHours >= SUPER_DAY_HOURS` (12). Its outcome is `QUALIFIED` or
`RECOVERED`. Backdated (`RECLAIM_TAG`) hours never count, same as everywhere
else. Introduce `SUPER_DAY_HOURS = 12` in `StreakRules.kt` and define
`SAVER_EARN_HOURS = SUPER_DAY_HOURS`, so the two can't drift apart.

**Reachable hours** (a pure function, unit-tested, no Context), evaluated at
`nowMs` with `NotificationPrefs`:

- Candidates are today's clock hours (by start timestamp) that are not yet
  counted, and that are either
  - currently ratable (the last completed hour and the one before it, when they
    start today), always included because they can be rated right now, or
  - the in-progress hour and every later hour starting up to 23:00. The
    11pm–12am hour belongs to today even though it's rated after midnight.
- A *future* hour is dropped when its reminder moment (its end, `start + 1h`)
  falls inside silent hours (`inSilentHours`), or inside an active pause with
  `PauseMode.OFF`. Silent hours stand for "I'm asleep or away", even though
  technically they only silence the reminder.
- Reminders switched off entirely (`remindersOn = false`) does **not** drop
  hours (builder's call). That's a standing choice to rate without prompts, not
  a sign the user is away. A pause and silent hours are.
- `needed = SUPER_DAY_HOURS - todayHours`.

Reachable hours are always computed **for the day the closed ring belongs to**
(the rated hour's day), not for the calendar day `nowMs` falls in.

**Invite shows** when, at the moment the ring closes, the day is Master and
`reachable >= needed + SUPER_DAY_SLACK`, where `SUPER_DAY_SLACK = 1` (owner
decision: one spare hour). If the check fails, the ring-closed celebration plays
alone and nothing about Super days appears that day, apart from the passive cues
below.

A ring closed **after midnight** (by the 10pm or 11pm hour, rated on the next
calendar day) gets no invite, and **not through a special case**: the
same rule applies (owner decision). By then the only reachable hours left for
that day are the at most two ratable ones, which is fewer than `needed + 1`.

**Once a day, for real** (builder's call): the ring-closed effect fires again
whenever the count goes 7 → 8, and with Undo on every delete that's common
(delete one of today's hours, Undo). So the invite is gated by a stored day key,
SharedPreferences `"b"` key `"super_invite_shown_day"` (the day's start millis),
written the moment the invite shows, in the same idiom as
`beginner_graduation_shown`. Not exported in the CSV. Beat 1 and the Super day
screen replaying after an undo is existing behaviour (the saver screen already
does it) and stays out of scope.

**Passive cues** (builder's call: quiet, so they use the exact fit without the
slack, `reachable >= needed`): while `8 <= todayHours < 12` in Master mode and 12
is reachable, the Now `StatusStrip` goal reads `H/12` and the Streak band ring
says "N more for a Super day". When 12 isn't reachable they fall back to today's
behaviour.

## After midnight (owner decision: count the late hours, celebrate them too)

The 10pm and 11pm hours of a day can be rated just after midnight, and they
count for the day they started in. The replay already credits them correctly
(outcome, Super-ness, saver). What has to change is the celebration side:

1. **Day-keyed trigger.** `streakMoment` and the ring-close effect in `NowTab`
   compare only `todayHours`, so a save for yesterday made after midnight
   changes nothing they watch. Both must compare counted hours **keyed by day
   start** across yesterday and today, and fire when exactly one new counted
   hour landed on whichever day. Comparing "today"/"yesterday" fields is wrong:
   if the app stays open across midnight, the before and after snapshots label
   different days. The guard's guarantees are unchanged: replay, import, a
   backdated save and a mode toggle can never celebrate. A day's mode is fixed
   at its end, so a toggle after midnight can't touch yesterday.
2. **Late ring-close celebrates too.** Today, closing the ring with the 11pm
   hour shows nothing. The same fix gives it `RingClosedCelebration`. A late
   Super day gets the Super day screen. The invite falls away by the normal
   rule (above).
3. **Screens name the day.** When the celebrated day isn't the current calendar
   day, the copy says so: beat 1's caption becomes "Yesterday's ring closed",
   and the Super day screen's title becomes "Yesterday was a Super day". The
   streak count and the saver row are unchanged.
4. **The Save button bridges midnight.** The pill moves to the new day's 0/8 at
   midnight, so while a ratable hour from yesterday is the one that would make
   it a Super day (yesterday is Master, at 11 counted hours), the Save label
   for that hour reads "Save 11pm – 12am · finishes your Super day". The
   builder may also add the same hint for the hour that would close yesterday's
   ring (at 7). The hint lives only on that hour's Save label.

Tests: the day-keyed guard across midnight with the app open and with a fresh
launch, a late 8th hour, a late 12th hour, and no invite on a late ring-close.

## The screens

Mockups were reviewed in the brainstorming session. Colours are from the "Night
bloom" palette: aubergine `BandBottomLight` field, `HoneyGold` inner ring, and
`#FFD54F` (light gold, already used by `RingClosedCelebration`) for the outer lap
and the Super day accents.

### Beat 1: ring closed (unchanged)

`RingClosedCelebration`, as today. The one change: when beat 2 follows, the end
of beat 1 must hand straight over to beat 2 with no flash back to the app
underneath (for example, fade the black scrim into beat 2's aubergine rather than
to transparent). How is up to the builder.

### Beat 2: "Make it a Super day" (new)

Full-screen, same takeover layer as the other celebrations.

- Overline "DAY COMPLETE" (honey, letter-spaced, like the saver screen's label).
- The ring: the closed honey inner ring with an outer track of 4 segments
  (hours 9–12) drawing itself around it. The next empty segment breathes slowly.
  Centre: "8" over "of 12".
- Title **"Make it a Super day"**. Body: "N more hours tonight. A Super day earns a
  streak saver." When savers are already at `SAVER_CAP`, the second sentence
  becomes "All 5 streak savers are already saved up." ("tonight" vs "today" is
  the builder's call, by clock.)
- The `SaverSlots` row, with the slot the user would earn pulsing. At the cap,
  no pulse.
- **One** honey button: **"Let's go"**. It dismisses, and so does system back.
  Taps elsewhere are swallowed, as in `StreakMomentCelebration`.
- No haptic (the ceremony budget is spent on beat 1). With system animations
  off, show the end state.

### Super day reached (replaces "Streak saver earned")

`StreakMoment.SaverEarned` becomes `StreakMoment.SuperDay(savers, earnedSaver)`.
It fires on the 12th counted hour of a Master day **even at the saver cap**
(today no moment fires at the cap). It keeps the `streakMoment` guard. Screen:
overline "SUPER DAY", both laps full with a faint halo ring and a few sparks, "12"
over "hours", title "Super day", body "Streak saver earned. N of 5 saved up." or,
at the cap, "All 5 streak savers saved up.", the saver row (new one highlighted
when earned), and the button "Keep going". It shows whether or not beat 2 was
shown that day.

## Where a Super day shows afterwards

- **`StreakRing`** (Streak band): in Master mode the overflow head is replaced by
  the 4-segment outer lap, filling one segment per hour past 8 and capping at 12.
  Beginner mode keeps the current overflow head.
- **`MiniHourRing`** (Now `StatusStrip`): the same two-lap idea at 20dp. If the
  outer lap doesn't read at that size on a device, the builder may simplify it to
  a thin outer arc.
- **Calendar** (Streak tab): a Super day is the normal honey complete tile plus
  the `Sym.Sparkle` glyph. **No halo or outline** (owner decision). The legend
  gains a "Super day" key.
- **Journal** (`JournalDayItem`): Super days get the sparkle and the words
  "Super day" in the day headline.
- **History** (`StreakLogContent`): one row per Super day titled "Super day",
  with the detail line mentioning the saver when one was earned. Existing event
  ids must not change, so the bell's read state and the 00:01 phone alerts don't
  re-fire old events. Whether this re-titles `SAVER_EARNED` or adds a new
  `SUPER_DAY` event type (one is needed for capped days) is the builder's call.
- **Copy**: "How Beeing works", the Streak-tab rules dialog and
  `SaverExplainerDialog` say "Rate 12 hours and it's a Super day, which earns a
  streak saver". Add **Super day** to the Lexicon in `CLAUDE.md`.

## Constraints

- Jetpack Compose and the existing celebration layer in `HourlyPulseApp`
  (`ringCelebration` / `streakMoment`). The invite decision is made where
  `NowTab` calls `onRingClosed`, so it should travel on `RingCelebration` (for
  example a nullable `superInvite` carrying hours needed and savers).
- Keep animated values out of composition (the 2026-10-27 performance lesson):
  read them in `graphicsLayer {}` / draw lambdas.
- Any new text-on-colour pairing goes into `PaletteContrastTest`.
- Verify with `./gradlew installDebug`. The owner tests on their own device:
  hand over a device test list, don't drive the phone.

## Tests (pure, JVM)

- Reachable hours: silent hours that cross midnight, an OFF pause vs. a QUIET
  pause, the two ratable hours counted even inside silent hours, the 11pm hour,
  yesterday's hours excluded after midnight, already-counted hours excluded.
- The invite threshold, with the slack, at its boundaries.
- `streakMoment`: fires `SuperDay` at the cap with `earnedSaver = false`, and
  never on replay, import or backdated saves.
- After midnight: see the tests listed in "After midnight".
- Replay unchanged: existing `StreakRules` tests pass untouched.

## Left to the builder

Animation timings and curves, the exact beat 1 → beat 2 handover, ring stroke
proportions, the halo and spark treatment on the Super day screen, and how
History models the event (within the id constraint above).
