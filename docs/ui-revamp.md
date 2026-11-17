# Cairn UI revamp: modern Material 3, Daymark colours

Status: design only. Nothing in this document has been implemented yet. It is written so that a coding agent can build it one work package at a time (section 6) without having to make design decisions of its own.

Owner brief: "revamp the entire app, make it modern like some Google apps, material UI, more modern robust aesthetic".

These earlier requirements still apply and override anything below that contradicts them:

| # | Requirement | Where it is enforced |
|---|---|---|
| H1 | Colours follow the **Daymark palette** (8 accents, Dusk default, neutral surfaces), not pastel | 2.1, `AppColors.kt`, `PaletteTest` |
| H2 | Light **and** dark mode, plus System | 2.1, Settings |
| H3 | Values are formatted **exactly like the COROS app** (this is done in `Mappers.kt`; the UI never re-formats) | 1, R3 acceptance |
| H4 | Data freshness is a **coloured dot** (green fresh, amber getting old, red stale), with wording as a second channel | `FreshnessDot`, 3.0 |
| H5 | Long-press on any data card opens **Add to home screen / Open in COROS** | `Modifier.widgetActions`, 3.10 |
| H6 | **Widget-first** IA: Widgets is the start tab, then Today / Activities / Fitness | 3.1 |
| H7 | **Everything fits its width**: no truncated values, no ellipsis on numbers, font scale 2.0 works | 5, `FitText` |
| H8 | **Sticky** widget-editor preview | 3.2 |
| H9 | **Compact** in-app update banner (one row, never modal) | 3.9 |

---

## 0. What exists today (read before changing anything)

- Theme: `core/designsystem/Theme.kt` builds the `ColorScheme` from `AppColors.roles(accent, dark)` (`AppColors.kt`). Optional Material You through `AppLook.dynamic`. `LocalCairnPalette` holds the hero gradient and `LocalCardColors` holds the semantic colours (good, warn, bad, cool and the sleep stages). The typography is the M3 default with tabular figures (`cairnTypography`).
- Tokens: `Spacing` (4/8/12/16/24/32 dp) and `CairnMotion` (critically damped spring, stiffness 320) are in `Motion.kt`. `HeroSurface` (28 dp gradient) is in `Cards.kt`. `ProgressRing`, `Sparkline`, `LoadBars` and `SegmentBar` are in `Charts.kt`.
- Shell: `app/ui/CairnRoot.kt` has one `Scaffold` that owns every top bar (`CenterAlignedTopAppBar` on tabs, `TopAppBar` on sub-screens), a plain `NavigationBar`, a snackbar and the long-press `CardActionsSheet`. `MainActivity` already calls `enableEdgeToEdge()`, and `android:enableOnBackInvokedCallback="true"` is already set in the manifest.
- Shared app-level UI (`app/ui/Components.kt`): `SectionCard`, `HeroCard`, `LevelPill`, `LegendItem`, `MetricRow`, `EmptyState`, `AgeLine`, `SectionTitle`. Also `FitText.kt` (`FitText`, `AdaptiveTiles`), `CardWidgets.kt` (`Modifier.widgetActions`), `SyncStatus.kt` (`SyncStatusBar`, `SyncErrorBanner`) and `UpdateBanner.kt`.
- Screens: `WidgetsHome.kt`, `WidgetStudio.kt` + `WidgetCustomizer.kt` (the editor; `WidgetConfigActivity.ConfigScreen` reuses it), `TodayScreen.kt`, `ActivitiesScreen.kt`, `FitnessScreen.kt`, `SettingsScreen.kt` (+ `ToolsScreen`, `TapTargetsScreen`), `ThemeUi.kt` (`DefaultLookScreen` content), `OnboardingScreen.kt`, `app/CorosAccountScreen.kt`, `app/FreshnessScreens.kt`, `app/InspectorScreen.kt`, `app/SyncDiagnosticsScreen.kt`.
- Icons: `AppIcons.kt` holds hand-copied Material *filled* path data. `material-icons-extended` is not a dependency.
- Library versions: Compose BOM `2025.05.01` (material3 1.3.x) and navigation-compose 2.9.0.

What looks dated today, and the fix for each:

1. Small centred titles with no hierarchy. Fix: a large top app bar that collapses.
2. Every card has the same 12 dp corners and 16 dp padding, and the chip rows wrap. Fix: larger shapes, grouped lists, segmented buttons.
3. Settings is a stack of cards full of chips. Fix: a Pixel-Settings-style grouped list with value rows that open choice sheets.
4. The editor preview uses a grey box with a drop shadow. Fix: a tonal "stage" with rounded corners.
5. Loading is a blank screen. Fix: skeleton placeholders.
6. Emoji and arrow glyphs (`🎲`, `▲▼`, `✓`) are used as icons. Fix: real icons.
7. The navigation bar uses the same filled icons whether or not a tab is selected. Fix: outlined icons that turn filled inside the pill indicator.

---

## 1. Design principles

These follow current Google apps: Google Fit / Health Connect, Pixel Settings, Pixel Weather and Material 3 Expressive.

1. **Content is the hero, chrome is quiet.** Neutral tonal surfaces (Daymark `#F7F7F5` / `#0F1012`) carry the screen. The accent colour appears only on interactive or selected elements, data marks (rings, bars) and the one hero card per screen.
2. **Large, soft, confident shapes.** Cards use 24 dp corners, heroes and sheets 28–32 dp, and buttons and selection indicators are pills. Group related rows into one rounded block instead of adding many separate cards.
3. **Tonal, not shadowed.** Depth comes from surface-container steps (Lowest → Highest). Only the FAB and modal surfaces get a shadow. A scrolled top bar changes colour; it does not cast a shadow.
4. **Big headline, then it collapses.** Every top-level screen opens with a large title (Large Top App Bar, 152 dp expanded) and collapses to 64 dp as you scroll. Sub-screens use the medium bar. The editor uses the small bar because the preview needs the height.
5. **Generous type, tabular numbers.** Hero numbers are display-sized and use tabular figures. Labels sit in the secondary colour (`onSurfaceVariant`). Text is never smaller than 12 sp at font scale 1.0.
6. **Springs, not tweens.** Motion uses the M3 spring scheme (section 2.7). Navigation is calm (no overshoot). Small selection feedback (nav pill, chips, FAB expand) may bounce a little. With "Remove animations" on, everything becomes an instant fade.
7. **Edge-to-edge, gesture-native.** Content scrolls behind transparent system bars. Predictive back previews the destination for sub-screens, sheets and the onboarding steps.
8. **One primary action per screen.** Widgets: Extended FAB "Add widget". Editor: "Add to home screen". Today, when data is stale: Extended FAB "Open COROS". Everything else is tonal or text.
9. **Honest data.** Values come pre-formatted from the mappers exactly as COROS shows them (H3). Freshness is always visible as a dot plus words (H4). Stale data is never hidden or greyed out.
10. **Dynamic colour is opt-in.** Daymark Dusk is the default. Material You only replaces the accent roles. Semantic colours (recovery, load, sleep stages, freshness) never change.

Material 3 Expressive APIs: the expressive components (`MaterialExpressiveTheme`, `LargeFlexibleTopAppBar`, `ShortNavigationBar`, `ButtonGroup`, `LoadingIndicator`, `MaterialShapes`) are marked `@ExperimentalMaterial3ExpressiveApi` and ship in specific material3 versions. **This design is buildable entirely with stable material3 APIs** (`LargeTopAppBar`, `MediumTopAppBar`, `NavigationBar`, `ExtendedFloatingActionButton`, `SingleChoiceSegmentedButtonRow`, `ModalBottomSheet`, `PullToRefreshBox`, `ListItem`). R1 bumps the Compose BOM to the newest **stable** BOM (check the BOM-to-library mapping page). If that material3 version also exposes the expressive APIs, the optional upgrades marked "(Expressive, optional)" may be used behind `@OptIn`. Never take an alpha dependency for them.

---

## 2. Tokens

All tokens live in `core/designsystem`. Colours stay plain ARGB `Int`/`Long` in `AppColors.kt`, so `PaletteTest` can check contrast on the JVM.

### 2.1 Colour roles (Daymark, Dusk accent shown)

The surfaces are the same for all 8 accents. For the other accents, `primary`, `primaryContainer` and `onPrimaryContainer` come from the existing `Accent` enum values (`light`/`dark`, `containerLight`/`containerDark`, `onContainerLight`/`onContainerDark`); **do not change those values**. Rows marked **new** or **changed** are the only edits to `AppColors.roles` and `Theme.scheme`.

