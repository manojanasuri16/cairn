# COROS MCP: what the read-only tools actually return

Captured on 2026-11-15 with the debug-only Tool explorer (Settings > Tools). 23 of the 34 tools are callable
(`query*` / `get*` only); the write tools, `queryMenstruationCycles` and the FIT download tools are blocked in code
(`ExplorerPolicy`). No values from the captures are stored in this repo.

Every reply is a JSON-string-encoded plain-text report (see `ToolReply.unwrapQuoted`), except `queryActivityLapData`,
which returns JSON with `columns` (Chinese labels) and rows.

| Tool | Gives | Cairn use |
|---|---|---|
| queryDailyHealthData | 7 days: steps, calories, exercise min, floors, stress avg, sleep summary, sleep HR, resting HR, HRV baseline | used (Today) |
| querySleepOverview | score, stage ratios, window, naps | used (Sleep) |
| querySleepHrv | nightly HRV avg, assessment (Above normal / Normal ...), normal range, baseline, plus a long time series | **new: HRV widget** (use the 7-day summary, skip the time series) |
| queryRecoveryStatus | recovery %, level, hours to full | used |
| queryTrainingLoadAssessment | short/long-term load, ratio, comment per day | used |
| queryFitnessAssessmentOverview | VO2max, running level, threshold pace, race predictions | used |
| querySportRecords | activity list (sport, date, duration, distance, pace, HR, calories, labelId, sportType) | used; also **weekly mileage** |
| getActivityDetail (labelId, sportType) | cadence, stride length, power, elevation gain/loss, **training load**, aerobic/anaerobic TE, training focus, best km, moving pace | **new: activity detail** |
| queryActivityLapData (labelId, sportType) | per-lap distance, time, pace, HR, cadence, power, elevation, etc. (JSON) | **new: laps**, estimated intensity split |
| queryAvgHeartRate | 7-day average with min/max | **new: heart widget** |
| queryRestingHeartRate | 7-day resting HR | **new: heart widget** |
| queryStressLevel | 7-day average stress (breakdown buckets were "No data") | **new: stress** |
| queryStressTimeSeries | very large (about 140 KB for 7 days) | **skip** (too heavy) |
| queryDevices | watch name/model, warranty (serial is sensitive, masked) | **new: device line** (name only) |
| queryTrainingSchedule | planned workouts (empty for this account: "No training schedule found.") | **new: next workout widget** (needs an empty state) |
| queryTrainingPlanLibrary / queryWorkoutLibrary | plans (none in progress) and a library of custom workouts | optional |
| queryHealthCheckTimeSeries | none found in the window | skip |
| queryUserInfo | profile | skip (personal) |
| queryCustomActivityLapData, queryScheduledWorkoutDetails, queryTrainingPlanDetails, queryWorkoutDetails | need ids | not needed |

## Not available from the server (be honest in the UI)
- **Threshold heart rate**: the fitness overview only has threshold *pace*.
- **True 80/20 zone balance**: no time-in-zone data. It can only be *estimated* from lap pace against threshold pace
  and must be labelled "estimated".
- **Stress zone breakdown** (relaxed/low/medium/high) returned "No data".
- **Next workout** depends on the user having a COROS training schedule.

## Budget note
Activity detail and laps are fetched only for new activities (cache by labelId). The 7-day summaries (HRV, avg/resting
HR, stress) change slowly: fetch at most every few hours. Keep the existing per-run and daily MCP call caps.
