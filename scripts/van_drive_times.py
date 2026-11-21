# /// script
# requires-python = ">=3.11"
# dependencies = ["shapely>=2.0"]
# ///
"""
Makes the bundled van drive-time table (ADR-0008): the drive minutes from every cell of the station table's grid
(ADR-0007, `scripts/drive_times.py`) to each known roadside van site, routed on OpenStreetMap's roads with the
same OSRM car profile, so the vans list shows real drive times with no routing service.

    uv run scripts/van_drive_times.py --page https://aircarecolorado.com/locations/van-locations
    uv run scripts/van_drive_times.py --page data/src/test/resources/vans/van-locations-2026-11-20.html
    uv run scripts/van_drive_times.py            # route catalog sites the table hasn't got yet

Van sites move weekly, so the sites come from a catalog that only grows, `scripts/van_sites.json`: every site
seen on AirCare's van page, keyed by its rounded coordinates and normalised description (never AirCare's
`locationID`, which changes between fetches). `--page` adds the sites on a saved or live van page to it.

**Append-only.** A site already in the table keeps its block byte for byte; only new sites are routed, so a run
with a newer road extract can't shift the times the app already shows. `--refresh-roads` routes every site again
(after `scripts/drive_times.py --refetch`), on purpose and by hand.

Needs Docker and the OSRM files `scripts/drive_times.py` leaves in build/drive-times/ (it prepares them if
missing). Nothing about any user goes anywhere: the grid is fixed, and the app looks its cell up on the phone.

File layout (`data/src/main/assets/van_drive_minutes.bin`), all integers big-endian, **site-major** so the app
reads only the sites on the day it shows:

    "WLVD"                 magic
    u8                     version (1)
    i32 × 4                south latitude, west longitude, latitude step, longitude step, in 1e-6 degrees
    i32 × 2                rows, columns
    (i32, i32) × rows      each row's span, from the south: its first column with a cell in the area and how
                           many columns from there; the same for every site (the gas program area, ADR-0007)
    i32                    sites
    per site:
      u16, bytes           its key, UTF-8
      u8 × cells           minutes from each cell in the spans, row by row from the south, west to east;
                           255 for none; cells = the sum of the spans' counts
"""

import argparse
import json
import re
import struct
import subprocess
import sys
import urllib.request
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

from shapely.geometry import Point

sys.path.insert(0, str(Path(__file__).resolve().parent))
import drive_times as dt  # noqa: E402  (the grid, the area and OSRM)

ROOT = dt.ROOT
CATALOG = ROOT / "scripts/van_sites.json"
OUT = ROOT / "data/src/main/assets/van_drive_minutes.bin"
MAGIC = b"WLVD"
VERSION = 1
NONE = dt.NONE

LATLNGS = re.compile(r"\bvar\s+latlngs\s*=\s*(\[.*?\]);", re.S)
SCHEDULE = re.compile(
    r"\s+((?:(?:Mon|Tues?|Wed|Thu(?:rs?)?|Fri)\s+)+)(\d{1,2}:\d{2}\s*[AP]M)\s*-\s*(\d{1,2}:\d{2}\s*[AP]M)\s*$",
    re.I,
)


def description(site_text):
    """The site's text without its days and hours: what stays the same from week to week."""
    return SCHEDULE.sub("", site_text).strip()


def normalise(text):
    """As `VanSite.normalise` in :domain: whitespace collapsed, trimmed, a trailing dot dropped, lower case."""
    text = re.sub(r"\s+", " ", text).strip()
    if text.endswith("."):
        text = text[:-1].rstrip()
    return text.lower()


def coordinate(value):
    """A coordinate to 5 decimals, half up, from its shortest decimal text: as `siteKey` in :domain."""
    return str(Decimal(repr(float(value))).quantize(Decimal("0.00001"), rounding=ROUND_HALF_UP))


def site_key(lat, lon, text):
    return f"{coordinate(lat)},{coordinate(lon)}|{normalise(description(text))}"


