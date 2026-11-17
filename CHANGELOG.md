## 1.5.1 - 2026-11-17

### Fixed
- Widget previews in the gallery are sharp, drawn at their real size and proportions instead of stretched.
- Workouts without a distance (strength, cardio and so on) show a proper sport icon, the duration as the main number, and calories, heart rate and load beneath, instead of a single letter.
- Rounded shapes (freshness dot, pills, tiles) render round in previews.

# Changelog

All notable changes to Cairn are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed
- Widgets look right at 2x2, 4x2 and 4x3: Last activity, Training load and Fitness have real 2x2 layouts (no one-liner under 250 dp wide), Overview tiles share the width equally, the HRV range bar draws bar, band and marker in one bitmap, the Sleep legend is one entry per row with a taller stage bar, and Today / Overview values step down in size instead of being cut.
- The sync button is 40 x 40 dp (footer 40 dp; 4x1 strips keep 28 dp).
- Widgets redraw without network on font scale, dark mode, locale, time zone, date and clock changes and every 15 minutes (freshness dot and age keep moving offline); a stuck "Syncing..." clears after 90 seconds.
- The widget editor keeps unsaved edits across rotation and dark mode changes.
- Low-opacity cards keep their text at 4.5:1 over both black and white wallpapers where any ink can.

### Removed
- The "Your widgets" list on the Widgets home. Long-press a placed widget on the home screen and choose Reconfigure to edit it.

## [1.5.0] - 2026-11-16

### Added
- Long-press any card in Today, Activities or Fitness for **Add to home screen** or **Open in COROS**.
- A compact in-app update notice. Cairn checks AppDrop at most every 12 hours. Update opens the AppDrop page; Later and Skip this version are in a menu.

### Changed
- Widget-first app: the home tab is **Widgets**, with a gallery of every widget and a "Your widgets" list (with Edit). Widget setup moved out of Settings; the default look and tap targets are in the Widgets menu.
- The widget editor keeps its live preview pinned at the top while the options scroll.
- The system "Add to home screen" dialog shows the widget as you configured it.
- After signing in, Cairn returns to the Widgets home. The Settings COROS account row opens the COROS account screen.

### Fixed
- The sign-in screen can always be cancelled or retried.
- When signed out, the sync banner offers **Sign in** instead of a Retry that could never work.

## [1.4.0] - 2026-11-15

### Added
- Four new widgets, each with three styles, the 11 looks, the sync button and freshness dot, a live-preview editor and size variants (2x2, 4x2, 4x3):
  - **Overnight HRV**: `65 ms` with COROS's own assessment word as a coloured pill and "Normal range 46-58 ms". Range bar (default), 7 nights, or number and pill.
  - **Heart rate**: Resting HR as the main number with its 7-day average, plus today's average and min-max. Resting and 7-day line (default), min / average / max bar, or a pair.
  - **Stress**: today's average with a level word (Relaxed, Low, Medium, High). Number and level (default), 7-day bars coloured by level, or a gauge arc. The level bands are an assumption, as COROS does not publish them.
  - **Weekly mileage**: this week's distance Monday to Sunday (`25.10 km`), the activity count and the change against last week. Bars per day (default), goal ring, or number and trend. Per-widget weekly goal (30 km or 20 mi to start) and km or miles override.
- Overview can show Weekly mileage (HRV, Heart rate and Stress were already there). Existing stats keep their ids.
- Debug demo data now fills the four widgets (7 nights of HRV, 7 days of heart rate and stress, runs this week and last week).

- Sign-in rework: an HTTPS bounce page with a local fallback and code login, plus fixes for sign-in screens that got stuck.
- Settings: the COROS account row opens the account screen (sign-in methods, diagnostics, sign in again); the Tools entry is now "Open tools".
- Widgets refresh after an app update, and a widget that fails to load shows a short message instead of staying on "Loading".

### Changed
- Charts in the new widgets stretch to the full width of the widget; picker previews and fallback images for all new widgets.
- Demo data: last month's runs now sit on days 1 to 14 so they never overlap "last week".

## [1.3.0]

### Changed
- Values and layout now follow the COROS app: names, order, units and number formats (for example `5'00" /km`, `6 h 47 min`, Intensity Trend as a percent).

### Added
- Release signing with the Cairn release key, and an AppDrop listing.
- New app icon.

## [1.2.0]

### Added
- Sync button on every widget and app header, with no app opening.
- Freshness dots (green, amber, red) with a short age when data is old.
- Manual sync any time, and automatic refresh every 5, 15, 30 or 60 minutes.
- Widget studio: pick a widget, its look and a size, then add it to the home screen already styled.
- Overview widget sizing fixes and a Customise screen with five sections (Style, Colour, Content, Size, Tap).

## [1.1.0]

### Changed
- New look based on the Daymark palette, with the Clean widget style as the default.

### Added
- Overview widget: a grid of 1 to 6 stats you choose.

## [1.0.0]

### Added
- Polish pass and first release build (R8 on, release signing configuration).

## [0.7.0]

### Added
- App screens: Today, Activities, Fitness, Settings and Diagnostics.
- Six widgets: Today, Recovery, Last activity, Training load, Sleep and Fitness.

## [0.6.0]

### Added
- Freshness helpers: tap a widget to open COROS and refresh after it syncs; optional stale reminder.

## [0.5.0]

### Added
- First widgets: Today, Recovery and Last activity.

## [0.4.0]

### Added
- Local data layer: cache, repositories, source merging and freshness.

## [0.3.1]

### Fixed
- COROS replies that arrive as JSON-encoded text are unwrapped correctly.

## [0.3.0]

### Added
- Sign in to COROS with its official OAuth page (MCP server). No password is stored.
- Automatic region detection for sign-in.

## [0.2.0]

### Added
- Health Connect inspector, to see what COROS writes to Health Connect.

## [0.1.0]

### Added
- Project scaffold: an Android app with Compose and an empty first screen.