| Role | Light | Dark | Notes |
|---|---|---|---|
| primary | `#4C55C7` | `#B4BAFF` | accent.light / accent.dark |
| onPrimary | `#FFFFFF` | `#0E1257` | 6.1:1 / 9.2:1 |
| primaryContainer | `#E0E2FF` | `#2E3478` | nav pill, selected chips, tonal buttons |
| onPrimaryContainer | `#0E1257` | `#E0E2FF` | 13.3:1 / 8.8:1 |
| secondary / tertiary | = primary | = primary | unchanged (Daymark is one-accent) |
| secondaryContainer | = primaryContainer | = primaryContainer | M3 uses it for the nav indicator and filter chips |
| background, surface | `#F7F7F5` | `#0F1012` | |
| surfaceDim **new** | `#E3E3E1` | `#0F1012` | pressed backdrop behind sheets |
| surfaceBright **new** | `#FFFFFF` | `#2B2D33` | |
| surfaceContainerLowest | `#FFFFFF` | `#0B0C0E` | |
| surfaceContainerLow | `#FFFFFF` | `#16171A` | grouped-list rows (light: same as card) |
| surfaceContainer | `#FFFFFF` | `#191A1E` | **cards**, nav bar, scrolled top bar |
| surfaceContainerHigh | `#F0F0F2` | `#212328` | tiles inside cards, editor preview stage |
| surfaceContainerHighest | `#E9E9EC` | `#2B2D33` | thumbnails backdrop, skeleton blocks, chart tracks |
| surfaceVariant | `#E6E6E9` | `#2B2D33` | |
| onSurface | `#1A1B1E` | `#E4E4E8` | 16.1:1 / 15.0:1 |
| onSurfaceVariant | `#5E6068` | `#AAADB6` | ≥4.6:1 on every surface above |
| outline **changed** | `#80828A` (was `#C3C4CA`) | `#8A8D96` (was `#5B5E66`) | ≥3:1 on all surfaces: outlined buttons, text fields, unselected tiles |
| outlineVariant | `#E0E0E4` | `#30323A` | dividers only (decorative) |
| error | `#C4453B` | `#F08A80` | |
| onError **new** | `#FFFFFF` | `#3A0B07` | 4.9:1 / 7.0:1 |
| errorContainer **new** | `#FCE4E1` | `#4A1D19` | sync error banner |
| onErrorContainer **new** | `#5C1610` | `#FFDAD5` | 11.0:1 |
| inverseSurface **new** | `#2E3033` | `#E4E4E8` | snackbar |
| inverseOnSurface **new** | `#F1F1F3` | `#1A1B1E` | |
| inversePrimary **new** | `#B4BAFF` | `#4C55C7` | snackbar action (accent.dark / accent.light) |

Semantic colours (`AppColors.card`, unchanged values; they are graphic colours at 3:1, and text uses `Contrast.ensure(c, bg, 4.5)`):

| Token | Light | Dark | Used for |
|---|---|---|---|
| good | `#2F8F68` | `#4CC38F` | fresh dot, recovery ≥80, load 0.8–1.3 |
| warn | `#B07A14` | `#E8B964` | getting-old dot, recovery 50–79, load 1.3–1.5 |
| bad | `#C9473C` | `#F07C72` | stale dot, recovery <50, load >1.5 |
| cool | `#2A7FB8` | `#7CC4F5` | load <0.8, base fitness series |
| deep / light / rem / awake | `#4C55C7` `#2A7FB8` `#AE426D` `#B8641A` | `#9AA3FF` `#7CC4F5` `#F0A0CB` `#F2B87A` | sleep stages |

Hero gradient: keep `AppColors.hero(accent, dark)` and `HeroSurface`. White ink, fitted to AA by `Palettes.hero`.

Dynamic colour (optional, off by default): when `AppLook.dynamic` is on (API 31+), use `dynamic{Light,Dark}ColorScheme` but then **overwrite** `background`, `surface*`, `outline*` and `error*` with the Daymark neutral values above. Only the accent family becomes dynamic, so cards keep the Daymark look. The hero uses `Palettes.hero(dynamic.primary, blend(black, dynamic.primary, 0.45), dark, tint)`. Semantic colours never change. In Settings, the accent swatches are disabled while dynamic is on, with the supporting text "Using your wallpaper colours".

### 2.2 Shape scale

Add `cairnShapes` and pass it to `MaterialTheme(shapes = …)`. Also add an object `CairnShapes` for the extra sizes that M3 stable does not name.

| Token | Radius | Used for |
|---|---|---|
| extraSmall | 4 dp | inner corners of grouped-list middle rows, chart bars |
| small | 8 dp | chips, tiny tags, progress tracks |
| medium | 12 dp | tiles inside cards (stat tiles, race tiles), thumbnails |
| large | 16 dp | text fields, menus, the update banner |
| CairnShapes.largeIncreased | 20 dp | look/layout selectable tiles, editor preview stage |
| extraLarge | 28 dp | **cards** (`SectionCard`), dialogs, bottom sheets (top corners) |
| CairnShapes.hero | 32 dp | hero card, empty-state illustration circle backdrop |
| CairnShapes.group | outer 24 dp / inner 4 dp | grouped lists (Pixel Settings style), with 2 dp gaps between rows |
| full / `CircleShape` | 50 % | buttons, FAB, nav indicator, pills, avatars, freshness dot |

Widget shapes on the home screen are **not** affected (they follow `Corners` / the launcher).

### 2.3 Type scale

Base: the M3 default type scale with the system font (Roboto / Roboto Flex on Pixel). **No bundled font**, which keeps the APK small and respects OEM fonts. Keep `cairnTypography()` (tabular figures everywhere) and change only these rows:

| Style | Size / line | Weight | Tracking | Use |
|---|---|---|---|---|
| displayLarge | 57 / 64 | Normal | -0.25 | not used in screens |
| displayMedium | 45 / 52 | SemiBold | -0.5 | recovery hero number |
| displaySmall | 36 / 44 | SemiBold | -0.25 | sign-in device code, big hero numbers |
| headlineLarge | 32 / 40 | Medium (**changed** from Normal) | -0.25 | card hero values (distance, load, VO2) |
| headlineMedium | 28 / 36 | Medium (**changed**) | 0 | expanded large top-bar title, tile values |
| headlineSmall | 24 / 32 | Medium (**changed**) | 0 | empty-state title, sheet titles |
| titleLarge | 22 / 28 | Medium (**changed**) | 0 | month header, collapsed bar title |
| titleMedium | 16 / 24 | Medium | 0.15 | card titles, list headlines |
| titleSmall | 14 / 20 | Medium | 0.1 | group headers (in `primary`) |
| bodyLarge | 16 / 24 | Normal | 0.5 | list headline text, metric labels |
| bodyMedium | 14 / 20 | Normal | 0.25 | supporting text |
| bodySmall | 12 / 16 | Normal | 0.4 | captions, hints |
| labelLarge | 14 / 20 | Medium | 0.1 | buttons, status line |
| labelMedium | 12 / 16 | Medium | 0.5 | nav labels, tile captions |
| labelSmall | 11 / 16 | Medium | 0.5 | nav labels at font scale ≥ 1.3 only |

Numeral roles go in a new `CairnTypeExtras` provided through `LocalCairnType` and read as `CairnTheme.type.x`:

- `heroNumeral` = displayMedium, SemiBold, tnum, -0.5. Ring centre, Recovery.
- `statNumeral` = headlineLarge, Medium, tnum. Card hero values.
- `tileNumeral` = headlineMedium, Medium, tnum. Vital tiles, race predictions.
- `unit` = labelLarge, `onSurfaceVariant`, baseline-aligned with the numeral.

### 2.4 Spacing and layout

The 4 dp grid stays as it is. Add `Spacing.xxl = 48.dp` and these layout tokens (object `CairnLayout`):

| Token | Value |
|---|---|
| screenGutter | 16 dp (< 600 dp wide), 24 dp (≥ 600 dp) |
| cardPadding | 20 dp (SectionCard), 16 dp (compact tiles) |
| cardGap | 12 dp between cards |
| groupGap | 24 dp between groups / sections, plus 8 dp above a group header |
| listRowMinHeight | 56 dp one-line, 72 dp two-line |
| maxContentWidth | 640 dp; content is centred on wide screens; the widget gallery grid uses 2 columns ≥ 600 dp |
| fabClearance | 88 dp bottom content padding when a FAB is visible |

