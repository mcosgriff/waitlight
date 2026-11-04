# /// script
# requires-python = ">=3.11"
# dependencies = ["shapely>=2.0"]
# ///
"""
Makes the bundled drive-time table (ADR-0007): the drive minutes from every cell of a grid over the emissions
area to each AirCare station, routed on OpenStreetMap's roads, so the app shows real drive times with no
routing service.

    uv run scripts/drive_times.py

Needs Docker. It downloads Geofabrik's Colorado extract (OpenStreetMap data, ODbL) into build/drive-times/,
prepares it with OSRM's car profile (the osrm-backend image, run locally), starts OSRM on localhost, asks its
table service for every cell to every station, and writes data/src/main/assets/drive_minutes.bin. Nothing
about any user goes anywhere: the grid is fixed, and the app looks its cell up on the phone.

Run it again when a station is added or moves in Stations.kt, or now and then for newer roads (pass --refetch
to download a newer extract). The extract and OSRM's files are cached in build/drive-times/.

- Grid: SOUTH..NORTH by LAT_STEP and WEST..EAST by LON_STEP (about 250 m square), around the gas emissions
  program area. A cell's time is from its centre.
- Area: only cells inside the gas emissions program area, or within AREA_MARGIN_DEGREES of its edge (about
  1.5 km, so a spot just inside isn't lost to a cell centre just outside), have times; outside it no gas vehicle
  needs a test. The outline is scripts/gas_emissions_area.geojson, taken from the "Gas Emissions Area
  Boundaries" folder of AirCare's "Combined Emissions Maps" KML: its border lines joined end to end into one
  ring. With a newer KML, `--kml <file>` writes the outline again from it.
- A cell whose centre is more than MAX_SNAP_METRES from a road OSRM can route on (fields, mountainsides,
  reservoirs) has no time, nor has a cell OSRM finds no route from. No time is NONE (255).
- Times are OSRM's car-profile durations, with no traffic, rounded to whole minutes and capped at 254.

File layout, all integers big-endian:

    "WLDT"                 magic
    u8                     version (2)
    i32 × 4                south latitude, west longitude, latitude step, longitude step, in 1e-6 degrees
    i32 × 3                rows, columns, stations
    i32 × stations         AirCare station ids, in the order of each cell's bytes
    (i32, i32) × rows      each row's span, from the south: its first column with times and how many columns
                           from there (0 for a row with none); cells outside a row's span have no time
    u8 × …                 minutes, row by row, then the span's columns from the west, then stations
"""

import argparse
import json
import re
import shutil
import struct
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

from shapely.geometry import Point, Polygon, shape

ROOT = Path(__file__).resolve().parent.parent
STATIONS_KT = ROOT / "domain/src/main/kotlin/io/cosgriff/waitlight/domain/Stations.kt"
OUT = ROOT / "data/src/main/assets/drive_minutes.bin"
AREA = ROOT / "scripts/gas_emissions_area.geojson"
AREA_FOLDER = "Gas Emissions Area Boundaries"
AREA_MARGIN_DEGREES = 0.015
MAX_AREA_GAP_DEGREES = 0.01  # the KML's border lines meet to within this
WORK = ROOT / "build/drive-times"

EXTRACT_URL = "https://download.geofabrik.de/north-america/us/colorado-latest.osm.pbf"
EXTRACT = "colorado-latest.osm.pbf"
OSRM_IMAGE = "ghcr.io/project-osrm/osrm-backend:v5.27.1"
PORT = 5055
USER_AGENT = "Waitlight drive-time table generator (+https://github.com/mcosgriff/waitlight)"

SOUTH, NORTH, LAT_STEP = 39.11, 40.73, 0.00225
WEST, EAST, LON_STEP = -105.7125, -104.2625, 0.0029
MAX_SNAP_METRES = 1500
NONE = 255
CHUNK = 400  # sources per table request

STATION = re.compile(r'Station\((\d+), "([^"]+)",.*?GeoPoint\(([-\d.]+), ([-\d.]+)\)')


def stations():
    found = [(int(i), name, float(lat), float(lon)) for i, name, lat, lon in STATION.findall(STATIONS_KT.read_text())]
    if not found:
        sys.exit(f"no stations found in {STATIONS_KT}")
    for _, name, lat, lon in found:
        if not (SOUTH <= lat <= NORTH and WEST <= lon <= EAST):
            sys.exit(f"{name} is outside the grid; widen it")
    return found


