# Testing and QA

Unit tests run on the JVM (`./gradlew testDebugUnitTest :core:model:test :core:common:test`). Everything below needs a device or emulator. Package name: `app.cairn`. Use synthetic or your own data; do not paste health data into issues.

## 1. Three-day battery check

```bash
adb shell dumpsys batterystats --reset            # start clean (unplug the phone afterwards)
# ... use the phone normally for 3 days, widgets on the home screen ...
adb shell dumpsys batterystats --charged app.cairn > cairn-battery.txt
adb shell dumpsys batterystats --checkin | grep -i "app.cairn"
adb shell dumpsys batterystats | grep -A12 "app.cairn"   # per-UID totals
adb shell dumpsys deviceidle whitelist | grep cairn      # should NOT be whitelisted
```

Look at: estimated power use (mAh and percent, should be well under 1%), wakelock time (near zero), JobScheduler job count and total runtime, network bytes. Also check Settings > Battery > Battery usage > Cairn. Attach `adb bugreport` only after removing personal data.

## 2. Standby bucket behaviour

```bash
adb shell am get-standby-bucket app.cairn
adb shell am set-standby-bucket app.cairn rare        # also try: frequent, working_set, restricted
adb shell dumpsys usagestats | grep -A3 app.cairn
adb shell dumpsys battery unplug                       # let jobs run on "battery"
```

Expected: periodic sync still runs but less often in `rare`/`restricted`; the widget footer shows an honest age ("Aging" then "Stale") rather than hiding it; opening Cairn or tapping a widget triggers a refresh. Restore with `adb shell am set-standby-bucket app.cairn active` and `adb shell dumpsys battery reset`.

## 3. WorkManager and JobScheduler state

```bash
adb shell dumpsys jobscheduler | grep -B2 -A20 "app.cairn"
adb shell dumpsys activity service SystemJobService | grep -i cairn
# Force the periodic job now (find the job id in the jobscheduler dump):
adb shell cmd jobscheduler run -f app.cairn <JOB_ID>
adb shell cmd jobscheduler get-job-state app.cairn <JOB_ID>
adb logcat -s WM-WorkerWrapper WM-SystemJobService:V     # Cairn never logs tokens or health data
```

Expect one unique periodic job (`cairn-sync-periodic`) and no stuck or duplicated jobs. The diagnostics screen shows the last stop reasons.

## 4. Release build

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Smoke test the release APK (R8 can break reflection): sign in to COROS, run a sync, add each widget, rotate, toggle dark mode, sign out. Check `adb logcat -s AndroidRuntime` for `ClassNotFoundException`, `NoSuchMethodException` or serialization errors.

## 5. Manual QA checklist

Widgets (Today, Recovery, Last activity, Training load, Sleep, Fitness):
- [ ] Add each at its smallest, a typical (2x2 and 4x2) and the largest size; resize live. Bigger cells show more, nothing clips, one hero number per widget.
- [ ] Today: ring only when tiny; ring plus sleep, resting HR, recovery rows on a 2x2; more rows when tall.
- [ ] Sleep: duration, score, stage bar, window and legend appear as height grows.
- [ ] Last activity: distance, duration, pace and average HR on a 2x2 or larger; one line when 4x1.
- [ ] Footer age is visible at every size and font scale; short form on narrow cells.
- [ ] Freshness tiers: fresh (normal), aging (muted), stale (error colour, bold, wording "Stale, last synced ...", tap opens COROS). Test by setting the phone clock forward or pausing sync for a day.
- [ ] Sign-in expiry: revoke access on COROS, sync; widgets show "Sign in again" and a tap opens Cairn account.
- [ ] Offline (airplane mode): widgets keep last data and age grows; no crash; recovers when online.
- [ ] Health Connect denied: revoke permissions; app and widgets show an explanation, not blank or crash.
- [ ] Units: switch km/mi in Settings; distance and pace update in app and widgets. Intensity Trend shows as a percent everywhere (0.47 is 47%); pace as 9'50" /km, distance with two decimals, durations as 6 h 47 min.
- [ ] Tap targets: body tap opens the chosen COROS screen or Cairn; footer is at least ~32dp tall; both reachable with a thumb.
- [ ] Light, dark, dynamic colour; launcher with different grid sizes.

