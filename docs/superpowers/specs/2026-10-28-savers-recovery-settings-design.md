# Streak savers, streak recovery, warnings bell, settings page — design

Date: 2026-10-28 · Status: draft for owner approval

## Why

The owner wants the streak to feel like a game worth protecting, with rewards
for exceptional days and a second chance after a loss, without the old weekly
freebie. The rest day was free and silent, so it neither rewarded effort nor
made a missed day feel like it cost anything. At the same time:

- "Send a bee back" makes no sense as a name ("back to where?").
- Backdated hours should keep the record honest, but they must not prop up the
  streak.
- The ☰ "Data options" dialog has outgrown a dialog. The owner wants a proper
  settings page, plus control over when notifications make noise.

Success looks like: a 12-hour day feels like an event, a missed day spends
something visible, a broken streak comes with a clear and time-boxed way back,
and the user always knows where they stand from the header.

## Goals

1. **Streak savers** replace rest days.
2. **Streak recovery**: one way back after a break, open for 3 days.
3. **A warnings bell** in the header, with an unread count.
4. **"Add a past hour"** replaces "send a bee back". Backdated hours no longer
   count toward anything, and they now take tags.
5. **A full settings page**, opened from a ⋮ button.
6. **Silent hours** and **pause notifications**.

## Non-goals

- No currency, shop or second reward type. Savers are the only reward.
- Savers can't be spent by hand, and there's no prompt to spend them.
- No changes to the rating flow, the dial, tags, the Past tab or Trends, beyond
  the copy and counting changes listed here.
- No server or cloud state. Everything stays in SharedPreferences `"b"`.

## Anti-goals (would make this a failure even if it works)

- Ceremony on every save. The saver animation fires only when a saver is
  actually earned (12th in-the-moment hour, under the cap).
- Backdated hours quietly counting toward the ring, the streak, savers or
  recovery anywhere in the app.
- A bell that nags: a badge that never clears, or duplicate items for the same
  event.
- History that lies. Savers, breaks and recoveries must be replayed from data,
  exactly like the current streak engine. They must never be stored as a live
  counter that drifts from the ratings.

## The rules (owner decisions unless marked *builder/designer call*)

### Which hours count

- **In-the-moment hours count; backdated hours never do.** An entry is
  backdated if it carries `RECLAIM_TAG` (`"💧reclaimed"`). The stored string
  stays as it is, since it's persisted data. "Counts" means: the daily ring
  (`todayHours`), the 8-hour Master goal, the 4-hour beginner goal, saver
  earning (12) and recovery (10). Backdated hours still appear in the journal,
  Trends, the today strip and averages, because they're part of the record.
- This is a change: today reclaimed hours count toward the 8.

### Streak savers

- **Start**: every user has **2 savers** at the start of their history, which
  is the first replayed day. A fresh install with no ratings shows 2.
  *Designer call:* because savers are replayed, an existing user's balance is
  whatever their history produces from a starting 2, not a flat 2 on upgrade.
- **Earn**: a Master-mode day with **≥12 distinct in-the-moment rated hours**
  earns **+1 saver**, at most one per day.
- **Cap**: **5**. At 5, a 12-hour day earns nothing, with no message and no
  animation.
- **Spend**: a finished Master-mode day under 8 counted hours, while a streak
  is running (`streak > 0`), **automatically uses 1 saver**. The streak holds
  but doesn't grow. *Designer call:* with no streak running, a miss spends
  nothing, since there's nothing to protect. This mirrors today's rest-day rule.
- **Rest days are removed**: `DayOutcome.REST`, `FORGIVE_WINDOW_DAYS` and their
  copy go. Days that used to replay as REST become SAVED or a break under the
  new rules.
- **Beginner mode**: beginner days stay transparent. They don't earn or spend
  savers, and a paused streak can't break. *Designer call, owner didn't object.*

### Streak recovery

- A **break** is a Master miss with a streak running and 0 savers.
- The **recovery window** is the **3 calendar days after the break day**.
- If **any one** of those days reaches **≥10 in-the-moment hours**, the streak
  is **restored**.
- *Designer call: restored count* = the streak before the break + every
  qualifying (≥8) day inside the window up to and including the recovery day.
  The broken day and any short days in the window add nothing and break nothing
  further.