def area_from_kml(path):
    """The gas program area's outline: the KML folder's border lines, joined end to end into one ring."""
    kml = "{http://www.opengis.net/kml/2.2}"
    folder = next(
        (f for f in ET.parse(path).getroot().iter(f"{kml}Folder") if f.findtext(f"{kml}name", "").strip() == AREA_FOLDER),
        None,
    )
    if folder is None:
        sys.exit(f"no {AREA_FOLDER!r} folder in {path}")
    pieces = []
    for line in folder.iter(f"{kml}LineString"):
        text = line.findtext(f"{kml}coordinates", "")
        pieces.append([tuple(float(v) for v in point.split(",")[:2]) for point in text.split()])
    if not pieces:
        sys.exit(f"no border lines in {AREA_FOLDER!r}")

    def gap(a, b):
        return max(abs(a[0] - b[0]), abs(a[1] - b[1]))

    ring = pieces.pop(0)
    while pieces:
        # The piece starting or ending nearest the ring's end, turned round if it's its end.
        best = min(range(len(pieces)), key=lambda i: min(gap(ring[-1], pieces[i][0]), gap(ring[-1], pieces[i][-1])))
        piece = pieces.pop(best)
        if gap(ring[-1], piece[-1]) < gap(ring[-1], piece[0]):
            piece.reverse()
        if gap(ring[-1], piece[0]) > MAX_AREA_GAP_DEGREES:
            sys.exit(f"the border lines don't meet near {ring[-1]}")
        ring += piece
    if gap(ring[-1], ring[0]) > MAX_AREA_GAP_DEGREES:
        sys.exit("the border lines don't close")
    drawn = Polygon(ring)
    # The hand-drawn creek line loops over itself in places; undo the loops and keep the main outline.
    polygon = drawn.buffer(0)
    if polygon.geom_type == "MultiPolygon":
        polygon = max(polygon.geoms, key=lambda p: p.area)
    if abs(polygon.area - abs(drawn.area)) > 0.01 * abs(drawn.area):
        sys.exit("the joined border isn't one outline")
    AREA.write_text(json.dumps({
        "type": "Polygon",
        "source": f"AirCare Colorado, Combined Emissions Maps KML, folder {AREA_FOLDER!r}",
        "coordinates": [[[round(x, 6), round(y, 6)] for x, y in polygon.exterior.coords]],
    }, indent=None) + "\n")
    print(f"wrote {AREA.relative_to(ROOT)}: {len(polygon.exterior.coords)} points")


def area():
    return shape(json.loads(AREA.read_text()))


def grid():
    rows = round((NORTH - SOUTH) / LAT_STEP) + 1
    cols = round((EAST - WEST) / LON_STEP) + 1
    return rows, cols


def download(refetch):
    path = WORK / EXTRACT
    if path.exists() and not refetch:
        return
    print(f"downloading {EXTRACT_URL}")
    request = urllib.request.Request(EXTRACT_URL, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request) as response, open(path.with_suffix(".part"), "wb") as out:
        shutil.copyfileobj(response, out)
    path.with_suffix(".part").rename(path)
    for old in WORK.glob("colorado-latest.osrm*"):
        old.unlink()


def docker(*args, detach=False):
    command = ["docker", "run", "--rm", "-v", f"{WORK}:/data"]
    if detach:
        command += ["-d", "-p", f"127.0.0.1:{PORT}:5000"]
    command += [OSRM_IMAGE, *args]
    result = subprocess.run(command, check=True, capture_output=detach, text=True)
    return result.stdout.strip() if detach else None


def prepare():
    if (WORK / "colorado-latest.osrm.mldgr").exists():
        return
    print("preparing the roads with OSRM's car profile (a few minutes)")
    docker("osrm-extract", "-p", "/opt/car.lua", f"/data/{EXTRACT}")
    docker("osrm-partition", "/data/colorado-latest.osrm")
    docker("osrm-customize", "/data/colorado-latest.osrm")


def serve():
    container = docker(
        "osrm-routed", "--algorithm", "mld", "--max-table-size", str(CHUNK + 100), "/data/colorado-latest.osrm",
        detach=True,
    )
    for _ in range(60):
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/nearest/v1/driving/-105.0,39.7", timeout=2)
            return container
        except OSError:
            time.sleep(1)
    subprocess.run(["docker", "stop", container])
    sys.exit("OSRM didn't start")


