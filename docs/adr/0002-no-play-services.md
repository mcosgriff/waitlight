# No Google Play services or Google SDKs

Waitlight uses no Play services and no Google SDK: location comes from Android's own `LocationManager`, there is
no Maps SDK and no Routes API, and there are no analytics, crash reporting or ads. Decided 2026-11-02, following
Glovebox.

## Considered Options

- **FusedLocationProvider and the Maps SDK**, as the design handoff suggests. Better battery use for location,
  and a real map on the detail screen. Turned down: both need Play services on the phone (de-Googled phones are
  among the people who sideload from Obtainium), the Maps SDK needs an API key in the APK, and both bring
  Google's telemetry with them.
- **Routes API for drive times.** Needs a key and sends the user's location to Google on every refresh.

## Consequences

- Drive times come from a table routed on OpenStreetMap's roads at development time and bundled (ADR-0007);
  until 2026-11-03 they were estimated from straight-line distance.
- Directions open the system's app picker on a `geo:` link to the station (ADR-0007); that is the user choosing
  to leave the app, for whichever map app they pick, not the app talking to Google.
- The detail screen's map is a bundled image per station, drawn from OpenStreetMap data (ADR-0006).
- Any library's dependency tree is checked for Play services and network code before it is added.
