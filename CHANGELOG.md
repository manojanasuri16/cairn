# Changelog

All notable changes to Cairn are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
