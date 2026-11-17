package app.cairn.demo

import app.cairn.core.model.Activity
import app.cairn.core.model.ActivityDetail
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DataSource
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.HrvDay
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import app.cairn.core.model.VitalsDay
import app.cairn.core.model.WeeklyMileage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A coherent, entirely invented dataset (a recreational runner) for screenshots. Nothing here comes from a real person. */
data class DemoDataset(
    val daily: List<DailySummary>,
    val sleep: List<SleepNight>,
    val recovery: Recovery,
    val load: List<TrainingLoadDay>,
    val fitness: FitnessSnapshot,
    val activities: List<Activity>,
    val vitals: List<VitalsDay>,
    val hrv: List<HrvDay>,
    val details: List<ActivityDetail>,
) {
    companion object {
        const val ID_PREFIX = "demo-"
        val PLACES = listOf("Riverside Loop", "Harbour Path", "Hill Park", "Canal Towpath", "Meadow Trail")
        const val WEEK_LOAD = 186
        private val RESTING = listOf(55, 56, 55, 54, 56, 55) // the six days before today; with today's 54 the 7-day mean is 55
        /** Daily average heart rate and the day's lowest, today first. */
        private val AVG_HR = listOf(68, 70, 66, 72, 69, 67, 71)
        private val MIN_HR = listOf(48, 47, 49, 46, 48, 47, 50)
        /** Average stress per day, today first: bands Low, Low, Relaxed, Medium, Low, Relaxed, Low. */
        private val STRESS = listOf(28, 34, 22, 55, 31, 24, 41)
        /** Overnight HRV per night, today first, against the range 50-66: Normal, Elevated, Normal, Low, Normal, Normal, Normal. */
        private val HRV = listOf(61, 68, 55, 49, 59, 63, 57)
        const val HRV_LOW = 50
        const val HRV_HIGH = 66
        /** This week's three runs total 25.10 km; last week's two total 22.00 km, so the widget reads "+14% vs last week". */
        const val WEEK_KM = 25.10
        const val LAST_WEEK_KM = 22.0

        fun build(today: LocalDate, now: Instant, zone: ZoneId = ZoneId.systemDefault()): DemoDataset {
            val stamp = Stamp(DataSource.COROS_MCP, now)
            fun <T> sv(v: T) = SourcedValue(v, DataSource.COROS_MCP, now)

            val daily = (0..6).map { i ->
                val d = today.minusDays(i.toLong())
                val rest = if (i == 0) 54 else RESTING[i - 1]
                DailySummary(
                    d, sv(if (i == 0) 9412 else 7800 + (i * 613) % 3400), sv(if (i == 0) 640 else 520 + (i * 47) % 260),
                    sv(if (i == 0) 48 else 22 + (i * 11) % 40), null, sv(STRESS[i]), sv(rest), sv(68), sv(if (i == 0) 7420.0 else 5600.0 + (i * 391) % 3000),
                )
            }
            val vitals = (0..6).map { i ->
                VitalsDay(today.minusDays(i.toLong()), stamp, avgHr = AVG_HR[i], minHr = MIN_HR[i], maxHr = if (i == 0) 142 else 130 + i, restingHr = if (i == 0) 54 else RESTING[i - 1], avgStress = STRESS[i])
            }
            val sleep = (0..6).map { i ->
                val d = today.minusDays(i.toLong())
                val score = if (i == 0) 82 else 70 + (i * 5) % 14
                val asleep = if (i == 0) 432 else 405 + (i * 9) % 50
                val deep = if (i == 0) 78 else 70 + i; val rem = if (i == 0) 116 else 100 + i
                val awake = if (i == 0) 30 else 24
                val start = d.minusDays(1).atTime(22, 48).atZone(zone).toInstant()
                SleepNight(
                    d, stamp, score, asleep, asleep + awake, deep, asleep - deep - rem, rem, awake, avgHr = 52, minHr = 46, maxHr = 63,
                    start = start, end = start.plusSeconds(((asleep + awake) * 60).toLong()),
                )
            }
            val hrv = (0..6).map { i ->
                HrvDay(
                    today.minusDays(i.toLong()), stamp, avgMs = HRV[i], assessment = if (HRV[i] > HRV_HIGH) "Elevated" else if (HRV[i] < HRV_LOW) "Low" else "Normal",
                    normalLowMs = HRV_LOW, normalHighMs = HRV_HIGH, baselineMs = 58,
                )
            }
            val recovery = Recovery(stamp, percent = 76, level = "Good", hoursToFullRecovery = 5)
            // Training Status: Load Impact = short-term (31), Base Fitness = long-term (48), Intensity Trend = ratio (64 %).
            val shortSeries = listOf(24, 27, 22, 29, 33, 28, 31)
            val load = (0..6).map { i ->
                val last = i == 6
                TrainingLoadDay(
                    today.minusDays((6 - i).toLong()), stamp, shortTermLoad = shortSeries[i], longTermLoad = 44 + i * 2 / 3,
                    loadRatio = if (last) 0.64 else 0.55 + i * 0.015, comment = if (last) "Productive" else "Maintaining",
                )
            }.let { l -> l.dropLast(1) + l.last().copy(longTermLoad = 48, shortTermLoad = 31) }
            val fitness = FitnessSnapshot(
                stamp, vo2max = 50, runningLevel = 68.0, thresholdPaceSecPerKm = 320,
                prediction5kSec = 1480, prediction10kSec = 3090, predictionHalfSec = 6780, predictionMarathonSec = 15700, // 4:21:40
            )

            // Three runs this week (Monday to today, so always three on any date): 66 + 58 + 62 = 186 load, 25.10 km.
            val monday = WeeklyMileage.mondayOf(today)
            val recent = listOf(0 to 66, 2 to 58, 4 to 62).mapIndexed { n, (off, ld) ->
                val date = maxOf(monday, today.minusDays(off.toLong()))
                Triple(run("this-$n", stamp, date, 6, 30 + n * 10, PLACES[n % PLACES.size], dist = if (n == 0) 8.1 else 7.0 + n, paceSec = 300 + n * 8, zone), ld, n)
            }
            // Two runs on last week's Monday (22.00 km), which is always more than 6 days before today, so outside the 7-day load window.
            val lastMonday = monday.minusDays(7)
            val lastWeek = listOf(10.5, 11.5).mapIndexed { n, km -> run("week-$n", stamp, lastMonday, 6 + n * 12, 25 + n * 5, PLACES[(n + 2) % PLACES.size], dist = km, paceSec = 310 + n * 6, zone) }
            // Twelve runs last month on days 1 to 14: more than 6 days before today (outside the 7-day load window) and before last week even when it starts in that month.
            val prev = today.minusMonths(1).withDayOfMonth(1)
            val last = (0 until 12).map { n ->
                val date = prev.plusDays((n * 13 / 11).toLong())
                run("last-$n", stamp, date, 6, 20 + (n % 4) * 5, PLACES[(n + 1) % PLACES.size], dist = 5.0 + (n % 5) * 1.5, paceSec = 305 + (n % 4) * 9, zone)
            }
            // One strength workout (no distance) two days before last week, so lists and widgets show the no-distance layout.
            val strength = Activity(
                id = "${ID_PREFIX}strength-${lastMonday.minusDays(2)}", stamp = stamp, date = lastMonday.minusDays(2), sport = "Strength",
                start = lastMonday.minusDays(2).atTime(18, 10).atZone(zone).toInstant(), durationSec = 53 * 60 + 25,
                avgHr = 121, calories = 312, location = PLACES[2], sportType = 402,
            )
            val activities = recent.map { it.first } + lastWeek + strength + last
            val details = recent.map { (a, ld, _) -> ActivityDetail(a.id, stamp, trainingLoad = ld, aerobicTe = 3.2, anaerobicTe = 0.8, trainingFocus = "Aerobic base") } +
                (lastWeek + strength + last).map { a -> ActivityDetail(a.id, stamp, trainingLoad = 55, aerobicTe = 3.0, anaerobicTe = 0.5, trainingFocus = "Aerobic base") }
            return DemoDataset(daily, sleep, recovery, load, fitness, activities, vitals, hrv, details)
        }

        private fun run(key: String, stamp: Stamp, date: LocalDate, hour: Int, minutes: Int, place: String, dist: Double, paceSec: Int, zone: ZoneId): Activity {
            val start = date.atTime(hour, 40).atZone(zone).toInstant()
            return Activity(
                id = "$ID_PREFIX$key-${date}", stamp = stamp, date = date, sport = "Run", start = start, durationSec = (dist * paceSec).toInt(),
                distanceMeters = dist * 1000, avgPaceSecPerKm = paceSec, avgHr = 148, calories = (dist * 62).toInt(), location = place, sportType = 100,
            )
        }
    }
}
