# Cairn widgets: quality audit, test matrix and easy customisation

Status: proposal (QA and architecture review, 2026-11-17). Source of truth for packages Q1 to Q4.
Scope: `widget/` (11 kinds, 31 styles, 33 providers), the editor in `app/` (WidgetCustomizer, WidgetStudio, WidgetConfigActivity,
WidgetsHome), and the tests that exist today. Nothing in this file has been changed in code. Line numbers are from `main` on this date.

How to read the verdicts:

- **CONFIRMED BUG**: the code reading (and where stated, arithmetic with the code's own constants) shows the failure. No device was needed.
- **LIKELY**: the code allows it, but proving it needs a render (Q1 harness) or a device.
- **OK**: checked; evidence given.

---

## 1. Failure-mode audit

### 1.1 Ranked findings (fix order)

| # | Area | Verdict | Severity |
|---|------|---------|----------|
| A1 | "Small (2x2)" Last activity, Training load and Fitness compose a 250 dp layout into a ~150 dp cell | CONFIRMED BUG | High |
| A2 | Widgets never redraw while offline: freshness dot and age freeze, "Syncing…" sticks after a sync tap | CONFIRMED BUG | High |
| A3 | No redraw on dark/light switch, font scale, locale, time zone or midnight | CONFIRMED BUG | High |
| A4 | Today "Compact stripe" chips cut their values (2x2 at font 1.0, every cell at 1.3+) | CONFIRMED BUG | High |
| A5 | Previews and render tests use shorter strings than production ("7h 41m" against "7 h 41 min · 82") | CONFIRMED BUG | High (it hides A4 and A6) |
| A6 | Fixed-height text rows (20 dp and 18 dp) clip 13 sp text at font scale 1.3 and above | CONFIRMED BUG (arithmetic) | High |
| A7 | Opacity 55 to 90 % fails AA for secondary text over light or dark wallpapers | CONFIRMED BUG (computed) | Medium-high |
| A8 | Editor loses every unsaved choice on rotation, dark mode switch or wallpaper colour change | CONFIRMED BUG | Medium |
| A9 | Last activity one-liner drops the second stat when two do not fit on line 1 | CONFIRMED BUG | Medium |
| A10 | RTL: day letters mirror, the bitmap bars do not | CONFIRMED BUG | Medium |
| A11 | Footer sync button is 40 x 28 dp (below the 40 dp minimum) | CONFIRMED BUG (against the target) | Medium |
| A12 | Pending pins: one slot per kind and the pin callback claims without a size | CONFIRMED BUG | Medium-low |
| A13 | "Compact footer" switch does nothing on Clean, the default theme | CONFIRMED BUG | Low |
| A14 | Stat-grid strip (4x1): tiles 26 dp tall in an 8 to 21 dp body | LIKELY | Medium |
| A15 | Editor preview is composed at a larger size than the placed widget | CONFIRMED (code), UX | Medium |
| A16 | Transparent widgets pick ink by dark mode, not by the wallpaper | LIKELY | Medium |
| A17 | Exceptions during composition show Glance's generic error layout, not Cairn's message | LIKELY | Medium-low |
| A18 | Gfx bitmap cache holds 32 entries (not bytes), so it thrashes with 16 sizes x 11 kinds | CONFIRMED (perf) | Low |
| A19 | Pin preview size check ignores bitmap bytes (ashmem blobs are not in `dataSize`) | LIKELY | Low |
| A20 | 5-digit and 6-digit step counts overflow the inner circle of a 64 to 72 dp ring | LIKELY | Medium-low |
| A21 | Empty or first-run widgets have no sync button | CONFIRMED (code), UX | Low |
| A22 | Mixed digits under Arabic locales; fixed English strings | CONFIRMED (code), cosmetic | Low |
| A23 | Overview is Exact while its preview and thumbnails pick from the Responsive list | LIKELY | Low |
| A24 | Cells larger than the biggest registered size (tablets, foldables, 5x5) stretch the largest layout | LIKELY | Low-medium |

### 1.2 Details and evidence

**A1. Small variants of the three wide-only widgets. CONFIRMED BUG.**
`widget/src/main/res/xml/widget_activity_small_info.xml`, `widget_load_small_info.xml` and `widget_fitness_small_info.xml` declare
`minWidth=110dp`, `targetCellWidth=2`, `minResizeWidth=250dp`. Android ignores a `minResizeWidth` larger than `minWidth`, so the widget
is placed, and can be resized, at 2 cells (about 110 to 180 dp). But `WidgetSizes.byKind` (WidgetSizes.kt:464-476) has no entry
narrower than 250 dp for `LastActivity`, `TrainingLoad` or `Fitness`. `WidgetSizes.pick` mirrors the framework: when nothing fits it
takes the smallest-area entry. So a 2x2 Last activity gets the 250 x 40 one-liner, cut off on the right, with a large empty band
below. Training load and Fitness get 250 x 110, cut off on the right. The studio offers these sizes for every kind
(`WidgetSize.entries` in WidgetCustomizer.kt:223, `WidgetVariants.receiverName`).
`WidgetSizesTest.SamplePlans.minimum` (WidgetSizesTest.kt:27-31) assumes 250 dp minimums, so the tests never look at these cells.
*Fix (Q2)*: add 110 to 200 dp sizes and compact plans for these three kinds (hero plus one stat; Training load: ratio plus mini bars;
Fitness: VO2max plus one prediction). Until then, hide Small for these kinds in the studio (`WidgetVariants.sizesFor(kind)`). Keep
the receivers: widgets already placed depend on them. *Test*: matrix tier "launcher cells" uses the real provider minimum read from
the XML (ProviderResourcesTest already parses it). The invariant "all views inside parent bounds" fails today.

**A2. Offline freeze. CONFIRMED BUG.**
Widgets redraw only through `GlanceWidgetUpdater.updateAll()`, which runs after a sync (SyncEngine.kt:340), after a sync tap
(FooterIndicator.kt `SyncActionCallback`), on app start, and on MY_PACKAGE_REPLACED. Every sync request needs
`NetworkType.CONNECTED` (Scheduling.kt:53). With no network, nothing redraws, and:
- the freshness tier and the short age baked into the RemoteViews stay as they were (a green "fresh" dot on data that is 10 hours old);
- after a sync tap, `userSyncRequestedMs` is set and the widget is redrawn as "Syncing…". `SyncIndicators.derive` ends that state after
  `SYNCING_WINDOW` (2 min, Freshness.kt:167), but no redraw is scheduled at that moment, so "Syncing…" stays until the network returns;
- "Couldn't sync" has the same problem past `ERROR_WINDOW` (10 min).
*Fix (Q2)*: a `WidgetTickWorker` with no constraints that only calls `updateAll()`, scheduled (a) every 30 min as periodic unique
work, (b) one-shot at `SYNCING_WINDOW` + 5 s after each sync tap, (c) one-shot at the next freshness tier boundary of the newest
data (fresh to aging, aging to stale), (d) at local midnight + 1 min. Inexact timing is fine. *Test*: unit-test the next-tick
calculation (pure: `TickPlanner.next(now, newestStamp, prefs, syncState)`). Manual checklist item M7.

**A3. No redraw on configuration or clock changes. CONFIRMED BUG.**
A search for `onConfigurationChanged`, `UI_MODE`, `LOCALE_CHANGED`, `TIMEZONE_CHANGED`, `TIME_SET` and `DATE_CHANGED` in `app/`, `widget/`
and `sync/` finds nothing. Effects:
- *Dark/light*: `skinFor` (Skin.kt:31-36) reads `isDarkMode(context)` when composing, and colours are baked as fixed `ColorProvider(Color)`
  values. Clean, Minimal and every transparent or see-through widget (which use `Palettes.onWallpaper(dark)`) keep the old mode until the
  next sync, which can be an hour or more away.
- *Font scale*: text sizes are capped at composition (`SizeRules.cappedSp` in Components.kt:60) for the font scale of that moment. If the
  user raises the scale from 1.0 to 2.0, the host renders the old sp values at 2.0, which overflows every layout until the next redraw.
  The plans themselves are right; they just never run again.
- *Locale and time zone*: number formats, weekday letters and "Today/Yesterday" stay stale.
- *Midnight*: `WidgetEnvironment.build` uses `LocalDate.now()` per tick. Until the next sync after midnight, Today shows yesterday's steps
  as today's.
*Fix (Q2)*: (1) use `ColorProvider(day, night)` (resolved by the host on Android 12+, so the switch is instant) for every text colour and
for the solid background on themes that follow dark mode. Clean and Minimal are nearly flat, so they can use the Solid path with a day/night
colour instead of a gradient bitmap. (2) `Application.registerComponentCallbacks` compares `fontScale`, `uiMode`, `densityDpi` and locales,
and calls `refreshWidgets()` when one changes (this works while the process is alive). (3) Add a manifest receiver for `LOCALE_CHANGED`,
`TIMEZONE_CHANGED` and `TIME_SET`, all three exempt from the implicit-broadcast limits. (4) The A2 midnight tick covers the date change.

**A4. Today "Compact stripe" cuts values. CONFIRMED BUG.**
TodayWidget.kt:116-132 and Layouts.kt:214-222. `per = (inner / 60).toInt().coerceIn(2, 5)`; each chip is `defaultWeight`, holding 13 sp
bold text with `maxLines = 1` and no fit check. On a 2x2 cell (declared 110, so `w` = 118 and `inner` = 118 - 2 x 14 = 90 dp) each chip
is 45 dp wide. The code's own estimate (`TextFit.widthDp`) puts "12,345" at 49.9 dp at font 1.0, and "8,432" at 53 dp at font 1.3. The
production metric values are much longer: the sleep chip gets `Fmt.duration(..) + " · score"`, i.e. "7 h 41 min · 82" (Mappers.kt:59-61),
and calories gets "1,820 kcal" (Mappers.kt:69). Both are ellipsized in every stripe cell. *Fix*: give chips their own values (sleep
"7h 41m", calories "1,820"), lay chips out with `TextFit.chunkByWidth`, and drop a chip rather than ellipsize. *Test*: the matrix
invariant "no ellipsized value".

**A5. Sample and production strings differ. CONFIRMED BUG (test and preview fidelity).**
`Samples.today()` (Samples.kt:12-17) uses "7h 41m", "52 bpm", "82%" and "1,820", with no sleep score and no "kcal". The mapper produces
"7 h 41 min · 82" and "1,820 kcal". `RenderedTreeTest.todayRowsAreAllPresentThatTheRuleShows` uses the same short values and skips
`TodayStripe` on purpose (RenderedTreeTest.kt:57). So the editor preview, the picker previews and the render tests all show text that is
shorter than what users see. *Fix (Q1)*: build every fixture through `WidgetMapper` from domain objects (a fixed `now`, `today` and locale),
and make `Samples` call the mapper too. Add a test asserting that `Samples.x()` text lengths are at least the mapper's output for the
same fixture.

**A6. Fixed-height rows clip text at larger fonts. CONFIRMED BUG (arithmetic).**
- Today rows: `MetricLine(..., GlanceModifier.height(20.dp))` (TodayWidget.kt:66), while the plan budgets `20f * t` per row
  (Layouts.kt:201, 209). At font scale 1.5 the 13 sp value renders at 19.5 sp, a line box of about 23 to 25 dp, inside 20 dp.
- Fitness ladder: `MetricLine(..., height(18.dp))` (FitnessWidget.kt:89), with 13 sp values.
- Sleep timeline lanes: `Row(height(18.dp))` with 11 sp text (SleepWidget.kt:183-187).
The plan also reserves `20 * t` while the view uses 20, so on top of clipping there is a band of unused space.
*Fix*: row height = the plan's row dp (`20f * Layouts.ts(fs)`), or wrap content with a minimum height.
*Test*: matrix invariant "TextView measured height <= laid-out height", checked at font scales 1.3, 1.5 and 2.0.

**A7. Contrast at partial opacity. CONFIRMED BUG (computed).**
`ResolvedConfig.onWallpaper` switches to the wallpaper palette only below 55 % (Config.kt:392). From 55 % to 100 % the theme palette
is used. That palette guarantees AA against the opaque card stops, not against the card blended over a wallpaper. WCAG ratios for the
card's `start` colour at the given opacity over a black or white wallpaper:

| Theme | Opacity | Wallpaper | ink | inkSub |
|---|---|---|---|---|
| Clean light | 70 % | black | 8.1 | **2.96** |
| Clean light | 80 % | black | 10.7 | **3.90** |
| Midnight | 60 % | white | **3.90** | **1.70** |
| Midnight | 80 % | white | 7.6 | **3.33** |
| Sunrise | 90 % | black | 11.1 | **4.27** |
| Ocean | 60 % | white | **3.95** | - |

The opacity slider (WidgetCustomizer.kt:199-203) covers this whole range. *Fix (Q2)*: decide per palette at resolve time. If the minimum
ratio of `ink` and `inkSub` over the card at α composited on `#000` and `#FFF` is below 4.5, use a contrast-safe ink (`Contrast.ensure`
against both composites), or treat the card as on-wallpaper. Snap the slider to 0 and 70 to 100 % in 5 % steps, and show "Text may be
hard to read" in the dead zone. *Test*: extend `PaletteTest` with an opacity sweep (0, 5 … 100) x 10 themes x dark/light x {black,
white, #808080} wallpapers. The matrix contrast invariant does the same on real pixels.

**A8. Editor state lost on configuration change. CONFIRMED BUG.**
`WidgetStudioScreen`: `var cfg by remember(kind, initialStyle) { mutableStateOf(WidgetConfig(...)) }` (WidgetStudio.kt:58), which is not
saveable. `ConfigScreen`: `var loaded by remember { ... }`, re-read from storage in `LaunchedEffect` (WidgetConfigActivity.kt:156-160).
The manifest declares no `configChanges` for `WidgetConfigActivity` (AndroidManifest.xml:83). Rotation, dark mode, font scale, or a
Material You recolour after a wallpaper change all recreate the activity and drop the edits. *Fix*: `rememberSaveable(stateSaver =
WidgetConfigSaver)` using `encode()`/`decode()` (already lossless and tolerant). In ConfigScreen, keep `loaded` saveable and load from
storage only when it is null.

**A9. One-liner loses a stat. CONFIRMED BUG.**
LastActivityWidget.kt:43-50 and 100-107. `twoParts = activityMain(allParts.take(2), …)` may return only the first part. `LineStyle` then
shows `meta = rest.drop(1)`, assuming `rest[0]` is on line 1. When it is not, `rest[0]` (duration or pace) appears nowhere. This happens at
narrow widths and font scale 1.3 and above. *Fix*: `meta = rest.filterNot { it in mainParts }`, with `activityMain` returning the parts it
used.

**A10. RTL charts. CONFIRMED BUG.**
`android:supportsRtl="true"` (AndroidManifest.xml:41). Day letters are Glance `Row`s (TrainingLoadWidget.kt:62, `Letters(...)` in
StressWidget.kt:69 and HrvWidget.kt:101), and they mirror in RTL. The bars and trend lines are bitmaps drawn left to right (Gfx.kt
`bars`, `bandBars`, `trend`), and they do not. Under Arabic or Hebrew, Monday's letter sits over Sunday's bar. No layout-direction
handling exists (search for `LayoutDirection` or `isRtl` finds nothing). *Fix*: when
`context.resources.configuration.layoutDirection == RTL`, pass reversed fractions (and `todayIdx`) to Gfx, or draw mirrored (with `rtl`
in the cache key). *Test*: matrix with `+ldrtl` and locale `ar-EG`, asserting that letter x order matches bar x order (read bar x from
the bitmap columns).

**A11. Sync button target. CONFIRMED BUG (against the requested >= 40 dp).**
`FooterRules.HEIGHT_DP = 28`, `BUTTON_W_DP = 40` (FooterIndicator.kt:304-305). The tap target is 40 x 28 dp. *Fix*: footer row 40 dp on
cells at least 130 dp tall (`Layouts.footerDp` goes to 40, and the plans already take it from `availDp`). Keep 28 dp only where
`FooterRules.showSyncIcon` could hide it (tiny cells), and widen the button to 48 x 28 there. Re-run the `WidgetSizes` generator
(`GEN_SIZES=1`), because thresholds move by 12 dp.

**A12. Pending pin slot per kind. CONFIRMED BUG.**
`PendingPins.key(kind)` (PendingPins.kt:619) holds one record per kind. `WidgetPinnedReceiver` calls `pins.claim(kind, now)` without
`onlySize` (WidgetPinnedReceiver.kt:683), although the callback carries `EXTRA_PIN_SIZE`. If two pins of the same kind are made within 10 min
(say Small then Wide), the first callback claims the second's look, and the second widget falls back to defaults plus the configure
screen. A record left over from a cancelled launcher dialog is also claimed by the next widget of that kind dragged from the picker within
10 min. *Fix*: key the records by `(kind, size)`, pass `onlySize` from `EXTRA_PIN_SIZE`, and clear the record when the studio regains
focus without a callback. `PendingPins` is already pure; add cases to its test.

**A13. Compact footer no-op. CONFIRMED BUG.**
`Framed` passes `compact = cfg.compactFooter || w < 150f || theme == Clean` (Components.kt:194). Clean (the default) is always compact, so
the "Compact footer" switch does nothing for most users. *Fix*: drop the theme clause, or hide the switch when it cannot apply. Q3 moves
it to Advanced anyway.

**A14. Stat-grid strip tile height. LIKELY.**
Layouts.kt:264-269 for `ActivityGrid` with `h < 90`: `tileH = avail.coerceIn(26f, 44f)`. For the registered 250 x 40 size
(`w,h` = 258 x 48): pad 6, footer 28, so `avail` = 48 - 12 - 28 = 8 dp, and the tile is 26 dp. On a real 4x1 of 55 to 75 dp the body is
21 to 41 dp. The tile is clipped by the weighted body Box, or squeezes the footer. *Fix*: below 26 dp of avail, fall back to the one-liner
(as the other styles already do). *Test*: invariant "inside parent bounds" at the 4x1 cells.

**A15. Editor preview is larger than the placed widget. CONFIRMED (code), UX.**
`LivePreview(kind, resolved, size.preview)` (WidgetCustomizer.kt:177) composes Small at 160 x 160, Wide at 320 x 160 and Large at 320 x 240.
The pin dialog uses `size.pinDp` (124 x 124, 272 x 124, 272 x 198; WidgetCatalog.pinPreviewViews). A typical 4x2 on a Pixel is 272-320 x
110-146, so the editor can show rows, tiles or side layouts that the placed widget drops. *Fix*: compose the editor preview at `pinDp`
(or at the size the placed widget reports, via `AppWidgetManager.getAppWidgetOptions` in WidgetConfigActivity), then scale it to the
frame. Under the preview, label it "As on a typical 4x2".

**A16. Transparent widget ink. LIKELY.**
`Palettes.onWallpaper(dark)` (Palette.kt:227) picks white ink in dark mode and near-black in light mode, whatever the wallpaper. A light
wallpaper in dark mode gives white on white. *Fix*: use `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)?.colorHints and
HINT_SUPPORTS_DARK_TEXT` (no permission needed; a null result keeps today's rule), and redraw from a
`WallpaperManager.OnColorsChangedListener` while the process is alive. Q3 "Glass" look shows a one-line caveat.

**A17. Composition-time exceptions. LIKELY.**
`CairnWidget.provideGlance` (CairnWidget.kt:75-88) catches exceptions thrown before and inside `provide()`. Exceptions thrown later, while
Glance composes `Body` inside the session (for example `error("not a MorePlans style")` in MorePlans.kt:157, or a `!!`), go to
`GlanceAppWidget.onCompositionError`. Its default shows Glance's own `errorUiLayout`, not Cairn's "Couldn't load this widget".
*Fix*: override `errorUiLayout` with a Cairn layout (`widget_error.xml`: "Tap to open Cairn", with a PendingIntent set in
`onCompositionError`), and log the class name only. *Test*: a Robolectric test with `runGlanceAppWidgetUnitTest` and a fixture that
throws, asserting the error layout id is used.

**A18. Bitmap cache by count. CONFIRMED (perf).**
`Gfx.cache = LruCache<Any, Bitmap>(32)` (Gfx.kt:19). Responsive composes up to 16 sizes per widget, each with its own ring, gauge or chart
size. One updateAll over 11 kinds makes far more than 32 distinct bitmaps, so the cache misses on every redraw, despite its comment.
Worst case (32 rings of 200 dp at 3.5x density) is about 60 MB pinned. *Fix*: `LruCache` with `sizeOf = bitmap.allocationByteCount`
and 8 MB max. Key by px values only (already done).

**A19. Pin preview size check. LIKELY.**
`PinPreview.extras` (Updater.kt:253-260) measures `Parcel.dataSize()` after `writeToParcel`. Bitmaps above about 16 KB are written as
ashmem blobs when the parcel allows file descriptors, so they are not counted. The 200 KB guard therefore tests only the view actions.
*Fix*: walk the RemoteViews' bitmaps (or add up the Gfx bitmaps drawn during the preview compose) and require the bitmap total to be
2 MB or less as well. The matrix measures both numbers (section 2.6).

**A20. Big numbers in small rings. LIKELY.**
`Layouts.ringNumeralSp` floors at 14 sp (Layouts.kt:167-171). On a 64 dp ring (2x2 Today with no rows), the inner width is
64 - 2 x 6 - 8 = 44 dp, and "12,345" at 14 sp is about 54 dp by the code's own estimate, so it crosses the ring stroke. 6-digit
values ("123,456", ultra days) are worse. *Fix*: below the floor, put the numeral under the ring (the Stack style) or grow the ring
by dropping a row. *Test*: matrix data state "huge"; the invariant "ring numeral inside the inner diameter" (text bounds within the
circle inscribed in the ring's ImageView minus the stroke).

**A21. Empty states cannot sync. CONFIRMED (code), UX.**
`MessageView` uses `footer = { Spacer(10.dp) }` (Components.kt:107). "No data yet today" or "No recovery data" while signed in gives no
way to sync without opening the app. *Fix*: for `MessageKind.Empty` with `AuthState.Ok`, use the normal footer (dot neutral, sync
button). SignedOut and NeedsSignIn keep tap to Account.

**A22. Locale formatting. CONFIRMED (code), cosmetic.**
`Fmt.distanceKm` and `Fmt.steps` use `String.format(locale)` and `NumberFormat(locale)`, so `ar-EG` gives Arabic-Indic digits. `Fmt.pace`,
`Fmt.raceTime` (`"%d:%02d".format` uses the default locale) and plain templates (`"$n %"`, `"$v bpm"`) give Latin digits. One widget then
mixes both. German comma decimals ("5,55 km") are fine: `TextFit` treats ',' like '.'. Labels are English-only (one `values/` folder):
"steps today", "Couldn't sync", and the MorePlans subtitle sizing string "+99% vs last week". *Fix*: pick one digit policy (recommended:
locale digits everywhere through a `Fmt.num(locale)`) and test it under `ar-EG`, `de-DE`, `fr-FR` and `hi-IN`.

**A23. Overview preview/placed mismatch. LIKELY.**
`OverviewWidget.sizeMode = SizeMode.Exact` (OverviewWidget.kt:43). `WidgetCatalog.previewViews` and `WidgetThumbnails.view` pick from
`WidgetSizes.byKind[Overview]` (CairnWidget.kt:155). The placed widget composes at the exact reported size, the previews at a snapped one,
so tile counts can differ between the editor and the home screen. *Fix*: for Overview, compose previews at the requested size without
`pick`.

**A24. Cells beyond the largest registered size. LIKELY.**
`maxResizeWidth/Height` is 320 dp (Overview 400). Tablets and foldable inner screens report 4x2 cells of 400 to 540 dp. Some launchers
ignore the maximum, and on one-cell-larger grids the host view exceeds the largest entry. The largest layout is then stretched, leaving a
dead band. *Test*: matrix cells 330 to 540 dp; invariant "dead band" is a warning there. *Fix*: add one XL entry per kind (e.g. 400 x 250)
if the matrix shows bands over 40 %.

### 1.3 Checked and OK

| Topic | Evidence |
|---|---|
| Exceptions while preparing or loading | `provideGlance` try/catch falls back to a message (CairnWidget.kt:75-88); data flows have `.catch { emit(Message) }` (CairnWidget.kt:100). |
| Config decoding | `WidgetConfig.decode` ignores unknown keys and bad values; style filtered by kind; opacity clamped (Config.kt:351-368). |
| Widget deletion cleanup | `CairnReceiver.onDeleted` clears `wcfg_<id>` and `wshown_<id>` with `goAsync` (Receivers.kt). Minor leak: `claimed_<id>` keys in `cairn_pending_pins` are never removed (tiny; remove in Q2). |
| Config for a deleted id | `WidgetConfigActivity` finishes when `getAppWidgetInfo(id)` is null (WidgetConfigActivity.kt:106-107). |
| App update | `PackageReplacedReceiver` redraws all widgets on `MY_PACKAGE_REPLACED`. `KeepAliveWorker` keeps WorkManager's receivers enabled. |
| Pending-pin expiry | 10 min TTL; expired or malformed records are removed on claim (PendingPins.kt:627-636). |
| 16-size RemoteViews limit | `WidgetSizesTest.everyKindHasAtMostSixteenSizesIncludingItsMinimum`. |
| Glance 10-children limit | Stripe 5 per row at most, Overview 6 tiles at most, charts are bitmaps. |
| Units | `WeeklyGoal` stored with its unit; `UnitsPref` override; `UnitsTest`. |
| Pre-Android-12 paths | `minSdk = 34` (build.gradle.kts:30): sized RemoteViews and the system corner radius are always available. |
| Data clear | DataStore and Room are emptied; widgets fall back to inherited defaults and "No data yet" messages; Gfx cache is in memory only. |
| Backup/restore | `allowBackup="false"`: restored widgets come back with defaults (no stale ids are migrated). Acceptable; noted for M10. |
| Two widgets of one kind | Config keyed by appWidgetId. Tap target is per kind by design ("applies to every Today widget", WidgetCustomizer.kt:229). |
| updateAll storms | Glance serialises updates per session; sync requests are unique work. Rapid taps cost a few recompositions only. Keep an eye on the matrix render-time numbers. |
| Body tap fallbacks | `TapLaunchPlanner.chain` ends in `Explain` when COROS is missing (TapTargets.kt:488-497, TapTargetsTest). |
| Stale footer tap | Opens COROS through the trampoline when `tier == Stale`, otherwise Cairn (Intents.kt:591-592). |
| TalkBack | Root `contentDescription` holds the whole summary (mapper `description`), the footer has its own exact-age description, and the sync button has "Sync now. …". To check on device: duplicate reading of child texts (manual M10). |
| Palette AA on opaque cards | `Palettes.build` fits `ink`/`inkSub` to 4.5 on the stops and the tile; semantic colours to 3:1 (Palette.kt:184-224); `PaletteTest`. |

---

## 2. Test matrix and harness (package Q1)

### 2.1 Goals

One JVM suite renders every widget the way a launcher does, and checks invariants that do not depend on the style:
compose with `GlanceRemoteViews` at the size the host would select, `RemoteViews.apply`, then measure and lay out at the real cell size in
px, then inspect the View tree and the pixels. The suite runs in the normal `testDebugUnitTest` in under about 4 minutes on the owner's
machine (7.6 GB RAM). A full exhaustive tier runs on demand.

### 2.2 Dimensions

| Dimension | Values | Notes |
|---|---|---|
| Kind x style | 31 (11 kinds) | `WidgetStyle.entries` |
| Declared sizes | every entry of `WidgetSizes.byKind` (159 in total) | the layout rule input |
| Real cells | launcher profiles below, 2x1 to 5x5, portrait (minW x maxH) and landscape (maxW x minH), limited by each provider's real min (from XML) and up to 1.2x its max | the size the RemoteViews is laid out in |
| Font scale | 0.85, 1.0, 1.3, 1.5, 2.0 | `RuntimeEnvironment.setFontScale` |
| Look | 10 `ThemeId` + "on wallpaper" = 11, each light and dark (14 distinct palettes: only Clean, Minimal and on-wallpaper change with dark) | `setQualifiers("+night")` |
| Background | Gradient 100, Solid 100, Solid 85, Solid 70, Solid 55, Solid 30, Transparent | composited over wallpapers #000, #FFF, #808080 |
| Corners, accent | System/ExtraRound x 7 accents | pairwise only |
| Content toggles | every `Part.forKind` subset (at most 2^5 = 32 for Today), units Inherit/Km/Mi, compact footer, hide sync icon, Overview metrics 1 to 6 (orders from a fixed list), weekly goal null/30 km/20 mi | pairwise |
| Data states | see 2.3 | per kind |
| Locale / direction | en-GB, de-DE, fr-FR, ar-EG (+ldrtl), hi-IN | pairwise; de-DE and ar-EG exhaustive on the default look |
| Density | 320, 420, 560 dpi | pairwise (bitmap px and parcel size change with density) |

Launcher profiles (cell size = n x pitch - inset, in dp; calibrate with `WidgetLog.options` on real devices in Q4 and replace the numbers):

| Profile | Width pitch / inset | Height pitch / inset | Source |
|---|---|---|---|
| `aosp` | 70 / 30 | 70 / 30 | Android sizing table (`WidgetSize.cellDpOf`) |
| `pixel5x5` | 74 / 24 | 74 / 24 portrait max, 56 / 24 landscape min | `WidgetSize.pinDp`; RenderedTreeTest cells 176x222, 164x206, 272x223, 272x75 |
| `oneui4x5` | 86 / 16 | 104 / 16 | estimate, to calibrate |
| `nova6x8` | 62 / 8 | 72 / 8 | estimate, small cells |
| `tablet` | 136 / 16 | 116 / 16 | Pixel Tablet landscape estimate (tests A24) |
| `foldInner` | 104 / 16 | 112 / 16 | estimate |

### 2.3 Data states (fixtures built through `WidgetMapper`, never hand-written UI strings)

All fixtures use `now = 2026-11-17T09:00Z`, `today = 2026-11-17`, and the case locale. Per kind:

- `full`: every field present, typical values (steps 8,432; sleep 461 min, score 82; RHR 52; recovery 82; calories 1,820; stress 30).
- `huge`: steps 123,456; distance 168.42 km; duration 26 h 3 min; calories 12,345 kcal; HR 212; HRV 250 ms; weekly 999.99 km; ratio 9.99.
- `zero`: 0 steps, recovery 0 %, 0 km week, stress 0, ratio 0.0, an empty 7-day series (all stubs).
- `negative`: VO2 trend -3, vs-last-week -100 %, a clock-skewed stamp in the future (footer must clamp to 0).
- `minimal`: only the hero field (each optional field null, one at a time, in the pairwise tier: `missing:<field>`).
- `longText`: sport "Trail running ultra", de-DE weekday and date, server level word "Überdurchschnittlich", a 40-char place name.
- `hcOnly`: only Health Connect rows (`DataSource.HealthConnect`), no COROS extras (no vitals, no HRV, no details).
- `fresh`, `aging`, `stale`: the newest stamp 10 min, 5 h, 30 h old (per `DisplayPrefs` defaults).
- `syncing`, `syncError`: `LocalSyncState` with `userSyncRequestedMs = now - 30 s`; a failed user run 1 min ago.
- `empty`, `signedOut`, `needsSignIn`: `WidgetUi.Message` from the mapper with an empty Room (`AuthState` varies).
- `loading`: `WidgetUi.Loading`.
- `demo`: the Demo dataset (DemoFeature) values.
Offline is not a render state: it is A2's clock behaviour, covered by `TickPlanner` unit tests.

### 2.4 Sampling tiers (fixed seed `20261117`)

| Tier | Cases | Content |
|---|---|---|
| T0 `core` (always) | about 2,300 | every kind x style x declared size (+ MARGIN) x font {0.85, 1.0, 1.3, 1.5, 2.0}, `full` data, look Clean light, Gradient 100. Overview x metrics 1 to 6. |
| T1 `cells` (always) | about 1,300 | default style of each kind x every profile cell that the provider allows x {portrait, landscape} x font {1.0, 1.5}, plus the two other styles at font 1.0 on `aosp` and `pixel5x5` only. |
| T2 `states` (always) | about 600 | every kind x style x every data state, at 3 cells (provider min, natural size, large), font 1.0 and 2.0. |
| T3 `pairwise` (always) | about 500 | pairwise (IPOG, implemented in the test, or a hand-rolled greedy with the fixed seed) over look x dark x bg x accent x corners x font x density x locale x content toggles x data state x cell. |
| T4 `parcel` (always) | about 130 | the full Responsive map (all declared sizes) for every style x font {1.0, 2.0} x data {full, huge} x density 560. |
| `full` (on demand) | 100k+ | the cartesian product of T0 to T3 dimensions, streamed (`Sequence`), sharded by `-PmatrixShard=i/n`. |

Runtime budget: about 4,800 renders at an average of 40 ms or less (compose + apply + measure + checks; pixels only for contrast cases)
is about 3.2 min. If T0 goes over 2 min, drop font 0.85 from T0 (keep it in T3). Each test class prints its wall time. CI fails if the
matrix exceeds 6 min.

### 2.5 Harness API (test sources: `widget/src/test/kotlin/app/cairn/widget/matrix/`)

```kotlin
enum class Tier { Core, Cells, States, Pairwise, Parcel, Full; companion object { fun selected(): Set<Tier> } } // -PwidgetMatrix=default|full|core,...

data class Cell(val wDp: Float, val hDp: Float, val source: String)          // "pixel5x5:4x2:portrait", "declared:250x110"
data class LookSpec(val theme: ThemeId, val dark: Boolean, val bg: Bg, val opacity: Int, val accent: AccentOption, val corners: Corners) { val id: String }
data class Env(val fontScale: Float, val locale: String, val rtl: Boolean, val densityDpi: Int) { val qualifiers: String }  // "+de-rDE-ldltr-420dpi"
data class MatrixCase(
    val kind: WidgetKind, val style: WidgetStyle, val cell: Cell, val env: Env, val look: LookSpec,
    val data: DataState, val tweaks: ConfigTweaks, val wallpaper: Int = 0xFF808080.toInt(),
) {
    /** Stable, file-system-safe id, e.g. "Today.TodayStripe@pixel5x5-2x2-p_fs1.3_Clean-L-Gradient100_full_default_en-GB". */
    val id: String
    fun config(): WidgetConfig                 // tweaks + look applied
}

object WidgetMatrix {
    fun cases(tiers: Set<Tier> = Tier.selected()): List<MatrixCase>
    fun core(): List<MatrixCase>; fun cells(): List<MatrixCase>; fun states(): List<MatrixCase>
    fun pairwise(seed: Long = 20261117L, maxCases: Int = 500): List<MatrixCase>
    fun full(shard: Int = 0, of: Int = 1): Sequence<MatrixCase>
    fun profiles(): List<LauncherProfile>        // table 2.2
    fun providerLimits(kind: WidgetKind, size: WidgetSize): ProviderLimits  // parsed from res/xml (reuse ProviderResourcesTest code)
}

object Fixtures { fun ui(kind: WidgetKind, state: DataState, locale: Locale): WidgetUi<*>; fun sync(state: DataState): SyncState? }

class RenderedWidget(
    val case: MatrixCase,
    val declared: DpSize?,                // the size picked (null for Overview / Exact)
    val root: View,                       // laid out at cell px
    val texts: List<TextNode>,            // every visible TextView: text, rect in root px, textSizePx, color, ellipsized, lineCount, measuredW/H
    val images: List<ImageNode>,          // ImageViews with rect and bitmap identity
    val clickables: List<ClickNode>,      // views with a click PendingIntent: rect, contentDescription, action kind (body/footer/sync)
    val plan: Layouts.Plan?,              // from PlanRecorder (below)
    val expected: Expected,               // hero text, value strings, planned row/tile ids -> value strings
    val parcelBytes: Int, val bitmapBytes: Long,
    val renderMs: Long,
) { fun bitmap(wallpaper: Int): Bitmap }    // lazily drawn with WidgetThumbnails.drawClipped over the wallpaper

object WidgetRenderer { fun renderCase(case: MatrixCase): RenderedWidget; fun renderResponsiveMap(case: MatrixCase): RemoteViews }

data class Violation(val rule: String, val detail: String, val rect: Rect? = null)
object Invariants {
    val all: List<Invariant>
    fun check(r: RenderedWidget): List<Violation>
}
fun interface Invariant { fun check(r: RenderedWidget): List<Violation> }
```

Render path (`WidgetRenderer.renderCase`):
1. `RuntimeEnvironment.setQualifiers(case.env.qualifiers + if (look.dark) "-night" else "-notnight")`,
   `RuntimeEnvironment.setFontScale(env.fontScale)`, `Locale.setDefault(...)`.
2. `declared = WidgetSizes.pick(byKind[kind], cell.w, cell.h)` (Overview: `cell` itself, matching Exact).
3. `GlanceRemoteViews().compose(ctx, declared) { CompositionLocalProvider(LocalSyncState provides Fixtures.sync(data)) { widget.Render(ui, cfg.resolve(kind, app)) } }`.
   `Render` is already public; cast `CairnWidget<*>` to `CairnWidget<Any?>` for the typed call.
4. `rv.apply(ctx, frame)`, then `measure(EXACTLY cell px)` and `layout`, exactly as `WidgetThumbnails.view` does but at the cell
   size, not the declared one.
5. Walk the tree into `TextNode`/`ImageNode`/`ClickNode`. Read click intents from the view's `OnClickListener` (or the RemoteViews
   `setOnClickPendingIntent` actions: Glance uses lambda-free PendingIntents, inspectable with Robolectric's `shadowOf(PendingIntent)`).
6. Parcel: `Parcel.obtain().also { rv.writeToParcel(it, 0) }.dataSize()`. Bitmap bytes = the sum of `allocationByteCount` over distinct
   bitmaps in `ImageNode`s. T4 builds `RemoteViews(declaredSizes.associate { SizeF(w,h) to composeAt(it) })` and measures the map.
7. Time steps 3 to 6 with `System.nanoTime()`.

Required test-only hook (the one source change Q1 may make, with no behaviour change): `object PlanRecorder { @Volatile var sink:
((WidgetKind, Layouts.Plan) -> Unit)? = null }`, called from `currentPlan()` (Components.kt:202) and with the `OverviewPlan` from
`OverviewWidget.Body`. Production leaves it null.

Robolectric setup: `@Config(sdk = [35])`, `@GraphicsMode(NATIVE)` (real text measuring and pixels, as ThumbnailTest already uses).
A calibration test (`MatrixCalibrationTest`) checks that native fonts are loaded: "0000000000" at 10 sp measures within ±20 % of
`TextFit.widthDp`. If it does not, every text-width invariant is reported as "harness not calibrated" instead of giving false passes.

### 2.6 Invariants

Each one has an id (used in failure output and in the allowlist).

| Id | Rule | Applies to |
|---|---|---|
| `I1.noThrow` | compose, apply and layout throw nothing (including Glance's error layout being chosen). | all |
| `I2.footer` | a footer row is present: a dot `ImageView`/Box at the bottom, inside the root, with height >= 24 dp. The age label is present when the tier is not Fresh. | `Content` |
| `I3.syncTarget` | a sync clickable exists with a rect >= 40 x 40 dp (A11), unless `FooterRules.showSyncIcon(hide, w, h)` is false. | `Content` |
| `I4.textFits` | for every value TextView (text in `expected.values` or the hero): `ellipsisCount == 0`, `layout.getLineWidth(i) <= width - padding` for each line, `measuredHeight <= height` (catches A6), `lineCount <= maxLines`. Labels may shorten but never ellipsize to a single letter plus "…". | all |
| `I5.noOverlap` | no two visible TextViews intersect by more than 1 px (global rects). Text over its own ring or gauge ImageView is allowed; text over another text is not. | all |
| `I6.inBounds` | every visible view rect lies inside its parent's rect (±1 px), and inside the root. | all |
| `I7.hero` | the hero string is present, visible, not ellipsized, and its text size >= `MIN_HERO_SP` (ring numerals `MIN_RING_NUMERAL_SP`) after the cap. A ring numeral's rect lies within the ring's inner diameter (A20). | `Content` |
| `I8.plannedRendered` | for each planned row, tile or chip id in `plan.blocks`, its expected value string is rendered exactly once. Rendered value count == planned count (the bug class behind CompositionTest and RenderedTreeTest, now for all 31 styles). The per-style mapping `plan block id -> value` lives in `Expected.of(kind, style, plan, ui, cfg)`. | `Content` |
| `I9.contrast` | for each TextView: sample the background under its rect from `bitmap(wallpaper)` drawn with all TextViews hidden (5 x 3 grid, worst sample); WCAG ratio >= 4.5, or 3.0 when text is >= 18.66 dp bold or 24 dp regular. Check over #000, #FFF and #808080 for bg != opaque. Transparent looks over the "wrong" wallpaper are expected to fail: they go in `Allow.onWallpaperAdverse` (A16) and are reported, not failed. | contrast cases |
| `I10.fillToBox` | the largest horizontal empty strip inside the body (between the top pad and the footer, outside every text and image rect) is <= 35 % of the body height, or <= 24 dp. Warning only for cells larger than the largest declared size + 40 dp (A24) and for `Message`/`Loading`. | `Content` |
| `I11.parcel` | single-size RemoteViews: `parcelBytes` <= 120 KB. Responsive map (T4): `parcelBytes` <= 400 KB, and `bitmapBytes` <= 0.5 x (1080 x 2400 x 4 x 1.5) = 7.8 MB at 560 dpi. Pin preview (`pinPreviewViews`): bytes + bitmap bytes <= `PinPreview.MAX_BYTES` + 2 MB. | T4, pin |
| `I12.time` | `renderMs` p95 <= 250 ms per case, map <= 2.5 s (JVM, warm). Reported per class; failing p95 is a warning in T0 to T3 and an error in T4. | all |
| `I13.taps` | body clickable covers >= 60 % of the body; its intent is the `OpenCorosActivity` trampoline with `EXTRA_WIDGET = kind`. Footer: stale → trampoline, otherwise `MainActivity`. Message states → MainActivity with the right `EXTRA_SCREEN`. | all |
| `I14.a11y` | root `contentDescription` is non-empty and contains the hero value; the sync button has a description starting "Sync now" or "Syncing"; every clickable >= 40 x 40 dp, except the body. | all |
| `I15.rtl` | under `+ldrtl`: day letter x order is reversed iff the bar order is reversed (A10). | rtl cases |

Allowlist: `matrix/Allow.kt` lists `(caseIdGlob, invariantId, reason, issue)`. It starts with the CONFIRMED bugs of section 1 so that
Q1 is green, and Q2 deletes entries as it fixes them. An allowlist entry that no longer fails is itself a failure ("stale allow"), so the
list only shrinks.

### 2.7 Failure output

- Soft-collect: each test class gathers every `Violation`, then fails once with up to 50 lines:
  `FAIL I4.textFits Today.TodayStripe@pixel5x5-2x2-p_fs1.3_Clean-L-Gradient100_full: "7 h 41 min · 82" ellipsized (needs 92 dp, has 45 dp) -> build/reports/widget-matrix/Today.TodayStripe@...png`
- PNG dump to `widget/build/reports/widget-matrix/<case id>.png`: the render at device px over the case wallpaper, failing views
  outlined in red, the rule id written in the corner. Plus `index.html` (a grid of thumbnails with case ids and rules) and `failures.csv`
  (`caseId,rule,detail,png`).
- `-PmatrixDumpAll=true` dumps every case (for visual review; slow).
- The summary line at the end of each class: `matrix[T0] 2312 cases, 0 failures, 14 allowed, p95 38 ms, total 91 s`.

### 2.8 JUnit and Gradle wiring

- Group by environment, not one test per case. `ParameterizedRobolectricTestRunner` with one parameter per `Env` x dark (a few dozen),
  each looping over its cases. `setQualifiers` once per group, so the sandbox is reused. Classes: `MatrixCoreTest`, `MatrixCellsTest`,
  `MatrixStatesTest`, `MatrixPairwiseTest`, `MatrixParcelTest`, `MatrixCalibrationTest`.
- `widget/build.gradle.kts` (Q1):
  ```kotlin
  tasks.withType<Test>().configureEach {
      systemProperty("widgetMatrix", (project.findProperty("widgetMatrix") ?: "default").toString())
      systemProperty("matrixShard", (project.findProperty("matrixShard") ?: "0/1").toString())
      systemProperty("matrixDumpAll", (project.findProperty("matrixDumpAll") ?: "false").toString())
      maxHeapSize = "2g"
      maxParallelForks = (project.findProperty("matrixForks") ?: "1").toString().toInt()
  }
  ```
  With `org.gradle.jvmargs=-Xmx4g` (gradle.properties), one 2 GB fork fits in 7.6 GB. `-PmatrixForks=2` only with `--no-daemon`, or with
  `-Dorg.gradle.jvmargs=-Xmx2g`.
- Commands:
  - matrix only: `./gradlew :widget:testDebugUnitTest --tests 'app.cairn.widget.matrix.*'`
  - one tier: `./gradlew :widget:testDebugUnitTest --tests 'app.cairn.widget.matrix.MatrixCellsTest'`
  - full, sharded: `./gradlew :widget:testDebugUnitTest --tests 'app.cairn.widget.matrix.*' -PwidgetMatrix=full -PmatrixShard=0/4` (run 0..3)
  - whole suite: `./gradlew testDebugUnitTest :core:model:test :core:common:test` (as in docs/testing.md)

### 2.9 Manual emulator checklist (for docs/testing.md, added in Q4)

What the JVM cannot see. Run on a Pixel emulator (API 35), one Samsung or One UI image if available, and a tablet or foldable AVD. Enable
`WidgetLog.enabled` in a debug build and keep `adb logcat -s CairnWidget` open; record the reported `OPTION_APPWIDGET_SIZES` into the
profile table in 2.2.

| Id | Step | Expect |
|---|---|---|
| M1 | Place each kind at 2x2, 4x2, 4x3 and 4x1 where allowed (including Small Last activity, Training load, Fitness) | No clipping, footer visible, layout matches the matrix PNG for that cell |
| M2 | Resize each live through every span, slowly, then fast | Re-layout on each step, no "Loading", no stale layout after letting go |
| M3 | Rotate the phone with widgets on screen; on a foldable fold and unfold | The landscape layout fits; nothing stays from the other orientation |
| M4 | Split screen and the recents view | Widgets unchanged (no relayout expected) |
| M5 | Settings: font size to max, display size to max, then back | Widgets readable within the A3 redraw time; no overflow at any step |
| M6 | Toggle dark theme | Clean, Minimal and transparent widgets switch at once (after A3 fix) |
| M7 | Airplane mode for 3 h; tap sync once at the start | "Syncing…" ends within about 2 min; the age and dot advance at each tick (after A2 fix) |
| M8 | Change wallpaper light/dark with transparent widgets | Ink stays readable (after A16 fix); Material You recolour does not reset an open editor (A8) |
| M9 | Change language to Deutsch and العربية, time zone ±12 h, set the clock past midnight | Formats, RTL charts and "today" update (A3, A10, A22) |
| M10 | TalkBack: swipe through each widget | One summary per widget, then footer, then sync button; no duplicated values |
| M11 | Reboot with widgets placed | Widgets redraw within a minute, no permanent "Loading" |
| M12 | Install an update over the current build (`adb install -r`) | Widgets redraw (MY_PACKAGE_REPLACED), configs kept |
| M13 | Clear app data | Widgets show "No data yet" and default looks; nothing crashes; the editor opens |
| M14 | Remove a widget, then open its old editor from Recents | The editor closes itself; no config written for the dead id |
| M15 | Pin two widgets of the same kind at different sizes from the studio within a minute | Each gets its own look (after A12 fix) |
| M16 | Uninstall COROS, tap a widget body, then a stale footer | The Cairn explanation screen; never a crash or a no-op |

---

## 3. Easy customisation: Quick mode (package Q3)

### 3.1 Problem

The editor offers five tabs (Style, Colour, Content, Size, Tap), a "More options" drawer (corners, accent, units, compact footer, hide
sync), 10 themes x 4 background choices x 101 opacity values, and per-kind content toggles. Most people want "make it look nice and put
it on my home screen". Quick mode makes that a one-screen, three-choice job and keeps everything else one tap away.

### 3.2 Screen

The preview stays pinned on top, as now: composed at `pinDp` (fixes A15), scaled, on the neutral backdrop. Under it, in Quick mode (the
default):

1. **Look**: a horizontal gallery of 8 cards (120 x 72 dp, a real mini render of this widget in that look, its name under it). A tap
   applies the look live; the selected card has a 2 dp primary outline and `selected` semantics. The last card is "Surprise me" (a dice
   icon).
2. **Layout**: the 2 or 3 styles of this kind as live thumbnails (current look, current size, sample data), with the style label
   under each. Hidden when the kind has one style (Overview: that slot becomes "Stats", a chip row of the chosen stats with an "Edit"
   link into Advanced > Content).
3. **Size**: three chips, Small 2x2 / Wide 4x2 / Large 4x3, showing only the sizes `WidgetVariants.sizesFor(kind)` supports (A1). In the
   configure screen (widget already placed), the chips are replaced by the line "Resize on your home screen".

Then one full-width primary button: "Add to home screen" (studio) or "Done" (configure). A quiet row under it: `Apply look to all` ·
`Copy look…` · `Reset` · `Undo` (only when there is something to undo). The top-right of the options area has an `Advanced` switch; on,
it shows today's 5 tabs unchanged under the same preview. The switch is remembered app-wide (`editor_advanced`). TalkBack order: preview
description, Look (a "Look" list), Layout, Size, primary button, quiet row, Advanced.

Thumbnails: rendered off the main thread with `WidgetThumbnails.view` + `render` at 2x the card px, and cached in an
`LruCache<ThumbKey, Bitmap>(4 MB)` keyed by `(kind, style, size, lookId, dark, fontScale)`. A grey placeholder shows until each one is
ready. 8 + 3 renders take about 0.5 s on a mid phone; the gallery stays usable meanwhile.

### 3.3 Data model

```kotlin
// widget/src/main/kotlin/app/cairn/widget/Looks.kt
data class WidgetLook(
    val id: String,              // stable, persisted: "paper", "night-run", ...
    val name: String,            // shown under the card
    val theme: ThemeId,
    val bg: Bg,
    val opacity: Int,            // 0..100; 0 iff bg == Transparent
    val corners: Corners,
    val accent: AccentOption,
)

object WidgetLooks {
    val curated: List<WidgetLook>                               // 3.4, in gallery order
    fun byId(id: String?): WidgetLook?
    /** The curated look this config shows, or null ("Custom") when any look field differs or is inherited. */
    fun of(cfg: WidgetConfig): WidgetLook?
    fun surprise(current: WidgetLook?, random: kotlin.random.Random): WidgetLook   // 3.6
}

/** Only the five look fields change; style, hidden parts, units, footer, metrics and goal are kept. */
fun WidgetConfig.withLook(l: WidgetLook): WidgetConfig = copy(theme = l.theme, bg = l.bg, opacity = l.opacity, corners = l.corners, accent = l.accent)
fun WidgetConfig.lookFields(): LookFields   // theme, bg, opacity, corners, accent (nullable as stored)
fun WidgetConfig.withLookFields(f: LookFields): WidgetConfig
fun WidgetConfig.resetLook(): WidgetConfig = copy(theme = null, bg = null, opacity = null, corners = Corners.System, accent = AccentOption.Theme)
```

Persistence and migration:
- **Per widget: nothing new.** A look writes its five values into the existing `WidgetConfig` fields and keys (`theme`, `bg`, `op`,
  `corner`, `accent`). The gallery selection is derived (`WidgetLooks.of(cfg)`), never stored. Placed widgets need no migration, and a
  widget configured in Advanced shows "Custom" (no card selected).
- **App-wide "last look"**: a new key `widget_last_look` in the same DataStore as `WidgetSettings`, holding `LookFields` encoded with the
  `WidgetConfig.encode()` grammar (`theme=…;bg=…;op=…;corner=…;accent=…`). It is written when the user taps Add to home screen or Done,
  and read when the studio opens: `WidgetConfig(style = smartDefault).withLookFields(lastLook ?: inherit)`. Unknown values decode to
  inherit, so old or newer data never breaks.
- The existing app-wide default (`AppAppearance`: `widget_default_style`, `_bg`, `_opacity`, edited in `DefaultLookScreen`) stays as the
  "inherit" target. "Reset" returns the widget to it (`resetLook()`), so it follows the app default again.
- `editor_advanced` (Boolean, default false) in `AppSettings`.

### 3.4 The 8 curated looks

All use opacity 100 or 0 (A7 shows 55 to 90 % is unsafe). Every look is checked by `WidgetLooksTest` (3.8) in light and dark mode:
`ink`, `inkSub` >= 4.5 and `accent` >= 3.0 against `textStops + tile` of the palette `skinFor` resolves (the accent goes through
`Palettes.build`'s `g()`, so overrides stay AA).

| # | id | Name | Theme | Background | Opacity | Corners | Accent | Character |
|---|---|---|---|---|---|---|---|---|
| 1 | `paper` | Paper | Clean | Solid | 100 | System | Theme | calm white card, follows dark mode (the default look) |
| 2 | `minimal` | Minimal | Minimal | Solid | 100 | System | Theme | green-grey tonal card, follows dark mode |
| 3 | `night-run` | Night run | Midnight | Gradient | 100 | System | Lime | near-black with a bright lime ring |
| 4 | `dusk` | Dusk | Dusk | Gradient | 100 | ExtraRound | Theme | indigo to rose |
| 5 | `forest` | Forest | Forest | Gradient | 100 | System | Theme | deep green, mint accents |
| 6 | `sunrise` | Sunrise | Sunrise | Gradient | 100 | ExtraRound | Coral | warm cream, coral ring |
| 7 | `deep-ocean` | Deep ocean | Ocean | Gradient | 100 | System | Sky | navy to blue, sky accents |
| 8 | `glass` | Glass | Minimal | Transparent | 0 | ExtraRound | Theme | no card; "Best on a calm wallpaper" caption (A16) |

Dawn, Violet and Berry stay in Advanced. With the theme's own accent, `Theme` keeps the tested palette, and the Lime, Coral and Sky
overrides resolve to their dark or light variant by the card's ink (`Palettes.build` line 202). The test must confirm 3.0 on these cards.
If one fails, swap it for `Theme` before shipping; do not tune the colours by eye.

### 3.5 Smart defaults

- **Style per size**: `SmartDefaults.style(kind, size)` = the style with the highest `Layouts.contentScore` at `size.pinDp` + MARGIN,
  with ties going to `WidgetStyle.default(kind)`, computed (not hand-written) so it follows layout changes. It applies when the studio
  opens and on each Size chip tap, until the user picks a Layout themselves (`styleTouched` flag in the editor state). It is never applied
  to a placed widget's stored style.
- **Look for a new widget**: the last look (3.3), else Paper.
- **Size**: `WidgetVariants.suggested(kind)` as today, limited to the supported sizes.

### 3.6 Actions

- **Apply this look to all my widgets**: `LookActions.applyToAll(look: LookFields, placed: List<PlacedWidget>, load, save)`. For each
  placed id it loads the config, applies `withLookFields`, and saves, then calls `GlanceWidgetUpdater.updateAll()` once. Only the five look
  fields change, and content and style are kept. A confirmation sheet first ("Use Night run on all 5 widgets?"); afterwards a snackbar
  with Undo restores the exact previous encoded strings, kept in memory for the snackbar's lifetime. It also writes `widget_last_look`.
- **Copy look from another widget**: a bottom sheet listing the placed widgets (`PlacedWidgets.list`) with small thumbnails in their
  own looks; a tap applies that widget's `lookFields()`. Hidden when there are no other placed widgets.
- **Reset**: `resetLook()` plus `style = SmartDefaults.style(kind, size)`. Content toggles are kept in Quick mode. Advanced has "Reset
  everything" (`WidgetConfig()`).
- **Undo**: the editor keeps `initial` (the config when it opened, in saved state, A8). "Undo" is enabled when `cfg != initial` and sets
  `cfg = initial`. It is one level on purpose: "back to how it was".
- **Surprise me**: `WidgetLooks.surprise(current, Random(seed))`. It draws from the curated list minus `current`, minus `glass` (it
  depends on the wallpaper), and from a second curated list `WidgetLooks.extras` of 8 pre-validated theme x accent pairs (e.g. Dawn+Amber,
  Violet+Pink, Berry+Theme, Ocean+Lime) that pass the same contrast test. The user-facing seed is `System.nanoTime()`; tests pass a
  fixed one. It never produces an untested combination, because only the two lists are sampled.

### 3.7 How Quick mode maps onto the existing model

| Quick control | Writes | Existing key |
|---|---|---|
| Look card | theme, bg, opacity, corners, accent | `theme`, `bg`, `op`, `corner`, `accent` in `wcfg_<id>` |
| Layout thumbnail | style | `style` |
| Size chip | studio only: which provider is pinned | none (as today) |
| Apply to all / Copy / Reset / Undo | look fields of one or all widgets | same keys |
| Advanced switch | `editor_advanced` | new app-wide key |
| Last look | `widget_last_look` | new app-wide key |

`WidgetCustomizer` gets `mode: EditorMode` (Quick or Advanced) and the same `cfg`/`onCfg` contract, so `WidgetStudioScreen` and
`ConfigScreen` change only in their bottom bars. Placed widgets are never rewritten by opening the editor; only Done, Add, Apply to all
and Copy write.

### 3.8 Unit tests to add (Q3)

- `widget/src/test/.../WidgetLooksTest`: 8 looks, unique ids and names; opacity 0 iff Transparent; AA for each look x {light, dark}
  (`ink`, `inkSub` 4.5; accent 3.0; semantic colours 3.0) via `skinFor`; `extras` pass the same; `of(cfg.withLook(l)) == l` for every
  look and kind; `of()` is null when any field differs or is inherited; `withLook` keeps style, hidden, units, compactFooter,
  hideSyncIcon, metrics and weeklyGoal; `encode()/decode()` round-trips after `withLook`.
- `LookFieldsCodecTest`: last-look encode/decode, garbage → inherit, unknown keys ignored.
- `SurpriseTest`: with a fixed seed, 1,000 draws never return `current` or `glass`, cover every candidate, and every result passes the
  contrast check.
- `SmartDefaultsTest`: a style for each kind x supported size; equals the default style when scores tie; never a style of another kind.
- `LookActionsTest` (pure, with in-memory load/save): apply-to-all changes only look fields of every id; Undo restores every byte; a
  single failed save does not stop the others and is reported.
- `EditorStateTest` (app, pure): Undo enabled iff `cfg != initial`; Reset keeps content toggles; the saver round-trips the editor state.
- `WidgetSizesForKindTest`: `WidgetVariants.sizesFor` excludes Small for Last activity, Training load and Fitness until A1 is fixed, then
  includes it (one assertion flips in Q2).
- Matrix (Q1 harness): add the 8 looks + `extras` as a look dimension in T3, with `I9.contrast` on real pixels.

---

## 4. Implementation packages (sequential, one Sonnet agent each)

Common rules: work on a branch per package; no health data in logs, fixtures or PNGs (fixtures are synthetic); keep receiver class names;
run the commands below before handing over; update this file's verdict column for what changed.

### Q1. Matrix harness and invariants (no behaviour change)

Scope: `widget/src/test/kotlin/app/cairn/widget/matrix/*` (sections 2.2 to 2.8), `Fixtures` built through `WidgetMapper`, `PlanRecorder`
hook (null in production), the `widget/build.gradle.kts` properties, `Allow.kt` seeded with the CONFIRMED bugs (A1, A4, A5-driven, A6,
A7, A9, A10, A11, A13, A14 when it reproduces), and `MatrixCalibrationTest`. Switch `Samples` to mapper-built content only if that does not
change previews visibly; otherwise leave it for Q2 and add the drift test as allowed.

Acceptance:
- `./gradlew :widget:testDebugUnitTest --tests 'app.cairn.widget.matrix.*'` passes in 4 min or less, wall time printed per class.
- Every invariant I1 to I15 is implemented and has a self-test: a deliberately broken fixture (a 400 dp-long value, an overlapping
  pair, a 20 dp row with 30 sp text, a #777 on #888 text) that must fail the rule.
- Each allowlist entry names its audit id; a run with the allowlist off reproduces at least A1, A4, A6 and A7, with PNGs in
  `widget/build/reports/widget-matrix/`.
- The calibration test passes (native fonts measured), or the run reports "not calibrated" and fails.
- `./gradlew testDebugUnitTest :core:model:test :core:common:test` stays green; the production APK is unchanged apart from the null hook
  (`./gradlew :app:assembleDebug`).

### Q2. Fixes for confirmed and likely bugs

Order: A1, A2, A3, A4, A5, A6, A9, A14, A7, A8, A10, A11, A12, A13, A17, A21, A15, A16, A18, A19, A20, A22, A23, A24 (the last four if
time allows; each must still be either fixed or re-verdicted with matrix evidence). For each fix: delete its allowlist entries, add the
unit test named in section 1, and re-run the `WidgetSizes` generator (`GEN_SIZES=1 ./gradlew :widget:testDebugUnitTest --tests
'app.cairn.widget.WidgetSizesTest.generate'`) when a layout threshold moves (A1, A6, A11), pasting the new lists into WidgetSizes.kt.

Acceptance:
- The allowlist holds only entries marked "won't fix" with a reason (expected: `Allow.onWallpaperAdverse` only).
- New pure tests: `TickPlannerTest` (A2), `ConfigChangeRefreshTest` (A3 diffing of configurations), `PendingPinsTest` cases for
  `(kind, size)` (A12), the `PaletteTest` opacity sweep (A7), `SamplesFidelityTest` (A5), the RTL mirror test (A10), and the
  error-layout test (A17).
- `./gradlew :widget:testDebugUnitTest :app:testDebugUnitTest :core:designsystem:test` is green, and the matrix stays at 4 min or less.
- Manual M1, M2, M5, M6, M7 on an emulator, with results noted in docs/testing.md.

### Q3. Quick mode editor, looks and apply-to-all

Scope: section 3: `Looks.kt` (widget), `SmartDefaults`, `LookActions`, `EditorMode` in `WidgetCustomizer`, thumbnails cache,
`widget_last_look` and `editor_advanced` keys, the studio and configure bottom bars, and the "Copy look" sheet. No change to stored
per-widget keys.

Acceptance:
- Opening the studio shows Quick mode with Look, Layout and Size, and one primary button. Advanced reveals the existing 5 tabs unchanged.
- All tests from 3.8 pass; the matrix T3 includes the looks and `I9.contrast` passes for all non-Glass looks over all three wallpapers.
- Placed widgets keep their exact stored strings after the update (test: decode → encode is identity for a corpus of 50 stored strings
  taken from the existing tests and fixtures).
- Apply to all + Undo restores every widget byte for byte (LookActionsTest), and redraws once.
- Rotation and dark mode switch in the editor keep the state (A8 verified for both modes).
- Commands: `./gradlew :widget:testDebugUnitTest :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`.

### Q4. Docs, checklist and final regression

Scope: add section 2.9 (M1 to M16) and the matrix commands to `docs/testing.md`; calibrate the launcher profiles in 2.2 from
`WidgetLog.options` output on at least the Pixel emulator (and a tablet AVD); run the full matrix once; update the verdicts in this file;
short owner-facing notes in README.md (what Quick mode is).

Acceptance:
- `./gradlew :widget:testDebugUnitTest --tests 'app.cairn.widget.matrix.*' -PwidgetMatrix=full -PmatrixShard=i/4` for i = 0..3 is green
  (or every failure has an issue line in this file).
- `./gradlew testDebugUnitTest :core:model:test :core:common:test :core:designsystem:test` and `./gradlew assembleRelease` succeed.
- Manual checklist M1 to M16 run once on an emulator; results (pass/fail, device, date) recorded in docs/testing.md.
- The profile table holds measured numbers with the device names, replacing the estimates.
