# Beeing for Wear OS: device checklist

Nothing here was tried on a real phone or watch: the work was built and unit-tested
only. Install both debug builds (same debug key), open Beeing on the phone once,
then work down the list. "Watch" means the Galaxy Watch.

```bash
./gradlew :app:installDebug
```

```bash
./gradlew :wear:installDebug
```

## Phone, on its own
- [ ] Settings → Apps → Beeing → Notifications shows one "Hourly Reminders" channel (plus the silent one), and the reminder plays the Beeing tone.
- [ ] Settings no longer has an "Hourly reminders" switch.
- [ ] Streak alerts arrive at about 2:01am, not 12:01am.
- [ ] Rate yesterday's 11pm hour at 12:30am when yesterday was one short of 8: no "a streak saver kept your streak" alert at 2am.
- [ ] Delete an hour in the journal and tap Undo: the hour is back, and stays back after the watch syncs.

## Setup
- [ ] Before the phone app has synced, the watch app says "Open Beeing on your phone to set up", and no watch reminders arrive.
- [ ] After opening the phone app, the watch shows the chime tip once, then the caught-up screen or the rating screen.
- [ ] With the watch app installed, the Silent hours and Pause sheets on the phone show the line about the watch.

## Rating on the watch
- [ ] The hourly reminder buzzes with a short double tap and the Beeing tone (when the watch isn't muted). The phone's copy is silent while the watch is connected and off the charger.
- [ ] With the watch on its charger, the phone rings for the reminder.
- [ ] With two hours waiting, the notification reads "2 hours waiting", and Rate opens the hour about to expire. Tapping the hour label switches between the two.
- [ ] The crown turns the score one step at a time, with a tick each step. Tapping a dot also sets it.
- [ ] Next opens the tags: your top 8 two to a row, then More, then "Save · N tags". Swiping right goes back to the score.
- [ ] After a save with another hour waiting, "Saved" flashes and the next hour opens.
- [ ] Leave the tags screen open across the hour: "That hour just closed", with your score and tags moved to the newer hour.
- [ ] Saving the hour that closes the ring: one strong haptic, the ring fills once, "Day complete · N-day streak". The phone doesn't celebrate it again later.
- [ ] The 12th Master hour shows the gold "Super day" screen.
- [ ] Rating 11pm at 12:30am that closes yesterday's ring shows "Yesterday's ring closed".

## Tile and complications
- [ ] The Tile shows the ring, flame + streak, shield + savers, and "2pm – 3pm is waiting" + Rate, or "All caught up". It updates right after a watch save, and after a phone save syncs.
- [ ] Each of the three complications fits a Galaxy watch face, renders in ambient mode, and only changes on a save, a sync or the hour.
- [ ] Taps: a complication opens the rating screen when an hour waits, otherwise the caught-up screen.

## Offline and sync
- [ ] Phone in flight mode (Bluetooth off) for two days, rating on the watch: the streak and saver numbers on the watch don't jump when the phone comes back.
- [ ] While they're apart, the sync mark shows next to the streak. It goes away after reconnecting.
- [ ] With Bluetooth off, rate the same hour on both devices: after reconnecting, both show the later one.
- [ ] Add a past hour on the phone for an hour the watch rated live while offline: after sync the live rating wins, and that day's "Add a past hour" allowance comes back.
- [ ] Battery: a day of normal use with the watch app installed costs about what it did before.