### 2.5 Elevation and tonal rules

- Cards: `surfaceContainer`, tonal elevation 0, shadow 0, **no border**.
- Tiles inside cards: `surfaceContainerHigh`. Things inside tiles (tracks, thumbnails backdrop): `surfaceContainerHighest`.
- Top app bar: `background` when at rest, `surfaceContainer` once content scrolls under it (the default `scrolledContainerColor`).
- Navigation bar: `surfaceContainer`. Indicator: `secondaryContainer` (= accent container).
- FAB: `primaryContainer` / `onPrimaryContainer`, elevation level 3 (6 dp), pressed level 4.
- Bottom sheets and dialogs: `surfaceContainerLow` / `surfaceContainerHigh` (M3 defaults), with a scrim.
- The editor preview stage is `surfaceContainerHigh` with no shadow. When options scroll under the sticky stage, draw a 1 dp `outlineVariant` divider under it (remove the current `shadow(4.dp)`).
- Never use drop shadows on cards in dark mode.

### 2.6 Iconography

- Style: **Material Symbols Rounded look**, 24 dp, 2 dp stroke weight equivalent. Use **outlined** at rest and **filled** when selected (nav bar, segmented buttons).
- Source: add `androidx.compose.material:material-icons-extended` (version from the BOM) and use `Icons.Rounded.*` / `Icons.Outlined.*`. Release builds have `isMinifyEnabled = true`, so R8 strips unused icons. Keep `AppIcons` as the single facade (`AppIcons.Sync = Icons.Rounded.Sync`, …) so that screens never import icon packs directly, and keep the custom sport glyphs.
- Tabs: Widgets `Widgets` (Outlined/Rounded), Today `Today`, Activities `AutoMirrored.DirectionsRun`, Fitness `MonitorHeart`.
- Replace glyph-as-icon cases: `🎲` → `Casino`; `▲`/`▼` → `ArrowUpward`/`ArrowDownward`; `✓` text → `Check` icon; the Settings gear in the top bar → `AccountButton` (2.8).
- Icon colours: `onSurfaceVariant` for actions; `primary` for leading icons in list rows and card titles; `onPrimaryContainer` inside tonal containers.

### 2.7 Motion

Replace `CairnMotion` with the M3 spring scheme. Keep the old `settle()`/`flick()` as aliases until every caller has moved.

| Token | dampingRatio | stiffness | Use |
|---|---|---|---|
| spatialFast | 0.9 | 1400 | chip/segment selection, small toggles |
| spatialDefault | 0.9 | 700 | card expand, FAB expand/collapse, sheet content |
| spatialSlow | 0.9 | 300 | screen transitions |
| effectsFast | 1.0 | 3800 | colour/alpha on press |
| effectsDefault | 1.0 | 1600 | fades, banner appear |
| effectsSlow | 1.0 | 800 | cross-fade of tabs |
| bouncy (Expressive) | 0.6 | 800 | nav pill indicator, selected look tile check badge only |

- Tabs: cross-fade (effectsSlow) plus a 2 % scale-in.
- Sub-screen push: slide in from 1/5 width plus fade (spatialSlow). Pop: the reverse.
- With `LocalReducedMotion`, everything snaps or fades instantly (existing behaviour), and the sync icon shows a still clock instead of spinning.
- Number changes animate with `AnimatedContent` (slide up 8 dp plus fade, spatialFast) only in hero numbers; plain values swap instantly.

**Predictive back.** navigation-compose 2.9 already drives `popEnterTransition`/`popExitTransition` from the back gesture. Make popExit `scaleOut(targetScale = 0.9f) + fadeOut` and popEnter `fadeIn + scaleIn(initialScale = 1.05f)` so the gesture scrubs them. `ModalBottomSheet` handles predictive back by itself. Onboarding uses `PredictiveBackHandler` to scrub the step transition.

### 2.8 Edge-to-edge and window insets

- `enableEdgeToEdge()` is already called in `MainActivity` and `WidgetConfigActivity`. Keep it.
- Outer `Scaffold` (`CairnRoot`): `contentWindowInsets = WindowInsets(0)`, plus `bottomBar` = navigation bar on tabs. Wrap the NavHost in `Modifier.padding(bottom = outer.calculateBottomPadding()).consumeWindowInsets(outer)`.
- Each screen draws its own top bar through `CairnScreen` (section 4), which applies status-bar insets. Lazy lists put the navigation-bar inset into `contentPadding` (not `Modifier.padding`) so content scrolls behind the gesture bar on sub-screens.
- Sheets: `ModalBottomSheet` defaults. Content uses `navigationBarsPadding()` (already done).

---

## 3. Screen-by-screen spec

### 3.0 Shared states (every data screen: Widgets, Today, Activities, Fitness)

| State | Trigger (existing models) | Presentation |
|---|---|---|
| Loading (first) | `state.loading && state.empty == null` with no rows/cards yet | 3 `SkeletonCard`s (heights 120/184/120 dp, `surfaceContainerHighest`, card shape, pulsing alpha 0.6↔1.0 at effectsSlow; static when reduced motion). Top bar and status row are already visible. `semantics { contentDescription = "Loading" }` on the column. |
| Refreshing | `SyncUi.syncing` | The pull-to-refresh indicator (when pulled) **and** the sync icon in the top bar spins. Content stays. |
| Empty | `state.empty: EmptyUi` | `EmptyState`: 96 dp circle in `primaryContainer` with a 40 dp icon (`CloudOff` for no source, `Login` for sign-in, `Sync` for nothing synced), `headlineSmall` title, `bodyMedium` hint, a `FilledTonalButton` "Open COROS account" when `EmptyAction.OpenAccount`. Centred, max 360 dp wide, 48 dp from the top. |
| Error | `SyncUi.error` with `ProblemKind` ∈ {NotSignedIn, NeedsSignIn, Network, Server, Partial, CapReached, Other} | `Banner(tone = Error)` at the top of content: `errorContainer`, `Warning` icon, message, action from `SyncProblems.label(...)` (Sign in / Retry), and a close button. Data below stays visible. |
| Offline | `ProblemKind.Offline` | `Banner(tone = Neutral)`: `surfaceContainerHigh`, `CloudOff` icon, "You're offline. Showing what Cairn saved." plus Retry. It is not red. |
| Stale | `Indicator.kind == Stale` (or Aging) | `StatusRow` dot turns red (amber) and the text shows the short age, e.g. "Stale · 2 d" (existing `StatusLine.text`). Values are **not** dimmed. **Today only**: an Extended FAB "Open COROS" (icon `OpenInNew`) appears. |
| Up to date flash | `SyncUi.upToDate` | `StatusRow` becomes a `secondaryContainer` pill with a `Check` icon and "Up to date" for 3 s (existing logic). |

`StatusRow` layout (replaces the visual part of `SyncStatusBar`): one 48 dp row at the very top of the content, under the top bar. It contains a 10 dp `FreshnessDot` and a `labelLarge` text (only when the data is not fresh, as today). The sync `IconButton` **moves into the top-bar actions** (`SyncAction`), so it stays reachable after the bar collapses. A long press on the row shows the exact age (existing). TalkBack reads `Indicator.description`.

### 3.1 App shell (`CairnRoot`)

- Outer `Scaffold`: snackbar host, plus the bottom navigation on tab routes only.
- `CairnNavigationBar`: 4 items (Widgets, Today, Activities, Fitness). The pill indicator is M3's default 64×32 dp in `secondaryContainer`. The selected icon is filled, unselected icons are outlined. Labels are always shown below 1.8× font scale; at ≥ 1.8× only the selected item keeps its label, and every item gets a `contentDescription` (existing rule). Height 80 dp.
- Every destination renders `CairnScreen(chrome = ScreenChrome(...))` (section 4). Tab screens use `TopBarStyle.Large`; sub-screens use `Medium` with a back arrow; the editor uses `Small`.
- Tab top-bar actions, right to left: `AccountButton` (40 dp circle, `surfaceContainerHighest`, `Person` icon, small `error` badge dot when COROS needs sign-in; opens Settings; label "Settings and account"), then `SyncAction`. The Widgets tab also gets an overflow menu (Default look, Tap targets).
- The long-press sheet and snackbar stay in the root.

### 3.2 Widgets home (`WidgetsHome.kt`), start destination

Layout (`LazyVerticalGrid`, 1 column < 600 dp, 2 columns ≥ 600 dp, items spanning full width where noted):

