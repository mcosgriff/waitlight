# Glossary

- **Station** — one of AirCare Colorado's 18 emissions-test sites, known by AirCare's `locationID`.
- **Reading** — a station's wait as AirCare words it: minutes, "more than N minutes", or "Station Open" (no
  estimate).
- **Last updated** — AirCare's own time for a station's reading. Freshness is measured from it, never from
  when the app fetched.
- **Band** — short, moderate or long, from a reading and the thresholds (`greenMax`, `yellowMax`).
- **Badge** — what a station shows: a band with its figure, open (no estimate), closed, or stale.
- **Fresh / stale** — a reading is fresh up to 30 minutes after its last updated time, stale after.
- **Due** — the newest data is 15 minutes old or more, so AirCare probably has newer.
- **Fastest overall** — least routed drive plus wait (wait capped at 90 minutes).
- **Hero** — the "Fastest overall right now" card at the top of the list.
- **Low-profile rims** — 17″+ rims or wheels over 34″; only four stations can test them.
- **RapidScreen** — AirCare's roadside emissions readers: units in white vans or small green boxes beside
  on-ramps that read passing cars' exhaust. Two clean readings can let a vehicle skip its next test. "Van" for short.
- **Van site** — a place a RapidScreen unit is posted for, on certain weekdays and hours; known by its site key
  (rounded coordinates and normalised description), never AirCare's `locationID`.
- **Catalog** — every van site ever seen on AirCare's van page (`scripts/van_sites.json`); it only grows, and the
  van drive-time table covers it (ADR-0008).
- **Renewal month** — the month a vehicle's registration renews, from the county's postcard; the app keeps the
  month and year only.
- **Window** — the RapidScreen window for a renewal: from 14 months before the renewal month until at least 90
  days before it. Two clean readings inside it can skip that renewal's test.
- **Reminder** — a local notification about a vehicle's window or test date; worked out and posted on the phone.