Accessibility:
- [ ] TalkBack: each widget reads one summary (title, values, age); the footer is a separate button saying where it opens; app screens read in logical order, rings and charts have descriptions.
- [ ] 200% font (Settings > Display > Font size, and Display size largest): no clipped or overlapping text in app screens or widgets; low-priority rows disappear before the hero or footer is cut.
- [ ] Reduced motion: `adb shell settings put global animator_duration_scale 0` and `transition_animation_scale 0`; navigation cross-fades instead of sliding, rings snap, onboarding fades. Restore with value `1`.
- [ ] Font scale from adb: `adb shell settings put system font_scale 2.0` (restore 1.0).

App:
- [ ] Onboarding, Today, Activities, Fitness, Settings, Diagnostics, sign out and delete data.
- [ ] Rotate and process death (`adb shell am kill app.cairn`) keep state.

v1.2 checks (emulator):
- [ ] Widget sync icon: tap it on every style and size; no app opens; footer shows "Syncing…" then the dot returns to green. Airplane mode: "Couldn't sync, tap to retry".
- [ ] Widget body tap opens COROS and Cairn syncs at once (Sync diagnostics shows a USER run) plus the 90 s and 6.5 min runs.
- [ ] Today pull-to-refresh and header sync icon: no snackbar, icon spins (still clock with animations off), "Up to date" for a few seconds, failure as an inline banner with Retry.
- [ ] Sync every 5 min: chain visible in diagnostics ("5 minute chain"); switching to 15 min cancels it.
- [ ] Add widget (Settings, app-icon long-press, card long-press): pick Wide 4x2, Add to home screen: the widget lands wide and already styled.
- [ ] Overview with 4 stats at a 2x2 and 4x2 cell shows four tiles.
- [ ] Today widget on a wide-font device: no label shown as "S…" (full, short or icon only).

## 6. Manual sign-in tests

Settings > COROS account. Start each case signed out.

1. **Happy path (bounce).** Tap Sign in, authorize on COROS. The COROS page forwards to Cairn (tap "Open Cairn" if it does not). Expect "Finishing sign-in...", then the COROS account screen "Signed in (US)" and a first sync (Sync diagnostics shows a SIGN_IN run).
2. **Process killed.** Tap Sign in, then `adb shell am kill app.cairn` while Chrome is open, authorize. Cairn must still sign in (pending record restored).
3. **Slow browser.** Leave the COROS page open 30 s after returning to Cairn: the "Still nothing?" hint appears, nothing is cancelled. After 5 minutes it becomes "Sign-in timed out" with Try again. A page opened more than 15 minutes later says "Sign-in session expired, please start again".
4. **Airplane mode.** Enable before tapping Sign in: "Network error". Enable after authorizing (before returning): the exchange retries once, then shows the network error; sign in again.
5. **Cancel / restart.** While waiting: Cancel returns to Sign in; Sign in, the local method and device code stay enabled and restart cleanly.
6. **Local method.** "Having trouble?" > "Try the local method" (deny notifications once to confirm it still works). The "Signing in to COROS" notification disappears afterwards.
7. **Device code.** The code shows on screen; approving on COROS signs in.
8. **Refresh token rejected.** Revoke Cairn in COROS settings, sync: widgets and the account screen say "Sign in again".
9. **Wrong region.** Account on EU: US attempt falls back to EU automatically only if COROS returns an account_not_found style error; otherwise "Try another region".
10. **Scripted/emulator:** `adb shell am start -a android.intent.action.VIEW -d "app.cairn.oauth://callback?code=x&state=y"` with no pending sign-in must show "Sign-in session expired".

