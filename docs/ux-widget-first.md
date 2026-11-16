# Cairn: widget-first UX, fixes and update notice

Status: design, not yet implemented. Written 2026-11-16 against `main` at `599e6bb` (Cairn 1.4.0).
Scope: the owner's seven points from real use. Each has a decision; each bug has a root cause with file and line.
Work is split into four packages (A to D). Each one builds and ships on its own, in order.

Device note: the owner reported "Android 13", but `minSdk = 34` (`build.gradle.kts:30,42`), so the phone runs Android 14 or later.
The one-liner seen in the pin dialog comes from the Android 15+ generated preview (see 3). That points to Android 15 or 16, maybe with an OEM skin whose own version number is 13.

---

## 1. Widget editor: preview pinned at the top

**Root cause.** The preview is inside the scrolling column.
- `WidgetStudio.kt:83`: `Column(Modifier.weight(1f).verticalScroll(...)) { WidgetCustomizer(...) }` wraps the whole customizer.
- `WidgetCustomizer.kt:163-166`: the customizer is one `Column`: preview `Box` (165), then `SectionTabs` (166), then the options. The preview therefore scrolls with the options.
- The same bug is in `WidgetConfigActivity.kt:169-175` (the launcher's configure screen and "Reconfigure").

**Decision.** Split the customizer into regions. Only the options region scrolls.

```
WidgetEditorScaffold(            // new, app/ui/WidgetEditor.kt; used by Studio, ConfigActivity and "Your widgets" edit
  preview  = { PreviewPane(views, size) },     // fixed, never scrolls
  tabs     = { SectionTabs(section) },         // fixed under the preview
  options  = { SectionOptions(section, ...) }, // the only verticalScroll
  bottom   = { EditorBottomBar(...) },         // fixed: "Add to home screen" or Cancel / Done
)
```

`WidgetCustomizer` becomes two composables: `SectionTabs` (already exists) and `SectionOptions(kind, cfg, section, ...)`, which holds the body of lines 167-220.
`LivePreview` is hoisted. A `rememberWidgetPreview(kind, resolved, size): State<RemoteViews?>` is shared by `PreviewPane` and the pin call (see 3), so the dialog gets exactly the same RemoteViews.

**Layout rules** (pure object `EditorLayout`, unit-tested):

| Window | Arrangement | Preview area |
| --- | --- | --- |
| Portrait, height >= 560 dp | Column: preview, tabs, options (weight 1, scroll), bottom bar | height = min(natural + 16 dp, 35% of window height), at least 120 dp |
| Portrait, height < 560 dp (small phone, split screen) | Same column | 30% of height, at least 104 dp |
| Landscape, or width >= 600 dp | Row: left pane 40% width holds the preview, centred and filling the height minus padding. Right pane: tabs, options (scroll), bottom bar | fits the left pane |

- **Scalable preview.** The RemoteViews are still composed at the real dp size (`WidgetSize.preview`, picked through `WidgetSizes.pick`), so the layout breakpoints match the placed widget. They are then scaled down uniformly: `scale = min(1, availW / w, availH / h)`. Use `Modifier.layout` so the scaled size is what gets measured, and `graphicsLayer(scaleX, scaleY)` to draw it. Never scale up.
- **200% font.** Keep composing at the system font scale, because the launcher uses it too, so the preview stays truthful. The cap keeps the preview from filling the screen. When `fontScale >= 1.5`, `SectionTabs` shows icons only (the label goes to `contentDescription`) and becomes a scrollable row, so five 56 dp tabs never wrap. The bottom bar holds a single full-width button. Its error text moves to the top of the options area so the bar stays one button tall.
- The bottom bar uses `navigationBarsPadding()`. The options column uses `imePadding()`, because the opacity and goal sliders bring no keyboard but future text fields might.
- The wallpaper backdrop (`Color(0xFF6B7280)`) stays. A small toggle in the preview corner switches between a light and a dark backdrop, so Transparent can be judged.
- TalkBack: the preview keeps its `contentDescription`. Pin the preview pane as `Modifier.semantics { liveRegion = Polite }` so a style change is announced.

---

## 2. Widget-first app: information architecture

**Bottom navigation (4 tabs):** `Widgets` (start destination) · `Today` · `Activities` · `Fitness`.
The top bar on every tab has a Settings gear, as it does now (`CairnRoot.kt:163`). The Widgets tab also gets an overflow menu: *Default look*, *Tap targets*, *Help: how widgets stay fresh*.

**Routes** (`CairnRoot.kt` `Routes`):

| Route | Screen | Replaces |
| --- | --- | --- |
| `widgets` | `WidgetsHomeScreen`: gallery and "Your widgets" | Studio chooser (`WidgetStudio.kt:104-122`) |
| `widgets/edit?kind=&size=&style=&id=` | `WidgetEditorScreen` (sticky editor). Without `id` it creates and pins a new widget; with `id` it edits a placed widget | `studio?kind=` |
| `widgets/look` | `DefaultLookScreen` (the existing `WidgetAppearanceCard`) | Settings > Widgets group |
| `widgets/tap` | `TapTargetsScreen` (exists) | Settings link |

- `Routes.tabs = [WIDGETS, TODAY, ACTIVITIES, FITNESS]`. `Routes.forScreen(null)` returns `WIDGETS`. Widget taps still carry `EXTRA_SCREEN` and land on their data screen. Today, Activities and Fitness keep working.
- Back-compat: `forScreen("Studio")` returns `WIDGETS`, so the `add_widget` launcher shortcut (`res/xml/shortcuts.xml`) keeps working.

**WidgetsHomeScreen** (`app/ui/WidgetsHome.kt`, `WidgetsHomeViewModel`):

```
LazyColumn
  [UpdateBanner]                       // package D, top of the main screen
  [SyncProblemBanner]                  // package C; only for a problem that stops widgets updating
  "Your widgets" (only when >= 1 placed)
     PlacedWidgetRow: scaled preview with its stored config | "Recovery · 2x2 · Midnight" | Edit · Reset look
     footnote: "To remove a widget, long-press it on your home screen."
  "Add a widget"   [Sample | My data] segmented toggle
     WidgetGalleryCard x 11: name, one-line blurb (move `blurb()` from WidgetStudio.kt:44),
       live preview in the default look, size chips S 2x2 / W 4x2 / L 4x3 (choosing one changes the preview),
       tap card -> widgets/edit?kind=K&size=S
```

State models:

```kotlin
data class PlacedWidget(val appWidgetId: Int, val kind: WidgetKind, val size: WidgetSize, val config: WidgetConfig)
data class GalleryItem(val kind: WidgetKind, val size: WidgetSize, val preview: RemoteViews?)
data class WidgetsHomeUi(val placed: List<PlacedWidget>, val gallery: List<GalleryItem>, val data: PreviewData, val pinSupported: Boolean)
enum class PreviewData { Sample, Live }
```

- **Inventory** (`widget/WidgetInventory.kt`). Loop over every receiver from `WidgetVariants`: add `WidgetVariants.allReceivers(): List<Class<*>>`, 33 entries, and drive `receiverClass` from the same table. Call `AppWidgetManager.getAppWidgetIds(ComponentName(ctx, cls))` for each. Map with `kindOf` and `sizeOf`, then read `WidgetSettings.configNow(id, kind)`. Refresh on `ON_RESUME`.
- **Orphan cleanup.** On load, delete stored `wcfg_<id>` and `wshown_<id>` for ids no longer placed. Add `WidgetSettings.storedIds()` and reuse `clear(ids)`.
- **Edit.** Opens the editor with `id`. Done calls `settings.save(id, cfg)`, `WidgetCatalog.updateOne`, then pops back.
- **Reset look.** Calls `settings.save(id, WidgetConfig())`, so the widget inherits the defaults again. There is no "remove widget" action: Android has no app API to remove a placed widget, so the UI says so.
- **Live data previews.** Add `WidgetCatalog.previewViews(..., data: PreviewData)`. In `Live` mode, take the first `WidgetUi.Content` from `widget(kind).flow(unit)` within 1 s, otherwise fall back to the sample. Render cards lazily. Cache RemoteViews in the ViewModel in an LRU of about 24 entries, keyed by kind, size, config hash and data mode. Compose them on `Dispatchers.Default`.
- Pinning unsupported (`!WidgetPinner.isSupported`): the editor's bottom button becomes "How to add it", which explains the launcher's Widgets picker and names the picker label.

**Removed from Settings** (`SettingsScreen.kt:263-268`): the whole "Widgets" group. "Add widget" moves to the Widgets tab. `WidgetAppearanceCard` moves to `widgets/look` as "Default look". "Widget tap targets" moves to `widgets/tap`. `SettingsNav.onAddWidget` and `onTapTargets` are deleted.
Settings keeps Appearance, Sync, Sources and COROS account, Notifications, Units, Tools, About (updates and feedback), and Your data.

**Onboarding** (`OnboardingScreen.kt`). Step 3, "Keep it fresh, then add a widget" (around line 124), currently pins `PinnableWidget.entries.first()` with default choices. It becomes "Pick your first widget":
- three gallery cards: Today, Recovery and Last activity, with live previews.
- tapping one finishes onboarding and opens `widgets/edit?kind=K`.
- "Skip" finishes onboarding on the Widgets tab.

---

## 3. Pin-dialog preview does not match the editor

**What the dialog shows.** The launcher's pin dialog draws one of three things:
- (a) a RemoteViews that the app passes in the pin request extras;
- (b) on Android 15+, the provider's generated preview from `setWidgetPreview`;
- (c) the static `android:previewLayout` or `previewImage` from the provider XML.

Cairn passes `null` extras (`Updater.kt:85`), so the dialog shows (b) or (c). Neither knows the user's configuration.

**Root cause of the one-liner** (verified in the Glance 1.2.0 bytecode in the Gradle cache):
1. `CairnWidget.providePreview` (`CairnWidget.kt:101-103`) does not override `previewSizeMode`. `GlanceAppWidget.previewSizeMode` defaults to `SizeMode.Single`. `composeForPreview` then composes at `AppWidgetProviderInfo.getMinSize()`.
2. For the 4x2 Last activity provider that size is `minWidth = 250dp`, `minHeight = 40dp` (`widget_activity_info.xml`).
3. At `h < 90` the style is forced to the one-liner: `LastActivityWidget.kt:40` and `Layouts.kt:180` (`ActivityLine`). The result is "10.20 km · 52 min · 5'07"/km".

The placed widget is right because it uses `SizeMode.Responsive(WidgetSizes.dp(kind))` (`CairnWidget.kt:37`) and its stored config.

Two further gaps:
- `registerWidgetPreviews` (`CairnWidget.kt:151-165`) registers only the 11 original receivers. The 22 Small, Wide and Large variants fall back to static XML.
- The static layouts (`res/layout/widget_preview_*.xml`) are hand-drawn in Dawn colours with non-COROS formatting such as `52m   5:07 /km`. The default look is Clean, so they never match either.

**Fix, primary: pass our own RemoteViews in the pin request.**
`AppWidgetManager.EXTRA_APPWIDGET_PREVIEW = "appWidgetPreview"`. Verified in the local `android-34` and `android-36` `android.jar`. `api-versions.xml` says `since="26"`, so it is fully available at our `minSdk 34`. It is not new in 33/34.
`requestPinAppWidget(ComponentName, Bundle extras, PendingIntent)` has been there since 26. The `extras` Bundle reaches the launcher as `LauncherApps.PinItemRequest.getExtras()`. Launcher3 and Pixel Launcher read `EXTRA_APPWIDGET_PREVIEW` from it and show that RemoteViews in the confirmation dialog. The contract is "the launcher may use it": it is a hint, not a guarantee.

```kotlin
// widget/Updater.kt
fun pin(context: Context, kind: WidgetKind, size: WidgetSize, config: WidgetConfig?, preview: RemoteViews? = null): Boolean {
    ...
    val extras = PinPreview.extras(preview)            // null when absent or too large
    return mgr.requestPinAppWidget(ComponentName(context, WidgetVariants.receiverClass(kind, size)), extras, callback)
}

object PinPreview {
    const val MAX_BYTES = 200_000                       // binder-safe; Glance ring bitmaps can be large
    fun extras(rv: RemoteViews?): Bundle? = rv?.takeIf { parcelSize(it) <= MAX_BYTES }
        ?.let { Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, it) } }
}
```

The editor passes the same RemoteViews instance that `PreviewPane` shows, composed by `WidgetCatalog.previewViews(context, kind, resolved, size.preview)` for the chosen style, size, theme and data mode. If the preview is not ready yet, the click awaits it for up to 500 ms, then pins without it.

**Fallbacks for launchers that ignore the extra** (for example One UI, MIUI, Nova; unverified per launcher):
1. Override `previewSizeMode` in `CairnWidget`: `override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(WidgetSizes.dp(kind))`. `SizeMode.Responsive` implements `PreviewSizeMode` (checked in bytecode). The generated preview then holds every breakpoint, and the launcher draws the one that fits its preview cell. That fixes the Hero-vs-one-liner mismatch on Android 15+ even without the extra.
2. `registerWidgetPreviews` loops over `WidgetVariants.allReceivers()` (all 33). It runs once per app version, because the system rate-limits it: store `previews_registered_version`, and on a rate-limit result, retry on the next start. It also re-runs when the Default look changes, using that look instead of `pickerTheme`.
3. Static XML previews (Android 14 and fallbacks): regenerate them in Clean colours with COROS formats, and pin the Wide activity preview to the Hero arrangement. This is low priority, because it only affects the system widget picker on Android 14.
4. The editor shows a one-line note under the button: "Some launchers show a generic preview. Your widget will look like this one." Show it only when the default launcher package is not `com.google.android.apps.nexuslauncher` or `com.android.launcher3`.

---

## 4. Sign-out leaves a "Retry" that can never work

**Root cause.**
- `ManualSync.gate` (`sync/.../Freshness.kt:37`) returns a plain string, "Not signed in to COROS. Sign in under COROS account, then try again."
- `SyncStatusViewModel.sync` stores it in `gateError` (`SyncStatus.kt:111`).
- `SyncErrorBanner` (`SyncStatus.kt:187-200`) always offers Retry, which calls `vm::sync` again and hits the same gate.
- Also, `gateError` is cleared only by Dismiss or by a later allowed sync. After signing in, the stale red banner stays until the user taps something.

**Decision.** Use a typed problem model in `:sync`, a pure function, and one action per kind.

```kotlin
// sync/SyncProblems.kt
enum class ProblemKind { NotSignedIn, NeedsSignIn, Offline, Network, Server, Partial, CapReached, NoSource, Demo }
enum class ProblemAction { SignIn, Retry, Details, OpenSources, TurnOffDemo, None }
data class SyncProblem(val kind: ProblemKind, val message: String, val primary: ProblemAction, val secondary: ProblemAction = ProblemAction.None, val retryAtMs: Long? = null)
object SyncProblems { fun classify(state: SyncState, gate: ManualSync.Gate?, signedIn: Boolean, online: Boolean, demo: Boolean, now: Instant, dismissedAtMs: Long): SyncProblem? }
```

`ManualSync.Gate.Skipped` gains `reason: ProblemKind`. The message stays for logs.

| Kind | Trigger | Message | Primary | Secondary |
| --- | --- | --- | --- | --- |
| NotSignedIn | no token (gate) | Not signed in to COROS. | **Sign in**: `account?signIn=true` | None |
| NeedsSignIn | outcome `NEEDS_SIGN_IN` / `state.needsSignIn` (refresh rejected) | COROS needs you to sign in again. | **Sign in again** | None |
| Offline | gate `!online` | You are offline. | None (clears itself on the connectivity callback) | Retry once online |
| Network | outcome `NETWORK_ERROR` | Couldn't reach COROS. Cairn will retry. | **Retry** | Details (Diagnostics) |
| Server | outcome `FAILED` | COROS sync failed (error). | **Retry** | Details |
| Partial | outcome `PARTIAL` | Some numbers didn't update. | **Retry** | Details |
| CapReached | gate cap or `SKIPPED` "budget" | Daily COROS request limit reached. Resets at midnight. | None | Details |
| NoSource | `SKIPPED` "no source" | No data source is set up. | **Set up** (Settings > Sources) | None |
| Demo | `DemoFeature.active` (debug) | Demo data. Sync is paused. | Turn off demo | None |

- "Already syncing" stays silent, as now.
- Banner colours: `errorContainer` for NeedsSignIn, Network, Server and Partial. Neutral `secondaryContainer` for NotSignedIn, Offline, CapReached, NoSource and Demo, because those are states, not failures.
- `SyncProblemBanner(problem, onAction)` replaces `SyncErrorBanner`. `CairnScaffold` routes `SignIn` to `account?signIn=true`, `Details` to `diagnostics`, and `OpenSources` to `settings`.
- `gateError` becomes `gateProblem` and is cleared when the auth state changes (combine `tokenStore` signed-in) and on every successful run.

---

## 5. After sign-in the user is stuck on the account screen

**Root cause, bounce or redirect path.**
- `OAuthRedirectActivity.openAccount` (`OAuthRedirectActivity.kt:55-62`) starts `MainActivity` with `EXTRA_SCREEN = Account` and `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
- `MainActivity` uses the standard launch mode and no `onNewIntent` (`MainActivity.kt`), so `CLEAR_TOP` recreates it.
- `Routes.forScreen("Account")` maps to `ACCOUNT` (`CairnRoot.kt:95`), which becomes the NavHost **start destination** (`CairnRoot.kt:120,189`).
- Nothing is beneath it, so the back arrow's `nav.popBackStack()` (`CairnRoot.kt:168`) is a no-op. The bottom bar is hidden because ACCOUNT is not a tab (`CairnRoot.kt:131,173`). Only killing the app gets the user out.

**Root cause, in-app paths** (Custom Tab, device code, local).
- `CorosAccountScreen` (`CorosAccountScreen.kt:110-131`) only swaps to the signed-in controls.
- Nothing navigates away, and the signed-in view is a debug tool list ("List tools", "Call ..."), not a finish line.

**Decision.**
- **One way in.** `MainActivity` gets `android:launchMode="singleTop"` and overrides `onNewIntent`, which pushes the intent into a `NavRequests` `SharedFlow`. `CairnScaffold` collects it and navigates. The start destination is always `WIDGETS`. A deep target is pushed on top of it, so Back always works: `nav.navigate(target) { launchSingleTop = true }`. Apply the same rule to widget taps.
- **Redirect.** `OAuthRedirectActivity` sends `EXTRA_SCREEN = Home` and `EXTRA_SIGNED_IN = true` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`. Add a `Home` value to `widget.Screen`; `forScreen` maps it to `WIDGETS`. On failure it keeps its own screen with "Try again" and "Back to Cairn", where Back to Cairn opens the account screen pushed on top of Widgets.
- **In-app.** `CorosAccountScreen(vm, onSignedIn: () -> Unit)` remembers whether the user was signed in when the screen opened. When `s.signedIn` flips from false to true while the screen is shown, it calls `onSignedIn` once. `CairnScaffold` then does `nav.popBackStack(WIDGETS, inclusive = false)`, or `navigate(WIDGETS) { popUpTo(0) }` if Widgets is not on the stack.
- **Confirmation.** A scaffold-level `SnackbarHostState` shows "Signed in to COROS. Syncing now..." (the sync already starts in `AppGraph.onSignedIn`, `CairnApp.kt:160`). The header status line then shows the normal syncing spinner.
- **Account screen when signed in.** Show "Signed in (Region)" as the title line, a primary **Go to widgets** button, and an outlined Sign out button. "List tools", "Call ..." and "Copy redacted sample" move under a collapsed "Developer" section, visible only in debug builds.
- **`account?signIn=true`** starts `vm.signIn(context)` on first composition when signed out and no attempt is running (`SignInMachine.startEnabled`). It is used by the banner's Sign in action.
- **Onboarding.** If onboarding is not done, the redirect lands on onboarding, as now. Onboarding's step 2 sees `signedIn` and moves on to "Pick your first widget".

---

## 6. Long-press on every card

**Today.**
- `TodayContent` (`TodayScreen.kt:69-93`) gives a sheet to Header, Activity, Sleep, WeeklyLoad, TrainingStatus, Recovery and RunningFitness.
- Missing: the Heart Rate, Resting HR and Stress tiles (`VitalTiles`, 129-150, plain `Card`), the HRV card (170) and "Last 7 days" (96).
- Activities has none: `MonthHeader` (88) and `ActivityRow` (103), where tap opens details.
- Fitness misses the level-only "Running Fitness" (85) and "Race Predictions" (87).

There are two defects in the existing helper:
- `Modifier.onLongPress` (`CardActions.kt:33-35`) uses `pointerInput { detectTapGestures }`. Combined with `.clickable` (ActivityRow) it competes for the same gesture.
- It gives no haptic feedback and no visual press state.

**Mapping** (single source: `CardWidgetMap` in `app/ui/CardWidgets.kt`, an exhaustive `when` per card enum):

| Screen | Card | Widget kind | Suggested style |
| --- | --- | --- | --- |
| Today | Steps / kcal / min header | Today | TodayRing |
| Today | Today's Activity | LastActivity | ActivityHero |
| Today | Heart Rate tile | HeartRate | HeartRange |
| Today | Resting HR tile | HeartRate | HeartSpark |
| Today | Stress tile | Stress | StressPill |
| Today | Sleep | Sleep | SleepBar |
| Today | Overnight HRV | Hrv | HrvRange |
| Today | Weekly Training Load | TrainingLoad | LoadBars |
| Today | Training Status | TrainingLoad | LoadDial |
| Today | Recovery | Recovery | RecoveryRing |
| Today | Running Fitness | Fitness | FitnessVo2 |
| Today | Last 7 days sparklines | Overview | OverviewGrid (metrics preset from the rows) |
| Activities | Month header | WeeklyMileage | MileageBars |
| Activities | Activity row (and its details sheet) | LastActivity | ActivityHero |
| Fitness | Training Status and Intensity Trend chart | TrainingLoad | LoadTrend |
| Fitness | Running Fitness (both variants) | Fitness | FitnessVo2 |
| Fitness | Race Predictions | Fitness | FitnessGrid |

**Component.**

```kotlin
data class WidgetLink(val kind: WidgetKind, val style: WidgetStyle? = null, val preset: WidgetConfig? = null)
class WidgetActions(val open: (WidgetLink) -> Unit)          // provided once by CairnScaffold
val LocalWidgetActions = staticCompositionLocalOf<WidgetActions?> { null }

// The only way a card gets the behaviour. combinedClickable: tap keeps its meaning (or none), long press opens the sheet,
// haptic LongPress, ripple, TalkBack custom action "Add as widget".
fun Modifier.widgetActions(link: WidgetLink, actions: WidgetActions, onClick: (() -> Unit)? = null): Modifier
```

- `SectionCard(link: WidgetLink? = null, ...)` and `HeroCard(link = ...)` replace `onLongPress`. `VitalTiles`, `MonthHeader` and `ActivityRow` call the modifier directly.
- One `CardActionsSheet` is hoisted to `CairnScaffold`; the per-screen `sheet` state in TodayContent and FitnessContent is deleted. The sheet header shows a small live preview of the widget in that style.
- Sheet actions:
  - **Add to home screen**: opens `widgets/edit?kind=&style=&size=suggested` with the style preselected. The user sees the sticky preview and the exact pin preview, and Add is one more tap.
  - **Open in COROS**: the existing `openCorosAndRefresh`.
  - Activity rows also get **Show details**.

**Discovery.**
- (1) Every linked card shows a small trailing widget glyph (`AppIcons.Widgets`, 18 dp, `onSurfaceVariant` at 70% alpha) in its `SectionTitle`. It is a 48 dp `IconButton`, labelled "Add <kind> widget", that opens the same sheet, so long press is never the only way in.
- (2) A one-time hint chip on Today: "Tip: long-press any card to put it on your home screen" with a close X. It is stored as `hint_card_widgets_seen` in `AppSettings` and cleared on the first long press or on X.

---

## 7. Update notice (for all of the owner's apps)

**What exists today.**
- `app/.../appdrop/AppDropUpdater.kt` is a byte-identical copy of `~/AppDrop/android/AppDropUpdater.kt` apart from the package line.
- It is called from `MainActivity.onResume` (`MainActivity.kt:16`) and from Settings "Check for updates" (`SettingsScreen.kt:353`, `force = true`).
- What it already does:
  - `GET {base}/api/public/apps/cairn/latest?current=<versionName>&device=<uuid>`, at most every **12 h**, stamped in `last_check`;
  - an **AlertDialog** (Update / Later / Skip this version) with up to 6 lines of plain-text notes;
  - **in-app install**: DownloadManager, SHA-256 check, `REQUEST_INSTALL_PACKAGES` prompt, FileProvider and the system installer, resumed on the next `check()` if the app was in the background;
  - a browser fallback to `apk.url` or `pageUrl`.
- Gaps:
  - it is a modal dialog, not a banner;
  - nothing is cached, so offline shows nothing and the result is lost;
  - "Later" does nothing, and it shows again on the next check, 12 h later;
  - it holds an `Activity` in the download receiver (a leak risk);
  - the UI cannot be themed.
- Among the other apps only **Tally** has it (`Tally/android/.../appdrop/AppDropUpdater.kt`, Activity-based, `android/` folder). Daymark, Bloom, Recal, WABox and Balance have none.

**Server.** The public API already has everything needed: **no endpoint change is required.**
`GET /api/public/apps/<slug>/latest?current=&device=[&beta=]` (`AppDrop/src/app/api/public/apps/[slug]/latest/route.ts`) returns:
- `latest.version` (tag without `v`), `latest.notes` (up to 5000 chars of markdown) and `publishedAt`;
- `latest.apk.{url: /d/<slug>/<assetId>, name, size, sha256}`;
- `pageUrl` (`/apps/<slug>?via=app`);
- `updateAvailable`, computed server-side with the same `compareVersions` as the client.

`/d/<slug>` (always latest) and `/apps/<slug>` are stable public URLs. Optional later additions: a `minSupported` or `critical` flag, and a short `summary` field so notes are not trimmed client-side.

**Decision: split the drop-in into a headless core and an optional UI.** Keep it in one file. No new dependencies, because Tally is not Compose.

```kotlin
object AppDropUpdater {
    enum class Ui { Dialog, None }                       // Dialog = current behaviour (default, Tally unchanged); None = app draws a banner
    fun init(context: Context, baseUrl: String, slug: String, ui: Ui = Ui.Dialog)
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val checkedAtMs: Long) : State
        data class Available(val version: String, val notesSummary: String, val notes: String, val sizeBytes: Long,
                             val pageUrl: String, val apkUrl: String, val publishedAt: String?) : State
        data class Downloading(val version: String) : State
        data class ReadyToInstall(val version: String) : State       // waiting on install permission or foreground
        data class Failed(val offline: Boolean) : State
    }
    val state: State                                     // last known; restored from cache at init
    fun addListener(l: (State) -> Unit): () -> Unit      // main thread; Compose wraps it in callbackFlow
    fun check(context: Context, force: Boolean = false)  // no Activity needed when ui == None
    fun update(activity: Activity)                       // existing download + verify + install; browser fallback kept
    fun openStorePage(activity: Activity)                // pageUrl
    fun later()                                          // snooze this version 3 days ("snoozed_until")
    fun skip(version: String)                            // existing "skipped_version"
    fun shouldShowBanner(nowMs: Long): Boolean           // Available && version != skipped && now >= snoozed_until && newer than installed
}
```

- **Throttle.** Once per **24 h** (`CHECK_INTERVAL_MS`). Also check on a manual "Check for updates" and on cold start if the last check failed and more than 1 h has passed. The check is never blocking: it runs on the io executor with a 10 s timeout.
- **Cache.** Persist the last good response in the prefs: `cached_version`, `cached_notes`, `cached_apk_url`, `cached_page_url`, `cached_size` and `cached_at`. On init, `state` is restored from the cache when the cached version is still newer than the installed one. Offline therefore still shows the banner, and the cache clears itself once updated, because the installed version catches up.
- **Version rule** (unchanged, mirrors the server's `lib/version.ts`): strip a leading `v`, compare the dotted numeric parts left to right with missing parts as 0, and treat a version with a prerelease suffix as lower than the plain release. Requirements: `versionName` must equal the tag without `v` (Cairn: `gradle.properties` `cairn.versionName=1.4.0` and tag `v1.4.0`), and `versionCode` must always increase.
- **Banner (Compose apps)**: optional second drop-in `AppDropUpdateBanner.kt`, copied by `appdrop add-updater --compose`.

  ```kotlin
  @Composable fun rememberAppDropUpdate(): State<AppDropUpdater.State>
  @Composable fun AppDropUpdateBanner(state: AppDropUpdater.State.Available, appName: String,
      onUpdate: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit, onNotes: () -> Unit, modifier: Modifier = Modifier)
  ```

  Contract:
  - a `secondaryContainer` card, not error red;
  - the title is "<App> 1.5.0 is available";
  - the body is up to 3 lines of the notes summary plus the size;
  - buttons are **Update** (filled), **Later** (text), and an overflow menu with Skip this version and Release notes (opens `pageUrl`).
  - It never steals focus, needs no dialog and is dismissible.
  - While downloading, the button shows "Downloading..." and is disabled. `ReadyToInstall` shows "Install".
  - TalkBack: the card is one merged node, and the buttons have labels.
- **Update button.** It keeps the existing **in-app download and install**. That is fewer steps than the browser, checksum-verified, and resumable. "Release notes" and the browser fallback open `https://appdrop-store.vercel.app/apps/cairn`. If the owner prefers browser-only, add a per-app flag `updateAction = InApp | Browser` (`Browser` opens `apk.url`, which is `/d/cairn/<id>`).
- **Cairn wiring.**
  - `init(this, base, "cairn", ui = Ui.None)`.
  - `MainActivity.onResume` calls `check(this)`.
  - `WidgetsHomeScreen` shows `AppDropUpdateBanner` at the top when `shouldShowBanner`.
  - Settings > About shows "Check for updates", with the result inline: "You're up to date (1.4.0)", "Couldn't check. You're offline." or "1.5.0 is available" with an Update button. No toasts.
- **Reuse across apps.** Update `~/AppDrop/android/` (core and banner) once, then re-run `appdrop add-updater [--compose]` in each app. It already overwrites only its own files. Apps without Compose (Tally, Balance if WebView or Capacitor) keep `Ui.Dialog`, which gains the cache, the 24 h interval and the snooze for free. Apps that do not have it yet (Daymark, Bloom, Recal, WABox, Balance) need:
  - the manifest snippet (`INTERNET`, `REQUEST_INSTALL_PACKAGES`, FileProvider);
  - `init`, `check` and a banner slot;
  - the version and tag discipline above.

**Is it possible?** Yes, fully client-side with today's AppDrop API. What is needed: the refactor above (one file, about 150 changed lines), one optional Compose file, a script flag in `appdrop.mjs` (`add-updater --compose`), and a docs update in `android-updates.md`. No server change.

---

## Work packages

Each package keeps the build green and can ship as its own release. Run `./gradlew testDebugUnitTest assembleDebug` before merging. Acceptance checks use an **Android 14 (API 34) Pixel emulator**. Note that API 34 has no generated previews, so the pin dialog there shows our extra or the XML preview. The `previewSizeMode` fix needs an optional API 35+ check.

### A. Sticky editor and exact pin preview
Changes:
- `WidgetEditorScaffold`, `EditorLayout`, `PreviewPane` with scaling, and `SectionOptions` (split out of `WidgetCustomizer`);
- `WidgetStudio` and `WidgetConfigActivity` use the scaffold;
- `rememberWidgetPreview` is hoisted;
- `WidgetPinner.pin(..., preview)` with `PinPreview.extras`;
- `CairnWidget.previewSizeMode = Responsive`;
- `WidgetVariants.allReceivers()`, and `registerWidgetPreviews` over all 33 receivers, once per version.

Acceptance:
- In Add widget for Last activity, scroll the Style options: the preview and the tabs stay fixed, and the bottom button stays visible.
- Rotate to landscape: two panes, nothing clipped.
- Set the font to 200% (Settings > Display > Font size max, plus `adb shell settings put system font_scale 2.0`): the preview is at most 35% of the height, the tabs are icons, and the options scroll.
- Choose Wide, Hero distance and Midnight, then tap Add to home screen: the launcher's dialog shows a Midnight Hero widget, not a one-liner and not Dawn. Repeat for Recovery Small Gauge and Overview Large.
- Long-press home > Widgets on a launcher that ignores the extra: the widget still lands configured, as before.

Unit tests:
- `EditorLayoutTest`: cap, minimum, landscape switch and fontScale icon-only rule.
- `PinPreviewTest`: key name `"appWidgetPreview"` and the size guard. Use a fake parcel-size function, to stay pure JVM.
- `PreviewSizeModeTest`: every `CairnWidget.previewSizeMode` is Responsive and equals `WidgetSizes.dp(kind)`.
- `LayoutRulesTest` addition: `Layouts.plan(ActivityHero, 250f, 145f, 1f).style == ActivityHero`, and at h = 40 it is `ActivityLine`. This documents the root cause.
- `VariantsTest`: `allReceivers()` has 33 distinct classes, and `kindOf` and `sizeOf` round-trip for each.

### B. Widget-first home, Settings cleanup, long-press everywhere
Changes:
- the routes and four tabs;
- `WidgetsHomeScreen` and its ViewModel, `WidgetInventory`, orphan cleanup, gallery with Sample / My data;
- the editor's `id` mode, which replaces in-app "Reconfigure";
- `widgets/look` and `widgets/tap`, and removal of the Settings Widgets group;
- `CardWidgetMap`, `Modifier.widgetActions`, `LocalWidgetActions`, the hoisted sheet, card glyphs and the one-time hint;
- the onboarding final step.

Acceptance:
- Cold launch opens Widgets.
- Place two widgets: both appear under Your widgets with their real look. Edit one, change its theme and press Done: the home-screen widget updates within seconds.
- Remove one from the home screen and return: it disappears from the list.
- Settings has no Widgets group.
- Every card in the mapping table opens the sheet on long press and on its glyph. Activity rows still open details on tap.
- TalkBack offers "Add as widget" on each card.
- A fresh install's onboarding ends on the first widget's editor.

Unit tests:
- `CardWidgetMapTest`: every `TodayCard`, Activities and Fitness card enum has a link, and `link.style.kind == link.kind`.
- `WidgetInventoryTest`: a fake id provider maps receivers to kind and size, and orphan ids are computed.
- `RoutesTest`: `forScreen(null) == WIDGETS`, `forScreen("Studio") == WIDGETS`, `forScreen("Home") == WIDGETS`, and the tab list.
- `StudioAndShortcutsTest` updated for the new route.

### C. Sign-in, error banner and navigation fixes
Changes:
- `SyncProblems` and the typed `Gate` reason; `SyncProblemBanner` with actions;
- clearing `gateProblem` on auth change;
- `MainActivity` singleTop plus `onNewIntent` plus `NavRequests`; start destination always WIDGETS, deep targets pushed on top;
- `OAuthRedirectActivity` targets Home;
- `CorosAccountScreen(onSignedIn)`, `account?signIn=true`, the scaffold snackbar, the "Go to widgets" button, and the Developer section.

Acceptance:
- Sign out in Settings, go back to Today, pull to refresh: a neutral banner "Not signed in to COROS" with a **Sign in** button and no Retry. Tapping it opens the account screen with the browser already launching.
- Finish the bounce sign-in: Cairn opens on Widgets with the snackbar "Signed in to COROS. Syncing now...". Back exits the app, so there is no stuck account screen.
- Repeat with the device-code method inside the app: it returns to Widgets automatically.
- Airplane mode, then sync: "You are offline", which clears when the network returns.
- Tap a widget whose tap target is Fitness: Fitness opens, and Back goes to Widgets.

Unit tests:
- `SyncProblemsTest`: one case per row of the table, plus "Already syncing" leads to null, dismissal handling, and NeedsSignIn beating Network when both are present.
- `ManualSyncGateTest`: the reasons are typed.
- `PostSignInRouterTest` (pure): navigation happens only on a false-to-true transition while visible, and never when the screen opened already signed in.

### D. Update notice banner
Changes:
- the `AppDropUpdater` core refactor (state, listeners, cache, 24 h interval, snooze, `Ui.None`) in **AppDrop's template first**, then copied into Cairn with `appdrop add-updater --compose`;
- `AppDropUpdateBanner` on Widgets home;
- inline Settings result;
- docs update in `AppDrop/docs/android-updates.md`.

Acceptance:
- With a fake older `versionName` (debug build with `cairn.versionName=1.3.0`): within one launch the banner "Cairn 1.4.0 is available" shows with notes, and no dialog appears.
- Later hides it for 3 days. Skip hides it until a newer version.
- Update downloads, verifies and opens the installer. A permission prompt comes back on resume.
- Airplane mode on a cold start still shows the cached banner.
- Settings "Check for updates" shows inline results.
- Tally, unchanged apart from re-running `add-updater`, still shows the dialog.

Unit tests: put the pure logic in a small `internal object UpdatePolicy` inside the same file.
- `compareVersions`: `1.10.0 > 1.9.0`, `v1.4.0 == 1.4.0`, `1.4.0 > 1.4.0-beta.1`, `1.4 == 1.4.0`.
- `shouldCheck(lastCheck, lastFailed, now, force)`.
- `shouldShowBanner(cached, installed, skipped, snoozedUntil, now)`.
- `parseLatest` on a JSON fixture copied from the route's shape, including `apk: null` and `latest: null`.
- `summary(notes)` trimming markdown to 3 lines.

---

## Not verified here
- Launcher3 and Pixel reading `EXTRA_APPWIDGET_PREVIEW` from `PinItemRequest.getExtras()` is from memory of the AOSP sources. No AOSP launcher source is available locally. One UI, MIUI and Nova behaviour is unknown, which is why the fallbacks exist.
- Whether click actions inside the extra RemoteViews can fire from the pin dialog. Glance sample previews may carry PendingIntents. Test on the emulator, and if needed compose the pin preview without actions.
- The RemoteViews parcel size for ring and gauge styles (Glance bitmaps) against the 200 KB guard.
- `setWidgetPreviews` rate-limit numbers on Android 15 and 16.
- Whether Balance, Bloom, Recal and WABox use Compose. Only `appdrop.json` `kind: android` and the folder layout were checked.
