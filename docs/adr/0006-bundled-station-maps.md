# The station maps are bundled images drawn from OpenStreetMap data

The detail screen's map is one image per station, in light and dark, drawn once at development time from
OpenStreetMap data and bundled in the APK. The app draws the pin itself, at the image's centre, in the badge's
colour, and makes no map network calls. Decided 2026-11-02 (issue #16).

## Considered Options

- **No map.** Directions already opens Google Maps, and it was what shipped first. Turned down: the design's
  detail screen leads with the map, and a glance at the streets around a station helps place it.
- **Map tiles from another host** (OSM tiles through osmdroid or MapLibre). A live, pannable map. Turned down:
  a second network host, which ADR-0001 keeps to one, the phone's location area sent to a tile server on every
  view, and a library to check for Google pieces and network code (ADR-0002), all for a map nobody pans.
- **OSM's standard raster tiles, stitched into the images.** Turned down: their labels are sized for one tile
  pixel per screen pixel, so they would be blurry or tiny on a phone, they bring a second licence (the tiles'
  CC BY-SA style) and a dark version would be a recolour. Drawing the data ourselves gives the design's flat,
  label-free look in both themes.

## How the images are made

`scripts/station_maps.py` (`uv run scripts/station_maps.py`) reads each station's coordinates from
`Stations.kt`, asks the Overpass API once per station for the roads, railways, water, parks and buildings
around it (with a descriptive user agent and a pause between requests), caches the answers in
`build/station-maps/`, and draws:

- `app/src/main/res/drawable-nodpi/map_station_<id>.webp` and `drawable-night-nodpi/…`, 480 × 230 dp at 3×
  (1440 × 690 px), centred on the station, about 2.6 m per dp. The app crops the sides to the screen's width.
- `StationMaps.kt`, the station id to drawable lookup (a `when`, no reflection).

**When a station is added or moves**, update `Stations.kt` and run the script again; it fetches only stations
not yet in its cache (`--refetch` fetches them all, for fresher streets). Commit the images and
`StationMaps.kt`, never the cache. A test fails while any station has no map.

## Consequences

- No network for the map, so ADR-0001's single host holds and the map works offline.
- 4–29 KB an image (lossy WebP), 36 images: about 0.48 MB of APK.
- The data is ODbL: "© OpenStreetMap contributors" shows on the map, linked to
  openstreetmap.org/copyright, and the notice is in `app/src/main/assets/licences/`. No tile style licence
  applies; the colours are ours (light from the design, dark from the dark theme).
- The streets are as of the last run. Roads near the stations rarely change; refetch when one visibly has.
