# The phone reads AirCare's page itself

Waitlight fetches `https://aircarecolorado.com/locations/wait-times/wait-times-interactive` and reads it on the
phone. AirCare has no API; the page is server-rendered HTML with the stations' waits, coordinates and "Last
updated" times in it. Decided 2026-11-02.

## Considered Options

- **A small server or cache of our own** that reads the page and serves JSON (the design handoff's preference).
  Parsing fixes would ship in one place and AirCare would see one reader instead of every phone. Turned down
  for now: it is a service to build, host and keep running, for a single-developer app with few users.

## Consequences

- When AirCare changes its page, the fix needs an app release. Until then the app shows the "Wait times
  unavailable" (broken) state with the last good data.
- To be a light reader: fetch only during opening hours and no more often than AirCare updates (`shouldFetch`).
  One exception, added 2026-11-02: opening the app always fetches once, whatever the time, so a launch shows
  what AirCare has now (and a release can be checked after hours). That is one request per launch, by a person.
- The reader sits behind an interface in `:data`, so a server could replace it later without touching the rest.
- AirCare's host is the only network host the app talks to.

## Amendment, 2026-11-20: the van page

The app also reads `https://aircarecolorado.com/locations/van-locations`, AirCare's roadside van (RapidScreen)
page, and each of its tabs' tables (`/packages/customaircare/vanlocationstabledisplay_new.php?day=N`, the ids read
from the page). Same host, same user agent, nothing about the user sent. It is fetched at launch and when the
vans tab first opens, whatever the time, then at most every 15 minutes while the tab is on screen on weekdays from
5:30 AM to 7 PM Denver time, when units can be out (`vanWorthFetching`). The app opening on the vans tab is one
fetch: the tab's opening, asked while the launch fetch is under way, takes it as its own (`VanRepository`). There
is no background fetch for vans.

The page has no API either, and a broken tab makes it answer HTTP 500 with the other tabs still in it, so the 500
is read like a 200. AirCare gives no time for its van statuses, so their age is the fetch time
(`docs/rules/vans.md`).
