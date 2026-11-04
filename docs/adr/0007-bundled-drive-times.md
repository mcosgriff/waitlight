# Drive times come from a bundled table routed on OpenStreetMap's roads

The list, the hero and the detail show drive minutes looked up in a table bundled with the app: the minutes from
every cell of a grid over the gas emissions program area (about 250 m square) to each station, routed once at development time
with OSRM's car profile on OpenStreetMap's roads (`data/src/main/assets/drive_minutes.bin`, made by
`scripts/drive_times.py`, read by `DriveTable`). There is no traffic in it. Where the table has no time, outside
the program area or far from a road, no drive time is shown. Decided 2026-11-03, replacing `DriveEstimate`.

## Why

`DriveEstimate` (5 minutes plus a minute a straight-line mile) was off by 10 minutes and more wherever the roads
don't run the way the crow flies (Golden to Aurora, Ken Caryl to the I-25 stations, Dacono to Greeley). That
error is big enough to change which station is "Fastest overall", so it isn't kept even as a fallback.

## Considered Options

- **Keeping the straight-line estimate as a fallback** outside the grid. Turned down: a wrong time is worse than
  none when it picks the hero; outside the program area no gas vehicle needs a test anyway.
- **An online routing service** (OpenRouteService, Stadia/Valhalla, TomTom, HERE; Google's Routes API is out
  under ADR-0002). The only way to get live traffic. Turned down: an API key in the APK, a second network host
  (ADR-0001), and the user's location sent off the phone on every refresh. Times without traffic are good enough.
- **Routing on the phone** (GraphHopper offline, or CoMaps' / Organic Maps' engine, which have no library for
  other apps). Exact from any point, but tens of MB of road graph, a native or large library to check
  (ADR-0002), and CPU on every refresh, for 18 fixed destinations.
- **A better-fitted straight-line model.** Smaller, but still wrong where the roads bend.

## How the table is made

`uv run scripts/drive_times.py` needs Docker. It downloads Geofabrik's Colorado extract into
`build/drive-times/`, prepares it with the `osrm-backend` image's car profile, runs OSRM on localhost, and asks
its table service for every cell in the program area to every station. The grid, the margin, the snap limit and
the file layout are in the script's docstring. Run it again when a station is added or moves, and now and then
for newer roads (`--refetch`).

The program area is `scripts/gas_emissions_area.geojson`: the "Gas Emissions Area Boundaries" folder of
AirCare's "Combined Emissions Maps" KML (county, Kiowa Creek and Douglas/Arapahoe border lines), joined end to
end into one outline. Cells within about 1.5 km of it count, so a spot just inside isn't lost to a cell centre
just outside. With a newer KML, `uv run scripts/drive_times.py --kml <file>` writes the outline again.

721 × 501 cells of about 250 m, 213,652 in the area, 195,695 of them with a route, × 18 stations. Each row keeps
only the stretch that has times: 3.7 MB, about 0.9 MB compressed in the APK, held in memory once read. A cell
is small enough that the nearest one's centre is at most about 180 m away, on the same streets. (The first
cut, 1 km cells over the whole rectangle, was 343 KB.)

## Consequences

- No network for drive times; the phone's location stays on the phone (the lookup is a grid cell, on the phone).
- A drive time is shown without "≈" ("12 min drive"); totals that add AirCare's wait keep it.
- OSRM's car profile drives at 80% of the posted limit and knows no traffic, so highway times run a little long
  at midday and short at rush hour.
- No location, or a point the table has no time for: miles only, and the station sorts after the rest in
  "Fastest overall" and is never the hero.
- The table is derived from OpenStreetMap data (ODbL), credited with the station maps (ADR-0006).
- Directions open the system's app picker on a `geo:` link to the station (CoMaps, Google Maps, OsmAnd…),
  rather than a Google Maps link.