def sites_on_page(page):
    """Every pin on a van page (a file or a URL), as catalog entries, tabs and duplicates merged."""
    if re.match(r"https?://", page):
        request = urllib.request.Request(page, headers={"User-Agent": dt.USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                html = response.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            # A broken tab makes the page a 500 while the other tabs still render.
            html = e.read().decode("utf-8", "replace")
    else:
        html = Path(page).read_text("utf-8", "replace")
    found = {}
    for block in LATLNGS.findall(html):
        for pin in json.loads(block):
            lat, lon = float(pin["lat"]), float(pin["lng"])
            key = site_key(lat, lon, pin["name"])
            found.setdefault(key, {"key": key, "description": description(pin["name"]), "lat": lat, "lon": lon})
    if not found:
        sys.exit(f"no van sites on {page}")
    return found


def load_catalog():
    return json.loads(CATALOG.read_text()) if CATALOG.exists() else []


def save_catalog(sites):
    CATALOG.write_text(json.dumps(sorted(sites, key=lambda s: s["key"]), indent=1, ensure_ascii=False) + "\n")


def grid_cells():
    """The grid's cells in the area, row by row from the south, and each row's span."""
    rows, cols = dt.grid()
    covered = dt.area().buffer(dt.AREA_MARGIN_DEGREES)
    spans, cells = [], []
    for r in range(rows):
        lat = dt.SOUTH + r * dt.LAT_STEP
        inside = [c for c in range(cols) if covered.contains(Point(dt.WEST + c * dt.LON_STEP, lat))]
        if inside:
            first, last = inside[0], inside[-1]
            spans.append((first, last - first + 1))
            cells += [(lat, dt.WEST + c * dt.LON_STEP) for c in range(first, last + 1)]
        else:
            spans.append((0, 0))
    return spans, cells


def header(spans):
    rows, cols = dt.grid()
    out = MAGIC + struct.pack(">B", VERSION)
    out += struct.pack(">4i", round(dt.SOUTH * 1e6), round(dt.WEST * 1e6), round(dt.LAT_STEP * 1e6), round(dt.LON_STEP * 1e6))
    out += struct.pack(">2i", rows, cols)
    out += b"".join(struct.pack(">2i", first, count) for first, count in spans)
    return out


def read_table(path):
    """The header bytes and each site's block, in file order, from an existing table; ({} if none)."""
    if not path.exists():
        return None, {}
    data = path.read_bytes()
    if data[:4] != MAGIC or data[4] != VERSION:
        sys.exit(f"{path} isn't a version {VERSION} van table")
    rows = struct.unpack(">i", data[21:25])[0]
    spans_end = 29 + rows * 8
    spans = [struct.unpack(">2i", data[29 + 8 * r:37 + 8 * r]) for r in range(rows)]
    cells = sum(count for _, count in spans)
    (count,) = struct.unpack(">i", data[spans_end:spans_end + 4])
    at, blocks = spans_end + 4, {}
    for _ in range(count):
        (length,) = struct.unpack(">H", data[at:at + 2])
        key = data[at + 2:at + 2 + length].decode()
        at += 2 + length
        blocks[key] = data[at:at + cells]
        at += cells
    if at != len(data):
        sys.exit(f"{path} has {len(data) - at} bytes past its last site")
    return data[:spans_end], blocks


def write_table(path, head, blocks):
    """[blocks] in their given order: old sites first, as they were, then the new ones."""
    with open(path, "wb") as out:
        out.write(head)
        out.write(struct.pack(">i", len(blocks)))
        for key, block in blocks.items():
            encoded = key.encode()
            out.write(struct.pack(">H", len(encoded)) + encoded + block)


def merge(old_blocks, new_blocks):
    """Append-only: every old block kept byte for byte and in place; a new block never replaces an old one."""
    merged = dict(old_blocks)
    for key, block in new_blocks.items():
        if key not in merged:
            merged[key] = block
    return merged


SITES_PER_REQUEST = 90  # with drive_times.CHUNK sources, inside OSRM's --max-table-size


def route(cells, sites):
    """Each site's block: minutes from every cell to it, as drive_times.py routes them."""
    blocks = {}
    for start in range(0, len(sites), SITES_PER_REQUEST):
        blocks.update(route_some(cells, sites[start:start + SITES_PER_REQUEST]))
    return blocks


def route_some(cells, sites):
    destinations = [(s["lat"], s["lon"]) for s in sites]
    columns = [bytearray() for _ in sites]
    for start in range(0, len(cells), dt.CHUNK):
        for row in dt.table(cells[start:start + dt.CHUNK], destinations):
            for k, minutes in enumerate(row):
                columns[k].append(minutes)
        print(f"\r{min(start + dt.CHUNK, len(cells))}/{len(cells)} cells", end="", flush=True)
    print()
    return {site["key"]: bytes(column) for site, column in zip(sites, columns)}


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--page", action="append", default=[], help="add the sites on this van page (file or URL)")
    parser.add_argument("--refresh-roads", action="store_true", help="route every site again, not just new ones")
    args = parser.parse_args()

    catalog = {s["key"]: s for s in load_catalog()}
    for page in args.page:
        added = {k: v for k, v in sites_on_page(page).items() if k not in catalog}
        catalog.update(added)
        print(f"{page}: {len(added)} new sites")
    save_catalog(list(catalog.values()))
    for s in catalog.values():
        if not (dt.SOUTH <= s["lat"] <= dt.NORTH and dt.WEST <= s["lon"] <= dt.EAST):
            sys.exit(f"{s['description']} is outside the grid; widen it")

    spans, cells = grid_cells()
    head = header(spans)
    old_head, old_blocks = read_table(OUT)
    if old_head is not None and old_head != head:
        sys.exit("the grid or the area changed since the table was made; that needs --refresh-roads and a new ADR")
    if args.refresh_roads:
        old_blocks = {}
    todo = [s for k, s in catalog.items() if k not in old_blocks]
    if not todo:
        print(f"{OUT.relative_to(ROOT)} already has all {len(old_blocks)} sites")
        return

    dt.WORK.mkdir(parents=True, exist_ok=True)
    dt.download(False)
    dt.prepare()
    container = dt.serve()
    try:
        print(f"routing {len(todo)} sites from {len(cells)} cells")
        new_blocks = route(cells, todo)
    finally:
        subprocess.run(["docker", "stop", container], capture_output=True)
    blocks = merge(old_blocks, new_blocks)
    write_table(OUT, head, blocks)
    for site in todo:
        block = new_blocks[site["key"]]
        print(f"  {site['description']}: {sum(1 for m in block if m != NONE)} cells routed")
    print(f"wrote {OUT.relative_to(ROOT)}: {len(blocks)} sites × {len(cells)} cells, {OUT.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
