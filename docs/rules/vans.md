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
- Fetched at launch and when the vans tab opens, then every 15 minutes while it's on screen on weekdays,
  5:30 AM to 7 PM Denver time (`vanWorthFetching`); never from the background.

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
- "Reading now" chip: today only, while a site is reading, a filter to reading sites. The bottom bar's badge: how many are reading now.

## Detail

- No map tiles for weekly sites: the header is drawn, with the site's name and status.
- Status, place, drive time, today's hours and "Status checked" with its date and time. **Directions** opens the system's app picker on
  a `geo:` link to the pin, as for stations.
- "When it's here": every date on the page's tabs the site is posted for, today in bold; "Next week: Not
  scheduled" when next week's tabs are read and it isn't on them.
- Disclaimer: weather, eligibility, unofficial.
