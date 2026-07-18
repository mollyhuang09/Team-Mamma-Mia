# Team-Mamma-Mia

# MyTeam

We will add a project description here once we decide what to do!

- Members: Molly Huang (mollyhuang09), Grace Zhang (gzjiayi), Angel Quach (AngelQuach), Taylor Yin (Taylor-TTT), Amber Yang (AmberY17), Yoana Yun (yyun02)
  
- Links:
  - [Team contract](./docs/team-contract.md)
  - [Meeting minutes](./docs/meetings/)

## Setup

The app needs a Google Maps API key to build, which is kept out of git via `local.properties` (already gitignored).

1. Copy the template and fill in your values:
   ```
   cp local.properties.example local.properties
   ```
2. Set `sdk.dir` to your local Android SDK path (Android Studio usually fills this in automatically on first sync).
3. Set `MAPS_API_KEY` to a Google Maps API key with the "Maps SDK for Android" API enabled:
   - Use the team-shared key (ask in the team chat — never post it in git, README, or issues), or create your own in the [Google Cloud Console](https://console.cloud.google.com/).
   - Restrict the key to Android apps, scoped to package name `com.studypin.app` + your machine's SHA-1 signing fingerprint, so it can't be abused if it leaks.
4. Sync Gradle / rebuild. Never commit `local.properties` — only `local.properties.example` should be tracked.
