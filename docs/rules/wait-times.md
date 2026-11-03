# Wait times

Rules for the station list, a station's detail and the widget. The code is in `:domain`; each rule below has a
test there.

## Stations

- The 18 stations are bundled (`Stations`), as AirCare's page listed them on 2026-11-02: AirCare's own
  `locationID`, name, address, phone, lanes and the page's own coordinates. A fetched wait is matched to its
  station by `locationID`, never by name.
- Phones differ: Fort Collins, Greeley and Loveland are **970-247-8378**; the rest 303-456-7090. (The design
  handoff says 303-456-7090 for all; the page says otherwise, and the page wins.)
- Low-profile rims (17″+) and wheels over 34″: only Sheridan, Boulder, Ken Caryl and Fort Collins. No diesel
  testing anywhere.
- Directions: `https://www.google.com/maps/dir/Current+Location/<url-encoded address>`.

## Hours and holidays

- Mon–Fri 8:00–17:30, Sat 8:00–13:00, closed Sunday and Colorado state holidays, in America/Denver. Open from
  opening time up to, not including, closing time.
- State holidays (C.R.S. 24-11-101, checked against the state's 2026 schedule): New Year's Day, MLK Day,
  Presidents' Day, Memorial Day, Juneteenth, Independence Day, Labor Day, Frances Xavier Cabrini Day, Veterans
  Day, Thanksgiving, Christmas. César Chávez Day is not one.
- Closed on the day the state observes a holiday (Saturday → Friday, Sunday → Monday) **and, when it falls on a
  Saturday, on the Saturday too**. This last part is our reading of AirCare's "closed state holidays", not
  something AirCare states; correct it if a Saturday holiday shows them open.
- Not modelled: AirCare's weather closures (below 15 °F). A fetch on such a day will find stale data, which the
  stale state already covers.
- "All stations open at …" is the next opening after now (`AirCareHours.nextOpening`), worded by
  `OpeningDay`: later today, tomorrow, or a later day.

## Reading AirCare

- One GET of `https://aircarecolorado.com/locations/wait-times/wait-times-interactive`, user agent
  `Waitlight (+https://github.com/mcosgriff/waitlight)`, nothing else sent. The waits come from the page's
  `var latlngs = [...]` script array: each object's `locationID`, `name` (station name and wait wording) and
  `infoWindow` (HTML with the stated hours and "Last updated"). The test fixture is that array as served on
  2026-11-02 (`data/src/test/resources/aircare/`).
- **All or nothing:** a page without the array, or with any station whose wording or time can't be read, is
  unreadable as a whole. A station whose `locationID` isn't in `Stations` is left out.
- A failed fetch or an unreadable page keeps each station's last good reading; an unreadable one marks it
  unusable, so every badge shows it as stale. No network is told from a failed fetch by a failed DNS lookup.
- **The waits survive a restart.** Each change (the waits with each station's "Last updated", and the last
  fetch's outcome) is saved to `waits.json` in the app's files, left out of backups and device transfers
  (`backup_rules.xml`, `data_extraction_rules.xml`), and loaded off the main thread when the repository
  starts; a fetch waits for the load. Loaded waits age by AirCare's own times like any others, so yesterday's
  show as stale or closed with no special case. A stored reading for a station no longer in `Stations`, or one
  that can't be a wait, is dropped; a file that can't be read is logged and the app starts empty, never crashes.
- Stated hours that differ from `AirCareHours.STATED_HOURS` are logged, and the waits still read.
- A tap on refresh fetches whenever the stations are open; otherwise only when `shouldFetch` says so.
- Wait wording seen: `Wait is 13 mins` (also `0 mins`, `1 min`), `Station Open` (no estimate),
  `The wait is more than 60 minutes. …`. Any other wording reads as nothing, and the page counts as
  unreadable.
- "Last updated" is Denver's local time, in two formats seen on the same page: `11/2/2026 4:14:15 PM` and
  `11/02/2026 04:16 PM`.

## Badges

- Thresholds `greenMax = 30`, `yellowMax = 60`, inclusive at the low end: under 30 short, 30–60 moderate, over
  60 long. "More than N" counts as N + 1. A long wait shows as "60+" (`yellowMax+`).
- Kinds: short, moderate, long, open (no estimate), closed (outside hours, whatever the reading), stale (the
  last reading, greyed, with its time).
- **Stale goes by each station's own timestamp**: older than 30 minutes, that station's badge is greyed even
  when the rest are fresh (Golden was an hour behind the others on 2026-11-02). Exactly 30 minutes is fresh.
- When the page can't be read, every badge shows its last good reading as stale.
- Every station is listed, with or without a reading. One without (before the first fetch) shows "no reading"
  while open, and closed outside hours like the rest.

## Freshness, states and refresh

- The data's age is its newest "Last updated", never the fetch time. AirCare updates about every 15 minutes.
- States, in order of precedence: **closed** (outside hours) → **broken** (page unreadable) → **stale** /
  **offline** (fetch failed or no network, and the newest data is over 30 minutes old) → **due** (newest data 15
  minutes old or more) → **live**. A failed fetch while the data is still fresh changes nothing on screen.
- Refresh button: disabled while live, with "Next update in ~N min" rounded up, never 0 and never over 15 (a
  phone clock behind AirCare's puts the newest update in the future; that reads as live); enabled and filled
  when due; enabled as a retry when stale, offline or broken; disabled while closed.
- Fetch only while open, and only when the newest data is at least 15 minutes old (`shouldFetch`).

## Sorting and the hero

- Distance is straight-line, for sorting. Drive minutes are an estimate from it (`DriveEstimate`: 5 minutes plus
  a minute a mile), always shown as "≈".
- Nearest: by distance. Fastest overall: drive + wait, any wait counted as at most 90 ("60+" counts as 90);
  stations without an estimate after the rest, by distance; ties go to the nearer. While closed, nearest.
- No location: alphabetical, whatever the chip.
- Low-profile rims chip: a filter, on top of either order.
- Both chips are remembered across launches.
- The location prompt shows on launch only until it's answered, either way, or location is allowed. Location
  revoked later in system settings: the list, alphabetical, with "Location off", which opens the prompt.
- Hero ("Fastest overall right now"): least drive + wait among the shown stations with a fresh estimate. Hidden
  while closed, stale, offline or broken, without a location, or when no station has an estimate.

## Open questions

- **ZIP entry** needs a ZIP's coordinates without a geocoding service: a bundled table of Front Range ZIP
  centroids is the likely answer.
- **Detail's map** can't use the Maps SDK (ADR-0002). Options: no map, a bundled static image per station, or
  map tiles from another host (which would be a second network host and needs its own decision).
