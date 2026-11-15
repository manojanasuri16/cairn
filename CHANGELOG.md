# Changelog

All notable changes to Cairn are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.4.0] - Unreleased

### Added
- Four new widgets, each with three styles, the 11 looks, the sync button and freshness dot, a live-preview editor and size variants (2x2, 4x2, 4x3):
  - **Overnight HRV**: `65 ms` with COROS's own assessment word as a coloured pill and "Normal range 46-58 ms". Range bar (default), 7 nights, or number and pill.
  - **Heart rate**: Resting HR as the main number with its 7-day average, plus today's average and min-max. Resting and 7-day line (default), min / average / max bar, or a pair.
  - **Stress**: today's average with a level word (Relaxed, Low, Medium, High). Number and level (default), 7-day bars coloured by level, or a gauge arc. The level bands are an assumption, as COROS does not publish them.
  - **Weekly mileage**: this week's distance Monday to Sunday (`25.10 km`), the activity count and the change against last week. Bars per day (default), goal ring, or number and trend. Per-widget weekly goal (30 km or 20 mi to start) and km or miles override.
- Overview can show Weekly mileage (HRV, Heart rate and Stress were already there). Existing stats keep their ids.
- Debug demo data now fills the four widgets (7 nights of HRV, 7 days of heart rate and stress, runs this week and last week).

### Changed
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
