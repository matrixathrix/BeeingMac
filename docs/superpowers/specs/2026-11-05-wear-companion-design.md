# Beeing for Wear OS — design

2026-11-05. Agreed with the owner in conversation; calls marked *(builder's call)*
or *(my call)* were not discussed and can be revisited.

## Intent

Rate the hour from the wrist, with the phone in a drawer or at home, for up to
**two days** (about the longest a watch battery lasts). The watch does one job:
**pick a score, pick tags, save** — the same core loop as the phone, minus
everything else. It must run comfortably on a **Galaxy Watch 4** (Exynos W920,
1.5 GB RAM): nothing runs, animates or polls while you're not looking at it.

When the two devices meet again they merge without asking anything, and from then
on every number on both agrees.

## Goals

1. Rate either of the two ratable hours on the watch, with ≥1 tag, fully offline.
2. Its own hourly reminder (Beeing's tone + its own vibration pattern), honouring
   silent hours and pause, even when away from the phone.
3. Ring-closed celebration on the watch: one strong haptic, the ring fills once.
4. Streak and saver counts on the watch that are **exactly** what the phone will
   show after sync, for any offline stretch up to (at least) two days.
5. A Tile and three complications as the glanceable surfaces.
6. Silent merge on reconnect: **the later save wins**, no prompt.
7. When the watch is connected and not charging, the watch buzzes and the phone
   stays silent.

## Non-goals

- History, the journal, Trends, editing or deleting past entries on the watch.
- "Add a past hour", notes, tag management, settings, the Super day invite.
- Streak alerts and the weekly report on the watch (they stay phone-only).
- A watch that works without ever having synced with the phone (first run asks
  you to open Beeing on the phone).

## Anti-goals (a failure even if it "works")

- Anything that animates, polls or refreshes on a timer while idle; per-minute
  complication updates; a countdown in a complication.
- Two buzzes for one hourly reminder (phone + watch, or two watch notifications).
- A streak or saver number on the watch that changes when the phone syncs.
- The phone replaying a celebration for a ring the watch already closed.
- Changing the stored `RECLAIM_TAG` string, tag strings, or anything that makes
  existing phone data read differently.

## Constraints

- Same `applicationId` (`com.example.beeing`) and the same signing key for both
  apps — the Data Layer only pairs apps that match. Release signing already exists
  (`~/.android-keystores/beeing-release.*`).
- Watch `minSdk` 30 (Wear OS 3). `GOAL_PROGRESS` complications need API 33: fall
  back to `RANGED_VALUE` below it.
- Phone storage stays SharedPreferences `"b"` with the existing row format; new
  fields are appended so old rows still read.
- The phone's existing rules (`StreakRules.kt`, the 2-hour window, the 10-hour edit
  window, beginner mode, the day a rating belongs to = the day its hour starts)
  are the rules on the watch. Nothing about them changes.

## Structure

Three Gradle modules:

- `:shared` — plain Kotlin/JVM library. **Moved** from `app`: `StreakRules.kt`,
  `SuperDay.kt`'s pure parts, `RatingEntry`, `ModeEvent`, the pure parts of
  `NotificationPrefs.kt` (`hourlyDelivery`), and the sync wire format. No Android
  or Compose dependencies, so it is unit-tested on the JVM.
- `:app` — the phone app, now depending on `:shared`.
- `:wear` — the watch app: Wear Compose (Material 3 for Wear), ProtoLayout Tiles,
  a complication data source, `play-services-wearable`. Marked standalone
  (`com.google.android.wearable.standalone = true`).

## The watch app

### Screens (renders were shown in conversation)

1. **Notification** — "How was 2pm – 3pm?" (or "2 hours waiting"), **Rate** and
   **Later**. One notification id: a new hour replaces the old one, so there is
   never more than one buzz per hour. Rate opens screen 2 on the hour that expires
   first (offset 1 when both are waiting, same as the phone).
2. **Score** — header with the hour range; ten score dots around the bezel, a big
   numeral and its `DIAL_WORDS` word in the centre; resting state reads "Pick a
   rating". **The crown turns the score** with a haptic tick per step; tapping a dot
   also works. **Next** continues once a score is chosen. When two hours are waiting,
   tapping the header switches between them.
3. **Tags** — header "7 · 2pm – 3pm"; the **top 8 tags** as chips, **two per row**;
   a **More** chip at the end opens the rest of the list; **Save · N tags** at the
   bottom, enabled at ≥1 tag. No cap on how many. **The crown scrolls here.** Swipe
   right goes back to the score. Selected chips are honey.
4. **Ring closed** — shown after the save that closes a ring: one strong haptic, the
   ring fills once (~600 ms) to "8/8", "Day complete", "N-day streak". Variants
   follow `RingCelebration`: beginner ("Beginner day complete", no streak claim),
   late ("Yesterday's ring closed"). At 12 counted Master hours the caption reads
   "Super day" instead *(my call — no separate screen)*. Closes on tap or after ~4 s
   *(builder's call on the exact time)*. System animations off ⇒ the full ring, no
   motion.

5. **Caught up** — what the app shows when opened from the launcher, a
   complication or the Tile with nothing waiting: the same content as the Tile
   (ring, streak + savers, "All caught up · next hour opens in N m") as a single
   static screen *(my call — Wear OS can't open a specific Tile, so the "→ the
   Tile" taps below land here)*.

After a save: if another hour is waiting, show a brief "Saved" tick and open screen
2 for it ("1 more" in the header); otherwise close the app (or show screen 4).

If the hour being rated closes while the screen is open, show "That hour just
closed" and move to the newer waiting hour with the score and tags kept, so one tap
on Save finishes *(my call — the phone simply blocks)*.

### Which tags

- The phone picks the **8 most-used tags over the last 30 days** (`visibleTags()`,
  so the reclaim sentinel never appears) and sends them with the full ordered list.
- The 8 are **shown in the user's own Tags-page order**, not by count, so chips
  don't move around.
- Tag names display through `tagChipText` (the existing display layer) — stored
  strings are sent and saved untouched.

### Tile

Today's ring (honey arc on a faint track, "5/8"), flame + streak count, shield +
"4/5" savers, and either "2pm – 3pm is waiting" + **Rate**, or "All caught up".
Redraws only when asked (after a save, a sync, the hourly alarm, the 2am rollover).
Tap Rate → screen 2.

### Complications (one data source each, several slot types)

| Name | Short / small | Long | Tap |
|---|---|---|---|
| Today's ring | progress arc + "5/8" (`GOAL_PROGRESS`, `RANGED_VALUE` below API 33) | "5 of 8 hours today" | rating screen if an hour waits, else Caught up |
| Waiting hours | hourglass + "2"; a faint tick when caught up | "2 hours waiting" / "All caught up" | rating screen on the expiring hour, else Caught up |
| Streak + savers | flame + "12", title "4/5" | "12-day streak · 4 savers" | Caught up (or the rating screen if an hour waits) |

States follow the phone: at risk = "12!" in red; beginner = leaf + "Paused 12"; no
streak = faint flame. Update period **0** — pushed via
`ComplicationDataSourceUpdateRequester` on the same four events as the Tile.
Monochrome icons so they render in ambient. **No minute countdowns.**

### Visual language

The dark "night field" palette (the watch is always dark), honey for "act here",
score bands for data only, `Sym` icons converted for Wear, Instrument Sans *(my
call — bundle the variable font; drop it if APK size matters on the watch)*. No
blur, glow, particles or shadows.

## Reminders

- The watch schedules its own exact alarm at the start of every hour (like
  `scheduleExactHourlyAlarm`), re-armed on boot, and posts the reminder itself —
  only when an hour is actually waiting.
- It applies its synced copy of **silent hours and pause** through the shared
  `hourlyDelivery` (SOUND / SILENT / NONE). There is no reminders on/off switch on
  either device (see "Phone change: no Hourly reminders switch").
- **Sound:** Beeing's tone `res/raw/beepingandroidbuzz.ogg` (owner approved for both
  devices) is the reminder channel's sound on the watch and the phone. **Vibration:**
  a Beeing pattern of its own (e.g. a short double tap — builder tunes it) so it
  doesn't feel like Galaxy's hourly chime, which uses the ordinary notification
  buzz.
- **Phone channels:** the owner asked for the tone as the app's default, so **every
  sounding channel** moves to it: hourly reminders, streak alerts and the weekly
  report. Sound is fixed when a channel is created, so each gets a new id
  (`hourly_bee_v2`, `streak_alerts_v2`, `weekly_report_v2`) and the old ids are
  deleted (a custom sound a user set on an old channel is lost — accepted). The
  silent twins stay as they are. The user can still change sounds in system settings.
- **First-run tip on the watch, once:** "Beeing already marks every hour. If
  Galaxy's hourly chime is on, you'll feel both — you can turn it off under Sounds
  and vibration." Keep Beeing at :00.

### Who buzzes when both are around

At each reminder the phone checks whether a node with the watch app's capability is
connected **and** the watch's last reported state is "not charging":

- yes ⇒ the phone posts its reminder on the existing silent channel
  (`hourly_bee_silent`), and the watch's own reminder does the buzzing;
- no ⇒ the phone rings as today.

The watch reports charging on/off as an event (`ACTION_POWER_CONNECTED` /
`DISCONNECTED`) through the Data Layer — no sensor kept running. The phone's
reminder is always `setLocalOnly(true)` once a watch app is installed, so it is never
bridged to the watch as a second notification.

### Phone settings copy

Shown only when the watch app is installed (Data Layer capability check), in the
sheets' existing 13sp muted style, no icon:

- **Silent hours** sheet, under the current description: "Beeing on your watch keeps
  the same silent hours. Changes reach your watch the next time it connects to your
  phone."
- **Pause** sheet, under the "Notifications are off until…" summary: "Your watch
  pauses too. If it isn't connected right now, the pause starts as soon as it
  reconnects."

## Streak and saver numbers on the watch

The watch runs the **real engine** from `:shared`, resumed from a checkpoint:

- `replay` gains a resumable form: a `ReplayCarry` (streak, savers, recovery window,
  idle-day run toward the comeback gift, anything else the walk carries) at the end
  of a given day, and a way to continue from it. **Invariant, unit-tested:** a full
  replay equals replay-to-checkpoint + resume over the rest, for every day split.
- On each sync the phone sends the carry at the end of the **day before yesterday**
  (always settled, whatever the time) *(my call)*, plus the mode events, and the
  counted hour slots it knows for yesterday and today.
- The watch resumes over yesterday, today and any days it has spent offline, using
  its own saves plus what the phone sent. Cost: a few days × 24 slots, once per save
  and at rollover — negligible.
- Until it syncs again the watch's numbers are exact for its own ratings; a small
  sync mark beside the numbers shows the phone hasn't confirmed them yet *(my call)*.
  If the phone had newer facts (a mode toggle, a phone-side rating), the numbers
  correct on the next sync — the only time a watch number changes underneath you.

## Sync

### Transport

`DataClient` (persists and delivers when the nodes meet, days later if need be), not
`MessageClient` (drops when unreachable).

- **Watch → phone:** one DataItem per save, keyed by hour slot, carrying the rating
  (score, tags, hour start, recorded time `id`, `modifiedAt`, origin = watch). The
  watch removes it once the phone's state shows it was merged *(builder picks the
  ack mechanism)*. Also the charging flag.
- **Phone → watch:** one state DataItem, replaced on every change that matters: the
  ordered tag list + top 8, the mode events / current goal, notification prefs
  (silent hours, pause), the engine checkpoint, yesterday's and today's rated slots
  (with which ones are counted), and the sync time.

### Merge rules (on the phone)

- **Same hour rated on both:** the save with the later `modifiedAt` wins, silently
  (owner decision). Ties go to the phone.
- **Deletes:** the phone keeps a tombstone (hour slot + `deletedAt`) for 7 days *(my
  call — longer than any offline stretch)*. An incoming watch rating older than the
  tombstone is dropped; a newer one wins. Undo removes the tombstone.
- **"Counts" is decided by when it was saved, not when it arrived:** a watch rating
  is in-the-moment when its recorded time falls inside the 2-hour window of its hour
  (allow ~5 min clock slack — builder's call). 11pm rated at 12:30am counts for
  yesterday even if the phone first sees it at 9am. Watch ratings are never
  `RECLAIM_TAG`.
- **A live rating always beats an "Added later" one for the same hour**, whatever
  the times *(my call)*. The phone offers an hour for "Add a past hour" when it looks
  unrated, and an unsynced watch rating looks unrated. If a watch rating arrives for
  an hour the phone has a `RECLAIM_TAG` entry for, the watch entry replaces it and the
  reclaim spend for it is removed (the user gets that day's allowance back). "Later
  save wins" applies only between two live ratings, or two backdated ones. This keeps
  the watch's count of the hour, and its celebration, true after sync.
- Mode events and reclaim spends are append-only logs and stay phone-only (the watch
  never writes them).

### Data model changes on the phone

- `RatingEntry` gains `modifiedAt` (defaults to `id` for old rows) and `origin`
  (phone / watch; old rows = phone). Appended to the stored row so old data reads.
- Tombstones get their own key in prefs `"b"`.
- CSV export/import is unchanged in v1 *(my call)*; imported rows get
  `modifiedAt = id`, `origin = phone`.

### No double celebration

The phone's ring-closed / Super day trigger (`singleNewHourDay` / `ringClosed` /
`streakMoment`) must **not fire for a day whose new counted hour came from the
watch** — the watch already celebrated it. That also means no Super day invite on the
phone after a watch-closed ring. Phone-saved hours behave as today.

## Phone change: no "Hourly reminders" switch

Owner decision: the switch is removed, and the watch never gets one. Pause (with an
end time, up to 48 h) covers breaks, Android's per-channel notification settings
cover "never", and the "who buzzes" rule keeps the phone quiet when the watch is on
duty. Remove `NotificationPrefs.remindersOn`, its prefs key, the Settings row
("Hourly reminders · A nudge each hour to rate the last one") and the
`!p.remindersOn` branch of `hourlyDelivery`. No migration: there is no installed
base yet. Silent hours and pause are the only reminder controls on either device.

## Phone change: streak alerts at 02:01

Unrelated to the watch but made urgent by it: `scheduleStreakAlerts`
(`StreakAlerts.kt`) fires at **00:01**, while yesterday's last two hours are still
ratable until 2am. Move it to **02:01**. And when a later rating changes the replay so
an already-posted alert's event no longer exists (e.g. "a saver kept your streak"
after a late watch rating completed the day), **cancel that notification**.

**The engine does not change** *(my call, per "the rules don't change")*: `replay`
still closes yesterday at midnight, so between 12am and 2am both devices show
yesterday as finished, and a late rating of 11pm can still flip it — exactly as the
phone behaves today. Only the alert moves. On the watch "2am rollover" means only
that yesterday's two hours stop being ratable, so the counts for yesterday are
dropped and the Tile/complications redraw (the 2am hourly alarm already fires).

## Edge cases the builder should cover

- Watch reboot (re-arm the alarm), app update, time-zone change while offline (day
  boundaries follow the device's local time, as on the phone).
- Watch never synced ⇒ "Open Beeing on your phone to set up", no reminders.
- Watch on charger ⇒ phone rings.
- Beginner ⇄ Master toggled on the phone while the watch is away ⇒ watch numbers
  correct on next sync; watch goal uses the last-known mode until then.
- Tag renamed/deleted on the phone while the watch used the old name ⇒ the entry
  keeps the string it was saved with (existing "picker-only" rule).

## Left to the builder

Ack mechanism for watch DataItems; the exact vibration pattern; the celebration's
auto-close time; DataItem paths and wire encoding; Wear library versions; how the
phone decides "watch app installed" (capability name); test structure. Verification
follows the repo's rule (`./gradlew installDebug`, plus `:wear` builds and `:shared`
JVM tests); on-device checks are a list handed to the owner.
