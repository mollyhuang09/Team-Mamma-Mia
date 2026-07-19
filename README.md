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
3. Set `MAPS_API_KEY` to a Google Maps API key
4. Sync Gradle / rebuild. Never commit `local.properties` — only `local.properties.example` should be tracked.
