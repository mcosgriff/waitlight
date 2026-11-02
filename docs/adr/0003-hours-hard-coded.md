# Opening hours and holidays are hard-coded

All stations keep the same hours (Mon–Fri 8:00–17:30, Sat 8:00–13:00, closed Sunday and Colorado state
holidays), so they are code (`AirCareHours`, `Holiday`) in America/Denver rather than read from the page.
Decided 2026-11-02.

## Considered Options

- **Read the hours from the page.** It states them only as prose ("8am to 5:30pm (M-F), 8am to 1pm (Sat),
  closed Sunday and state holidays"), with no holiday dates, so the holidays would need coding anyway, and the
  app has to know the hours while offline and before the first fetch.

## Consequences

- The app knows when the stations are closed without the network, and never fetches outside hours.
- The fetch compares the page's stated hours with `AirCareHours.STATED_HOURS` and logs when they change.
- Holidays follow the statute's rules (nth weekday, fixed dates, weekend observance), checked against the
  state's published 2026 schedule in a test. A change to the statute needs a code change.
