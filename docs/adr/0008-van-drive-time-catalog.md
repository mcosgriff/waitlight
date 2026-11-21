# Van drive times come from a growing catalog of routed sites

The vans list shows drive minutes to each roadside van site looked up in a table bundled with the app, like the
stations' (ADR-0007): minutes from every cell of the same 250 m grid over the gas emissions program area to each
known site, routed once at development time with OSRM's car profile on OpenStreetMap's roads
(`data/src/main/assets/van_drive_minutes.bin`, made by `scripts/van_drive_times.py`, read by `VanDriveTable`).
Decided 2026-11-20.

Van sites change every week, so the table can't be made once for a fixed list. It covers a **catalog** of every
site seen on AirCare's van page (`scripts/van_sites.json`), keyed by the site's rounded coordinates and
normalised description, never AirCare's `locationID`, which changes between fetches. The catalog only grows.

## Why

Matthew wants road-network times, not straight-line: the reasons ADR-0007 dropped the straight-line estimate
hold for vans too, and more so, since ramp sites sit on highways that a straight line crosses anywhere.

## Considered Options

- **Straight-line distance or an estimate from it.** Turned down, as in ADR-0007.
- **Routing on the phone** (GraphHopper offline): any site the moment it's posted, but tens of MB of road graph
  and a large library to check (ADR-0002).
- **An online routing service**: an API key, a second host (ADR-0001) and the user's location sent off the phone.

## How the table is made

`uv run scripts/van_drive_times.py --page <van page file or URL>` adds the page's sites to the catalog and routes
the ones the table hasn't got, using `scripts/drive_times.py`'s grid, area, OSRM image and cached Colorado
extract (Docker needed). The layout is in the script's docstring: one block per site with its key, so the app
reads only the sites on the days it shows.

- **Append-only.** A run never changes a known site's block: old blocks are copied byte for byte and new ones
  added at the end (`scripts/test_van_drive_times.py` checks it). So newer roads in a later extract can't move
  times the app already shows. `--refresh-roads` routes every site again, by hand, after a newer extract.
- A daily GitHub Action that adds new sites by pull request is planned (#57). Until then, new sites are added by
  running the script.

## Consequences

- A site that isn't in the catalog yet shows no drive time, and sorts after the timed ones, never by distance.
- Size: about 214 KB raw and 83 KB compressed per site. The first 53 sites are 11.3 MB raw, about 4.4 MB more in
  the APK. If the catalog grows past what's reasonable, a 500 m grid for vans (about a quarter of the size, ±1 min)
  is the fallback, with a new ADR.
- Directions open the system's app picker on a `geo:` link to the site's pin, as for stations. A ramp pin may
  snap to the highway's mainline in some map apps; if a drive check shows it, the catalog gets a navigation
  point on the cross street for that site.
- Derived from OpenStreetMap data (ODbL), credited with the station maps (ADR-0006).
