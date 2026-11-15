# Cairn

A small, calm Android app and set of home screen widgets that show your COROS training data (steps, sleep, recovery, last activity, training load, fitness) at a glance, with an honest note on how old the data is.

Cairn reads from the official COROS MCP server (and optionally Health Connect), caches everything on the phone and draws widgets from that cache.

## Features

- Home screen widgets: Overview (a grid of 1 to 6 stats you choose), Today, Recovery, Last activity, Training load, Sleep, Fitness. Each has three style variants, ten colour styles, Clean by default (a calm tonal card in the spirit of Google Health and Apple Health) plus the Daymark family (Dusk, Dawn, Forest, Midnight, Sunrise, Minimal, Ocean, Violet, Berry; gradient, solid or transparent with opacity), and a per-widget settings screen with a live preview. They adapt to the size you give them: bigger cells show more.
- Every widget has a freshness footer: fresh, aging or stale, in words and not colour alone. A stale footer opens COROS so you can sync.
- App screens: Today, Activities, Fitness, Settings, Diagnostics.
- Units (km or miles), eight accent colours (Dusk default) with light, dark or system mode and optional Material You, TalkBack labels, large font support, reduced motion.
- Background sync with WorkManager, kept light on battery.

## Screenshots (placeholders)

- `docs/screenshots/today.png` Today screen
- `docs/screenshots/activities.png` Activities
- `docs/screenshots/fitness.png` Fitness and training load
- `docs/screenshots/widgets-light.png` all widgets, light
- `docs/screenshots/widgets-dark.png` all widgets, dark
- `docs/screenshots/widget-stale.png` stale footer

## Build

Requirements: JDK 21, Android SDK (platform 36).

1. Create `local.properties` in the repo root (it is gitignored):
   ```properties
   sdk.dir=/path/to/Android/sdk
   ```
2. Build and test:
   ```bash
   ./gradlew assembleDebug testDebugUnitTest lint
   ./gradlew assembleRelease   # R8 on; signed with your debug keystore unless a release keystore is configured
   ```

Adding a real release keystore, versioning and R8 notes are in [docs/plan.md](docs/plan.md) section 12. Device test steps are in [docs/testing.md](docs/testing.md).

## Signing in to COROS

Cairn uses COROS's official OAuth sign-in for its MCP server. You sign in on COROS's own page in a browser tab; Cairn never sees your password. The resulting tokens are stored encrypted on the phone (Android Keystore) and are only ever sent to COROS. Signing out revokes them and "Delete data" clears the cache. Nothing is sent to any server run by Cairn: there isn't one.

## Honest limits

- Cairn cannot force your watch to sync. Data is only as fresh as COROS's last sync from the watch to its cloud. Open the COROS app to sync, then Cairn catches up.
- MCP replies are not a documented stable API. COROS can change them; Cairn parses defensively and shows less rather than wrong data, but things may break after a COROS update.
- Android may delay background work on battery saver or low standby buckets. The footer always shows the real age.

## Privacy

All data stays on your phone. No analytics, no ads, no crash reporting service. Tokens and health data are never logged. Backups of app data are disabled.

## Licence

Not chosen yet. Until the owner picks one, all rights are reserved. (Placeholder: ask the owner before adding a LICENSE file.)
