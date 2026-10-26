# Beeing reskin: "Night bloom" + Plus Jakarta Sans

Date: 2026-10-27 · Status: approved by the owner in chat (direction B, Jakarta font)

## Intent

The app currently looks like stock Android: Material You's wallpaper-derived
colours, flat grey cards, and two rounded "kids' app" display fonts (Baloo 2,
Fredoka). The owner wants it to look **clean, modern and tastefully
designed**, with Swiggy's home screen as the reference: one bold brand colour
used as a band at the top, a calm content page below, one sparing accent,
soft white cards, a real custom typeface with strong size/weight contrast,
and Swiggy's bottom nav bar style. **Uniform across every tab, sheet and
dialog.**

## Goals

1. Beeing has its **own fixed palette** (no dynamic colour) in a light and a
   dark variant; the app follows the phone's dark-mode setting.
2. **Plus Jakarta Sans** everywhere, bundled in the APK (works offline).
3. Every tab opens with a **brand band**: aubergine gradient, rounded bottom
   corners (~26dp), white text, holding that tab's header content.
4. Content sits on a calm page in **cards** that are white (light) /
   soft charcoal (dark) with a gentle shadow in light mode only.
5. **One accent, honey gold**, for the primary action and "selected" states.
6. Swiggy-style **bottom nav**.
7. Sheets, dialogs, chips, buttons and text fields all read as the same app.

## Non-goals

- No layout or behaviour changes. The journal, Trends, dial geometry, the
  bee, pinned Save/Lock buttons and tags-only scrolling all stay as they are,
  apart from moving header content into the band.
- No illustrations or imagery (owner: "skip the illustrations").
- The notification's RemoteViews layout is out of scope (system-rendered).

## Anti-goals (failure even if it "works")

- Any screen still showing Material You wallpaper colours, the old blue
  primary, or Baloo/Fredoka.
- The Save (rating state) or Lock (caught-up state) button getting pushed off
  screen or covered at any height. The tags-only scroll region must keep
  absorbing shortfall. The owner called this out specifically.
- Score colours losing meaning: 1-4 red, 5-7 amber, 8-10 green stays the data
  language, and colour still never travels alone (digits stay).
- Honey gold used as decoration everywhere, so it stops meaning "act here".

## Decisions (the owner's)

- Direction **B, "Night bloom"**: aubergine band (light `#4A1D4F → #2E0F33`,
  dark `#2C1030 → #1C0920`, ~160° gradient), honey-gold accent `#F5A623`.
- Font **Plus Jakarta Sans** (not Outfit, which B originally paired with).
- Follow the system dark mode setting.
- On the Now tab, **"How was your hour?" and the hour picker + countdown move
  into the band** (so the rating card is just dial, tags and note, and loses
  its title row to pay for the band's height).
- Nav bar like Swiggy's: plain bar, outline icons, active tab = filled icon +
  accent-coloured bold label, **no pill**.

## Palette (Claude's call within the owner's direction)

| Role | Light | Dark |
|---|---|---|
| Page (`background`) | `#F7F5F2` warm off-white | `#110E12` plum-tinted near-black |
| Card (`surface`) | `#FFFFFF` | `#1D1A1F` |
| Muted fill (`surfaceVariant`: chips, dial cells, tracks) | `#F1EEEA` | `#2A2630` |
| Primary text (`onSurface`/`onBackground`) | `#1B1A1C` | `#ECE7EA` |
| Secondary text (`onSurfaceVariant`) | `#76717A` | `#9C969B` |
| Hairlines (`outlineVariant`) | `#EEE9E4` | `#2A2630` |
| Accent (`primary`) | `#F5A623` | `#F5A623` |
| On accent (`onPrimary`) | `#2E0F33` (aubergine ink, not white: white on honey fails contrast) | same |
| Accent as text/icon on page (active nav label etc.) | `#C7820A` (darker, readable on white) | `#F7B733` |
| Band | gradient above | gradient above |
| Error | M3 default red, tuned to sit with the palette | same |

`HoneyGold` (streak identity) and the score bands keep their roles. Score band
hues may be nudged for contrast on white cards, with digits always present.
`AccentOrange`/`ChipNeutral*` fold into the scheme (accent / surfaceVariant).

## Typography

Plus Jakarta Sans bundled as static weights 400/500/600/700/800 under
`res/font/`. The M3 `Typography` is fully defined on it, so plain `Text()`
inherits it. Hierarchy: titles ExtraBold with slight negative tracking, labels
SemiBold, body Regular, secondary text in `onSurfaceVariant`. Every explicit
`BalooFontFamily`/`FredokaFontFamily` use is replaced, and those families and
their font files are deleted.

## Components

- **`BrandBand`** (new, shared): full-bleed gradient, bottom-rounded, draws
  under the status bar (the status bar icons go light on it), with a slot for
  each tab's header. On Now it holds the menu, the "Beeing" title, the streak
  pill, and either the question + hour picker + countdown (rating state) or
  "All caught up" + countdown (caught-up state). On Streak it holds the streak
  hero; on Past, the journal header (month · date jump · "Trends ›"), and in
  Trends the period controls. Exact contents per tab are left to the builder,
  keeping each tab's current information.
- **Cards**: one shared style (`surface` fill, 20dp radius, light-mode-only
  soft shadow) replaces the `surfaceVariant@0.4` fills at every site.
- **Nav bar**: as decided above, on `surface` with a hairline top edge.
- **Chips**: unselected = `surfaceVariant` fill, no outline; selected =
  accent fill + `onPrimary` text. Applies to tag pickers, the sort toggle and
  the Zoom picker.
- **Buttons**: primary = accent fill / `onPrimary` text, 14-16dp radius.
  The Lock button stays a strong neutral (inverse surface), so honey stays
  reserved for "commit".
- **Sheets/dialogs**: `surface` background, 28dp top corners for sheets,
  Jakarta titles, accent text buttons.

## Constraints

- Keep `CLAUDE.md`'s design rules: one job per screen, colour never alone,
  ceremony budgeted by rarity, the active window and so on. Update its
  "Design system" section (colour roles: blue/primary → honey accent;
  card fill) and the file map rows that mention fonts or fills.
- Performance: a gradient band and shadows are cheap; no blur/RenderEffect
  (the history in `CLAUDE.md` explains why blur behind the dial stutters).
- Edge-to-edge: the band must sit correctly under the status bar on all
  three tabs, with no flash when paging between them.

## Left to the builder

Exact per-tab band contents and heights; how the scheme maps onto every less
common M3 role; chip, button and text-field details within the rules above;
the font download source; and tuning score-band hues for contrast. Verify
on-device in both light and dark mode, on all three tabs plus Trends, the
edit sheet, the manage-tags dialog, the menu/settings dialogs and the
celebration.
