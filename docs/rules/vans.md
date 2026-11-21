# Roadside vans

Rules for the vans list, a van site's detail and the bottom bar's "Roadside vans" tab. The code is in `:domain`
(`VanSites.kt`, `VanSchedule.kt`, `VanDriveTable.kt`) and `:data` (`vans/`); each rule below has a test there.

## Reading AirCare's van page

- `https://aircarecolorado.com/locations/van-locations` (ADR-0001's amendment), then each tab's table,
  `vanlocationstabledisplay_new.php?day=N`, with `N` read from the tab's iframe. Day ids are opaque; never guess
  them. Same user agent as the wait-times page, nothing else sent.
- Four tabs, each an `<h3 class="jl_magic_tabs …">` title: "M-Th (Current Week)", "Friday (Current Week)",
  "M-Th (Next Week)", "Friday (Next Week)". No dates anywhere.
- Each tab's `var latlngs = [...]` gives a pin per site (full text, `lat`, `lng`, `icon`); its table gives City,
  the status image's `alt` and the same text. Pin and row are joined on normalised text: whitespace collapsed,
  trimmed, a trailing dot dropped, case ignored. The row gives city and status; the pin's icon is a cross-check,
  logged when it disagrees.
- A tab with no script (AirCare's "An unexpected error occurred." inside it) is **unavailable**, not an error for
  the page; its days show "not posted yet". The page then answers HTTP 500, which is read like a 200.
- A page with no tab the app can read is **unreadable**: the last good schedule stays, every status shows as
  "Scheduled", and a banner says so. A table that can't be fetched leaves that tab's sites without a status.
- A pin whose text has no days and hours, or with no row in a table that was fetched, is left out and logged.
- The last good schedule is saved to `vans.json` in the app's files (out of backups) and loaded at start.
- Fetched at launch and when the vans tab first opens, then every 15 minutes while it's on screen on weekdays,
  5:30 AM to 7 PM Denver time (`vanWorthFetching`, `VanTabFetches`); the tab coming back doesn't fetch again
  unless 15 minutes are up. The app opening on the vans tab is one fetch, not two. Never from the background.

## Sites

- A site's text ends `(<Mon|Tues|Wed|Thur|Fri> )+ h:mm AM - h:mm PM`; the rest is its description
  (`VanSiteText`). Its hours are from the opening time up to, not including, the closing time.
- **Site key**: coordinates to 5 decimals plus the normalised description (`VanSite.siteKey`), the same in
  `scripts/van_drive_times.py`. Never `locationID`, which changes between fetches for the same site.
- Display name (`VanName`): "ON-RAMP TO EB I-70 FROM HARLAN ST." is "Harlan St → EB I-70"; "X from Y" is
  "Y → X"; "NB TAFT HILL RD. after HARMONY RD." is "NB Taft Hill Rd past Harmony Rd". Road codes (I-70, US-285,
  SH-192, C-470) and directions stay in capitals; "(Exit 184)" goes with the highway; "(two units at site)" is
  the "2 units" tag, not part of the name. The content description says "to" for the arrow.
- **Place** is from the bundled ZIP table by the pin's coordinates (`ZipTable.placeOf`, ADR-0005), not AirCare's
  city, which is sometimes wrong ("Fort Lupton" for a Fort Collins site); AirCare's city only when no ZIP is near.

## The RapidScreen rule as the app words it

- Two clean readings within 14 months, at least **90 days** before the renewal month (July: May 1 last year to
  April 1), as AirCare's van page says. Their RapidScreen info page says 60 days (to May 1); the app keeps the
  stricter 90, decided 2026-11-20, so nobody counts on a reading that comes too late.

## Days

- "Current Week" is the week of today's date in Denver, by the injected `Clock`; on Saturday and Sunday it is
  still the week just ended, until it's known when AirCare rolls the tabs over (#59).
- The day chips: today (on a weekday), then each weekday to the end of next week. On a weekend the list starts
  at next Monday, with "No vans out on weekends".
- A day whose tab is there with no sites on it: "No vans on Thursday, Nov 26". A day whose tab is missing or
  unavailable: "Friday's sites aren't posted yet".

## Status

- Live status only for today, from today's tab, and **its age is the fetch time**: AirCare gives none. Older
  than 30 minutes, it falls back to "Scheduled".
- Today, at or after a site's closing time: "Done for today". Before its opening time: "Starts at 6:35 AM",
  unless the site is already on site.
- `On-site: Testing` is "Reading cars now"; `On-site: Not Testing` "Set up, not reading"; `On-site: Status
  Unknown` "On site, status unknown"; `Not On-site: Currently Scheduled` "Not set up yet"; `Not On-site: Not
  Currently Scheduled` during its hours "Done for today" (weather can close a site early).
- Another day, or no fresh live status: "Scheduled".
- Badges: a rounded square with an icon and a content description, never colour alone: reading (green, radar),
  set up (grey, pause), unknown (light grey, ?), done (outline, clock), scheduled and starts (outline, calendar).

## States

- The header says when the page was fetched, always with the date: "Status checked Nov 20, 5:57 PM" (a schedule
  kept from an earlier day must not read as today's).

- **Live**: a weekday with the page read. **Weekend**: next week from Monday, info banner. **Stale**: the last
  fetch failed and the statuses are over 30 minutes old; the schedule still shows. **Broken**: the page couldn't
  be read; the last good schedule with every status "Scheduled", error banner, "Wait times still work". **Empty**:
  no schedule yet.

## Sorting, the hero and drive times

- Nearest first by drive minutes from the van table (ADR-0008); sites without a time after, by name. **Never by
  straight-line distance**, and no miles shown. "Here" is the wait list's (fix, else ZIP); precise or approximate
  location stays the user's choice.
- Hero, "Nearest van reading cars now": today's reading site with the least drive, with its place, drive and
  closing time. Hidden on another day, when none is reading, or without a drive time.
- Each site's drive time is a pill, as its row's `VanDrive` says (`vanList` decides). **Only while the site is
  out** (today, within its hours, not done for the day) is it `Banded`, in the wait badges' bands, colours and icons
  (`WaitThresholds.STANDARD`): green with a tick under 30 minutes, yellow with a clock 30–60, red with a warning
  over 60, read aloud as "Short drive, 9 minutes". Otherwise it's `Plain`, grey with no icon ("9 minute drive").
- **Out of the way**: when the day's nearest site by drive is over 20 minutes (`nearestFarAway`,
  `VanRules.FAR_MINUTES`), a note says a station test may be quicker. No note without drive times.
- "Reading now" chip: today only, while a site is reading, a filter to reading sites. The bottom bar's badge: how many are reading now.

## Detail

- No map tiles for weekly sites: the header is a sketch, turned to the site's direction of travel
  (`VanName.heading`, from "NB/SB/EB/WB" on the highway or road): the road runs that way, an arrow shows the
  traffic, a ramp joins from the right of it (US), and the unit sits on the ramp, or on the road past the cross
  street. Labels stay upright. Without a direction it's drawn eastbound.
- Status, place, drive time, today's hours and "Status checked" with its date and time. **Directions** opens the system's app picker on
  a `geo:` link to the pin, as for stations.
- "When it's here": every date on the page's tabs the site is posted for, today in bold; "Next week: Not
  scheduled" when next week's tabs are read and it isn't on them.
- Disclaimer: weather, eligibility, unofficial.

## Drive-by log

- The user logs a drive-by from a site's detail: a date (none after today) and a time (none after now), in
  Denver (`driveByAt`). The site's key, description and point are kept with it, so an entry outlives the site.
- Kept in `drive_bys.json` in the app's files. It is the user's own record, so unlike the fetched data it **is**
  in backups and device transfers. A file that can't be read is kept aside as `drive_bys.json.bad`, never lost.
- "N logged in the last 14 months" counts entries on or after the same day 14 months back (`driveByLog`). The
  app never says a vehicle qualifies: it can't see readings or whether they were clean; the renewal postcard's
  "PASSED ROADSIDE EMISSIONS" is the official word.
- The log lists entries newest first, older-than-14-months ones marked, each deletable. A year shows on dates
  outside the current year.

## Vehicles and reminders

The code is in `:domain` (`Vehicles.kt`), `:data` (`vehicles/`) and `:app` (`ui/vehicles/`, `reminders/`). The
design is `design/vehicles/source/` (issue #66).

- **A vehicle** is a description in the user's own words ("2011 Volkswagen GTI") and its registration renewal
  month and year, no day. **No VIN**, and no vehicle lookups of any kind: nothing would use a VIN, and ADR-0001
  keeps the app on AirCare's host. Several vehicles are allowed.
- Kept in `vehicles.json` in the app's files and, like `drive_bys.json`, **in** backups and device transfers. A
  file that can't be read is kept aside as `vehicles.json.bad`.
- **The window** (`RapidScreenWindow.of`): opens on the first of the month 14 months before the renewal month;
  closes on the first of the month three months before it, or earlier if that is under 90 days before it (the
  stricter number, decided 2026-11-20). Both days count. July 2027: May 1, 2026 to April 1, 2027. March 2027:
  January 1 to December 1, 2026. May 2027: Feb 1 is only 89 days before, so it closes Jan 31.
- **The test is due** by the end of the month after the renewal month: the renewal month plus AirCare's month of
  grace. "Renews July 2027 · test due by Aug 31, 2027". "If the vans don't count: Station test in July".
- **Where today falls** (`WindowState`): before (days until it opens), open (days left, 0 on its last day; how far
  through), closed (too late for this renewal: plan a station test), past (the test date has gone; the likely
  next renewal is two years on, as most vehicles test every two years, and the user sets it from the postcard).
  A window with 14 days or fewer left is "closing soon".
- **Counted drive-bys**: a drive-by is a vehicle's when it was logged for it, or, while there is only one
  vehicle, when it has none (logged before vehicles were). Counted only inside the window, in Denver, up to now.
  "1 of 2 drive-bys", then "3 drive-bys" past two. The app still **never says a vehicle qualifies**: only the
  renewal postcard's "PASSED ROADSIDE EMISSIONS" does.
- **Log a drive-by** asks which vehicle drove past only with two or more; the first with an open window is
  picked to start. Each choice says where its window stands and what the drive-by would make the count.
- **Reminders**, local notifications only, worked out on the phone (WorkManager), nothing fetched or sent; each
  is switched on or off on the vehicle's page. All four go out at 9 AM Denver time:
  - when the window opens;
  - halfway through it (the middle day, rounded later), **only** with fewer than two drive-bys logged;
  - 14 days before it closes;
  - on the first of the renewal month, that the test is due by the end of the next month.
- "Remind me" on a new vehicle turns all four on. `POST_NOTIFICATIONS` is asked for only when a reminder is
  turned on; refused, the reminders stay off and the vehicle's page says notifications are off, with a way to
  Android's settings.
- A reminder goes out **once** per renewal. One whose day has already passed when the vehicle is added, its
  renewal changed or the reminder turned on is skipped, not sent late. One that's no use any more (after the
  window closed; after the test date for the test reminder) is dropped. If several come due together (the phone
  was off), only the latest is posted. Changing the renewal starts the reminders afresh.
- Tapping a reminder opens that vehicle's page over the vans tab. No "van posted near you" reminder: it would need
  a background fetch of the van page, which ADR-0001 doesn't allow.

