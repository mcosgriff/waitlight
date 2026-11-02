# Waitlight — design handoff

Android app that shows AirCare Colorado emissions-test wait times, nearest station first, with green / yellow / red wait badges.

## What's in this folder

| Folder | Contents |
|---|---|
| `png/` | 3× renders of every screen (1170 px wide). **Start here.** |
| `html/` | Static HTML of each screen (rendered, no interactivity). Open in a browser; inspect for exact spacing, sizes and colors. |
| `source/` | The editable design source (`*.dc.html` + `canvas.json`). `Main.dc.html` holds the list screen's full logic (sorting, thresholds, states, both themes) in its `<script>` block — read it as a spec. |

Screens (`png/`): `Main`, `Detail`, `Permission`, `Widget`, their `*Dark` versions, and five list-screen states (`StateDue`, `StateClosed`, `StateStale`, `StateOffline`, `StateBroken`, each also `-dark`).

## Stack (suggested)

Kotlin + Jetpack Compose, Material 3. Jsoup for parsing, WorkManager for periodic refresh, FusedLocationProvider for location, Glance for the home-screen widget. Font: Roboto Flex.

## Data source

- Page: `https://aircarecolorado.com/locations/wait-times/wait-times-interactive` (server-rendered HTML, no API).
- Per station: name, address, wait text, phone, lanes, hours, "Last updated" timestamp, Google Maps directions link.
- Wait text variants seen: `Wait is 13 mins`, `Station Open` (no estimate), `The wait is more than 60 minutes. THIS TIME DOES NOT INCLUDE TIME IN LINE OUTSIDE OF THE TICKET GATE.`
- AirCare refreshes roughly every 15 minutes. Use each station's **AirCare "Last updated" timestamp** for freshness, not the app's fetch time.
- Hours are identical for all stations: **Mon–Fri 8:00–17:30, Sat 8:00–13:00, closed Sunday and Colorado state holidays** (America/Denver). Hard-code; compare against the parsed text and log if it changes.
- No diesel testing anywhere. Low-profile rims (17″+) / wheels over 34″: only Sheridan, Boulder, Ken Caryl, Fort Collins.
- Phone (all stations): 303-456-7090.
- Directions link format: `https://www.google.com/maps/dir/Current+Location/<url-encoded address>`.
- Preferably fetch via a small server/cache you control and have the app read that, so every phone isn't hitting AirCare and parsing fixes ship in one place. Don't fetch outside open hours.

Station list (18): Arvada, Aurora, Boulder, Broomfield, Castle Rock, County Line, Dacono, Denver (Lipan), Denver SE (Warren), Fort Collins, Golden, Greeley, Ken Caryl, Longmont, Loveland, Northglenn, Parker, Sheridan. Addresses are in `source/Main.dc.html`. The page has no coordinates — geocode once and ship lat/lng in the app.

Distances and drive times in the mockups are rough estimates from Arvada 80003; compute real ones (straight-line distance for sorting, Directions/Routes API for drive minutes if used).

## Wait badges

Thresholds are inclusive at the low end and configurable: `greenMax = 30`, `yellowMax = 60`.

| Wait | Meaning | Light bg / text | Dark bg / text | Icon (Material Symbols, Compose `Icons.Rounded.*`) |
|---|---|---|---|---|
| < 30 min | Short | `#1B7F3B` / `#FFFFFF` (icon `#1B7F3B`) | `#6FCF8A` / `#00391A` | `check_circle` / `CheckCircle` |
| 30–60 min | Moderate | `#F2B807` / `#2E2300` (icon `#8A5F00`) | `#F2C94C` / `#2E2300` | `schedule` / `Schedule` |
| > 60 min | Long — badge shows "60+" | `#C62828` / `#FFFFFF` | `#FF8A80` / `#4A0000` | `warning` / `Warning` |
| "Station Open", no estimate | Open | `#6B7570` / `#FFFFFF` | `#8C9792` / `#101513` | `remove_circle` / `RemoveCircle` |
| Closed (outside hours) | Closed | `#6B7570` / `#FFFFFF` | `#8C9792` / `#101513` | `bedtime` / `Bedtime` |
| Stale data | Last known number, greyed | `#E3E6E1` / `#3E4843` | `#2A3430` / `#C4CEC9` | `history` / `History` |

