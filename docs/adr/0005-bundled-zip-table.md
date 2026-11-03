# ZIP codes come from a bundled Census table

Someone who won't share their location types a ZIP code instead, and the list header names where "here" is
("Arvada, CO 80003"). Both need a ZIP's coordinates and place name with no geocoding service, so the app
carries a table of every Colorado ZIP code with a Census area (ZCTA): its centroid and a place name
(`data/src/main/assets/colorado_zips.txt`, made by `scripts/colorado_zips.py`, read by `ZipTable`). Decided
2026-11-02 (#17).

## Sources

All US Census Bureau, public domain (works of the US government, 17 U.S.C. 105); noted in
`app/src/main/assets/licences/census-zcta.txt`.

- Centroids: the 2026 ZCTA gazetteer's internal points.
- Place names: the 2020 ZCTA-to-place relationship file, the Colorado place sharing the most land with the ZCTA,
  without "city", "town" or "CDP"; a ZCTA in no place takes its county, by the same rule, from the 2020
  ZCTA-to-county file ("Park County").
- Outlines: the 2020 cartographic boundary ZCTAs (1:500,000), simplified to about 30 m
  (`colorado_zip_shapes.txt`), so the header names the ZIP a fix is in. Added 2026-11-02: naming the nearest
  centroid's ZIP called north Arvada (80003) Westminster (80031), whose centroid is nearer there.
- Colorado: the gazetteer has no state column, so ZCTAs 800xx to 816xx, the USPS prefixes for Colorado. The
  script stops if any of their points falls outside Colorado's bounding box.

527 ZIPs, 18 KB as text (about 8 KB compressed in the APK); their outlines 666 KB (about 250 KB compressed).

## Considered Options

- **Naming a fix by the nearest centroid only**, as the table first did: no outlines to carry, but wrong near
  borders, where a neighbouring ZIP's centroid can be nearer than the fix's own.
- **A geocoding service** (Google's, Nominatim, the Census geocoder). A second network host (ADR-0001), the
  typed ZIP or the phone's location sent off the phone, and for Google a key (ADR-0002).
- **Front Range ZIPs only.** Smaller, but a ZIP from elsewhere in Colorado would be refused though every
  station is still reachable; the whole state is only 527 lines.
- **USPS city names** ("Littleton" for 80127, where the Census says Dakota Ridge). Closer to what people write
  on envelopes, but USPS's ZIP-to-city data isn't freely licensed. Census places are what's on the map; a ZIP
  that spans several towns takes the one covering most of its land.

## Consequences

- No network for ZIP entry or the header's place; nothing typed leaves the phone.
- A ZIP that is only PO boxes or one building has no ZCTA, so it isn't in the table and is refused as unknown.
- Distances from a ZIP are from its centroid, so drive times stay estimates, as with location.
- Distances from a fix are from the fix; only the header's name comes from the outlines. A fix outside every
  outline (a gap left by simplifying) falls back to the nearest centroid's ZIP. Outlines that can't be read are
  logged and the app names ZIPs by nearest centroid, as before.
- A fix more than 25 miles from every centroid (outside Colorado) names no place: the header says "Near you".
- New ZIPs reach the app only when the script is run again and the table shipped; the 2020 relationship files
  hold until the 2030 census.
