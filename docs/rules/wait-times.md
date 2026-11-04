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
- Directions: the system's app picker (`Intent.createChooser`, every time) on `geo:<lat>,<lon>?q=<lat>,<lon>(<name>)`
  (`Station.mapUri`), so the user picks the map app (CoMaps, Google Maps, OsmAnd…).
- **The detail's map is a bundled image** per station, light and dark, drawn from OpenStreetMap data by
  `scripts/station_maps.py` and centred on the station's coordinates (ADR-0006). No map network calls. The app
  draws the pin at the centre in the badge's colour; the map's content description names the station, and the
  wait is in the text below, so colour is not the only signal. "© OpenStreetMap contributors" shows on the map,
  linked to openstreetmap.org/copyright (ODbL; `assets/licences/`). A station added or moved in `Stations` needs
  the script run again; a test fails while any station has no map.

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
- Why a fetch happens decides whether it does (`FetchReason`, `worthFetching`): **opening the list at app start
  always fetches**, open or closed, fresh or not, once per list (the screen coming back doesn't count); a tap on
  refresh fetches whenever the stations are open; anything else only when `shouldFetch` says so.
- Wait wording seen: `Wait is 13 mins` (also `0 mins`, `1 min`), `Station Open` (no estimate),
  `The wait is more than 60 minutes. …`, and `Station Closed` (every station after hours; a fixture of that
  page is saved too). A closed station has no reading and keeps its last good one; a page with every station
  closed still reads. Any other wording reads as nothing, and the page counts as unreadable.
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
- Apart from the launch fetch and a tap on refresh, fetch only while open, and only when the newest data is at
  least 15 minutes old (`shouldFetch`). Outside hours the launch fetch is saved, and the list still shows closed.

## Background refresh

- With the app closed, WorkManager checks every 15 minutes (its shortest period) while the stations are open,
  and only with a network. Each check goes through `shouldFetch`, never as a manual refresh, so it fetches only
  when the newest data is at least 15 minutes old: never more often than AirCare updates, never outside hours.
- No wakes while closed (`backgroundPlan`): once a check 15 minutes on would find the stations closed (from
  17:15 on a weekday, 12:45 on a Saturday), the checks stop and one wake is set for `AirCareHours.nextOpening`,
  which starts them again; it fetches nothing itself. Evenings, Sundays and holidays cost one wake, at opening.
- The plan is set again at every app start and after every check, so it survives reboots, updates and a
  cancelled job. Android may run a check late (Doze, battery saver); the waits then just age as usual.
- A background fetch is saved like any other (`waits.json`). Every check redraws the widget, fetched or not.

## Sorting and the hero

- Distance is straight-line, for sorting by nearest. Drive minutes come from the bundled routed table
  (`DriveTable`, ADR-0007): no traffic, shown without "≈" ("12 min drive"). Never from straight-line distance.
- No drive time where the table has none (outside the gas emissions program area, far from a road): miles
  only.
- Nearest: by distance. Fastest overall: drive + wait, any wait counted as at most 90 ("60+" counts as 90);
  stations without an estimate after the rest, by distance; ties go to the nearer. While closed, nearest.
- No location: alphabetical, whatever the chip.
- Low-profile rims chip: a filter, on top of either order.
- Both chips are remembered across launches.
- The location prompt shows on launch only until it's answered, either way, or location is allowed. Location
  revoked later in system settings: the list, alphabetical, with "Location off", which opens the prompt.
- Hero ("Fastest overall right now"): least drive + wait among the shown stations with a fresh estimate. Hidden
  while closed, stale, offline or broken, without a location, or when no station has an estimate.

## Where "here" is

- "Use my location" asks for precise location, with approximate in the same request, so Android's dialog offers
  both. Either is allowed location; approximate is off by a mile or two, which shows in the miles and drive
  estimates. Someone who gave approximate switches in system settings; the app doesn't ask again.
- **Location permission wins:** with location allowed and a fix found, distances are from the fix. Otherwise
  from the saved ZIP code's centroid, if there is one. Otherwise there's no "here" and the list is
  alphabetical (`here`). A ZIP saved before location was allowed stays saved, unused, until location goes.
- ZIP codes come from the bundled table (ADR-0005): every Colorado ZIP with a Census area, 800xx–816xx. A typed
  ZIP is five digits and in the table; otherwise it's refused, "Enter a 5-digit ZIP code" or "… isn't a
  Colorado ZIP code". The field takes digits only, at most five, on a number keyboard.
- **Go** saves the ZIP, answers the prompt and opens the list. The prompt opened again shows the saved ZIP and
  offers to stop using it, which clears it.
- The header names where "here" is, as "Arvada, CO 80003": for a ZIP, that ZIP's place; for a fix, the ZIP whose
  outline it's in (`ZipTable.placeOf`; of two where simplified borders overlap, the nearer centroid's), else the
  nearest ZIP centroid, or "Near you" when none is within 25 miles. Allowed but no fix and no ZIP: "Finding
  your location…". Neither: "Location off". A ZIP and "Location off" are links to the prompt.
- Drive times from a ZIP are looked up from its centroid, like a fix.

## The widget

- The three nearest stations (`widgetContent`, `WIDGET_STATIONS`), nearest first by straight-line distance, each
  with its badge, name, miles and a status word. Same badges and rules as the list (`StationRow.badge`):
  closed outside hours, stale on each station's own timestamp, all stale when the page can't be read, "no
  reading" before the first fetch. Every badge has its icon and the list's content description.
- "Here" is the list's ("Where "here" is"; `HereSource.current`): the fix, else the saved ZIP code.
- **Without either it shows a line asking to open the app and allow location or enter a ZIP code, not the first
  three alphabetically**, which would read as the nearest three.
- The fix is the last one the app found, kept on disk (`LastLocationStore`, `location.txt`, out of backups):
  Android gives an app without background location access no location while the widget is drawn in the
  background. It is forgotten once location is no longer allowed; the widget then uses the ZIP, or asks.
- The header shows "Updated" and the newest AirCare time of all stations (weekday in front when not today),
  not "N min ago" as the design draws it: the widget is only redrawn now and then, and a minute count would go
  wrong between redraws. A clock time stays right.
- Redrawn after every background check, at the stations' opening (`OpeningWorker`), whenever the app's waits
  or location change, and on its own (`widgetRedrawAt`, no network needed) at the moment its first fresh badge
  goes stale or at closing, whichever is first. No redraws while closed beyond the opening one.
- Colours from `waitColors(dark)`, following the phone's light or dark; the system font; 28dp corners.
- A tap on a station opens its detail over the list; anywhere else opens the list.