1. *(full span)* `UpdateBanner`.
2. *(full span)* `StatusRow`.
3. *(full span, optional R2b)* **"On your home screen"**: header plus a horizontal `LazyRow` of placed widgets (`placedWidgets(context)`). Each one is a 160×120 dp `SelectableTile` that shows the live thumbnail of that widget's saved config, with the kind label underneath. Tapping it opens the in-app editor for that `appWidgetId` (new route `widgets/edit/{id}`, reusing `ConfigScreen`'s body). The section is hidden when there are no placed widgets.
4. *(full span)* Section header "Add a widget" (`titleSmall`, primary) and the one-line hint (`bodyMedium`, `onSurfaceVariant`). The hint text stays as it is today, including the launcher-cannot-pin variant.
5. Gallery cards (`WidgetGalleryCard`):
   - Container `surfaceContainer`, 28 dp corners, 12 dp internal padding.
   - **Stage**: `surfaceContainerHighest`, 20 dp corners, 16 dp padding, min height 140 dp, centred `WidgetThumbnail` of the current size.
   - Title row: `kind.label` in `titleMedium`, with a trailing `ChevronRight` (decorative). Blurb in `bodyMedium`, `onSurfaceVariant`, wraps freely.
   - Size: `SegmentedChoice` with 3 segments (labels `s.cells`, e.g. "2×2"). At font scale ≥ 1.5 it falls back to a FlowRow of `FilterChip`s (built into `SegmentedChoice`).
   - The whole card above the size control is one click target ("Customise <kind> widget").
6. **Extended FAB** "Add widget" (`Add` icon). It is expanded when the top bar is expanded and collapses to a 56 dp FAB as the bar collapses. It opens `WidgetKindSheet`: a `ModalBottomSheet` with a 2-column grid of kinds (icon + label + one-line blurb). Picking one opens the studio with the suggested size. When pinning is unsupported, the FAB label is "Design a widget".

States: the widget gallery never waits for data (thumbnails use synthetic samples), so there is no loading or empty state. Error and offline banners show under the status row.

**Default look** (sub-screen, Medium bar): `WidgetAppearanceCard` restyled. The preview strip goes inside a stage, the style swatches become a `LazyRow` of `SelectableTile`s, background becomes a `SegmentedChoice` (Gradient / Solid / Transparent), and opacity becomes a `Slider` with a value label.

**Tap targets** (sub-screen): `GroupedList` with one row per widget kind (`headline` = kind label, `supporting` = current target description, trailing chevron) → existing `TapTargetPickerDialog` restyled as a `ChoiceSheet`.

### 3.3 Widget editor (`WidgetStudioScreen`, `WidgetConfigActivity.ConfigScreen`, `WidgetCustomizer`)

The structure is unchanged (H8: the preview is **sticky** and the options scroll). Restyle only:

- Top bar: `Small`. Title "Customise <Kind>". Actions: `Undo` (enabled per `EditorUndo.canUndo`) and `RestartAlt` "Reset look" IconButtons. These replace the text-button row. "Apply this look to all my widgets" and "Undo apply to all" move into an overflow menu.
- **Preview stage** (sticky): an outer 16 dp gutter, then `PreviewStage`: `surfaceContainerHigh`, 20 dp corners, 12 dp padding. Inside is a "wallpaper" backdrop (default `#6B7280`, as today) with 16 dp corners, holding the `ScaledBox` live preview. Under the stage is a 32 dp row with the caption "Live preview · Wide 4×2" (`labelMedium`, `onSurfaceVariant`) and, right-aligned, a 2-segment mini toggle "Light / Dark wallpaper" (`#D7DAE0` / `#2A2D33`) so transparent looks can be checked. The height rules stay in `EditorLayout` (35 % / 30 %), and side-by-side ≥ 600 dp is unchanged.
- Mode switch: replace the "Advanced" `Switch` with a full-width `SegmentedChoice` **Quick | Advanced** directly under the stage.
- Quick panel:
  - **Look** row: `LazyRow` of `SelectableTile` (120 dp wide, 20 dp corners). Selected tiles get a 3 dp `primary` border plus a 20 dp `primary` check badge in the top-end corner; unselected tiles get a 1 dp `outline` border. The last tile is "Surprise me" with the `Casino` icon.
  - **Layout** row: the same tile style.
  - **Size**: `SegmentedChoice`.
  - The note text under the size control stays.
- Advanced panel: the section switcher becomes a `PrimaryScrollableTabRow` with icon + label tabs (Style, Colour, Content, Size, Tap), icon-only at font scale ≥ 1.5 (existing rule). Inside each section:
  - Style: `OptionRow`s become `GroupedListItem` with a radio (`role = RadioButton`).
  - Colour: theme swatches in a `LazyRow`. "Match the app" is the first swatch. Background uses `SegmentedChoice` and opacity a `Slider`.
  - Content: Overview stats use a `GroupedList` whose reorder buttons are `ArrowUpward`/`ArrowDownward`/`Close` 48 dp IconButtons; "Add a stat" uses `FilterChip`s. Part toggles become `GroupedListItem`s with a trailing `Switch` (instead of a leading `Checkbox`).
  - Size: radio list.
  - Tap: `GroupedListItem` "When tapped" → opens the picker.
  - The "More options" expander becomes a `GroupedListItem` with an `ExpandMore`/`ExpandLess` trailing icon.
- Bottom bar (pinned): `Surface(surfaceContainer)` with a 1 dp `outlineVariant` top divider while content is scrolled under it, 16 dp padding plus nav-bar insets.
  - Studio: a 56 dp full-width `Button` "Add to home screen" with the `Add` icon. The error message sits above the button in `error` colour.
  - Config: `OutlinedButton` Cancel and `Button` Done, both 56 dp and weighted 1:1.
- States:
  - Preview rendering failure: the stage shows `BrokenImage` with "Preview unavailable", and the editor still works.
  - Pin failure: the existing message.
  - Config activity before the config has loaded: `CairnLoadingIndicator` in the stage area (no blank surface).

### 3.4 Today (`TodayScreen.kt`)

Top bar: Large, title "Today". Pull to refresh stays. The card order stays `TodayOrder.present(state)` (the COROS order).

- Status row → banners → `CardHint` (restyled as a `Banner(tone = Info)` with the `Widgets` icon) → cards.
- **Header stats** (`TodayHeader`): one `SectionCard`, 3 equal columns separated by 1 dp `outlineVariant` vertical dividers. Each column has a value in `CairnTheme.type.tileNumeral` (FitText, floor 18 sp) and a label in `labelLarge` `onSurfaceVariant`. The merged semantics stay as they are.
- **Today's activity**: `SectionCard(title = "Today's Activity", icon = sport badge)`. The row has a 56 dp `SportBadge` in `primaryContainer`, the hero value in `statNumeral` (FitText floor 20 sp), and secondary values in a FlowRow with `·` separators. Below: date (bodyMedium, `onSurfaceVariant`) and title (titleMedium).
- **Vital tiles** (Heart Rate / Resting HR / Stress): a row of `StatTile`s with equal weights and equal heights (`IntrinsicSize.Min`), each a separate card (`surfaceContainer`, 24 dp corners, 16 dp padding). A tile shows title (labelLarge), value + unit baseline-aligned (`tileNumeral` + `unit`) and caption (bodySmall). **Fit rule**: if the 3 tiles do not fit at the current width/font scale (a tile measures < 96 dp), switch to a 2-column `FlowRow` and then 1 column. Never ellipsize.
- **Sleep**: `SectionCard(title = "Sleep", icon = AppIcons.Bedtime)`. Score (`statNumeral`) + rating word (titleMedium, `onSurfaceVariant`) in a FlowRow, then duration (titleLarge). `SegmentBar` is 12 dp tall with 6 dp corners, gaps of 2 dp between stages, followed by the window line and legend items.
- **HRV**, **Training status**, **Running fitness**: `SectionCard` with a title icon (see the `CardTitle` table below). Values use `statNumeral` and `MetricRow` for label/value pairs.
- **Weekly load**: total in `statNumeral` + "Suggested …". Bars are rounded 6 dp, 60 % column width. Today's bar is `primary`, the rest `primary` at 38 % alpha over `surfaceContainerHighest` tracks.
- **Recovery hero**: `HeroCard` (32 dp corners, accent gradient). The ring sizing stays `HeroFit`. The number is `heroNumeral`; `LevelPill` and time-to-full sit below. A long press works as on all cards.
- **Last 7 days**: `SectionCard` with rows of title + latest value + `Sparkline` (2 dp stroke, `primary`, last point a 6 dp dot).
- Extended FAB "Open COROS", **only when** freshness is Aging/Stale (3.0); it collapses with the top bar.
- Card title icons (`CardTitle(text, icon)`):

  | Card | Icon |
  |---|---|
  | Today's activity | the sport glyph |
  | Heart Rate | `MonitorHeart` |
  | Resting HR | `Favorite` |
  | Stress | `Psychology` |
  | Sleep | `Bedtime` |
  | HRV | `Timeline` |
  | Weekly load | `BarChart` |
  | Training status | `TrendingUp` |
  | Recovery | `BatteryChargingFull` |
  | Running fitness | `DirectionsRun` |
  | Last 7 days | `ShowChart` |

  Icons are 20 dp, `primary`, and decorative (`null` description).

