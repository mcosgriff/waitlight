# /// script
# requires-python = ">=3.11"
# dependencies = [
#     "pillow>=11",
#     "requests>=2.32",
# ]
# ///
"""Renders the station detail's map images from OpenStreetMap data (ADR-0006).

    uv run scripts/station_maps.py            # render all stations, fetching only what isn't cached
    uv run scripts/station_maps.py --refetch  # fetch every station's OSM data again

For each station in `domain/.../Stations.kt` it asks the Overpass API once for the roads, water, parks and
buildings around the station's coordinates, caches the answer in `build/station-maps/` (not committed), and
draws two images centred on the station, in Waitlight's own light and dark map style:

    app/src/main/res/drawable-nodpi/map_station_<id>.webp
    app/src/main/res/drawable-night-nodpi/map_station_<id>.webp

It also writes `StationMaps.kt`, the station id to drawable lookup. The app draws the pin itself, at the
image's centre. Run it again after a station is added or moves in `Stations.kt`, then commit the images and
`StationMaps.kt`.

The data is © OpenStreetMap contributors, under the ODbL. No map tiles are used, so no tile style's licence
applies; the style is ours.
"""

import argparse
import io
import json
import math
import re
import sys
import time
from pathlib import Path

import requests
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
STATIONS_KT = ROOT / "domain/src/main/kotlin/io/cosgriff/waitlight/domain/Stations.kt"
RES = ROOT / "app/src/main/res"
LOOKUP_KT = ROOT / "app/src/main/kotlin/io/cosgriff/waitlight/ui/detail/StationMaps.kt"
CACHE = ROOT / "build/station-maps"

OVERPASS = "https://overpass-api.de/api/interpreter"
USER_AGENT = "Waitlight station map generator (+https://github.com/mcosgriff/waitlight)"

# The image, in dp: wider than any phone (the app crops the sides), as tall as the design's map (230dp).
WIDTH_DP, HEIGHT_DP = 480, 230
DENSITY = 3  # pixels per dp: one image per theme in drawable-nodpi, which Android never scales; the app fits
             # it to the map's 230dp height and crops the sides (ContentScale.Crop), so the scale stays 2.6 m/dp
SUPERSAMPLE = 2  # drawn at twice the size and scaled down, for smooth edges
METRES_PER_DP = 2.6  # about a kilometre across a 390dp-wide phone

STYLES = {
    "light": {
        "land": "#DCE6E2", "green": "#CADDCB", "water": "#B5D0DC", "building": "#D0DAD6",
        "rail": "#C3CCC8", "minor": "#EEF1EC", "road": "#F4F6F2", "major": "#FAFBF8",
    },
    "dark": {
        "land": "#1A2320", "green": "#1B2B22", "water": "#162C36", "building": "#212C28",
        "rail": "#2E3A36", "minor": "#27322E", "road": "#2E3A35", "major": "#3A4842",
    },
}

# Road widths in dp, and the style colour each is drawn in.
ROADS = {
    "motorway": (11, "major"), "trunk": (10, "major"), "primary": (9, "major"),
    "secondary": (8, "road"), "tertiary": (7, "road"),
    "motorway_link": (6, "major"), "trunk_link": (6, "major"), "primary_link": (6, "major"),
    "secondary_link": (5, "road"), "tertiary_link": (5, "road"),
    "residential": (5, "road"), "unclassified": (5, "road"), "living_street": (4, "road"),
    "service": (2.5, "minor"),
}
ROAD_ORDER = list(reversed(ROADS))  # minor roads first, so major ones are drawn over them

STATION = re.compile(r'Station\((\d+), "([^"]+)",.*?GeoPoint\(([-\d.]+), ([-\d.]+)\)')


def stations():
    found = [(int(i), name, float(lat), float(lon)) for i, name, lat, lon in STATION.findall(STATIONS_KT.read_text())]
    if not found:
        sys.exit(f"no stations found in {STATIONS_KT}")
    return found


