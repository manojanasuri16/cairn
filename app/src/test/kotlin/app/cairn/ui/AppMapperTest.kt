package app.cairn.ui

import app.cairn.core.model.Activity
import app.cairn.core.model.ActivityDetail
import app.cairn.core.model.HrvDay
import app.cairn.core.model.VitalsDay
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DataSource
import app.cairn.core.model.DisplayPrefs
import app.cairn.core.model.DistanceUnit
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Freshness
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import app.cairn.source.healthconnect.HcAvailability
import app.cairn.sync.RunRecord
import app.cairn.sync.SyncOutcome
import app.cairn.widget.AuthState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMapperTest {
    private val now = Instant.parse("2030-03-15T12:00:00Z")
    private val today = LocalDate.parse("2030-03-15")
    private val us = Locale.US
    private fun ago(h: Long) = now.minus(Duration.ofHours(h))
    private fun <T> sv(v: T, at: Instant) = SourcedValue(v, DataSource.COROS_MCP, at)
    private fun stamp(at: Instant) = Stamp(DataSource.COROS_MCP, at)

    private fun today(
        daily: List<DailySummary> = emptyList(), sleeps: List<SleepNight> = emptyList(), recovery: Recovery? = null,
        load: List<TrainingLoadDay> = emptyList(), auth: AuthState = AuthState.Ok, prefs: DisplayPrefs = DisplayPrefs(),
    ) = AppMapper.today(daily, sleeps, recovery, load, auth, now, today, prefs, us, ZoneOffset.UTC)

    @Test fun todayFull() {
        val d = DailySummary(today, steps = sv(6204, ago(1)), calories = sv(1820, ago(1)), avgStress = sv(31, ago(1)), restingHr = sv(52, ago(2)))
        val night = SleepNight(today, stamp(ago(5)), score = 84, asleepMinutes = 461, deepMinutes = 90, lightMinutes = 250, remMinutes = 100, awakeMinutes = 0,
            start = Instant.parse("2030-03-14T23:10:00Z"), end = Instant.parse("2030-03-15T07:02:00Z"))
        val s = today(listOf(d), listOf(night), Recovery(stamp(ago(1)), percent = 82, hoursToFullRecovery = 4))
        assertEquals("82 %", s.recovery!!.valueText)
        assertEquals(0.82f, s.recovery!!.fraction, 0.001f)
        assertEquals("Well recovered", s.recoveryLevel)
        assertEquals("4 hrs till full recovery", s.timeToFull)
        assertEquals(listOf("6,204" to "steps", "1,820" to "kcal"), s.header.map { it.value to it.label })
        assertEquals("7 h 41 min", s.sleep!!.durationText)
        assertEquals("84", s.sleep!!.scoreText)
        assertEquals("Optimal", s.sleep!!.rating)
        assertEquals("23:10–07:02", s.sleep!!.window)
        assertEquals(listOf("Deep", "Light", "REM"), s.sleep!!.stages.map { it.label }) // zero-length awake dropped
        assertNull(s.resting) // the daily summary figure is never shown without queryRestingHeartRate data
        assertEquals("31", s.stress!!.value)
        assertEquals(Freshness.Fresh, s.footer!!.tier)
        assertEquals("Updated 1h ago", s.footer!!.text) // newest stamp wins
        assertNull(s.empty)
    }

    @Test fun staleFooterUsesExplicitWordingAndThresholds() {
        val d = DailySummary(today, steps = sv(100, ago(14)))
        val stale = today(listOf(d)).footer!!
        assertEquals(Freshness.Stale, stale.tier)
        assertTrue(stale.text.startsWith("Stale, last synced 14h ago"))
        val relaxed = today(listOf(d), prefs = DisplayPrefs(freshHours = 6, agingHours = 24)).footer!!
        assertEquals(Freshness.Aging, relaxed.tier)
    }

    @Test fun missingFieldsAreOmitted() {
        val s = today(listOf(DailySummary(today, steps = sv(500, ago(1)))))
        assertNull(s.recovery); assertNull(s.sleep)
        assertNull(s.resting); assertNull(s.heart); assertNull(s.hrv); assertNull(s.weekly); assertNull(s.status); assertNull(s.running)
        assertEquals("500", s.header.single().value)
    }

    @Test fun previousDaySummaryIsNotToday() {
        val s = today(listOf(DailySummary(today.minusDays(1), steps = sv(9000, ago(20)))), auth = AuthState.Ok)
        assertTrue(s.header.isEmpty())
        assertEquals("No data yet today", s.empty!!.title)
    }

    @Test fun emptyStatesFollowAuth() {
        assertEquals(EmptyAction.OpenAccount, today(auth = AuthState.SignedOut).empty!!.action)
        assertEquals("Sign in to COROS again", today(auth = AuthState.NeedsSignIn).empty!!.title)
        assertEquals(EmptyAction.None, today(auth = AuthState.Ok).empty!!.action)
        assertEquals(3, today().sparks.size) // sparklines still exist, saying "no data"
        assertTrue(today().sparks.all { it.latest == null && it.description.endsWith("no data") })
    }

    @Test fun sparklinesCoverSevenDaysWithGaps() {
        val daily = listOf(0L, 1L, 3L, 6L).map { DailySummary(today.minusDays(it), steps = sv(1000 * (7 - it.toInt()), ago(1))) }
        val s = today(daily).sparks.first { it.title == "Steps" }
        assertEquals(7, s.values.size)
        assertEquals(listOf(1000f, null, null, 4000f, null, 6000f, 7000f), s.values) // oldest first, today last
        assertEquals("7,000", s.latest)
        assertTrue(s.description.contains("4 of 7 days"))
    }

    @Test fun sleepSparkAndLoadSpark() {
        val sl = SleepNight(today, stamp(ago(5)), asleepMinutes = 420)
        val load = TrainingLoadDay(today, stamp(ago(2)), shortTermLoad = 310)
        val s = today(sleeps = listOf(sl), load = listOf(load))
        assertEquals("7 h", s.sparks.first { it.title == "Sleep" }.latest)
        assertEquals("310", s.sparks.first { it.title == "Training load" }.latest)
    }

    @Test fun activitiesUnitsAndMissingFields() {
        val a = Activity("a1", stamp(ago(2)), today, "trail_run", start = Instant.parse("2030-03-15T07:30:00Z"), durationSec = 2760, distanceMeters = 8046.72, avgPaceSecPerKm = 313, avgHr = 151, calories = 540)
        val bare = Activity("a2", stamp(ago(30)), today.minusDays(2), "gym")
        val km = AppMapper.activities(listOf(a, bare), AuthState.Ok, now, today, DisplayPrefs(), us, ZoneOffset.UTC)
        assertEquals(listOf("8.05 km", "46:00", "5'13\" /km", "151 bpm", "540 kcal"), km.rows[0].stats)
        assertEquals("Trail run", km.rows[0].sport)
        assertEquals("\uD83C\uDFC3", km.rows[0].glyph)
        assertTrue(km.rows[0].details.any { it.label == "Date" && it.value == "Friday, 15 Mar, 07:30" })
        assertTrue(km.rows[1].stats.isEmpty())
        assertEquals(Freshness.Fresh, km.footer!!.tier)
        val mi = AppMapper.activities(listOf(a), AuthState.Ok, now, today, DisplayPrefs(DistanceUnit.Mi), us, ZoneOffset.UTC)
        assertEquals(listOf("5.00 mi", "46:00", "8'24\" /mi", "151 bpm", "540 kcal"), mi.rows[0].stats)
    }

    @Test fun activitiesCapsAtOneHundredAndEmpty() {
        val many = (1..105).map { Activity("id$it", stamp(ago(1)), today, "run") }
        assertEquals(100, AppMapper.activities(many, AuthState.Ok, now, today).rows.size)
        val e = AppMapper.activities(emptyList(), AuthState.Ok, now, today)
        assertEquals("No activities yet", e.empty!!.title)
        assertEquals(EmptyAction.OpenAccount, AppMapper.activities(emptyList(), AuthState.SignedOut, now, today).empty!!.action)
    }

    @Test fun fitness() {
        val days = (0L..6L).map { TrainingLoadDay(today.minusDays(it), stamp(ago(2)), shortTermLoad = 100 + it.toInt(), longTermLoad = 90, loadRatio = 1.2, comment = "optimal") }
        val snap = FitnessSnapshot(stamp(ago(20)), vo2max = 52, runningLevel = 73.3, thresholdPaceSecPerKm = 261, prediction5kSec = 1300, predictionHalfSec = 6723)
        val f = AppMapper.fitness(days, snap, AuthState.Ok, now, today, DisplayPrefs(), us)
        assertEquals("120%", f.ratio)
        assertEquals("Optimal", f.comment)
        assertEquals("52", f.vo2max)
        assertEquals("4'21\" /km", f.thresholdPace)
        assertEquals(listOf("5K" to "21:40", "Half marathon" to "1:52:03"), f.predictions.map { it.label to it.value })
        assertEquals("73.3", f.running!!.value); assertEquals(0.733f, f.running!!.fraction, 0.0001f)
        assertEquals("Intensity Trend", "Intensity Trend") // label lives in the screen
        assertEquals(7, f.chart!!.short.size)
        assertEquals(106f, f.chart!!.short.first())
        assertEquals(Freshness.Fresh, f.footer!!.tier) // newest of load (2h) and snapshot (20h)
        assertEquals("7'00\" /mi", AppMapper.fitness(days, snap, AuthState.Ok, now, today, DisplayPrefs(DistanceUnit.Mi), us).thresholdPace)
    }

    @Test fun fitnessEmptyAndPartial() {
        assertNotNull(AppMapper.fitness(emptyList(), null, AuthState.Ok, now, today).empty)
        val onlyVo2 = AppMapper.fitness(emptyList(), FitnessSnapshot(stamp(ago(1)), vo2max = 50), AuthState.Ok, now, today)
        assertNull(onlyVo2.empty); assertNull(onlyVo2.chart); assertNull(onlyVo2.ratio)
        assertEquals("50", onlyVo2.vo2max)
    }

    // ---- COROS-style Today ----
    private val sat = Instant.parse("2030-03-15T10:00:00Z") // 2030-03-15 is a Friday
    private fun corosToday(): TodayState {
        val d = DailySummary(today, steps = sv(9443, ago(1)), calories = sv(796, ago(1)), exerciseMinutes = sv(55, ago(1)), restingHr = sv(52, ago(1)))
        val night = SleepNight(today, stamp(ago(5)), score = 87, asleepMinutes = 407, deepMinutes = 60, lightMinutes = 250, remMinutes = 97,
            start = Instant.parse("2030-03-14T23:10:00Z"), end = Instant.parse("2030-03-15T07:02:00Z"))
        val load = listOf(TrainingLoadDay(today, stamp(ago(1)), 20, 42, 0.47, "Decreasing"))
        val vitals = (0L..6L).map { VitalsDay(today.minusDays(it), stamp(ago(1)), restingHr = 50 + it.toInt()) }.map {
            if (it.date == today) it.copy(avgHr = 70, minHr = 44, maxHr = 154, avgStress = 30) else it
        }
        val hrv = listOf(HrvDay(today, stamp(ago(1)), 65, "Above normal", 46, 58, 52))
        val run = Activity("L1", stamp(ago(1)), today, "Outdoor Run", start = Instant.parse("2030-03-15T08:00:00Z"), durationSec = 3277, distanceMeters = 5550.0, avgPaceSecPerKm = 590, location = "Riverside", sportType = 100)
        val earlier = Activity("L0", stamp(ago(1)), today.minusDays(3), "Outdoor Run", durationSec = 1800, distanceMeters = 4000.0, sportType = 100)
        val details = mapOf("L1" to ActivityDetail("L1", stamp(ago(1)), 112), "L0" to ActivityDetail("L0", stamp(ago(1)), 40))
        val fit = FitnessSnapshot(stamp(ago(3)), runningLevel = 73.3, predictionMarathonSec = 4 * 3600 + 9 * 60 + 57)
        return AppMapper.today(listOf(d), listOf(night), Recovery(stamp(ago(1)), percent = 99, level = "Fresh", hoursToFullRecovery = 3), load, AuthState.Ok, now, today, DisplayPrefs(), us, ZoneOffset.UTC,
            TodayExtras(vitals, hrv, listOf(run, earlier), details, fit))
    }

    @Test fun corosCardsShowCorosValuesAndWording() {
        val s = corosToday()
        assertEquals(listOf("9,443" to "steps", "796" to "kcal", "55" to "min"), s.header.map { it.value to it.label })
        val a = s.todayActivity!!
        assertEquals("5.55 km", a.distance); assertEquals("54:37", a.duration); assertEquals("9'50\" /km", a.pace)
        assertEquals("Friday, 15 Mar", a.date); assertEquals("Friday  Riverside Run", a.title)
        assertEquals("70", s.heart!!.value); assertEquals("44-154", s.heart!!.range); assertEquals("Daily average", s.heart!!.caption)
        assertEquals("50", s.resting!!.value); assertEquals("Today", s.resting!!.time) // queryRestingHeartRate, not the daily 52
        assertEquals("7-day avg 53 bpm", s.resting!!.average) // mean of 50..56
        assertEquals("30", s.stress!!.value); assertEquals("Average Stress", s.stress!!.caption)
        assertEquals("Optimal", s.sleep!!.rating); assertEquals("6 h 47 min", s.sleep!!.durationText)
        assertEquals("Above normal", s.hrv!!.assessment); assertEquals("Average 65 ms", s.hrv!!.average)
        assertEquals("Normal Range 46-58 ms", s.hrv!!.range); assertEquals("Baseline 52 ms", s.hrv!!.baseline)
        assertEquals("152", s.weekly!!.total) // 112 today + 40 three days ago
        assertNull(s.weekly!!.suggested)
        assertEquals("MTWTFSS", s.weekly!!.bars.joinToString("") { it.letter })
        val st = s.status!!
        assertEquals("Decreasing", st.status); assertEquals("152", st.sevenDayLoad); assertEquals("20", st.loadImpact); assertEquals("42", st.baseFitness); assertEquals("47%", st.intensityTrend)
        assertEquals("99 %", s.recovery!!.valueText); assertEquals("Fresh", s.recoveryLevel); assertEquals("3 hrs till full recovery", s.timeToFull)
        assertEquals("73.3", s.running!!.value); assertEquals("Marathon 4:09:57", s.running!!.marathon); assertEquals(0.733f, s.running!!.fraction, 0.0001f)
        assertTrue(s.status!!.description.contains("Intensity Trend 47 percent"))
    }

    @Test fun todayCardsFollowTheCorosOrder() {
        assertEquals(
            listOf("Header", "Activity", "HeartRate", "RestingHr", "Stress", "Sleep", "Hrv", "WeeklyLoad", "TrainingStatus", "Recovery", "RunningFitness"),
            TodayOrder.present(corosToday()).map { it.name },
        )
        // Cards without data drop out but the rest keep their order.
        val partial = corosToday().copy(heart = null, hrv = null, running = null)
        assertEquals(listOf("Header", "Activity", "RestingHr", "Stress", "Sleep", "WeeklyLoad", "TrainingStatus", "Recovery"), TodayOrder.present(partial).map { it.name })
    }

    @Test fun weeklyLoadAppearsOnlyWithKnownLoads() {
        val run = Activity("L1", stamp(ago(1)), today, "Run", sportType = 100)
        val s = AppMapper.today(listOf(DailySummary(today, steps = sv(1, ago(1)))), emptyList(), null, emptyList(), AuthState.Ok, now, today, extras = TodayExtras(activities = listOf(run)))
        assertNull(s.weekly) // the load of that run is not cached yet
        val s2 = AppMapper.today(listOf(DailySummary(today, steps = sv(1, ago(1)))), emptyList(), null, emptyList(), AuthState.Ok, now, today,
            extras = TodayExtras(activities = listOf(run), details = mapOf("L1" to ActivityDetail("L1", stamp(ago(1)), 20))))
        assertEquals("20", s2.weekly!!.total)
    }

    @Test fun monthGroupingTotalsAndOrder() {
        val acts = listOf(
            Activity("a", stamp(ago(1)), LocalDate.parse("2030-03-14"), "Outdoor Run", durationSec = 3600, distanceMeters = 5550.0),
            Activity("b", stamp(ago(1)), LocalDate.parse("2030-03-02"), "Outdoor Run", durationSec = 1800, distanceMeters = 4030.0),
            Activity("c", stamp(ago(1)), LocalDate.parse("2030-02-20"), "Outdoor Run", durationSec = 5400, distanceMeters = 10000.0),
        )
        val m = AppMapper.activities(acts, AuthState.Ok, now, today, DisplayPrefs(), us, ZoneOffset.UTC).months
        assertEquals(listOf("Mar", "Feb"), m.map { it.title })
        assertEquals("9.58 km", m[0].distance); assertEquals("2 activities", m[0].count); assertEquals("1.5 hours", m[0].hours)
        assertEquals("1 activity", m[1].count)
        assertEquals(listOf("a", "b"), m[0].rows.map { it.id })
    }

    @Test fun activityTitleUsesLocationWhenPresent() {
        val withPlace = Activity("a", stamp(ago(1)), LocalDate.parse("2026-11-15"), "Outdoor Run", location = "Riverside")
        assertEquals("Sunday  Riverside Run", ActivityText.title(withPlace, us))
        assertEquals("Sunday  Run", ActivityText.title(withPlace.copy(location = null), us))
        // The Location is already the title: the sport is not appended again.
        assertEquals("Sunday  Riverside Run", ActivityText.title(withPlace.copy(location = "Riverside Run"), us))
        assertEquals("Sunday  Riverside Run", ActivityText.title(withPlace.copy(location = "riverside run"), us).replace("riverside run", "Riverside Run"))
        assertEquals("Sunday  Trail run", ActivityText.title(withPlace.copy(location = " ", sport = "TRAIL_RUN"), us))
    }

    @Test fun healthConnectStatus() {
        val need = setOf("a", "b")
        assertEquals(HcStatus.Unknown, SourceStatus.healthConnect(null, null, need))
        assertEquals(HcStatus.Unknown, SourceStatus.healthConnect(HcAvailability.Available, null, need))
        assertEquals(HcStatus.Unavailable, SourceStatus.healthConnect(HcAvailability.Unavailable, emptySet(), need))
        assertEquals(HcStatus.UpdateRequired, SourceStatus.healthConnect(HcAvailability.UpdateRequired, emptySet(), need))
        assertEquals(HcStatus.NotGranted, SourceStatus.healthConnect(HcAvailability.Available, emptySet(), need))
        assertEquals(HcStatus.PartlyGranted, SourceStatus.healthConnect(HcAvailability.Available, setOf("a"), need))
        assertEquals(HcStatus.Granted, SourceStatus.healthConnect(HcAvailability.Available, setOf("a", "b", "c"), need))
        assertTrue(SourceStatus.canGrant(HcStatus.PartlyGranted)); assertFalse(SourceStatus.canGrant(HcStatus.Granted))
    }

    @Test fun refreshFeedback() {
        fun run(at: Long, o: SyncOutcome) = RunRecord(at, "MANUAL", o.name)
        assertTrue(RefreshFeedback.positive(run(1000, SyncOutcome.SUCCESS), 900))
        assertFalse(RefreshFeedback.positive(run(1000, SyncOutcome.PARTIAL), 900))
        assertFalse(RefreshFeedback.positive(run(800, SyncOutcome.SUCCESS), 900)) // an older run is not ours
        assertFalse(RefreshFeedback.positive(null, 0))
    }

    @Test fun routesAndOnboarding() {
        assertEquals(Routes.ACTIVITIES, Routes.forScreen("Activities"))
        assertEquals(Routes.ACCOUNT, Routes.forScreen("Account"))
        assertEquals(Routes.FRESHNESS, Routes.forScreen("Freshness"))
        assertEquals(Routes.TODAY, Routes.forScreen(null))
        assertEquals(Routes.TODAY, Routes.forScreen("nonsense"))
        assertTrue(OnboardingChoice.Both.useCoros && OnboardingChoice.Both.useHealthConnect)
        assertFalse(OnboardingChoice.HealthConnect.useCoros)
        assertFalse(OnboardingChoice.Coros.useHealthConnect)
    }
}