Badge: 52 dp circle, minutes 18 sp bold over "min" 10 sp; status icon in a 22 dp circle (surface color) overlapping bottom-right. Every badge needs a content description, e.g. "Short wait, 13 minutes" / "Last known short wait, 13 minutes as of 2:14 PM, may be out of date". Color is never the only signal.

## Theme tokens

| Token | Light | Dark |
|---|---|---|
| background | `#F6F7F4` | `#0F1513` |
| surface (cards, rows) | `#FFFFFF` | `#1A2320` |
| onSurface | `#17201B` | `#E4EBE7` |
| muted text | `#4A554F` / `#56615B` | `#A6B2AC` |
| divider | `#E3E6E1` | `#26302C` |
| primary (buttons, selected chips) | `#1F4E5F` / on `#FFFFFF` | `#8CC7D6` / on `#0B2730` |
| hero card | `#1F4E5F` | `#16343E` |
| chip unselected border | `#C9CFCB` | `#3E4B46` |
| tag (low-profile OK) | `#E2EDF0` / `#1F4E5F` | `#1F3A43` / `#A9D6E2` |
| warning banner | `#FFF1C7` / `#4A3A00` | `#2A2410` / `#F2E3AE` |
| info banner | `#E3E6E1` / `#17201B` | `#222D29` / `#E4EBE7` |
| error banner | `#FBE3E1` / `#7A1A14` | `#3A1714` / `#FFC9C3` |

Radii: rows 16 dp, hero card 20 dp, chips/buttons fully rounded, widget 28 dp. Min touch target 44–48 dp.

## Behavior

**List (Main)**
- Default sort: nearest. Chips: `Nearest`, `Fastest overall` (drive + wait), `Low-profile rims` (filter toggle).
- Hero card "Fastest overall right now": station with the lowest drive minutes + wait minutes (cap wait at 90 for "60+"); hidden when closed or data is stale.
- Row: badge, name, address (ellipsized), optional "Low-profile rims OK" tag; right side distance + "N min drive" (or "Opens 8 AM" when closed). Tap → Detail.

**Refresh button**
- Disabled (greyed) while the newest AirCare timestamp is < 15 min old; subtitle "Next update in ~N min".
- ≥ 15 min old: enabled, filled primary, subtitle "New data is ready — tap refresh" (`StateDue`).
- Disabled while all stations are closed.
- Enabled in stale/offline/broken states (acts as retry).

**States** (see `png/State*`)
- `closed`: outside hours → all badges Closed; hero replaced with "All stations are closed · All stations open at 8 AM tomorrow" (compute the next opening day: after Sat 1 PM / Sunday / before a holiday → next business day).
- `stale`: fetch failed and data > ~30 min old → amber "Couldn't refresh" banner with Try again; greyed badges.
- `offline`: no network → neutral "You're offline" banner with Try again; greyed badges.
- `broken`: page fetched but parsing failed → red "Wait times unavailable" banner, last good data shown greyed, no retry button.

**Detail**
- Map (Maps SDK, dark style in dark theme) with pin colored by status; back button.
- Big badge, name, status word, "≈ N min total · N min drive (N mi)", "AirCare updated h:mm".
- Buttons: Directions (Google Maps link above), Call (tel:3034567090).
- Info card: Address, Hours, Lanes, Low-profile rims.
- Tip card: "Mid-to-late afternoons are quietest. Avoid Mondays, the day after a holiday and the last few days of the month."
- Footer disclaimer: estimate only, excludes time outside the ticket gate, no diesel testing.

**Location off (Permission)**
- "Use my location" (requests permission) or ZIP entry; without either, list all stations alphabetically.

**Widget**
- Nearest 3 stations with badge, name, distance, status word; "N min ago". Follows system dark mode.

## Out of scope for v1

Accounts, history charts, full map view. Optional later: favorite station (pins to top + widget), opt-in "notify me when wait drops below N", link to AirCare's "Need to Know" page.