## Sticky editor and exact pin preview (package A)

Manual checks on a device or emulator:

1. Widgets > Add widget > any kind. Scroll the options (Style, Colour, Content, Size, Tap, and "More options"): the preview and the tab row stay at the top, with a faint shadow, and "Add to home screen" stays at the bottom. The preview is at most about 35% of the screen height.
2. Repeat from a launcher-started configure screen (long-press a placed widget > Reconfigure): preview pinned, Cancel / Done pinned.
3. Rotate to landscape or use a tablet (width 600 dp or more): preview on the left, options on the right. Set the font size to the largest: tabs show icons only, nothing overflows. Open the keyboard or split-screen: the bottom bar stays reachable.
4. Pin Last activity 4x2 with the Hero style: the system "Add to home screen" dialog shows the Hero layout, not the one-line layout. (Launchers that ignore the extra show the generated or static preview; the generated one must still be the full layout, not the one-liner.)
5. Android 15+: open the widget picker and check the previews of the 33 providers (including Overnight HRV, Heart rate, Stress and Weekly mileage in all sizes): none is shown in its minimum-size, one-line layout.

## Widget-first home, Settings cleanup, long-press (package B) and sign-in navigation (package C2)

- [ ] First launch: onboarding step 3 ends with "Choose a widget" and lands on the Widgets tab (gallery, "Add your first widget" empty state). Bottom bar: Widgets, Today, Activities, Fitness; Settings gear in the top bar.
- [ ] Gallery: 11 cards with live previews in your default look, size chips 2x2/4x2/4x3 change the preview; tapping a card opens the sticky editor with that size. Menu: Default look (style, background, opacity) and Tap targets.
- [ ] Place two widgets, return to Widgets: "Your widgets" lists both (kind, size, style); Edit opens the configure screen for that widget; Done returns. Remove one on the home screen; it disappears from the list on return.
- [ ] Settings has no Widgets group (Appearance, Sync, Sources, COROS account, Notifications, Units, Tools, About, Your data).
- [ ] Long-press every card (Today header, Today's Activity, Heart Rate / Resting HR / Stress tiles, Sleep, HRV, Weekly load, Training status, Recovery, Running fitness, Last 7 days; Activities month header, a row, the details sheet; Fitness training status, running fitness, race predictions): haptic, then "Add to home screen" opens the editor preset to that widget and style; "Open in COROS" works. A plain tap on a row still opens its details.
- [ ] One-time Today tip "press and hold any card": dismiss with X, or it disappears after the first long press; it does not return after restarting the app.
- [ ] Signed out: sync banner shows "Sign in" (no Retry) and opens the COROS account screen, which starts sign-in. After signing in (in-app, and via the browser redirect) the app lands on Widgets with "Signed in to COROS" once, the stale banner is gone, and Back leaves the app (no account screen left). The account screen shows a "Done" button when signed in. A network error still offers Retry.

## Update banner (debug build, emulator)
- [ ] `adb shell am broadcast -n app.cairn/.demo.FakeUpdateReceiver -a app.cairn.FAKE_UPDATE` (optional `--es version 1.6.0 --es notes '"New widgets and a calmer sign-in"' --ez apk false`): the "Cairn 1.5.0 is available" card appears at the top of Widgets and under the sync line on Today, Activities and Fitness; no dialog.
- [ ] Update opens the AppDrop store page; X hides it (24 h); Skip this version hides it until a newer version; `--ez clear true` removes it.
- [ ] Dark/light, font scale 2.0 (buttons wrap, nothing clipped), animator scale 0 (no motion); content below stays usable.
- [ ] Airplane mode + cold start with the fake cache: the banner still shows.
- [ ] Settings > About > Check for updates shows "You're up to date (version)" or the banner inline, no toast.