def bbox(lat, lon):
    """The image's area plus a margin, as Overpass's (south, west, north, east)."""
    half_w = WIDTH_DP * METRES_PER_DP / 2 * 1.2
    half_h = HEIGHT_DP * METRES_PER_DP / 2 * 1.2
    dlat = half_h / 110_540
    dlon = half_w / (111_320 * math.cos(math.radians(lat)))
    return lat - dlat, lon - dlon, lat + dlat, lon + dlon


def fetch(station_id, lat, lon, refetch):
    """The station's OSM data, from the cache when it's there. One request per station, a pause between."""
    cached = CACHE / f"{station_id}.json"
    if cached.exists() and not refetch:
        return json.loads(cached.read_text())
    s, w, n, e = bbox(lat, lon)
    query = f"""
[out:json][timeout:60][bbox:{s},{w},{n},{e}];
(
  way[highway~"^({'|'.join(ROADS)})$"];
  way[railway=rail];
  way[building];
  nwr[natural=water]; way[waterway~"^(river|stream|canal|ditch|drain)$"]; nwr[waterway=riverbank];
  nwr[leisure~"^(park|golf_course|pitch|playground|nature_reserve)$"];
  nwr[landuse~"^(grass|meadow|recreation_ground|cemetery|forest|village_green)$"];
);
out geom;
"""
    print(f"fetching OSM data for station {station_id}", file=sys.stderr)
    attempts = 4  # Overpass answers 429 or 504 when busy; wait and ask again, a few times at most
    for attempt in range(attempts):
        response = requests.post(OVERPASS, data={"data": query}, headers={"User-Agent": USER_AGENT}, timeout=120)
        if response.status_code not in (429, 502, 503, 504) or attempt == attempts - 1:
            break
        time.sleep(30 * (attempt + 1))
    response.raise_for_status()
    data = response.json()
    CACHE.mkdir(parents=True, exist_ok=True)
    cached.write_text(json.dumps(data))
    time.sleep(5)
    return data


def closed(ring):
    return len(ring) > 3 and ring[0] == ring[-1]


def join(pieces):
    """Ways joined end to end into rings. Only closed rings are kept: filling an open one would close it with a
    straight chord across whatever is missing from the data, a wrong wedge of colour."""
    pieces = [list(p) for p in pieces]
    joined = []
    while pieces:
        ring = pieces.pop()
        changed = True
        while ring[0] != ring[-1] and changed:
            changed = False
            for i, piece in enumerate(pieces):
                if piece[0] == ring[-1]:
                    ring += piece[1:]
                elif piece[-1] == ring[-1]:
                    ring += list(reversed(piece))[1:]
                else:
                    continue
                pieces.pop(i)
                changed = True
                break
        if closed(ring):
            joined.append(ring)
    return joined


def rings(element):
    """An area's (outer, inner) rings: a closed way, or a multipolygon's members joined end to end. Inner rings
    are its holes (an island in a lake, a courtyard), left unfilled."""
    if element["type"] == "way":
        geometry = element.get("geometry", [])
        return ([geometry], []) if closed(geometry) else ([], [])
    if element["type"] != "relation":
        return [], []

    def members(role):
        return [m["geometry"] for m in element.get("members", []) if m.get("role") == role and m.get("geometry")]

    return join(members("outer")), join(members("inner"))


def kind(tags):
    # Buildings first: a recreation centre is building=yes with leisure=sports_centre, and is still a building.
    if "building" in tags:
        return "building"
    if tags.get("natural") == "water" or tags.get("waterway") == "riverbank":
        return "water"
    if "waterway" in tags:
        return "waterline"
    if "leisure" in tags or "landuse" in tags:
        return "green"
    if tags.get("railway") == "rail":
        return "rail"
    if tags.get("highway") in ROADS:
        return "road"
    return None