def table(sources, destinations):
    """Minutes from each source to each destination, or NONE: one row per source."""
    coords = ";".join(f"{lon:.6f},{lat:.6f}" for lat, lon in [*sources, *destinations])
    n = len(sources)
    url = (
        f"http://127.0.0.1:{PORT}/table/v1/driving/{coords}"
        f"?sources={';'.join(map(str, range(n)))}"
        f"&destinations={';'.join(map(str, range(n, n + len(destinations))))}"
    )
    with urllib.request.urlopen(url, timeout=120) as response:
        answer = json.load(response)
    if answer.get("code") != "Ok":
        sys.exit(f"OSRM: {answer.get('code')} {answer.get('message')}")
    out = []
    for snap, durations in zip(answer["sources"], answer["durations"]):
        if snap["distance"] > MAX_SNAP_METRES:
            out.append([NONE] * len(destinations))
            continue
        out.append([NONE if d is None else min(254, round(d / 60)) for d in durations])
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--refetch", action="store_true", help="download a newer Colorado extract")
    parser.add_argument("--kml", type=Path, help="write the gas program area's outline again from this KML first")
    args = parser.parse_args()

    if args.kml:
        area_from_kml(args.kml)
    program = area()
    covered = program.buffer(AREA_MARGIN_DEGREES)
    found = stations()
    for _, name, lat, lon in found:
        if not program.contains(Point(lon, lat)):
            print(f"note: the {name} station is outside the gas program area")
    WORK.mkdir(parents=True, exist_ok=True)
    download(args.refetch)
    prepare()

    rows, cols = grid()
    cells = [(SOUTH + r * LAT_STEP, WEST + c * LON_STEP) for r in range(rows) for c in range(cols)]
    inside = [covered.contains(Point(lon, lat)) for lat, lon in cells]
    west, south, east, north = covered.bounds
    if not (SOUTH <= south and north <= NORTH and WEST <= west and east <= EAST):
        sys.exit("the gas program area runs off the grid; widen it")
    destinations = [(lat, lon) for _, _, lat, lon in found]

    container = serve()
    try:
        minutes = []
        for start in range(0, len(cells), CHUNK):
            chunk = [k for k in range(start, min(start + CHUNK, len(cells))) if inside[k]]
            routed = iter(table([cells[k] for k in chunk], destinations) if chunk else [])
            wanted = set(chunk)
            minutes += [next(routed) if k in wanted else [NONE] * len(destinations)
                        for k in range(start, min(start + CHUNK, len(cells)))]
            print(f"\r{min(start + CHUNK, len(cells))}/{len(cells)} cells", end="", flush=True)
        print()
    finally:
        subprocess.run(["docker", "stop", container], capture_output=True)

    # Each row's span: from its first cell with a time to its last.
    spans = []
    for r in range(rows):
        timed = [c for c in range(cols) if any(m != NONE for m in minutes[r * cols + c])]
        spans.append((timed[0], timed[-1] - timed[0] + 1) if timed else (0, 0))
    with open(OUT, "wb") as out:
        out.write(b"WLDT")
        out.write(struct.pack(">B", 2))
        out.write(struct.pack(
            ">4i", round(SOUTH * 1e6), round(WEST * 1e6), round(LAT_STEP * 1e6), round(LON_STEP * 1e6),
        ))
        out.write(struct.pack(">3i", rows, cols, len(found)))
        out.write(struct.pack(f">{len(found)}i", *(i for i, *_ in found)))
        for first, count in spans:
            out.write(struct.pack(">2i", first, count))
        for r, (first, count) in enumerate(spans):
            out.write(bytes(m for c in range(first, first + count) for m in minutes[r * cols + c]))

    routed = sum(1 for row in minutes if any(m != NONE for m in row))
    print(f"wrote {OUT.relative_to(ROOT)}: {rows}×{cols} cells ({sum(inside)} in the area, {routed} routed) × {len(found)} stations")
    # A few to eyeball against a map app.
    by_name = {name: k for k, (_, name, _, _) in enumerate(found)}
    for label, lat, lon, station in [
        ("Arvada 80003 → Arvada", 39.8254, -105.0644, "Arvada"),
        ("Golden → Aurora", 39.7555, -105.2211, "Aurora"),
        ("Ken Caryl → County Line", 39.5770, -105.1100, "County Line"),
        ("Dacono → Greeley", 40.0847, -104.9392, "Greeley"),
    ]:
        r, c = round((lat - SOUTH) / LAT_STEP), round((lon - WEST) / LON_STEP)
        print(f"  {label}: {minutes[r * cols + c][by_name[station]]} min")


if __name__ == "__main__":
    main()
