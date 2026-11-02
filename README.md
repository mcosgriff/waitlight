# Waitlight

[![CI](https://github.com/mcosgriff/waitlight/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mcosgriff/waitlight/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/mcosgriff/waitlight?sort=semver)](https://github.com/mcosgriff/waitlight/releases/latest)
[![Licence: GPL-3.0](https://img.shields.io/github/license/mcosgriff/waitlight)](LICENSE)
[![Android 14+](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)](#install)

AirCare Colorado emissions-test wait times on your phone, nearest station first, with green / yellow / red
wait badges. Waitlight reads AirCare's public wait-times page itself, only during opening hours. It has no
account, no server, no ads and no tracking.

Not affiliated with AirCare Colorado or Envirotest. Wait times are AirCare's own estimates and don't include
time in line outside the ticket gate.

> **Status: in development, no release yet.** The business rules (stations, hours and holidays, badges,
> freshness, sorting) are built and tested; reading AirCare's page, location and the screens are next.

## What it does

What's planned, from the design in [`design/`](design/README.md):

- **All 18 AirCare stations, nearest first**, or sorted by **fastest overall**: estimated drive time plus the
  current wait. A card at the top names the fastest station right now.
- **Wait badges you can read without colour:** green under 30 minutes, yellow 30–60, red over 60, each with
  its own icon and a spoken description. Grey when a station is open with no estimate, or closed.
- **Honest about old data.** Each station goes by its own "Last updated" time from AirCare. A station more
  than 30 minutes behind is greyed with the time of its last reading, even when the others are current.
- **Knows the hours:** Mon–Fri 8:00–5:30, Sat 8:00–1:00, closed Sundays and Colorado state holidays. Outside
  them it says when the stations open next, and doesn't fetch.
- **Low-profile rims filter** for the four stations that test 17″+ rims (Sheridan, Boulder, Ken Caryl, Fort
  Collins).
- Station detail with lanes, phone and directions in Google Maps, and a home-screen widget.

## Privacy

- **One network host:** `aircarecolorado.com`, to read the public wait-times page. Nothing is sent to it but
  the page request; nothing is sent anywhere else.
- **Your location stays on the phone.** It's used only to measure the straight-line distance to each
  station, through Android's own location service, with no Google Play services. Without location
  permission the list is alphabetical.
- Drive times are estimates from that distance, always marked "≈". There's no routing service.
- No Google SDKs, analytics, crash reporting or ads. Directions open Google Maps by an ordinary link, which
  you choose to tap.

## Install

Waitlight isn't on the Play Store. Once the first release is out, add `https://github.com/mcosgriff/waitlight`
in [Obtainium](https://github.com/ImranR98/Obtainium), which installs and updates it from GitHub Releases,
or download the APK from the [latest release](https://github.com/mcosgriff/waitlight/releases/latest).
Android 14 or later.

## Verify your download

Every release APK is signed with the same key. Its SHA-256 certificate fingerprint will be published here
with the first release. Check an APK with `apksigner verify --print-certs waitlight-vX.Y.Z.apk`, or the
installed app with [AppVerifier](https://github.com/soupslurpr/AppVerifier).

To check that an APK was built by this repo's release workflow from its tag:
`gh attestation verify waitlight-vX.Y.Z.apk --repo mcosgriff/waitlight`. Each release also carries the signed
provenance as `waitlight-vX.Y.Z.apk.intoto.jsonl`, which `gh attestation verify` takes with `--bundle`.

## Building

| Module | What lives there |
|---|---|
| `:domain` | Pure Kotlin: the station list, hours and holidays, reading AirCare's wording, badges, freshness, sorting. Unit-tested, no Android. |
| `:data` | Fetching and reading AirCare's page, location, settings, the Hilt module. |
| `:app` | Jetpack Compose UI, Navigation 3, theme; later the widget and background refresh. |

1. Open this folder in Android Studio and let Gradle sync. JDK 17.
2. Run on a device or emulator with the `app` run configuration (debug app id `io.cosgriff.waitlight.debug`).
3. `./gradlew :domain:test` runs the business rules' tests in seconds; `./gradlew build` runs everything,
   lint included. Lint fails the build on a warning.
4. Upgrade the Android Gradle Plugin with Tools → AGP Upgrade Assistant.

Commit messages and PR titles follow [Conventional Commits](https://www.conventionalcommits.org/). The rules
a change is held to are in [`CLAUDE.md`](CLAUDE.md) and [`docs/rules/`](docs/rules/); the reasons behind the
big decisions are in [`docs/adr/`](docs/adr/); the words used for things are in [`GLOSSARY.md`](GLOSSARY.md).

## Releasing (maintainers)

1. Create a release keystore once and **back it up**. Every update must be signed with the same key
   ([ADR-0004](docs/adr/0004-application-id-and-signing-key-are-permanent.md)):
   ```
   keytool -genkeypair -v -keystore waitlight-release.jks -storetype PKCS12 -alias waitlight -keyalg RSA -keysize 4096 -validity 36500
   ```
2. Add the signing secrets to the `release` environment (Settings → Environments → release → Environment
   secrets), not as repository secrets. The environment needs a maintainer's approval and accepts only `v*`
   tags.
   - `KEYSTORE_B64`: `base64 -i waitlight-release.jks | gh secret set KEYSTORE_B64 --env release`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`waitlight`), `KEY_PASSWORD` (the same as the keystore's, for PKCS12)
3. Release: merge the open **release PR** (`chore(main): release X.Y.Z`). The `release-please` workflow keeps
   it up to date from the Conventional Commits on `main`: `fix:` bumps the patch version, `feat:` the minor,
   a breaking change the major. It updates `CHANGELOG.md` and `version.txt`. Merging tags `vX.Y.Z`, creates
   the GitHub Release and starts the signed build once CI has passed on that commit; approve the waiting
   `release` run in the Actions tab. Release tags can't be moved or deleted.

`applicationId` (`io.cosgriff.waitlight`) must never change.

## How Waitlight is made

Waitlight is written with an AI coding assistant, and this says how, so nobody has to guess.

- **I decide what it does and how it works.** What the app should do, what it must never do (talk to any
  host but AirCare, use Google's SDKs, show an old wait as if it were current), and the architecture that
  keeps those promises are my calls. They're written down, with their reasons, in [`CLAUDE.md`](CLAUDE.md),
  [`docs/rules/`](docs/rules/) and [`docs/adr/`](docs/adr/).
- **Claude writes most of the code.** It works from those decisions. Every commit it had a hand in says so
  (`Co-Authored-By: Claude`), so the history shows which work is which.
- **Nothing reaches `main` without me.** Every change is a pull request that I review and merge myself.
- **It's tested.** The business rules live in a pure Kotlin module with unit tests (the holiday list, for
  one, is checked against the state's published schedule), and CI runs them, lint and both builds on every
  pull request.

If you find something that looks careless, open an issue: it's mine to fix, whoever typed it.

## Licence

GPL-3.0 ([`LICENSE`](LICENSE)). Roboto Flex is under the SIL Open Font License
(`app/src/main/assets/licences/`).