- *Designer call:* during an open window, days under 8 don't spend savers (you
  have none, or you just earned one inside the window; it isn't spent while the
  window is open). Once the window closes without a 10-hour day, the break is
  final, and any qualifying days in the window stand as a new streak.
- Today counts as a recovery day while it's in progress: hitting 10 today
  restores immediately.
- **While the window is open, the streak counter shows the old count, in red
  with a warning "!"** (owner decision). This applies to the `StatusStrip` on
  every tab and to `StreakMeter`. The flame and number turn red, and a small
  "!" sits beside the number. The accessibility description becomes "{N}-day
  streak at risk · {D} day(s) left to win it back". *Designer call:* use a light
  red that passes `PaletteContrastTest` on the aubergine band (e.g. ~#FF6B6B,
  to be verified), not the 1–4 score red, which is too dark there. The "!"
  means the colour never travels alone. Under the hood `currentStreak` is the
  in-window count, and the UI shows `recoverableStreak` while
  `recoveryOpen`. Once the window closes without recovery, the counter drops to
  the new streak and goes back to normal colours.
- *Designer call: if the window closes without recovery*, normal streak rules
  apply to the window days after the fact: the new streak is the run of
  consecutive qualifying days ending at the close. So [8h, 3h, 8h] → 1, and
  [8h, 8h, 3h] → 0. The forgiveness of short days is part of the recovery
  reward and only applies if you recover.
- *Designer call: beginner mode during a window.* The window is always 3
  **calendar** days. Beginner days use up window days and can't recover the
  streak, even at 10+ hours, because they stay transparent. Switching modes
  neither freezes nor closes the window. This closes the "switch to beginner
  for a free extension" loophole. The existing beginner pause-confirm doesn't
  change.

### Warnings bell

The bell is for **warnings only**, not a general feed. It's a 36dp circular
`OnBandFill` button on the band's top row with a honey badge showing the unread
count. Tapping it opens a bottom sheet titled **"Heads up"** listing items newest
first. Unread items have a honey dot, opening the sheet marks everything read
and clears the badge, and there's a "Mark all read" action.

Items, with their exact copy patterns (counts are live values):

| Trigger | Title | Detail |
|---|---|---|
| A saver was used | "A streak saver kept your streak alive" | "You missed {Day}. Your {N}-day streak is safe. {S} saver(s) left." |
| Savers ≤ 2 after a spend | "Only {S} saver(s) left" | "Rate 12 hours in one day to earn another." |
| Streak broke (no savers) | "Your {N}-day streak broke" | "Rate 10 hours on one of the next 3 days to win it back." |
| Recovery window, ≤2 days left | "Only {D} day(s) left to win back your {N}-day streak" | "Rate 10 hours on one of them." |
| Streak recovered | "Streak recovered" | "Your {N}-day streak is back." |

- "Streak broke" and "recovered" were added by the designer. The owner listed
  the others.
- One item per event: the "days left" warning is one item per window day
  (D=2, then D=1). It is never re-posted on every app open.
- Items are **derived from the replay** (events with day keys) plus one
  persisted value: the last-read timestamp or the set of read item ids. Keep
  the newest ~30 items. *Builder call:* how exactly to key them.
- A setting (below) can turn bell warnings off.
- *Designer call: no backlog on upgrade or import.* Items for events before
  the first launch of this version are stored as already read. The same
  baseline is reset after every import. Otherwise an existing user's first
  launch would show a badge of ~30 stale items about weeks-old days that the new
  rules replayed.

### Saver-earned celebration

Full screen, in the style of the existing `RingClosedCelebration`. The owner
approved the mockup: a honey shield with a flame inside drops in and floats on
a glowing aubergine background, with sparks, the label "12-HOUR DAY", the title
"Streak saver earned", the line "It will step in automatically the next time
you miss a day.", a row of 5 shield slots (filled up to the new count, the new
one pops in, empty ones dashed), and a "Keep going" button. One haptic on the
drop.

- Fires **when the save that makes the 12th in-the-moment hour lands**, only if
  the balance was under 5.
- It must never fire on replay, import, a backdated save, or a goal or mode
  toggle. Use the same "previous value" guard idea as the ring celebration.
- If the ring celebration is ever pending at the same moment, it plays first.
- Respects system animations off: show the static end state.
- A recovered streak gets *builder call*: a lighter treatment (or reuse this
  screen with "Streak recovered" and the flame), never silence.

### Saver symbol

