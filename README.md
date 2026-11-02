# Waitlight

AirCare Colorado emissions-test wait times on your phone, nearest station first, with green / yellow / red
wait badges. Waitlight reads AirCare's public wait-times page during opening hours; it has no account, no
server, no ads and no tracking.

Not affiliated with AirCare Colorado or Envirotest. Wait times are AirCare's estimates and don't include time
in line outside the ticket gate. No station does diesel testing.

## Install

Waitlight isn't on the Play Store. Add `https://github.com/mcosgriff/waitlight` in
[Obtainium](https://github.com/ImranR98/Obtainium), or download the APK from the latest release.
Android 14 or later.

## Building

Open the folder in Android Studio. JDK 17.

| Command | What it runs |
|---|---|
| `./gradlew :domain:test` | The business rules: pure Kotlin, fast. |
| `./gradlew build` | Everything, lint included. Lint fails on a warning. |

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/); `release-please` builds
the version and changelog from them. The rules a change is held to are in [`CLAUDE.md`](CLAUDE.md) and
[`docs/rules/`](docs/rules/); the reasons behind the big decisions are in [`docs/adr/`](docs/adr/).

## Licence

GPL-3.0 (`LICENSE`). Roboto Flex is under the SIL Open Font License (`app/src/main/assets/licences/`).
