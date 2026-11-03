# Waitlight — notes for Claude

Android app that shows AirCare Colorado emissions-test wait times, nearest station first, with green / yellow /
red wait badges. No account, no server of its own. Distributed by sideloading via Obtainium from GitHub
Releases, not the Play Store. Public repo, GPL-3.0 (`LICENSE`). Built the way Glovebox
(`~/IdeaProjects/glovebox`) is: same module split, build setup and conventions.

**Design: `design/`.** Start from `design/README.md` (the handoff: tokens, badges, behaviour, states), then
`design/png/<Name>.png` for each screen and `design/source/<Name>.dc.html` for exact spacing and copy.
`design/source/Main.dc.html`'s `<script>` is the list screen's logic written as a spec. Before building a
screen, look at its PNG and read its source.

This file, `docs/rules/` and `docs/adr/` hold the rules and the decisions behind them. How a feature works is
in its code and tests.

## Status

Modules, theme, the business rules (`:domain`, tested), fetching and reading AirCare's page (`:data`,
`aircare/`, tested against a saved copy), location (`:data`, `location/`) and the list, detail and location
prompt screens are in. The list fetches while it's on screen, and the last waits are kept across restarts
(`WaitTimesStore`, a JSON file in `filesDir`). Not built yet: keeping the location choice across restarts, ZIP
entry and the detail map (both open questions in `docs/rules/wait-times.md`), the widget, background refresh. `./gradlew build` is green
(Gradle 9.6.0, AGP 9.5.0, lint included). Keep it green; lint fails the build on a warning as on an error
(`warningsAsErrors`, in `:app` and `:data`), and there is no baseline: fix a warning, or suppress that one case
with a comment saying why.

## Commands

- `./gradlew :domain:test` — fast pure-Kotlin tests. Run often.
- `./gradlew build` — everything, lint included.
- `./gradlew :app:installDebug` — install on a connected phone (debug app id `io.cosgriff.waitlight.debug`).
- Release: merge the PR `release-please` keeps open (version and changelog come from Conventional Commits);
  it tags `vX.Y.Z` and starts `release.yml`, which builds the signed APK after approval in the `release`
  environment (secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

## Modules

- `:domain` — pure Kotlin, **no Android imports**. All business rules live here and get unit tests: the station
  list (`Stations`), opening hours and holidays (`AirCareHours`, `Holiday`), AirCare's wording
  (`WaitText`, `AirCareTimestamp`), badges (`badgeFor`), freshness and the list's states (`listState`,
  `refreshButton`, `shouldFetch`), sorting and the hero (`arrange`, `fastestOverall`).
- `:data` — fetching and reading AirCare's page (`AirCareClient`, `AirCarePage`, `WaitTimesRepository`, on Ktor
  with OkHttp), location (`LocationSource`, approximate only), settings (DataStore), the Hilt module. The one `Clock` and `HttpClient` come from here.
  `./gradlew :data:testDebugUnitTest` runs its tests.
- `:app` — Compose UI, navigation (Navigation 3), ViewModels, theme, and later the Glance widget and WorkManager.

## Decisions — don't change without asking

Each has an ADR in `docs/adr/` with its reasons and the options turned down.

- **The phone reads AirCare's page itself** ([ADR-0001](docs/adr/0001-phone-reads-aircare-directly.md)). No
  server of our own. Fetch only during opening hours, and no more often than AirCare updates
  (`shouldFetch`). The page is the one network host the app talks to.
- **No Google Play services or Google SDKs** ([ADR-0002](docs/adr/0002-no-play-services.md)): Android's own
  `LocationManager`, not FusedLocationProvider; no Maps SDK; no Routes API. Drive times are estimated from
  straight-line distance (`DriveEstimate`) and always shown as estimates. Directions open Google Maps by link.
  No analytics, crash reporting or ads. Check any new library's dependency tree for network code and Google
  pieces before adding it.
- **Opening hours and holidays are hard-coded** ([ADR-0003](docs/adr/0003-hours-hard-coded.md)), in Denver's
  time. The fetch compares the page's stated hours with `AirCareHours.STATED_HOURS` and logs a change.
- **Freshness comes from each station's own AirCare "Last updated" time**, never the fetch time. A station's
  badge goes stale on its own timestamp, even when the others are fresh.
- **applicationId `io.cosgriff.waitlight` must never change. The release signing key must never change**
  ([ADR-0004](docs/adr/0004-application-id-and-signing-key-are-permanent.md)).
- **IDE is Android Studio.** Keep AGP current (Studio's Upgrade Assistant).
- minSdk 34, targetSdk 37, compileSdk 37, JDK 17, Kotlin + Compose + Material 3, Hilt (KSP), Navigation 3
  with `@Serializable` routes that are `NavKey`s. Dynamic colour is off; use the theme tokens.

## Design tokens

In `ui/theme/Theme.kt`, from `design/README.md`. Material roles carry the base tokens (background `#F6F7F4` /
`#0F1513`, surface `#FFFFFF` / `#1A2320`, primary `#1F4E5F` / `#8CC7D6`). Badge, hero and banner colours,
which Material doesn't name, come from `MaterialTheme.waitColors` (`waitColors(dark)` outside composition, for
the widget).

- Font: Roboto Flex, one variable font in `res/font/roboto_flex.ttf` (OFL, `assets/licences/`), through
  `MaterialTheme.typography`. The widget uses the system font.
- Corners from `MaterialTheme.shapes`: 16dp `medium` for rows and banners, 20 `large` for the hero card, 28
  `extraLarge` for the widget. Chips and buttons are fully rounded.
- Touch targets ≥ 48dp. **Colour is never the only signal:** every badge has its icon and a content
  description ("Short wait, 13 minutes"; "Last known short wait, 13 minutes as of 2:14 PM, may be out of date").

## Rules by feature

**Before changing a feature, read its rules file.** Each rule there binds as firmly as one in this file.

- **Wait times** (stations, hours and holidays, reading AirCare, badges, freshness and states, sorting, the
  hero card): `docs/rules/wait-times.md`

## Conventions

- Business logic goes in `:domain` with a test; ViewModels stay thin.
- **`:domain` returns no English:** typed results (`Badge`, `ListState`, `OpeningDay`) that `:app` words.
- **All wording lives in `res/values/strings.xml`.** Composables use `stringResource` /
  `pluralStringResource`. Names are `<screen>_<what>`. Never build a phrase by cutting up another; give it its
  own resource.
- **A composable used by more than one screen lives in `ui/components/`**; one used by a single screen stays in
  that screen's package.
- **A screen is a `…Content` composable** (plain state and callbacks) wrapped by a `…Screen` that reads the
  ViewModel. A `…Content` that needs the time takes it as a parameter, never `Instant.now()`.
- Time comes from the injected `Clock`, never `Instant.now()` or `System.currentTimeMillis()` in logic.
- Never show placeholder data as if real; mark sample values clearly.
- Commit messages and PR titles are Conventional Commits (`feat:`, `fix:`, …); `pr-title.yml` checks.
- Any Python helper scripts: use `uv run` with inline script dependencies.
- Public repo: never commit keystores or `local.properties`.

## Agent skills

### Issue tracker

GitHub Issues on `mcosgriff/waitlight`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage roles, each label named after its role. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