A new `Sym.Saver`: a shield with the streak flame inside, as a vendored
Material Symbols-style vector. In the bell list it's tinted red for "used", honey
for "left/earned".

## "Add a past hour" (replaces "send a bee back")

- **Name**: "Add a past hour". The Streak-tab card becomes "Missed an hour? Add
  a past hour · {n} available", and the spent state becomes "You've added 2 past
  hours today. More tomorrow."
- **Limits**: **2 per calendar day** (unchanged) and **up to 12 clock hours
  back** (was 10; `RECLAIM_WINDOW_HOURS` → 12). The 2 normally ratable hours
  are still excluded.
- **Entry form**: score + **tags (≥1 required, the same chip picker as the Now
  tab)** + optional note. *Designer call:* the note goes from mandatory to
  optional, since tags now carry the context the note used to force.
- **Badge**: 🕐 `Sym.FromMemory` with the label **"Added later"** in history
  lists.
- **Copy sweep**: remove every "send a bee back" / "revisit" / "bees left" /
  "bee sent back" string (`NowTab` slipped-hours link, `StreaksTab` card, pick
  dialog, `ReclaimHourDialog`, the rules explainer, "How Beeing works", beginner
  explainer, history log rows, `BeeIcons` comments). The action icon is no
  longer the bee (`Sym.Reclaim`); *builder call* on a history/add-time glyph.
  Internal identifiers (`RECLAIM_*`, `isReclaimed`) may stay, per the repo's
  "copy diff small" precedent.
- **The "lowest tag" and tag stats** already exclude the sentinel via
  `visibleTags()`. The new user tags on backdated entries do count in tag stats,
  since they're real tags.

## Header and settings

### Top row (all three tabs)

`Beeing` title (left, the hamburger is removed) · `StatusStrip` (unchanged:
streak + ring; **savers are not shown here**, owner picked layout B) · **bell**
· **⋮**. The ⋮ is a plain 28dp-wide icon with no circle, so the bell reads as
the actionable one. The same row goes on Now, Streak and Past. Today only Now
has a menu. Must fit a 360dp phone; it was checked in the mockup with "Beeing"
at its rest size.

Savers are shown on the **Streak tab** (*designer call:* a row of 5 shield
slots in or under `StreakMeter`, with copy "2 of 5 savers").

### Settings page

The ⋮ opens a **full-screen settings page** (not a dialog): a band with a back
arrow and the title "Settings", over grouped cards on the page. Each row has an
icon tile, a name, a one-line explainer, and a trailing switch, value or ›.
System back returns. It replaces the "Data options" `AlertDialog` entirely.

Sections and rows, in order:

1. **Your goal**: Beginner mode (switch; the existing pause-confirm and
   graduation logic is unchanged).
2. **Streak**: Streak savers ("{S} of 5" ›, opens an explainer of the earn,
   spend and recovery rules) · Streak warnings (switch: show warnings in the
   bell).
3. **Notifications**: Hourly reminders (switch, *designer call*) · Silent hours
   (›) · Pause notifications (›).
4. **Tags**: Manage tags ({count} ›, opens the existing `ManageTagsDialog`).
5. **Backup & data**: Daily auto-backup (switch + "last backed up …") · Backup
   folder (›, the existing picker) · Export data · Import data (both with the
   existing launchers and import confirm).
6. **Help**: How Beeing works (›, the existing dialog with rewritten copy).
7. Footer: "Beeing · Version {versionName}" / "Developed by AthrixMatrix".

## Notifications: silent hours and pause

### Silent hours (daily)

- The user sets a start and an end time (it may cross midnight, e.g. 10 PM to
  7 AM) plus an on/off switch. The row reads "Every day, 10 PM to 7 AM · no
  sound", or "Off".
- During silent hours, hourly reminders **still post, with no sound or
  vibration**.
- Android fixes a channel's sound at creation, so this needs a **second
  channel** (e.g. `hourly_bee_silent`, sound off, vibration off).
  `NotificationReceiver` picks the channel when it posts. The weekly report
  follows the same rule. *Builder call* on whether it gets its own silent
  channel.

### Pause notifications (until a time)

- The row reads "Pause notifications · Until a time you choose, up to 2 days".
  While a pause is running, the row turns honey and reads "Paused until Thu
  9 PM · Quiet/Off" with an **End now** button.