def render(data, lat, lon, style):
    scale = DENSITY * SUPERSAMPLE  # pixels per dp
    width, height = WIDTH_DP * scale, HEIGHT_DP * scale
    metres_per_lon = 111_320 * math.cos(math.radians(lat))

    def xy(point):
        x = (point["lon"] - lon) * metres_per_lon / METRES_PER_DP * scale + width / 2
        y = (lat - point["lat"]) * 110_540 / METRES_PER_DP * scale + height / 2
        return x, y

    image = Image.new("RGB", (width, height), style["land"])
    draw = ImageDraw.Draw(image)

    def line(points, width_dp, colour):
        w = max(1, round(width_dp * scale))
        draw.line(points, fill=colour, width=w, joint="curve")
        r = w / 2
        for x, y in (points[0], points[-1]):  # round caps
            draw.ellipse((x - r, y - r, x + r, y + r), fill=colour)

    elements = data["elements"]
    by_kind = {}
    for element in elements:
        k = kind(element.get("tags", {}))
        if k:
            by_kind.setdefault(k, []).append(element)

    for area in ("green", "water", "building"):
        for element in by_kind.get(area, []):
            outer, inner = rings(element)
            if not inner:
                for ring in outer:
                    draw.polygon([xy(p) for p in ring], fill=style[area])
                continue
            # With holes: fill through a mask, the outer rings on and the inner rings off again.
            mask = Image.new("L", image.size, 0)
            shape = ImageDraw.Draw(mask)
            for ring in outer:
                shape.polygon([xy(p) for p in ring], fill=255)
            for ring in inner:
                shape.polygon([xy(p) for p in ring], fill=0)
            image.paste(style[area], mask=mask)
    for element in by_kind.get("waterline", []):
        if element.get("geometry"):
            line([xy(p) for p in element["geometry"]], 3, style["water"])
    for element in by_kind.get("rail", []):
        if element.get("geometry"):
            line([xy(p) for p in element["geometry"]], 2, style["rail"])
    roads = [
        e for e in by_kind.get("road", [])
        if e.get("geometry") and e["tags"].get("area") != "yes" and e["tags"].get("service") != "parking_aisle"
    ]
    roads.sort(key=lambda e: ROAD_ORDER.index(e["tags"]["highway"]))
    for element in roads:
        width_dp, colour = ROADS[element["tags"]["highway"]]
        line([xy(p) for p in element["geometry"]], width_dp, style[colour])

    return image.resize((WIDTH_DP * DENSITY, HEIGHT_DP * DENSITY), Image.Resampling.LANCZOS)


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    buffer = io.BytesIO()
    image.save(buffer, "WEBP", quality=82, method=6)
    path.write_bytes(buffer.getvalue())


def write_lookup(ids):
    cases = "\n".join(f"    {i} -> R.drawable.map_station_{i}" for i in sorted(ids))
    LOOKUP_KT.write_text(f"""package io.cosgriff.waitlight.ui.detail

import androidx.annotation.DrawableRes
import io.cosgriff.waitlight.R

// Generated by scripts/station_maps.py; run it again rather than editing this by hand.

/**
 * Each station's map image (ADR-0006): OpenStreetMap data, © OpenStreetMap contributors, centred on the
 * station. Null for a station without one.
 */
@DrawableRes
fun stationMap(stationId: Int): Int? = when (stationId) {{
{cases}
    else -> null
}}
""")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--refetch", action="store_true", help="fetch OSM data again even when it is cached")
    args = parser.parse_args()
    all_stations = stations()
    for station_id, name, lat, lon in all_stations:
        data = fetch(station_id, lat, lon, args.refetch)
        for theme, folder in (("light", "drawable-nodpi"), ("dark", "drawable-night-nodpi")):
            save(render(data, lat, lon, STYLES[theme]), RES / folder / f"map_station_{station_id}.webp")
        print(f"{station_id} {name}: {len(data['elements'])} OSM elements", file=sys.stderr)
    write_lookup([s[0] for s in all_stations])


if __name__ == "__main__":
    main()
