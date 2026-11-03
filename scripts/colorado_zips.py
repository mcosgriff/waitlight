# /// script
# requires-python = ">=3.11"
# ///
"""
Makes the bundled table of Colorado ZIP codes (#17, ADR-0005): each ZIP's centroid and a place name, so the app
can sort stations from a typed ZIP and name where "here" is, with no geocoding service.

    uv run scripts/colorado_zips.py

It downloads three US Census Bureau files (public domain, 17 U.S.C. 105) and writes
data/src/main/assets/colorado_zips.txt. Run it again when the Census publishes a newer gazetteer (change
GAZETTEER_YEAR); the relationship files are the 2020 ones, which hold until the 2030 ZCTAs.

- Centroids: the ZCTA gazetteer's internal point (INTPTLAT, INTPTLONG). A ZCTA is the Census's area for a ZIP;
  ZIPs that are only PO boxes or one building have none and so aren't in the table.
- Colorado: the gazetteer has no state column, so a ZCTA is Colorado's when its first three digits are 800 to
  816 (the USPS prefixes for Colorado). Every such point must also fall in Colorado's bounding box, or the
  script stops.
- Place name: of the Colorado places (cities, towns and census-designated places) a ZCTA overlaps in the
  ZCTA-to-place relationship file, the one sharing the most land with it, without its " city" / " town" /
  " CDP" suffix. A ZCTA overlapping no place (rural ones) takes the county it shares the most land with,
  from the ZCTA-to-county file ("Park County").

Each line is `zip|latitude|longitude|place`, latitude and longitude to 4 places (about 10 m), sorted by ZIP.
Lines starting with # are comments.
"""

import io
import os
import sys
import urllib.request
import zipfile
from collections import defaultdict

GAZETTEER_YEAR = 2026
GAZETTEER = (
    f"https://www2.census.gov/geo/docs/maps-data/data/gazetteer/{GAZETTEER_YEAR}_Gazetteer/"
    f"{GAZETTEER_YEAR}_Gaz_zcta_national.zip"
)
REL = "https://www2.census.gov/geo/docs/maps-data/data/rel2020/zcta520/"
PLACES = REL + "tab20_zcta520_place20_natl.txt"
COUNTIES = REL + "tab20_zcta520_county20_natl.txt"

COLORADO_FIPS = "08"
FIRST_PREFIX, LAST_PREFIX = 800, 816
# Colorado's corners, with a little room: 37-41 N, 102.04-109.06 W.
LAT_RANGE = (36.9, 41.1)
LON_RANGE = (-109.2, -101.9)
SUFFIXES = (" city", " town", " CDP")

OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "data", "src", "main", "assets", "colorado_zips.txt"))


def fetch(url: str) -> bytes:
    print(f"Downloading {url}", file=sys.stderr)
    request = urllib.request.Request(url, headers={"User-Agent": "Waitlight build script"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def rows(text: str):
    """A pipe-delimited Census file as dicts, by its header."""
    lines = text.lstrip("﻿").splitlines()
    header = [h.strip() for h in lines[0].split("|")]
    for line in lines[1:]:
        if line.strip():
            yield dict(zip(header, (v.strip() for v in line.split("|"))))


def colorado(zcta: str) -> bool:
    return len(zcta) == 5 and zcta.isdigit() and FIRST_PREFIX <= int(zcta[:3]) <= LAST_PREFIX


def centroids() -> dict[str, tuple[float, float]]:
    archive = zipfile.ZipFile(io.BytesIO(fetch(GAZETTEER)))
    name = next(n for n in archive.namelist() if n.endswith(".txt"))
    text = archive.read(name).decode("utf-8")
    # Older gazetteers are tab-delimited; newer ones use pipes.
    if "|" not in text.splitlines()[0]:
        text = "\n".join("|".join(line.split("\t")) for line in text.splitlines())
    points = {}
    for row in rows(text):
        zcta = row["GEOID"]
        if not colorado(zcta):
            continue
        lat, lon = float(row["INTPTLAT"]), float(row["INTPTLONG"])
        if not (LAT_RANGE[0] <= lat <= LAT_RANGE[1] and LON_RANGE[0] <= lon <= LON_RANGE[1]):
            sys.exit(f"{zcta} at {lat},{lon} is outside Colorado's box; check the prefix rule")
        points[zcta] = (lat, lon)
    return points


def largest(url: str, geoid: str, name: str) -> dict[str, str]:
    """For each Colorado ZCTA, the name of the Colorado area in [url] sharing the most land with it."""
    best: dict[str, tuple[int, str]] = {}
    for row in rows(fetch(url).decode("utf-8")):
        zcta = row["GEOID_ZCTA5_20"]
        if not colorado(zcta) or not row[geoid].startswith(COLORADO_FIPS):
            continue
        land = int(row["AREALAND_PART"] or 0)
        if zcta not in best or land > best[zcta][0]:
            best[zcta] = (land, row[name])
    return {zcta: label for zcta, (_, label) in best.items()}


def place_name(label: str) -> str:
    for suffix in SUFFIXES:
        if label.endswith(suffix):
            return label[: -len(suffix)]
    return label


def main() -> None:
    points = centroids()
    places = largest(PLACES, "GEOID_PLACE_20", "NAMELSAD_PLACE_20")
    counties = largest(COUNTIES, "GEOID_COUNTY_20", "NAMELSAD_COUNTY_20")
    counts = defaultdict(int)
    lines = [
        f"# Colorado ZIP codes (ZCTAs): centroid from the US Census Bureau's {GAZETTEER_YEAR} ZCTA gazetteer,",
        "# place from its 2020 ZCTA-to-place (or, failing that, ZCTA-to-county) relationship file. Public domain.",
        "# Made by scripts/colorado_zips.py; don't edit by hand. zip|latitude|longitude|place",
    ]
    for zcta in sorted(points):
        lat, lon = points[zcta]
        if zcta in places:
            name = place_name(places[zcta])
            counts["place"] += 1
        elif zcta in counties:
            name = counties[zcta]
            counts["county"] += 1
        else:
            sys.exit(f"{zcta} has neither a place nor a county")
        if "|" in name:
            sys.exit(f"{zcta}'s name {name!r} has a |")
        lines.append(f"{zcta}|{lat:.4f}|{lon:.4f}|{name}")
    with open(OUT, "w", encoding="utf-8", newline="\n") as out:
        out.write("\n".join(lines) + "\n")
    print(
        f"Wrote {len(points)} ZIPs ({counts['place']} named by place, {counts['county']} by county) to {OUT}",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