- The sheet has:
  - **How**: two side-by-side choice cards. **Quiet** (muted speaker): "Still
    arrive, no sound. You can keep rating." **Off** (crossed-out bell): "No
    notifications. A real break from Beeing."
  - **Until**: chips "In 1 hour" · "In 3 hours" · "Tomorrow 7 AM" · "Pick a
    time". "Pick a time" opens a day + hour wheel, at hourly resolution, capped
    at **now + 48 hours**.
  - A read-back line ("Notifications are off until **Thu 9 PM** · 2 days") and
    the confirm button "Pause until Thu 9 PM".
- **Quiet** posts on the silent channel. **Off** skips posting. Either way the
  hourly alarm keeps rescheduling, so reminders resume on their own at the end
  time. The pause end is a persisted timestamp, checked when each alarm fires,
  so it survives reboot.
- **Saver warning (Off only)**: before confirming, if the number of Master days
  the pause could cost exceeds the current saver balance, show a red card above
  the button: "This covers {D} days and you have {S} saver(s). If you don't
  rate on your own, your {N}-day streak could break on {Day}." *Designer call:*
  D = calendar days the pause overlaps, not counting today if today is already
  qualified, and not counting beginner days. {Day} = the first day the balance
  would run out. Only shown while a streak is running. Quiet never warns.

## Engine changes (where the logic lives)

All in `StreakEngine.kt`'s single `replayDays` walk, so the meter, calendar,
log, bell and settings can't disagree:

- Count only non-reclaimed hours toward every threshold. Keep the full hour
  list for display.
- Walk-state: `savers` (start 2, cap 5), `openWindow` (break day index + the
  streak before the break + qualifying days so far).
- New `DayOutcome` values replace REST, e.g. `SAVED` (saver used) and
  `RECOVERED`. `ReplayDay` gains `saverEarned`, `saverSpent`, `saversAfter`,
  `recoveryWindowDaysLeft`.
- `StreakState` gains `savers`, `recoveryOpen`, `recoveryDaysLeft`,
  `recoverableStreak`, and `todayCountedHours` (the ring reads this).
- `computeStreakLog` gains "Saver earned", "Saver used", "Streak broke" and
  "Streak recovered" events (icons from `Sym`). The bell's items are a filtered
  view of these.
- Constants at the top: `SAVER_START = 2`, `SAVER_CAP = 5`,
  `SAVER_EARN_HOURS = 12`, `RECOVERY_DAYS = 3`, `RECOVERY_HOURS = 10`,
  `RECLAIM_WINDOW_HOURS = 12`. Delete `FORGIVE_WINDOW_DAYS`.

**History is replayed under the new rules**, the same precedent as the
2026-08-01/08-03 changes. Past rest days become saver spends or breaks, and the
current streak may change. Unit tests should pin: earn at 12 (and not at 12 with
backdated hours), the cap at 5, spend on a miss, no spend at streak 0, a break
at 0 savers, recovery at 10 on day 1/2/3, no recovery on day 4, the restored
count, beginner transparency, and the starting balance of 2.

## Copy and docs

- Rewrite the Streak-tab rules dialog, "How Beeing works" and the beginner
  explainer for savers + recovery + "Add a past hour". Remove the rest-day and
  🌙 copy and the calendar legend's "rest day" key. *Builder call:* saved days
  get a small shield in the calendar, and recovered days get the normal
  complete tint.
- Update `CLAUDE.md`: the product rules, lexicon (saver, recovery, "Add a past
  hour", "Added later"; remove rest day and "send a bee back"), file map
  (settings page file, bell), and a history entry.

## Left to the builder

File layout (e.g. `SettingsScreen.kt`, `WarningsSheet.kt`,
`SaverCelebration.kt`), the navigation mechanism for the settings page inside
the existing Scaffold/pager, the read-state storage format for the bell, the
glyph for "Add a past hour", the calendar marks for saved days, and the
recovered-streak treatment. Each within the existing design system (BrandBand,
BeeCardDefaults, OnBandFill, honey = act here, `PaletteContrastTest` for any new
colour pairings).

## Mockups

`.superpowers/brainstorm/884-1793206120/content/` (git-ignored):
`bell-and-saver-v2.html` / `-420.html` (header B, the "Heads up" sheet, the saver
celebration), `settings.html` (⋮ placement, settings page), `silence.html`
(Notifications section, pause sheet). The pause sheet's Off card copy is now
"No notifications. A real break from Beeing."
