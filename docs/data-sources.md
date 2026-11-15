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
| querySleepHrv | nightly HRV avg, assessment (Above normal / Normal ...), normal range, baseline, plus a long time series | used: Overnight HRV card (only the assessment summary is parsed; the time series is skipped) |
| queryRecoveryStatus | recovery %, level, hours to full | used |
| queryTrainingLoadAssessment | short/long-term load, ratio, comment per day | used |
| queryFitnessAssessmentOverview | VO2max, running level, threshold pace, race predictions | used |
| querySportRecords | activity list (sport, date, duration, distance, pace, HR, calories, labelId, sportType, Location). Accepts `startDate`/`endDate` (yyyyMMdd) and `limit` | used: two windowed calls (this month, last month; `startDate`/`endDate` yyyyMMdd, `limit` 100, no paging exists) so a per-call cap cannot hide a month; each refreshed every 6 h |
| getActivityDetail (labelId, sportType) | cadence, stride length, power, elevation gain/loss, **training load**, aerobic/anaerobic TE, training focus, best km, moving pace | used: per-activity training load (cached by labelId, fetched once per new activity of the last 7 days) |
| queryActivityLapData (labelId, sportType) | per-lap distance, time, pace, HR, cadence, power, elevation, etc. (JSON) | **new: laps**, estimated intensity split |
| queryAvgHeartRate | 7-day average with min/max | used: Heart Rate card (today's daily average, min and max) |
| queryRestingHeartRate | 7-day resting HR | used: Resting HR card and its 7-day average |
| queryStressLevel | 7-day average stress (breakdown buckets were "No data") | used: Stress card ("Average Stress") |
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

## COROS app card -> tool and field (how Cairn reproduces the COROS screens)
| COROS card / label | Tool: field | Cairn value and format |
|---|---|---|
| Header: steps, kcal, min | queryDailyHealthData: Steps, Calories, Exercise | "9,443 steps", "796 kcal", "55 min" |
| Today's Activity | querySportRecords: Distance, Duration, Average Pace, Location, date | big "5.55 km" (two decimals), "54:37", `9'50" /km`, "Sunday, 15 Nov", title "Sunday  <Location> Run" |
| Heart Rate | queryAvgHeartRate: today's average, Min, Max | "70 bpm", "Daily average", "44-154". The server gives no latest-reading time, so it is **not** called "latest" |
| Resting HR | queryRestingHeartRate: today's value | the row with the **latest date** (never the daily summary's figure, which can differ by 1 bpm; with no row the card is hidden); caption "Today" or the date; "7-day avg" = mean of the 7 rows (COROS's "30-day Avg" is not available) |
| Stress | queryStressLevel: Average Stress | "30", "Average Stress" (zone buckets are "No data", skipped) |
| Sleep | querySleepOverview: Sleep Score, Main Sleep (asleep), window, stage ratios; queryDailyHealthData: stage minutes | score big + rating word, "6 h 47 min", stage bar, window under the bar |
| Overnight HRV | querySleepHrv, HRV Assessment: HRV Avg, the text after the dash, Normal Range, Baseline | the server's own assessment word, "Average 65 ms", "Normal Range 46-58 ms", "Baseline 52 ms" |
| Weekly Training Load | getActivityDetail: Training Load, summed over the activities of the last 7 days (today and 6 days back) | big sum, Monday-Sunday bars. COROS's "Suggested a-b" range is **not** in any tool, so it is omitted |
| Training Status | queryTrainingLoadAssessment: Comment, Short-Term Load, Long-Term Load, Load Ratio | status word = Comment; **Load Impact** = short-term load; **Base Fitness** = long-term load; **Intensity Trend** = Load Ratio x 100 as a percent (0.47 -> "47%"); "7-Day Training Load" = the sum above |
| Recovery | queryRecoveryStatus: Recovery, Level, Estimated Full Recovery | "99 %", state word ("Fresh"), "3 hrs till full recovery" |
| Running Fitness | queryFitnessAssessmentOverview: Running Level, Marathon Prediction (also VO2max, Threshold Pace, 5K/10K/Half) | value exactly as returned (one decimal only when present, e.g. 73.3), "Marathon 4:09:57", 0-100 ring. No trend arrow: no history is cached |
| Activities tab (All activities) | querySportRecords, this and last month | month header "Nov  9.58 km  3 activities  2.2 hours", then per activity: icon, distance, duration, pace, weekday/date and title |

Formats: pace `9'50" /km`; durations `54:37` (clock) and `6 h 47 min`; distance two decimals; percent `99 %` on big cards and `47%` for Intensity Trend; dates `Sunday, 15 Nov`; `bpm` lower case.

## Assumptions and discrepancies
- **Sleep rating words are an assumption.** COROS does not publish its score bands. Cairn uses Optimal (80 and above), Good (60-79), Fair (40-59), Poor (below 40).
- **Sleep discrepancy (observed 2026-11-15).** The COROS app showed score 87 and 6 h 47 min, while `querySleepOverview` (and the matching `queryDailyHealthData` block) returned 73 and 6 h 4 min for the same wake-up day. Both tools agree with each other, so the likeliest cause is that Cairn read an earlier or partial reading and the watch synced a fuller night afterwards (or the app counts a nap). The tools expose no field that explains it, so this is **unverified**. Decision: `querySleepOverview` stays the source (it is the only one with the window and score); when two readings of the same wake-up day exist, the one with the **later wake time** wins (`Merge.coalesceSleep`), and today's night is re-read at most every 3 hours (it used to be read once). Check on the device after the next sync whether the numbers now match the COROS app.
- Activity titles: the server has no title field. "Sunday  Riverside Run" is built as weekday + Location + sport name without "Outdoor"/"Indoor" (assumption). If the Location already ends with the sport word (COROS: "Riverside Run") it is used as is.
- Running Level: the server returns an integer ("73"), so Cairn shows 73; a decimal such as 73.3 would be kept, but COROS's 73.3 is not in the tool output.
- Intensity Trend, Load Impact and Base Fitness names and the load-ratio-as-percent mapping come from comparing the COROS screens with the tool output; they are not documented by COROS.

## Fetch schedule (budget)
Per run at most 4 calls including a `tools/list` refresh; daily cap 600. Order after daily and recovery: sleep (retry hourly while today's night is missing, refresh every 3 h), load, sport records (every 6 h or on a new exercise), `getActivityDetail` (at most 2 per run, only for COROS activities of the last 7 days that have no cached detail), fitness (daily), then resting HR, average HR, stress and HRV (each at most every 3 h). Replies are cached in Room (`vitals`, `hrv`, `activity_detail`).