### 3.5 Activities (`ActivitiesScreen.kt`)

Top bar: Large, "Activities".

- `LazyColumn` with **sticky month headers** (`stickyHeader`). Header: `background` colour, 8 dp vertical padding; title in titleLarge, then a FlowRow of distance (titleMedium), count and hours (bodyMedium, `onSurfaceVariant`). The month header keeps its long press (`CardId.ActivitiesMonth`).
- The rows of a month form one **grouped list** (`CairnShapes.group`: the first row has 24 dp top corners, the last 24 dp bottom corners, the middle rows 4 dp, with 2 dp gaps; container `surfaceContainer`). Row (`ActivityListItem`), 16 dp padding, min 72 dp:
  - leading: 48 dp `SportBadge` (circle, `primaryContainer`, glyph `onPrimaryContainer`)
  - headline: hero value (`row.hero ?: row.distance`) in titleLarge, tnum
  - supporting: secondary values in a FlowRow with `·` separators (bodyMedium)
  - overline row: date (labelMedium, `onSurfaceVariant`); title on its own line (bodyLarge)
  - Tap opens the detail sheet. A long press opens the card actions (existing `widgetActions(CardId.ActivityRow, onClick, "Show details")`).
- Detail sheet (`ModalBottomSheet`, 28 dp top corners):
  - header: 56 dp badge, sport (headlineSmall), date (bodyMedium)
  - a hero stats grid using `AdaptiveTiles` (tiles `surfaceContainerHigh`, 12 dp corners)
  - the rest of `details` as a `GroupedList` of label/value rows
  - a bottom row of `FilledTonalButton` "Open in COROS" and `OutlinedButton` "Add as widget"; both also exist in the long-press sheet
- Optional (R3 stretch): a sport `FilterChip` row under the status row, listing only the sports present ("All" selected by default).
- States: as in 3.0. The empty state sits under the status row, and the list skeleton is 6 rows of 72 dp in one grouped block.

### 3.6 Fitness (`FitnessScreen.kt`)

Top bar: Large, "Fitness". Pull to refresh (add it; Today already has it).

1. **Running fitness hero**: `HeroCard`. A 120 dp ring on the left (`ProgressRing`, track = palette track, colour = palette accent), with the level number on the right (`heroNumeral`, FitText floor 22 sp) and "Marathon 3:42:10" in ink-sub. If `running == null`, there is no hero and the VO2/threshold card shows instead.
2. **Training status** `SectionCard`: the status word (headlineMedium) and a `LevelPill("Intensity Trend 121%")` in a FlowRow, `MetricRow`s, then the chart: `LoadBars` 120 dp tall, bars rounded 4 dp, Load Impact = `primary`, Base Fitness = `cool`, gridlines `outlineVariant`, legend underneath.
3. **VO2max & threshold**: two `StatTile`s side by side (fit rule as in 3.4).
4. **Race predictions**: `SectionCard` holding `AdaptiveTiles` (tiles `surfaceContainerHigh`, 12 dp corners, distance label in labelLarge, time in `tileNumeral`).

States: as in 3.0.

### 3.7 Settings (`SettingsScreen.kt`) in Pixel Settings style

Top bar: Large, "Settings". Content is a single column of **group headers plus grouped lists**.

0. **Account card** (top): `Surface(primaryContainer, 28 dp)`, 20 dp padding. Leading 48 dp circle with `Person`; headline "COROS account"; supporting "Signed in" / "Not signed in" / "Sign in again needed", with a `FreshnessDot`-style status dot (good / bad). Trailing: a `FilledTonalButton` "Sign in" when signed out, else a chevron. The whole card opens the COROS account screen.
1. **Appearance**:
   - "Accent" row with an inline `LazyRow` of 8 accent swatches (40 dp circles; the selected one has a 3 dp `onSurface` ring plus a `Check` icon)
   - "Theme" `SegmentedChoice` System / Light / Dark
   - "Use wallpaper colours" `GroupedListItem` with a `Switch` (supporting "Material You. Off keeps Daymark colours.")
