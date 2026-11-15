# Cairn: Research and Architecture Plan

Status: draft for review, 2026-11-15
Scope: an Android app (Kotlin, Jetpack Glance, Material 3) with home-screen widgets that show COROS watch data. Everything runs on the device. There is no Cairn backend.

---

## 0. TL;DR

- **There is now an official, sanctioned way to read COROS cloud data: the COROS MCP server** (`https://mcp.coros.com/mcp`). It uses standard OAuth 2.0 with PKCE, dynamic client registration, the device-code grant and refresh tokens (access token 30 days, refresh token 180 days, rotating). Its tools cover almost everything COROS shows: daily health (steps, calories, stress, RHR), sleep with score and stages, overnight HRV with baseline and evaluation, **recovery %, training load, VO2max, threshold, race predictions**, activities, laps and the training schedule. Cairn never has to see the user's password. [S1][S2][S3]
- **Health Connect is the on-device, account-free fallback.** COROS writes only basic types to it: steps, distance, heart rate, sleep, resting HR (likely) and exercise sessions. It does **not** write HRV, training load, recovery, Base Fitness or VO2max. These lists come from third-party guides and the Apple Health equivalent, so we confirm them on the device in milestone M1. [S6][S7][S8]
- **The unofficial Training Hub API** (`teamapi.coros.com`, MD5 password login) works, but it breaks COROS ToS §4(e)/(h) and §7. It rate-limits aggressively and invalidates tokens when you log in elsewhere. The "mobile" variant logs the user out of the COROS phone app. **Recommendation: do not build it.** At most it could be an opt-in experimental adapter, and that is a user decision (§9). [S4][S5][S9][S10]
- **Freshness is bounded by the COROS phone app.** Data only leaves the watch when the COROS app syncs over BLE, and both Health Connect and the cloud update at that moment. Cairn **cannot** make the watch sync. What Cairn can do:
  - help keep the COROS app alive in the background (COROS's own support says background sync works unless the app is killed or battery-optimised) [S11];
  - offer a one-tap "Open COROS to sync" on the widget;
  - post an opt-in nudge when data gets old;
  - poll cheaply and always show the data's age honestly.

---

## 1. Research findings

### 1.1 What the COROS app shows (App 4.x, since July 2025)

COROS App 4.0 reorganised the app into four tabs: Progress, Activities, Explore and Profile. Its "today" view is called **Progress**. [S12][S13][S14]

| COROS screen | Contents (public descriptions plus API field names) |
|---|---|
| **Progress (today)** | Workouts done today, last night's sleep, overnight HRV, steps and calories, plus EvoLab cards: rolling 7-day training load (added Aug 2025), recovery %, fatigue, Base Fitness, load ratio, VO2max and running performance. [S12][S13][S15] |
| **Activities** | List of recent activities (sport, date, distance, duration, pace, HR, training load). A statistics button shows trends over time. Activity detail adds laps, HR and power zones, notes and RPE. [S13][S4] |
| **Explore** | Routes: create, saved routes, send to watch. Out of scope for Cairn. |
| **Profile** | Profile, devices and storage, settings, third-party integrations (Data Sync, including Health Connect), training plans and workouts. [S13][S14] |

These screens map to Cairn widgets in §5. Cairn does not clone the COROS app. It surfaces the 5-6 numbers people actually glance at.

### 1.2 Health Connect: what COROS writes

Setup: COROS app, then Profile, Settings, 3rd Party Apps, Data Sync, Health Connect. You then choose the data types in Health Connect, under Apps, COROS. [S6][S16]

| Type | Health Connect record | Written by COROS? | Confidence |
|---|---|---|---|
| Steps | `StepsRecord` | Yes | Medium (HeiaHeia guide, Apple Health parity) [S6][S8] |
| Distance (walk, run, ride, swim) | `DistanceRecord` | Likely | Medium (Apple Health parity) [S8] |
| Heart rate samples | `HeartRateRecord` | Yes | High [S7][S6] |
| Resting HR | `RestingHeartRateRecord` | Likely (added in App 4.0 for Apple Health) | Low to medium [S8] |
| Sleep | `SleepSessionRecord` (+ stages?) | Yes; stage detail unknown | Medium [S6][S8] |
| Workouts | `ExerciseSessionRecord` | Yes, completeness varies by sport | High [S7][S17] |
| Weight | `WeightRecord` | Possibly | Low [S6] |
| HRV (RMSSD) | `HeartRateVariabilityRmssdRecord` | **No** for Apple Health; unconfirmed for Health Connect | Low [S8][S17] |
| Training load, recovery, Base Fitness, VO2max, stamina | (no Health Connect type, or `Vo2MaxRecord`) | **No** | High [S8] |

Health Connect mechanics that matter for Cairn:

- **No push notifications.** "As your app can't get notified of new data", apps must poll. Changes tokens (`getChangesToken` / `getChanges`) give cheap diffs. **An unused token expires within 30 days**, so we need a timestamp fallback. [S18]
- **Background reads need `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND`** plus a feature-availability check. Google's own sample runs it from a WorkManager periodic job (1 h). [S19]
- **History limit:** by default an app can read only 30 days before its first permission grant. Older data needs `PERMISSION_READ_HEALTH_DATA_HISTORY`. [S19]
- Use `aggregate()` for steps so duplicate sources (phone steps plus COROS) are de-duplicated. Since June 2026, on-device phone steps carry a per-device synthetic package name. Filter by COROS's `DataOrigin` when we want "watch steps only". [S19]
- `metadata.lastModifiedTime` on COROS-origin records tells us **when COROS last synced** into Health Connect. This is our best freshness signal for this source.

### 1.3 Official COROS MCP server (new in 2026; the most important finding)

Repo: `coroslab/COROS-MCP` (docs only; the server is hosted by COROS). [S1][S2][S3]

- **Endpoint:** `https://mcp.coros.com/mcp` redirects by region to `mcpeu`, `mcpus` or `mcpcn.coros.com`. It is stateless MCP over Streamable HTTP (the earlier `Mcp-Session-Id` sessions were removed). [S2]
- **Auth** (public discovery doc, fetched read-only from `/.well-known/openid-configuration`):
  - Authorization endpoint `/oauth2/authorize`, token endpoint `/oauth2/token`, and `device_authorization_endpoint` `/oauth2/device_authorization`.
  - Dynamic client registration at `/connect/register`, with public clients allowed (`token_endpoint_auth_method: none`).
  - PKCE `S256`; scopes `openid offline_access mcp.tools`.
  - Grants: `authorization_code`, `refresh_token` and the **device code** grant.
  - Revocation endpoint available.
  - The official helper registers with a loopback redirect `http://127.0.0.1:43123/callback`. [S3][S20]
- **Token lifetime:** access 30 days; refresh 180 days, with a new refresh token issued on every refresh. [S2]
- **Tools relevant to Cairn:** [S1]
  - `queryDailyHealthData` (steps, calories, stress, sleep, HR overview)
  - `querySleepOverview` (score, stages, window, naps)
  - `querySleepHrv` (daily HRV, normal range, evaluation; up to 7 days)
  - `queryRestingHeartRate`, `queryAvgHeartRate`, `queryStressLevel`, `queryHealthCheckTimeSeries`
  - `queryRecoveryStatus` (current %, level, time to full recovery; current value only)
  - `queryTrainingLoadAssessment`, `queryFitnessAssessmentOverview` (VO2max, running level, threshold pace, race predictions)
  - `querySportRecords`, `getActivityDetail`, `queryActivityLapData`
  - `queryTrainingSchedule`, `queryUserInfo`, `queryDevices`
- **Limits:** the only documented quota is 50 FIT files per 24 h. No other rate limit is published. The docs also say data "must first sync to the COROS App and then upload to the cloud". [S2]
- **Caveats:**
  1. Tool outputs are written for LLMs. Some are formatted text (for example "Sleep Overview …"), some are JSON. The format is not a versioned API contract, and tools have already been removed once (`analyzeActivityDetail`, 2026-10-23). Parsing has to be defensive and tested against fixtures. [S1][S21]
  2. COROS positions it for "AI platforms". The README says compatible MCP clients may connect, and COROS calls MCP the self-service route for developers who don't qualify for the partner API. [S2][S22] Whether a widget app that polls periodically is welcome is **not explicitly stated** (see §9).
  3. The cloud is only as fresh as the last phone sync.

### 1.4 Unofficial Training Hub API (from the cloned repos plus ToS)

Sources: `Pinta365/coros` (TypeScript), `xballoy/coros-api` (NestJS CLI) and `cygnusb/coros-mcp` (Python). The repos were read as data only; none of their code was run. [S4][S5][S9]

- **Hosts:** `teamapi.coros.com` (US/other), `teameuapi.coros.com` (EU), `teamcnapi.coros.com` (CN). A token is valid only in its own region.
- **Auth:** `POST /account/login` with `{account, accountType: 2, pwd: md5(password)}`. This returns `accessToken` and `userId`. Requests then send headers `accessToken` and `yfheader: {"userId":…}`. The success envelope is `result: "0000"`, and `"1019"` means the token is invalid.
- **Token:** cygnusb assumes a 24 h TTL (empirical, not documented). Pinta365 warns that **logging into the Training Hub web app or the phone app can invalidate the token**. The client therefore has to keep the password to re-login, which is the worst storage posture.
- **Endpoints:**
  - `/dashboard/query`: last ~7 days of sleep HRV
  - `/analyse/query`: 28-day `t7dayList` with VO2max, LTHR and stamina
  - `/analyse/dayDetail/query?startDay&endDay`: up to ~24 weeks of `rhr`, `trainingLoad`, `trainingLoadRatio`, `tiredRateNew`, `ati`, `cti`, `performance`, `staminaLevel`
  - `/activity/query` (paged)
  - `/activity/detail/query`: centi-scaled units; timezones are in quarter-hours; `avgSpeed` is actually pace in s/km
  - `/training/schedule/query`
- **Mobile API** (`api*.coros.com/coros/data/statistic/daily`, sleep stages): it needs an AES key extracted from the COROS APK's native library, and **logging in there logs the user out of the COROS phone app**. Never use it. [S9]
- **Rate limiting:** "The API rate-limits aggressively". A 429 arrives as an HTML body. [S4]
- **ToS** (COROS Terms of Service, updated 2023-04-23):
  - §4(e) forbids "any robot, spider, scraper, or other automated means to access the Service".
  - §4(h) forbids bypassing access restrictions.
  - §7 forbids allowing "any third party to log on to the Service using your account information".
  - "Any unauthorized use automatically terminates the permissions and licenses granted to you", and COROS may suspend or terminate accounts. [S10]
  - Strictly speaking Cairn is the user's own client on their own device, but it is still automated access through an undocumented API. The risk to the account is low-probability and high-impact.
- **Partner API:** only for registered companies, by application to api@coros.com. Not applicable to a personal app. [S22][S23]

### 1.5 Keeping widgets fresh without opening COROS: what is possible

The data path is:

```
Watch --BLE (proprietary, COROS app only)--> COROS phone app --+--> Health Connect (on device)
                                                               +--> COROS cloud --> MCP / Training Hub
```

Everything downstream of the COROS app is at most as fresh as its last BLE sync.

| Idea | Feasible? | Notes |
|---|---|---|
| Cairn talks BLE to the watch to trigger a sync | **No** | The protocol is proprietary and undocumented. The watch is bonded to the COROS app, and a second GATT client could disrupt its sync. Reverse engineering conflicts with the ToS. `CompanionDeviceService` only helps for devices you control. [S18] |
| Cairn tells the COROS app to sync (intent or API) | **No public API** | COROS exposes no sync intent. Launching its activity is the only "trigger". |
| Keep the COROS app alive so it syncs in the background | **Yes, best lever** | COROS support: the app must keep running in the background, force-closing blocks BLE sync, and it should be excluded from battery optimisation (Samsung: "Never sleeping apps"). Cairn's onboarding can check `PowerManager.isIgnoringBatteryOptimizations("com.yf.smart.coros.dist")` (to verify in M1 that this works for another package with a `<queries>` entry). It can deep-link to COROS's App-info page and point to dontkillmyapp.com-style OEM steps. [S11] |
| Widget "Sync" button opens COROS | **Yes** | A widget tap may start an activity (a user-initiated `PendingIntent`, which is exempt from background-activity-launch limits). Opening COROS makes it sync. Cairn then enqueues a one-time refresh 2 and 5 minutes later. |
| Staleness notification ("COROS last synced 14 h ago — tap to open") | **Yes, opt-in** | Needs `POST_NOTIFICATIONS` (13+). At most one per day, only within waking hours, and never while the data is fresh. |
| Health Connect push or callback on new data | **No** | Must poll. Changes tokens make polling cheap. [S18] |
| Cloud push (FCM) | **No** | Needs a backend and a COROS webhook (partner API only). |
| Periodic polling | **Yes** | WorkManager periodic, minimum interval 15 min. `updatePeriodMillis` minimum is 30 min. Google warns against per-minute updates. [S24] On Android 16, job runtime quotas tighten by standby bucket, and jobs started in the foreground now count toward the quota. Our jobs are short (under 5 s), so this is fine. [S25] |
| Exact alarms | **Not justified** | `SCHEDULE_EXACT_ALARM` is denied by default on 14+. `USE_EXACT_ALARM` is restricted by Play policy to alarm and calendar apps. Exact timing buys nothing when the source is only as fresh as the last COROS sync. [S26] |
| Foreground service to poll continuously | **No** | Battery cost, a mandatory notification, FGS-type restrictions, and Android 16 counts concurrent jobs against quota anyway. [S25] |
| Refresh on screen unlock (`USER_PRESENT`) | **No** | It is an implicit broadcast and can't be manifest-registered since Android 8. |
| Refresh when Cairn's own UI opens | **Yes** | Always do it. |

**Conclusion:** Cairn's job is to show the data's age honestly, minimise how often it is stale (polling every 30 to 60 min plus refresh on tap or open), and make the fix one tap away. "Always fresh without ever opening COROS" is only achievable to the extent that the COROS app itself is allowed to run in the background.

---

## 2. Decision: data sources

**Recommended primary: official COROS MCP (OAuth). Recommended fallback and complement: Health Connect. Unofficial Training Hub API: not built (see decision D2).**

| Source | Coverage | Sanctioned | Needs account and network | Freshness signal | Role |
|---|---|---|---|---|---|
| COROS MCP | Everything incl. recovery, load, HRV, VO2max, sleep score | Yes (official, OAuth) | Yes | Newest sample timestamp in response + fetch time | **Primary** |
| Health Connect | Steps, HR, sleep, RHR, exercise sessions | Yes (Android platform) | No | `lastModifiedTime` of COROS-origin records | **Fallback + offline + fast path** |
| Training Hub (unofficial) | Similar to MCP, raw JSON | No (ToS risk) | Yes, plus password | Fetch time | Not built (D2) |

How Cairn chooses per metric:

- Metrics available from both sources (steps, sleep duration, RHR, last activity): show whichever has the **newer underlying data**. Health Connect wins on ties because it is local, cheap and works offline.
- COROS-only metrics (recovery, load, HRV, VO2max, race predictions): MCP only. Without MCP, the widget shows a "Connect COROS account" state rather than fake values.
- The user can run **Health Connect only** (no account at all). Cairn still works with reduced widgets.

## 3. Data-staleness strategy

1. **Store three timestamps per metric**:
   - `observedAt`: the moment the value describes (for example the sleep wake-up time or the last step bucket)
   - `sourceUpdatedAt`: when COROS last pushed it (Health Connect `lastModifiedTime`; for MCP, the newest sample time)
   - `fetchedAt`: when Cairn last checked
2. **Widgets always show data age** in the footer, based on `sourceUpdatedAt`: "Synced 2h ago · 07:42". Two forms are shown together:
   - The **absolute time** never becomes wrong between re-renders.
   - The **relative label** is recomputed on every worker run (every 30 min or less), using floor buckets ("just now", "5m", "30m", "1h", "2h", … "yesterday", "3 d"). Glance has no live relative-time view, and a `Chronometer` only counts mm:ss, so the label stays coarse rather than ticking.
3. **Staleness tiers:**
   - **Fresh** (under 3 h): normal styling.
   - **Aging** (3 to 12 h): footer in the secondary colour.
   - **Stale** (over 12 h, or "today" metrics from a previous day): values at reduced emphasis, a sync glyph, and a "Tap to open COROS" footer. The meaning is carried by text, not by colour alone.
   - **Expired** (over 48 h, or auth broken): a placeholder with the reason ("Sign in again", "Health Connect permission removed").
   - Thresholds are configurable.
4. **Day rollover:** at local midnight, "today" metrics (steps, calories) switch to "No data yet today", with yesterday's value shown small. The periodic worker re-renders near midnight without needing an exact alarm. A worker running in the first run after 00:00 is sufficient.
5. **Refresh triggers:**
   - WorkManager periodic job (default 30 min; Health Connect needs no network, MCP requires `NetworkType.CONNECTED`)
   - App foreground
   - Widget tap (refresh, or open COROS and refresh after 2 and 5 min)
   - Widget added or resized
   - Boot and app update (WorkManager restores itself)
   - Exponential backoff on MCP errors, with a hard floor of 15 min between MCP calls
6. **Never block rendering on the network.** Widgets render from Room only. Workers write Room, then call `updateAll()`.

---

## 4. Architecture

### 4.1 Layers

```
Glance widgets / Compose app UI
        |  (read only, Flow)
   Repository (per domain: Daily, Sleep, Recovery, Load, Activities)
        |  merges sources, computes freshness
   Room (cache, source of truth for UI)   +   DataStore (settings, sync state)
        ^                                            ^
   SyncEngine (WorkManager CoroutineWorkers) --------+
        |
   +-----------------+----------------------+----------------------------+
   | HealthConnect   | CorosMcp source      | (TrainingHub source, opt.) |
   | source          | Ktor + MCP Kotlin SDK|  not built by default      |
   +-----------------+----------------------+----------------------------+
                      | AuthManager (AppAuth: DCR + PKCE / device code)
                      | TokenStore (Keystore AES-GCM-wrapped DataStore)
```

### 4.2 Module layout (Gradle, version catalog, convention plugins in `build-logic/`)

```
Cairn/
  app/                      # Application, DI graph, navigation, onboarding, settings
  core/model/               # Pure Kotlin data types: DailySummary, Sleep, Recovery, Activity, Freshness
  core/database/            # Room entities, DAOs, migrations
  core/datastore/           # Settings + sync-state DataStore, TokenStore (Keystore)
  core/designsystem/        # Material 3 theme, type scale, colors, shared Glance + Compose tokens
  core/common/              # Time/units formatting, relative-age formatter, dispatchers
  data/                     # Repositories, source merging, freshness computation
  source/healthconnect/     # HC client wrapper, permissions, changes-token sync, record mappers
  source/corosmcp/          # OAuth (AppAuth), MCP client, tool adapters, defensive parsers + fixtures
  sync/                     # WorkManager workers, scheduling policy, backoff, stale-nudge notifications
  widget/                   # GlanceAppWidget + receivers, layouts per size, previews
  docs/                     # plan.md, decisions
```

Keep modules coarse; this is a one-person app. The split exists mainly to keep `source/*` swappable and testable with fixtures.

### 4.3 Platform targets

- **Decided: minSdk 34** (see §9). Build environment: SDK at `/home/manoja/android-sdk` (referenced by the gitignored `local.properties`), Gradle wrapper, JDK 21 with `jvmTarget = 17`, same toolchain family as the user's other apps (Daymark: AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1; bump if Glance requires it).
- ~~minSdk 28 (Android 9).~~ (original recommendation, superseded) That is the Health Connect floor; on 9 to 13 it is an APK, and from 14 on it is a framework module. If the user only ever uses a modern phone, **minSdk 34** removes Health Connect APK edge cases (decision D7).
- **compileSdk/targetSdk 36** (Android 16). Bump to the newest stable SDK at M0 if a newer one is out.
- Kotlin 2.x, JDK 17+, R8 enabled, 16 KB page-size aligned (no native libs planned).

### 4.4 Libraries (versions current on Google Maven/Maven Central, Nov 2026)

| Purpose | Library |
|---|---|
| Widgets | `androidx.glance:glance-appwidget` 1.2.0 (stable) + `glance-material3` |
| Background | `androidx.work:work-runtime-ktx` 2.12.0 |
| Health | `androidx.health.connect:connect-client` 1.1.0 (stable; 1.2.0-alpha only if needed) |
| Cache | Room 2.8.x (KSP) |
| Settings | DataStore 1.3.0 (Preferences + one Proto/bytes store for tokens) |
| HTTP | Ktor client 3.x with OkHttp engine (the MCP Kotlin SDK is Ktor-based); kotlinx.serialization |
| MCP | `io.modelcontextprotocol:kotlin-sdk` (client, Streamable HTTP). Fallback: a small hand-rolled JSON-RPC client (initialize, tools/list, tools/call) if the SDK is heavy or unstable. |
| OAuth | `net.openid:appauth` 0.11.x (supports DCR, PKCE, Custom Tabs); `androidx.browser` 1.10 |
| Crypto | Android Keystore directly (AES-256-GCM, non-exportable, StrongBox when available) or Tink with `AndroidKeystoreKmsClient`. **Do not use `androidx.security:security-crypto` / EncryptedSharedPreferences: deprecated.** |
| DI | Hilt (or manual DI; small app). Hilt-WorkManager integration if Hilt. |
| UI app | Compose BOM, Material 3, Navigation Compose |
| Tests | JUnit5/Kotest, Turbine, MockWebServer (fixtures of redacted MCP responses), Robolectric for Room, Glance `glance-appwidget-testing` |

### 4.5 OAuth on Android (to validate in M2)

**Verified 2026-11-15** (unauthenticated GET of `/.well-known/oauth-authorization-server`, `openid-configuration`, `oauth-protected-resource`):

- `mcpus`, `mcpeu` and `mcpcn.coros.com` each serve their own metadata with `issuer` = that host. `mcp.coros.com` also answers `/.well-known/*`. MCP endpoint is `<host>/mcp`.
- Endpoints: `/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`, `/oauth2/device_authorization`, `/connect/register` (DCR). PKCE `S256` only. Scopes `openid mcp.tools offline_access`. `token_endpoint_auth_methods_supported` includes `none`.
- Grants: `authorization_code`, `refresh_token`, `urn:ietf:params:oauth:grant-type:device_code` (also `client_credentials`, `token-exchange`, unused). Revocation exists, so sign-out revokes.
- Implemented in `source:corosmcp` (hand-rolled OkHttp + kotlinx.serialization): DCR per sign-in with the loopback redirect, PKCE, device-code fallback, refresh, revoke. Still to confirm on device: whether COROS accepts an ephemeral loopback port.

1. Pick the regional MCP host (US default `mcpus.coros.com`, EU `mcpeu.coros.com`, CN `mcpcn.coros.com`).
2. Dynamic client registration once per install (RFC 7591), scope `mcp.tools offline_access`, `token_endpoint_auth_method: none`, PKCE S256. **Loopback redirect first**: an ephemeral `http://127.0.0.1:<port>/callback` receiver plus a Custom Tab. Laps shows COROS accepts this. A custom scheme (`app.cairn:/oauth2redirect`) is untested.
3. **If loopback fails on device**, fall back to the **device-code grant**: Cairn opens the verification URL in a Custom Tab and polls `/oauth2/token`. This avoids redirect URIs entirely.

> **Reference: Laps** (github.com/coderang-gk/Laps, MIT, read-only). A similar COROS widget app. Use it only to cross-check the OAuth flow, regional hosts and MCP text parsing (`parseMcpSportRecords`). Do not copy its hardcoded fallback IPs, its direct password login (`/account/login`, ToS-violating, rejected in D2), or its plain-SharedPreferences token storage. Re-implement; do not fork.
4. Store only `client_id`, the refresh token, the access token and its expiry, plus the issuer URL, in the Keystore-encrypted store. **Never store the COROS password.** Exclude the store from backup and device transfer (`dataExtractionRules`). Never log tokens. Use OkHttp/Ktor logging with header redaction.
5. "Sign out" calls the revocation endpoint, wipes the token store, and optionally wipes the cache.

### 4.6 Sync engine details

- `PeriodicSyncWorker` (unique, KEEP). Its steps:
  1. Run Health Connect `getChanges` per record type (one token per type, per Google guidance).
  2. On an expired token, re-read from the last timestamp (or 30 days) and de-duplicate by HC record id.
  3. Call MCP tools in a small fixed set:
     - every run: `queryDailyHealthData(days=2)` and `queryRecoveryStatus`
     - once the morning sleep is in: `querySleepOverview` and `querySleepHrv`
     - after a new activity appears: `queryTrainingLoadAssessment`
     - once a day: `queryFitnessAssessmentOverview`
     - when Health Connect shows a new exercise session, or every 6 h: `querySportRecords(limit=5)`
  4. Write Room, recompute freshness, then `updateAll()` on the widgets.
- **Budget:** at most 3 to 4 MCP calls per run, at most 2 runs per hour, which is roughly 100 small requests a day. That is polite for an undocumented quota. Adapt: if `sourceUpdatedAt` hasn't moved in 6 h, poll MCP hourly instead.
- Log `WorkInfo.stopReason` and expose it in a hidden diagnostics screen (useful under Android 16 quotas). [S25]

---

## 5. Widget set

All widgets use `SizeMode.Responsive` with 2 or 3 breakpoints. They render from Room only and share one footer component: data age plus source glyph. Tap on the body opens the matching Cairn screen. Tap on the footer when stale opens COROS.

| # | Widget | Sizes (cells) | Content | Source |
|---|---|---|---|---|
| W1 | **Today** | 2x2, 4x2, 4x3 | 2x2: steps ring + steps. 4x2: steps, sleep duration (score), overnight HRV vs baseline, RHR, recovery %. 4x3 adds calories and stress. | MCP + HC (HC-only mode drops HRV and recovery) |
| W2 | **Recovery** | 2x2, 3x2 | Recovery % (large numeral, arc), level word ("Fully recovered"), time to full recovery, HRV evaluation | MCP |
| W3 | **Training load** | 4x2, 4x3 | 7-day load bars (Glance `Box` rows or a pre-rendered bitmap), load ratio / fatigue label, VO2max and trend | MCP |
| W4 | **Last activity** | 4x1, 4x2 | Sport icon, title or date, distance, duration, pace or speed, avg HR, training load. 4x1 is a single line. | HC exercise session + MCP detail |
| W5 | **Sleep** | 2x2, 4x2 | Duration, score, stage bar (deep/light/REM/awake), window "00:28–08:30", HRV | MCP (HC fallback: duration and stages if COROS writes them) |
| W6 | **Fitness / race** (optional) | 4x2 | VO2max, threshold pace, predicted 5K/10K/HM/M | MCP, refreshed daily |

Ship W1, W2 and W4 first. They cover the "Progress" glance that people open COROS for.

---

## 6. Design notes (Apple principles, applied to Android and Material 3)

Distilled from `.agents/skills/apple-design/SKILL.md`. It is written for web motion, so only the transferable principles are applied here.

- **Purpose and simplicity, not minimalism (§16).** One hero number per widget, at most 4 secondary values. Each widget answers one question: "Am I recovered?", "How did I sleep?", "What did I do?". Advanced data sits one tap deeper, in the app.
- **Hierarchy from weight + size + leading as a set (§15).**
  - Hero numerals are large, medium or semibold, with tight leading. Labels are small, with slightly positive tracking.
  - Use the platform font (Roboto / Google Sans via the default `FontFamily`) and honour the user's font scale. Glance uses `sp`, so test at 200%.
  - Size-specific tracking: negative on large numerals, roughly 0 for body, slightly positive for captions. Glance `TextStyle` has no `letterSpacing`, so apply it in the Compose app. In widgets, rely on the system font's optical tuning, and use `AndroidRemoteViews` only if a numeral looks loose.
- **Familiarity.** Use Material 3 widget conventions:
  - system corner radius (`system_app_widget_background_radius` / inner radius)
  - `GlanceTheme` with dynamic colour (Material You), plus a fixed fallback palette below Android 12
  - a widget picker preview (generated previews on Android 15+)
  - an in-app configure activity only where a choice is needed (for example which metric W1 shows at 2x2)
- **Feedback: status, completion, warning, error (§16).** The freshness footer is the status channel. Stale, expired and auth-error states use explicit words, never a silent blank. A tap on refresh shows an immediate "Refreshing…" state in the widget (Glance state update) before the work completes, which is the widget version of the "respond on press" rule (§1).
- **Restraint with colour; materials and depth (§12).** Widgets are flat, solid surfaces. Glance has no blur and translucency hurts wallpaper legibility, so the "material" idea becomes a tonal surface (`surfaceContainer`) with the colour carried on solid accent elements (ring and bars). Never layer light-on-light.
- **Craft.** Values never shift width between updates: use fixed slots, and tabular figures in the app via `fontFeatureSettings = "tnum"`. Pick one spacing grid (4 dp; widget padding 12 to 16 dp) and use it everywhere. Show units in a smaller weight next to numbers ("7.2 km").
- **Motion (in the Compose app only; widgets cannot animate).**
  - Default springs critically damped: `spring(dampingRatio = 1f, stiffness ≈ (2π/0.35)² ≈ 320)`.
  - Use a slight bounce (`dampingRatio 0.8`) only after a flick, for example dismissing a sheet.
  - Sheets and drawers follow the finger 1:1, are interruptible, and hand off velocity on release (Compose `AnchoredDraggable` does this).
  - Use predictive back.
  - Respect "Remove animations" (`Settings.Global.TRANSITION_ANIMATION_SCALE == 0` or `ANIMATOR_DURATION_SCALE == 0`): use cross-fades instead of slides (§14).
- **Haptics (§13).** Use them only on meaningful commits (pull-to-refresh threshold reached, sign-in success) via `HapticFeedbackConstants.CONFIRM` / `REJECT`, fired on the same frame as the visual.
- **Responsibility and agency (§16).** Ask for Health Connect permissions only for the types used, at the moment the user enables a widget that needs them. Explain the COROS account option plainly, including what is and isn't stored. Sign out and "Delete all Cairn data" are always one level deep in settings.
- **Wayfinding and labels.** Use specific tab names in the app ("Today", "Activities", "Fitness"), mirroring COROS but narrower.

---

## 7. App screens (minimal)

- **Onboarding:**
  1. Choose sources (Health Connect / COROS account / both).
  2. Grant permissions.
  3. Run the COROS background health check (battery optimisation status for the COROS app, a link to its App-info page, OEM tips).
  4. Add a widget (`requestPinAppWidget`).
- **Today:** the same metrics as W1 and W2, with 7-day sparklines.
- **Activities:** the last 20 activities, with detail on tap.
- **Fitness:** load, VO2max, race predictions.
- **Settings:** sources, refresh interval (15/30/60 min), stale thresholds, stale nudge on/off, units, sign out, delete data, diagnostics (last runs, stop reasons, HC token state).

---

## 8. Milestones

Each milestone ends with the user's commit and push of the listed work. Claude does not commit.

| # | Milestone | Done when | Commit |
|---|---|---|---|
| M0 | **Scaffold** | Gradle version catalog, `build-logic`, module skeletons, app builds and launches an empty Compose screen, lint and unit-test tasks wired, `.gitignore` covers `local.properties` and keystores | `chore: project scaffold` |
| M1 | **Health Connect spike** | Diagnostics screen lists the record types COROS (`com.yf.smart.coros.dist`) actually writes on the user's phone, with counts and the latest `lastModifiedTime`. Background-read feature check. Battery-optimisation check of the COROS package verified. **Update §1.2 of this doc with the real list.** | `feat(hc): health connect inspector` |
| M2 | **COROS MCP spike** | OAuth works on device (DCR + PKCE custom scheme, or device-code fallback). `tools/list` succeeds. One call per needed tool captured as **redacted** fixtures under `source/corosmcp/src/test/resources`. Token stored via Keystore. Sign-out revokes. | `feat(mcp): oauth + mcp client spike` |
| M3 | **Data layer** | Room schema, repositories, source merge rules, freshness model, defensive MCP parsers with fixture tests, HC mappers with tests | `feat(data): cache, repositories, freshness` |
| M4 | **Sync engine** | Periodic + on-demand workers, changes-token handling incl. expiry path, MCP call budget and backoff, `updateAll()`, diagnostics of stop reasons | `feat(sync): workmanager sync engine` |
| M5 | **Widgets v1** | W1 Today, W2 Recovery, W4 Last activity, all sizes, freshness footer and stale tiers, light/dark/dynamic colour, previews | `feat(widget): today, recovery, last activity` |
| M6 | **Freshness helpers** | Tap-to-open-COROS with delayed refresh, opt-in stale notification (one per day max), onboarding COROS background check | `feat(sync): freshness nudges` |
| M7 | **App screens** | Onboarding, Today, Activities, Fitness, Settings (sign out, delete data, intervals) | `feat(app): core screens` |
| M8 | **Widgets v2** | W3 Training load, W5 Sleep, W6 Fitness (optional) | `feat(widget): load, sleep, fitness` |
| M9 | **Polish and hardening** | Accessibility (TalkBack labels on widgets, 200% font), reduced motion, R8 release build, a 3-day battery check via `adb shell dumpsys batterystats`, Android 16 standby-bucket tests (`am set-standby-bucket … rare`) | `chore: polish and release build` |
| M10 | *(only if D2 = yes)* Training Hub adapter behind an "experimental" toggle; web login only, never mobile | `feat(source): experimental training hub` |

---

## 9. Decisions needed from the user

> **Resolved by the user (2026-11-15):** D1 = MCP + Health Connect. D2 = no unofficial API (M10 dropped). D3/D4/D5 = MCP polling OK every 30 to 60 min; refresh token in Keystore-encrypted storage. D7 = **minSdk 34**. D8 = **personal sideload** (no Play review). Still open: D6, D9 to D12 (defaults used until answered: stale nudge opt-in at 12 h, 30 days of history, Material You dynamic colour, W1/W2/W4 first). Scope: Cairn stays focused on recovery and daily health, not training plans.

1. **D1. Primary source.** Recommended: **COROS MCP (official OAuth) primary plus Health Connect fallback.** Alternatives:
   - (b) **Health Connect only**: no account and fully offline, but no recovery, HRV, load or VO2max.
   - (c) **MCP only**: simpler, but no offline or local fast path.
2. **D2. Unofficial Training Hub API: yes or no?**
   - It requires storing your **COROS password** on the phone (the token is invalidated whenever you log in elsewhere, so re-login is needed).
   - It breaches ToS §4(e)/(h) and §7, and COROS may suspend or terminate accounts.
   - It is rate-limited aggressively.
   - Recommendation: **no**, now that the official MCP exists. If yes: opt-in, web login only, never the mobile API (it logs you out of the COROS app), and the password stays Keystore-encrypted.
3. **D3. Is MCP polling acceptable use?** COROS documents MCP for "AI platforms" and "compatible MCP clients", but does not state rules for a widget app calling it periodically. Options:
   - (a) proceed with the conservative budget in §4.6;
   - (b) first email COROS (api@coros.com / support) to ask;
   - (c) refresh MCP only on app open and on widget tap, with no periodic MCP calls.
4. **D4. Token storage.** Cairn would keep a COROS OAuth refresh token (valid 180 days, rotating) in Keystore-encrypted storage, excluded from backup. Do you accept this? Do you want biometric unlock gating? Gating would break background refresh, so this plan does not recommend it.
5. **D5. Refresh cadence vs battery.** Default every 30 min (Health Connect), with MCP adapting between 30 and 60 min. Choose 15, 30 or 60.
6. **D6. Stale nudges.** Do you want an opt-in notification when COROS hasn't synced for 12 h or more (max one per day, waking hours)? Is the 12 h threshold right?
7. **D7. minSdk.** 28 (broad) or 34 (simpler Health Connect, if this is only for your own modern phone)? Which phone and Android version will you use?
8. **D8. Distribution.**
   - Personal sideload: no Play review.
   - Google Play: the Health Connect permissions declaration and background-read justification are required, and the privacy policy needs Health data disclosures.
   - Which one?
9. **D9. Health history.** Request `READ_HEALTH_DATA_HISTORY` (older than 30 days, for trends), or keep to 30 days?
10. **D10. Visual identity.** Material You dynamic colour (matches your wallpaper), or a fixed Cairn palette?
11. **D11. COROS account region** (EU, US or CN), for the OAuth issuer and testing. Discovery handles this automatically, but tell us if you have accounts in more than one region.
12. **D12. Widget scope for v1.** Today, Recovery and Last activity first (recommended), or a different set?

---

## 10. Explicitly impossible or out of scope

- Forcing the watch to sync, or reading the watch directly over BLE.
- Real-time (minute-level) widget data, or any data fresher than the COROS app's last sync.
- Push updates without a backend.
- Health Connect HRV, training load, recovery or VO2max from COROS (COROS doesn't write them; to confirm in M1).
- Exact-alarm or foreground-service polling (policy, battery, and no benefit).

---

## Sources

- [S1] COROS MCP Tool Guide: https://github.com/coroslab/COROS-MCP/blob/main/docs/en/tools.md
- [S2] COROS MCP FAQ and setup guide: https://github.com/coroslab/COROS-MCP/blob/main/docs/en/faq.md , https://github.com/coroslab/COROS-MCP/blob/main/docs/en/getting-started.md
- [S3] COROS MCP README and login skill (OAuth flow, scopes, registration): https://github.com/coroslab/COROS-MCP (commit 9f58a4b; `skill/coros_mcp_login_gateway/scripts/coros_mcp_login.py`)
- [S4] Pinta365/coros (unofficial TS client; rate limit and token invalidation notes): https://github.com/Pinta365/coros (commit 240ac18)
- [S5] xballoy/coros-api (unofficial CLI): https://github.com/xballoy/coros-api (commit cc497de)
- [S6] HeiaHeia: Connecting COROS (Health Connect setup, types): https://support.heiaheia.com/connecting-coros
- [S7] Zone Training Log: Connect COROS on Android: https://www.zonetraininglog.com/faq/connect/android/coros
- [S8] Constant: Does COROS sync with Apple Health? (synced vs not synced metrics): https://trainconstant.com/blog/coros-apple-health
- [S9] cygnusb/coros-mcp (unofficial; endpoints, fields, mobile-login caveat): https://github.com/cygnusb/coros-mcp (commit 99aac24), `docs/mobile-token.md`
- [S10] COROS Terms of Service (updated 2023-04-23): https://coros.com/terms
- [S11] COROS support: Troubleshooting unsynced workouts (background running, battery optimisation, manual sync): https://support.coros.com/hc/en-us/articles/4404797030548-Troubleshooting-Unsynced-Workouts-on-COROS-Devices
- [S12] COROS: App 4 feature update: https://coros.com/stories/coros-metrics/c/app-4-feature-update
- [S13] the5krunner: COROS 4.0 What's New: https://the5krunner.com/2025/07/07/coros-4-0-whats-new/
- [S14] COROS press release, July 2025 software update: https://coros.com/ca/stories/press-release/c/coros-announces-july-2025-software-update
- [S15] COROS support: July 2025 Feature Update Highlights: https://support.coros.com/hc/en-us/articles/37715516062740-July-2025-Feature-Update-Highlights
- [S16] COROS support: Supported 3rd Party Apps: https://support.coros.com/hc/en-us/articles/360040256531-Supported-3rd-Party-Apps
- [S17] HRV4Training: COROS x HRV4Training: https://marcoaltini.substack.com/p/coros-x-hrv4training ; Core Direction Android guide: https://app.coredirection.com/guides/connect-android
- [S18] Android: Health Connect, Synchronize data (changes tokens, 30-day expiry, no notifications, CompanionDeviceService): https://developer.android.com/health-and-fitness/health-connect/sync-data
- [S19] Android: Health Connect, Read data (background read, history limit, aggregate, SPN steps): https://developer.android.com/health-and-fitness/health-connect/read-data
- [S20] COROS MCP OAuth discovery (public metadata): https://mcp.coros.com/.well-known/openid-configuration
- [S21] COROS MCP changelog: https://github.com/coroslab/COROS-MCP/blob/main/changelog/CHANGELOG.md
- [S22] COROS support: Connect Your Data / partner API vs MCP self-service: https://support.coros.com/hc/en-us/articles/53181260265492-Connect-Your-Data
- [S23] COROS support: Submit an API Application: https://support.coros.com/hc/en-us/articles/17085887816340-Submit-an-API-Application
- [S24] Android: Manage and update GlanceAppWidget (30-min updatePeriodMillis, WorkManager 15 min): https://developer.android.com/develop/ui/compose/glance/glance-app-widget
- [S25] Android 16 behavior changes (JobScheduler quota, standby buckets): https://developer.android.com/about/versions/16/behavior-changes-all
- [S26] Android 14: Schedule exact alarms denied by default: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms ; https://developer.android.com/training/scheduling/alarms
- Apple-design principles: `/home/manoja/Cairn/.agents/skills/apple-design/SKILL.md`

Notes on verification:
- COROS support pages returned HTTP 403 to direct fetches. Claims attributed to them come from search-result summaries and should be re-checked in a browser.
- The Health Connect type list is unconfirmed until M1.
- MCP redirect-URI acceptance and response formats are unconfirmed until M2.