2. **Sync**: "Refresh every" (value as supporting text, e.g. "30 min"); "Getting old after" ("6 h"); "Stale after" ("24 h"). Each opens a `ChoiceSheet` with radio rows, and the existing explanatory sentences become the sheet's description. The 5-minute battery note shows inside the sheet and as supporting text when 5 min is chosen.
3. **Sources**: "Health Connect" (supporting `SourceStatus.label`; trailing `FilledTonalButton` "Grant" when `canGrant`); the sign-in hint line goes under it.
4. **Notifications**: "Remind me when COROS hasn't synced" with a `Switch`, plus "After" (value row → `ChoiceSheet`, visible only when on). The blocked-permission message goes in `error` supporting text.
5. **Units**: `SegmentedChoice` Kilometres / Miles.
6. **Widgets**: "Default look" →, "Tap targets" → (the same routes as the Widgets overflow menu; duplicating them here is intentional).
7. **Tools**: "Sync diagnostics", "Data sources", "Keep data fresh", plus the debug-only Explorer and Demo mode rows. **Fold the Tools sub-screen into this group** (the route stays for deep links and shows the same list).
8. **About**:
   - "Version" (supporting `VERSION_NAME`)
   - "Check for updates": tapping shows the inline result as supporting text (Checking… / You're up to date / error) in a `liveRegion`, and the `UpdateBanner` appears under the group when there is an update
   - "Send feedback"
   - the privacy sentence as a footer (bodySmall)
9. **Your data**: "Sign out of COROS" (enabled only when signed in) and "Delete all Cairn data", whose headline and icon are in `error`. The dialogs stay as they are (an icon `DeleteForever` is added to the delete dialog, and the confirm button gets `error` colour).

Group header: `titleSmall` in `primary`, 16 dp start padding, 8 dp below, 24 dp above.

### 3.8 COROS account (`CorosAccountScreen.kt`)

Top bar: Medium, "COROS account", back. Max width 480 dp, centred.

- **Signed out, idle**:
  - illustration header: 88 dp `primaryContainer` circle with a `Watch` icon, then "Connect COROS" (headlineSmall) and the one-line why (bodyMedium)
  - 56 dp full-width `Button` "Sign in"
  - a `GroupedListItem` "Having trouble signing in?" (expandable) that reveals "Try the local method" / "Sign in with device code" / "Open the sign-in page again" as rows with leading icons, with the sentence "These cancel the current attempt and start a new one." as a caption
  - "Advanced" expandable with the region `SegmentedChoice` and the caption "Force a region (no automatic fallback)"
- **Waiting for browser**: a status `SectionCard` with `CairnLoadingIndicator` (24 dp) and the status text; then the device code (if any) in `displaySmall` monospace tnum inside a `surfaceContainerHigh` 16 dp box with a `ContentCopy` IconButton ("Copy code"); the instruction text; and the resume hint in `primary` when `resumeHint`. Cancel is an `OutlinedButton`.
- **Failed**: `Banner(tone = Error)` with `flow.reason.message`; the primary button reads "Try again"; "Try another region" is an `OutlinedButton` when applicable; the trouble list auto-expands (existing logic).
- **Needs sign-in again** (signed in but expired): `Banner(tone = Warning)` "COROS needs you to sign in again" with the action "Sign in again".
- **Signed in**: a status card (good dot + "Connected", region as supporting text), a 56 dp `Button` "Done", an `OutlinedButton` "Sign out", and debug tools.
- **Sign-in diagnostics**: always last, as a collapsed `GroupedListItem` "Sign-in diagnostics" that expands to the 3 secret-free lines (method, stage, last failure) in bodySmall monospace. Never show tokens, codes after success, or emails.

### 3.9 Update banner (`UpdateBanner.kt`) (H9)

Keep the behaviour and the mapper exactly as they are. Visual changes:

- Container `primaryContainer` / `onPrimaryContainer`, 16 dp corners (`large`), min height 56 dp, one row: 24 dp `SystemUpdate` icon in `primary`, title (titleSmall, 1 line) plus notes (bodySmall, 1 line, ellipsis allowed **for notes only**), a compact `Button` (height 36 dp, padding 12 dp horizontal) "Update" / "Retry" / "Install", the overflow menu, and a close button.
- Progress state: the title plus a `LinearProgressIndicator` with a 4 dp track and rounded caps.
- At font scale ≥ 1.5 it stacks (existing logic). The banner itself never exceeds 2 rows.
- Placement: Widgets (top), Today / Activities / Fitness (under the status row), Settings → About (only after "Check for updates").

### 3.10 Card actions sheet (`CardActions.kt`) (H5)

`ModalBottomSheet`:

- header row with a 64×48 dp `WidgetThumbnail` of the card's widget kind (default style), then kind label (titleMedium) and "Widget" (bodySmall)
- a `GroupedList` of 2 rows: `Add` "Add to home screen" / "Choose its size and look, then place it", and `OpenInNew` "Open in COROS" / "Cairn refreshes shortly after"

Haptic `LONG_PRESS` on open (existing behaviour in `widgetActions` stays). TalkBack: every card exposes a custom action "Add to home screen" and "Open in COROS" via `customActions`, in addition to the long press.

### 3.11 Onboarding (`OnboardingScreen.kt`)

Full screen, no top bar, edge-to-edge.

- Top row: a 3-segment progress indicator (3 × `LinearProgressIndicator` pills with 4 dp gaps, the filled segments in `primary`) and the `TextButton` "Skip setup".
- Each step: a 72 dp `primaryContainer` circle with an icon (`Hub` sources, `Link` connect, `Widgets` finish), a headlineMedium title, and a bodyMedium subtitle.
  - Step 1: the choice cards become a `GroupedList` of radio rows (`selectableGroup`).
  - Step 2: source cards are `SectionCard`s with the status as supporting text and a `FilledTonalButton` for grant / sign-in.
  - Step 3: the COROS background check card plus a widget preview card (the Today widget thumbnail on a stage).
- Bottom bar pinned: `TextButton` Back (when step > 0) and a 56 dp `Button` Continue / "Choose a widget", padded for insets.
- Predictive back between steps (2.7). Slide transitions stay, with spatialSlow.

### 3.12 Secondary sub-screens (Medium top bar, back, `GroupedList` body)

- **Keep data fresh** (`FreshnessScreen`):
  - when COROS is missing: `Banner(Error)`
  - an explanatory paragraph
  - a primary `Button` "Open COROS" plus `StatusRow` + the "Sync now" tonal button
  - group "COROS background" (installed, battery status rows, "Open COROS app info" →, OEM tips as an expandable row)
  - group "Cairn" (battery row, "Battery optimisation list" →, "Cairn app info" →)
- **Sync diagnostics** / **Data sources inspector**: keep the content and switch to `GroupedList` sections with `MetricRow`-style label/value rows. Values use monospace for timestamps and counts. They are read-only and need no new states beyond loading (`CairnLoadingIndicator`).
- **Explorer (debug)**: untouched except for the chrome.

---

## 4. Shared components to build

Location: `core/designsystem/src/main/kotlin/app/cairn/core/designsystem/` unless marked *(app)*. Components in `core/designsystem` must not depend on app or widget model types. Pass strings, colours, `ImageVector`s, lambdas and a `Modifier` for gestures. Long-press and widget actions are passed as `interaction: Modifier` (the app passes `Modifier.widgetActions(card)`). It is applied **inside** the clip, before padding, so the ripple follows the corners.

```kotlin
// Theme.kt
@Composable fun CairnTheme(look: AppLook = AppLook(), content: @Composable () -> Unit)        // + shapes, LocalCairnType
object CairnTheme {                                                                             // accessors, like MaterialTheme
    val type: CairnTypeExtras @Composable get()
    val semantic: CardColors @Composable get()       // LocalCardColors
    val hero: Palette @Composable get()              // LocalCairnPalette
}
@Immutable data class CairnTypeExtras(val heroNumeral: TextStyle, val statNumeral: TextStyle, val tileNumeral: TextStyle, val unit: TextStyle)

// Shapes.kt
val cairnShapes: Shapes
object CairnShapes { val largeIncreased: Shape; val hero: Shape; fun group(position: GroupPosition): Shape }
enum class GroupPosition { Only, First, Middle, Last; companion object { fun of(index: Int, count: Int): GroupPosition } }

// Layout.kt
object CairnLayout { val cardPadding: Dp; val cardGap: Dp; val groupGap: Dp; val maxContentWidth: Dp; val fabClearance: Dp
    @Composable fun screenGutter(): Dp }                                                      // 16 / 24 by window width

// Motion.kt
object CairnMotion { fun <T> spatialFast(): FiniteAnimationSpec<T>; fun <T> spatialDefault(): …; fun <T> spatialSlow(): …
    fun <T> effectsFast(): …; fun <T> effectsDefault(): …; fun <T> effectsSlow(): …; fun <T> bouncy(): …
    fun <T> settle(): FiniteAnimationSpec<T> /* = spatialDefault, kept */; fun <T> settle(reduced: Boolean): FiniteAnimationSpec<T> }

// Screen.kt
enum class TopBarStyle { Large, Medium, Small }
@Composable fun CairnScreen(
    title: String,
    modifier: Modifier = Modifier,
    style: TopBarStyle = TopBarStyle.Large,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable (expanded: Boolean) -> Unit = {},   // expanded = collapsedFraction < 0.5
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
)   // Scaffold + nestedScroll(exitUntilCollapsedScrollBehavior for Large/Medium, pinned for Small); title via FitText (2 lines max when expanded)

// Navigation.kt
@Immutable data class NavItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)
@Composable fun CairnNavigationBar(items: List<NavItem>, selectedRoute: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)

// Cards.kt
@Composable fun SectionCard(modifier: Modifier = Modifier, title: String? = null, icon: ImageVector? = null,
    interaction: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(CairnLayout.cardPadding),
    content: @Composable ColumnScope.() -> Unit)
@Composable fun HeroCard(modifier: Modifier = Modifier, title: String? = null, interaction: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit)                 // replaces HeroSurface (keep HeroSurface as alias)
@Composable fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null,
    caption: String? = null, description: String? = null, interaction: Modifier = Modifier)
@Composable fun StatRow(stats: List<Pair<String, String>>, modifier: Modifier = Modifier, description: String? = null)
@Composable fun SkeletonCard(height: Dp, modifier: Modifier = Modifier)

// Lists.kt
@Composable fun GroupHeader(text: String, modifier: Modifier = Modifier)
@Composable fun GroupedList(modifier: Modifier = Modifier, header: String? = null, content: @Composable ColumnScope.() -> Unit)
@Composable fun GroupedListItem(
    headline: String, position: GroupPosition, modifier: Modifier = Modifier,
    supporting: String? = null, leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null,
    role: Role? = null, selected: Boolean? = null, destructive: Boolean = false, enabled: Boolean = true,
)   // min 56/72 dp, headline bodyLarge, supporting bodyMedium; never maxLines on headline/supporting
@Composable fun MetricRow(label: String, value: String, modifier: Modifier = Modifier)   // moved from app; value wraps to its own line, never ellipsized

// Choice.kt
@Composable fun <T> SegmentedChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, icon: ((T) -> ImageVector?)? = null)   // SingleChoiceSegmentedButtonRow; FlowRow of FilterChips if fontScale ≥ 1.5 or a label would not fit
@Composable fun <T> ChoiceSheet(title: String, options: List<T>, selected: T, label: (T) -> String,
    onSelect: (T) -> Unit, onDismiss: () -> Unit, description: String? = null)          // ModalBottomSheet + selectableGroup radio rows
@Composable fun SelectableTile(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    width: Dp = 120.dp, contentDescription: String = label, content: @Composable BoxScope.() -> Unit)

// Status.kt
enum class DotTone { Good, Warn, Bad, Neutral }
@Composable fun FreshnessDot(tone: DotTone, modifier: Modifier = Modifier, size: Dp = 10.dp)
@Composable fun StatusRow(tone: DotTone, text: String?, description: String, modifier: Modifier = Modifier,
    upToDate: Boolean = false, onLongPress: (() -> Unit)? = null)
@Composable fun SyncAction(syncing: Boolean, onSync: () -> Unit, modifier: Modifier = Modifier)  // IconButton, spins unless reduced motion
enum class BannerTone { Info, Neutral, Warning, Error }
@Composable fun Banner(tone: BannerTone, icon: ImageVector, message: String, modifier: Modifier = Modifier,
    title: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null)
@Composable fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null)
@Composable fun LevelPill(text: String, color: Color, modifier: Modifier = Modifier)          // moved from app
@Composable fun LegendItem(color: Color, text: String, modifier: Modifier = Modifier)        // moved from app
@Composable fun CairnLoadingIndicator(modifier: Modifier = Modifier, size: Dp = 48.dp)       // CircularProgressIndicator; Expressive LoadingIndicator optional

// Fab.kt
@Composable fun CairnExtendedFab(text: String, icon: ImageVector, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)

// Text.kt (moved from app/ui/FitText.kt; AdaptiveTiles stays in app because it uses MetricUi)
@Composable fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    floor: TextUnit = 14.sp, textAlign: TextAlign? = null)

// Preview.kt
enum class Wallpaper(val argb: Long) { Mid(0xFF6B7280), Light(0xFFD7DAE0), Dark(0xFF2A2D33) }
@Composable fun PreviewStage(wallpaper: Wallpaper, modifier: Modifier = Modifier, caption: String? = null,
    captionTrailing: @Composable (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit)

// Account.kt
@Composable fun AccountButton(needsAttention: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)  // 48 dp target, 40 dp visual

// Charts.kt (restyle only, same signatures): round caps, track = surfaceContainerHighest, ProgressRing stroke default 10 dp, bars 4–6 dp corners
```

App-level wrappers *(app, `app/ui/Chrome.kt`)*:

```kotlin
@Immutable class ScreenChrome(val title: String, val style: TopBarStyle, val onBack: (() -> Unit)?, val actions: @Composable RowScope.() -> Unit)
@Composable fun CairnScreen(chrome: ScreenChrome, modifier: Modifier = Modifier,
    floatingActionButton: @Composable (Boolean) -> Unit = {}, bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit)                          // delegates to the designsystem CairnScreen
@Composable fun CardTitle(text: String, icon: ImageVector?)                // SectionCard title slot used by data cards
@Composable fun SyncStatusBar(footer: FooterUi?, modifier: Modifier = Modifier, vm: SyncStatusViewModel = viewModel())  // keeps VM wiring, renders StatusRow + Banner
@Composable fun WidgetKindSheet(onPick: (WidgetKind) -> Unit, onDismiss: () -> Unit)
```

The old app copies of `SectionCard`, `HeroCard`, `LevelPill`, `LegendItem`, `MetricRow` and `EmptyState` become thin deprecated wrappers that delegate to the design-system versions. This lets R1 land without touching every caller; R2–R4 migrate the callers, and R5 deletes the wrappers.

---

## 5. Accessibility

| Area | Rule | How it is checked |
|---|---|---|
| Contrast | Text ≥ 4.5:1, large text (≥ 24 sp or 18.66 sp bold) and graphics/boundaries ≥ 3:1 for every role pair in 2.1, all 8 accents, light and dark. Semantic colours used as text go through `Contrast.ensure(…, 4.5)`. | Extend `PaletteTest`: iterate `Accent.entries × {light, dark}` and assert the pairs: onSurface/onSurfaceVariant on background, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest; primary on background and surfaceContainer; onPrimary/primary; onPrimaryContainer/primaryContainer; onErrorContainer/errorContainer; outline ≥ 3 on background and surfaceContainer; each semantic colour ≥ 3 on surfaceContainer. |
| Colour is never the only signal | The freshness dot always has words (status text, or the spoken description and long-press). Sleep and legend colours always come with labels. Selected tiles have a border, a check icon and `selected` semantics. | Review plus TalkBack pass |
| Touch targets | Every interactive element ≥ 48×48 dp (`minimumInteractiveComponentSize`). This includes the update banner buttons (40 dp visual inside a 48 dp target), editor reorder buttons, swatches and the AccountButton. | Compose `assertHeightIsAtLeast(48.dp)` where unit-testable; manual check with Accessibility Scanner |
| Font scale 2.0 | No clipped or ellipsized values. Large titles use FitText (floor 22 sp, 2 lines). Nav labels use the ≥ 1.8 rule. `SegmentedChoice` falls back to chips at ≥ 1.5. Vital tiles reflow to 2 and then 1 column. The update banner and editor tabs stack or go icon-only at ≥ 1.5. Only notes and hints may wrap; numbers never ellipsize. | `@Preview(fontScale = 2f)` for every screen (exist for Today/Activities/Fitness; add for Widgets, Settings, Account, Editor, Onboarding); emulator screenshots in R5 |
| TalkBack | Data cards merge descendants with the mapper's `description`. Headings: group headers, card titles and month headers get `heading()`. Custom actions for long press (3.10). Live regions: the status text, "Check for updates" result and editor apply note (polite). The sync button reads "Sync now" / "Syncing". Dialogs and sheets have titles. Decorative icons have `contentDescription = null`. | Manual TalkBack walk in R5 (checklist in `docs/testing.md`) |
| Focus and keyboard | Logical traversal order (top bar → status → content → FAB → nav). Sheets trap focus. Nothing is reachable only by long press. | Manual |
| Motion | Respect `ANIMATOR_DURATION_SCALE == 0` (`LocalReducedMotion`): no spin, no slide, no skeleton pulse, no bounce. | Toggle "Remove animations" in R5 |
| Haptics | CONFIRM on finished sync/delete (existing), LONG_PRESS on card actions; nothing else. | Review |

---

## 6. Implementation plan

Order: **R1 → (R2, R3, R4 in any order or in parallel; their files don't overlap) → R5.** Each package must build and pass the unit tests on its own:

```
./gradlew :core:designsystem:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

Rules for every package:

- Do not change `Mappers.kt` / `widget/Mappers.kt` formatting. `AppMapperTest`, `ActivityDisplayTest`, `WidgetFirstTest`, `EditorLayoutTest`, `LooksTest` and `PaletteTest` must stay green.
- Do not change the widgets' Glance UI (`widget/`). This revamp is the in-app UI only.
- Keep `Modifier.widgetActions` on every data card (H5).
- Use only Demo mode's fictional data for screenshots. Never commit tokens, real health values or account details.
- Do not commit unless the owner asks. Follow `CHANGELOG.md` style when they do.

### R1: Tokens, theme, shell and shared components

Files touched:

- `gradle/libs.versions.toml`: bump `composeBom` to the newest **stable** BOM (verify the material3 version on the BOM mapping page), and add `compose-material-icons-extended` (no version, from the BOM).
- `app/build.gradle.kts`, `core/designsystem/build.gradle.kts`: add icons-extended (designsystem: `api` or `implementation` as needed by the `ImageVector` params).
- `core/designsystem/.../AppColors.kt`: the new and changed roles in 2.1 (extend `AppRoles` with `onError`, `errorContainer`, `onErrorContainer`, `surfaceDim`, `surfaceBright`, `inverse*`).
- `core/designsystem/.../Theme.kt`: scheme mapping, the dynamic-colour neutral override, `cairnShapes`, type changes, `CairnTypeExtras` / `LocalCairnType`, and the `CairnTheme` accessor object.
- New in `core/designsystem/`: `Shapes.kt`, `Layout.kt`, `Screen.kt`, `Navigation.kt`, `Lists.kt`, `Choice.kt`, `Status.kt`, `Fab.kt`, `Text.kt` (FitText moved), `Preview.kt`, `Account.kt`.
- `core/designsystem/.../Motion.kt` (springs), `Cards.kt` (SectionCard, HeroCard, StatTile, StatRow, SkeletonCard), `Charts.kt` (restyle).
- `core/designsystem/src/test/.../PaletteTest.kt`: the role-pair matrix (section 5). New `ShapesTest.kt` for `GroupPosition.of`.
- `app/ui/AppIcons.kt`: point the facade at `Icons.Rounded/Outlined.*`, and add `Casino`, `ArrowUpward`, `ArrowDownward`, `Undo`, `RestartAlt`, `OpenInNew`, `CloudOff`, `Person`, `Watch`, `ContentCopy`, `ExpandMore`, `ExpandLess`, the tab icons and the card title icons.
- `app/ui/Chrome.kt` (new): `ScreenChrome`, app `CairnScreen`, `CardTitle`.
- `app/ui/Components.kt`: deprecated wrappers delegating to the design system.
- `app/ui/FitText.kt`: keep `AdaptiveTiles`; `FitText` delegates to (or is replaced by) the design-system version.
- `app/ui/CairnRoot.kt`: outer Scaffold without a top bar; `CairnNavigationBar`; each destination builds a `ScreenChrome` (title from `Routes.title`, style, back, actions: `SyncAction` + `AccountButton`, plus the overflow on Widgets) and passes it to the screen. Predictive-back-friendly pop transitions (2.7).
- **Mechanical only** in every screen file: change the signature to take `chrome: ScreenChrome` and wrap the existing body in `CairnScreen(chrome) { inner -> … }`, passing `inner` as content padding. No visual redesign of screen bodies in R1.

Acceptance:

- [ ] The build and all unit tests pass. The new `PaletteTest` matrix passes for 8 accents × light/dark.
- [ ] Every tab has a large title that collapses on scroll; sub-screens show a medium title with back; the Studio and config screens show a small bar.
- [ ] The nav bar shows a pill indicator, filled selected and outlined unselected icons, and labels per the font-scale rule.
- [ ] The sync icon is in the top bar on every tab and spins while syncing (still clock with Remove animations).
- [ ] Edge-to-edge: no double status-bar padding, and no content hidden under the nav bar (check gesture and 3-button navigation).
- [ ] The back gesture on a sub-screen shows a predictive preview of the home and can be cancelled.
- [ ] Dynamic colour off: the app looks the same as before except for shapes, type and the shell. Dynamic colour on (API 31+): accents change, surfaces stay Daymark neutral, and semantic colours are unchanged.

### R2: Widgets home and widget editor

Files: `app/ui/WidgetsHome.kt`, `WidgetStudio.kt`, `WidgetCustomizer.kt`, `ThemeUi.kt` (Default look, swatches, accent picker visuals), `TapTargetsUi.kt`, `WidgetThumbnail.kt` (stage only), `app/WidgetConfigActivity.kt` (ConfigScreen chrome and bottom bar), new `app/ui/WidgetKindSheet.kt`. Optional R2b: route `widgets/edit/{id}` in `CairnRoot.kt` plus "On your home screen" (coordinate if R3/R4 run in parallel: R2b is the only package allowed to edit `CairnRoot.kt` after R1, and it should run after R3/R4 merge).

Acceptance:

- [ ] Gallery cards match 3.2: stage, segmented size, the whole card tappable. 2 columns at ≥ 600 dp.
- [ ] The Extended FAB collapses with the top bar and opens the kind sheet; picking a kind opens the studio with the suggested size.
- [ ] Editor: the preview stays pinned while options scroll (H8), in portrait, landscape and ≥ 600 dp side-by-side. The wallpaper toggle switches the backdrop. Quick/Advanced is a segmented button. Undo and Reset are top-bar icons, and "Apply to all" is in the overflow with its confirm dialog.
- [ ] No emoji or arrow glyphs remain (`grep -n "🎲\|▲\|▼\|✓" app/src/main` is empty).
- [ ] "Add to home screen" pins exactly as before (the pin dialog preview matches the editor). Config activity Done/Cancel behave as before.
- [ ] `EditorLayoutTest`, `LooksTest`, `StudioAndShortcutsTest` and `WidgetFirstTest` pass.
- [ ] Font scale 2.0: size segments fall back to chips, the look tiles keep their labels on 1 line or wrap without clipping, and the bottom button text fits.

### R3: Today, Activities and Fitness

Files: `app/ui/TodayScreen.kt`, `ActivitiesScreen.kt`, `FitnessScreen.kt`, `SyncStatus.kt` (render `StatusRow` + `Banner`; VM unchanged), `CardActions.kt` (sheet restyle + TalkBack custom actions), `SportKind.kt` (badge colours), `CardWidgets.kt` (custom actions only).

Acceptance:

- [ ] All 3.0 states are visible: skeleton on first load, empty with action, error banner (sign-in / retry), neutral offline banner, stale dot plus the Today "Open COROS" FAB (only when Aging/Stale), and the up-to-date flash.
- [ ] The Today card order is still `TodayOrder.present`. Recovery is the only hero on Today, Running fitness the only hero on Fitness.
- [ ] Values are byte-for-byte the mapper output (spot-check against `AppMapperTest` fixtures in previews: "9'50\" /km", "54:37", "6 h 47 min").
- [ ] Activities has sticky month headers and grouped rows; the detail sheet has the stat grid, details list, and Open in COROS / Add as widget.
- [ ] Every data card still long-presses to the action sheet, and TalkBack offers the same two custom actions.
- [ ] Vital tiles reflow at font scale 2.0 with no ellipsis. The `@Preview(fontScale = 2f)` previews render without clipping.
- [ ] Fitness has pull-to-refresh.

### R4: Settings, account, onboarding, update banner and secondary screens

Files: `app/ui/SettingsScreen.kt` (+ `ToolsScreen`, `TapTargetsScreen` chrome), `app/CorosAccountScreen.kt`, `app/ui/OnboardingScreen.kt`, `app/ui/UpdateBanner.kt`, `app/FreshnessScreens.kt`, `app/SyncDiagnosticsScreen.kt`, `app/InspectorScreen.kt`.

Acceptance:

- [ ] Settings matches 3.7: account card, group headers, grouped rows, choice sheets for intervals and thresholds, a segmented theme and units control, the dynamic switch disabling the accent swatches, and a destructive-styled delete with the existing dialogs.
- [ ] Every setting still persists through the same VM/DataStore calls (nothing renamed). The 5-minute note, notification-permission flow and update-check inline result still work.
- [ ] Account screen: each state in 3.8 is reachable (use the existing sign-in machine states in a `@Preview` per state). The device code has a copy button. Diagnostics are collapsed and secret-free. After sign-in, the app still returns to the Widgets home with the snackbar (`PostSignIn`).
- [ ] Onboarding: segmented progress, pinned bottom actions, predictive back between steps, Skip works.
- [ ] Update banner: one row at 56 dp at font scale 1.0, ≤ 2 rows at 2.0. The fake-update broadcast from `docs/testing.md` shows it on Widgets and under the status row of the three data tabs.
- [ ] Freshness, Diagnostics and Inspector use grouped lists with a medium bar.

### R5: Polish, cleanup and screenshots

Files: delete the deprecated wrappers in `app/ui/Components.kt` once nothing uses them; `docs/testing.md` (add the revamp checklist); `README.md` (screenshots section); new `docs/screenshots/revamp/*.png`; `CHANGELOG.md` entry (only if the owner asks to commit).

Steps:

1. `grep -rn "Deprecated" app/src/main/kotlin/app/cairn/ui/Components.kt`. Migrate any remaining callers, then delete the wrappers.
2. Run the emulator (paths in the dev-environment notes), install the debug APK, and turn on **Demo mode** (Settings → Tools, debug only; fictional data).
3. Capture `adb exec-out screencap -p` for: Widgets, Studio (Quick and Advanced), Today (top and scrolled), Activities plus the detail sheet, Fitness, Settings, COROS account (signed out), Onboarding step 1, and the long-press sheet. Each in light and dark at font scale 1.0, plus Today/Settings/Studio at font scale 2.0 (`adb shell settings put system font_scale 2.0`, then reset to 1.0). Name the files `<screen>-<light|dark>[-fs2].png`.
4. Walk the section 5 table with TalkBack on, with "Remove animations" on, and with Accessibility Scanner on Widgets/Today/Settings. Fix any findings.
5. Run the whole `docs/testing.md` checklist once.

Acceptance:

- [ ] No deprecated wrappers remain. The build, unit tests and `./gradlew :app:lintDebug` show no new errors.
- [ ] Screenshots are committed under `docs/screenshots/revamp/` and contain only demo data (no real names, emails, tokens or health values).
- [ ] Accessibility Scanner shows no touch-target or contrast findings on the checked screens.
- [ ] Release build (`assembleRelease`, or debug-signed fallback) installs and runs. The APK size increase from icons-extended is < 1 MB after R8 (compare with the previous release APK and note it in the CHANGELOG).

---

## 7. Not in scope / decisions deferred to the owner

- Home-screen widget visuals (Glance) are unchanged. A separate pass could align widget corner radii and type with these tokens.
- Bundling a brand font (for example Google Sans Flex) is not planned. The system font keeps the APK small and respects OEM fonts.
- The Activities sport filter (R3 stretch) and "On your home screen" (R2b) are optional and can be dropped without affecting the rest.
- Material 3 Expressive alpha components are not used unless the stable BOM chosen in R1 ships them.
