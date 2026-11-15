package app.cairn.core.model

import java.time.Instant
import java.time.LocalDate

/** Per-day totals. Each field carries its own provenance so sources can be merged field by field. */
data class DailySummary(
    val date: LocalDate,
    val steps: SourcedValue<Int>? = null,
    val calories: SourcedValue<Int>? = null,
    val exerciseMinutes: SourcedValue<Int>? = null,
    val floors: SourcedValue<Int>? = null,
    val avgStress: SourcedValue<Int>? = null,
    val restingHr: SourcedValue<Int>? = null,
    val avgHr: SourcedValue<Int>? = null,
    val distanceMeters: SourcedValue<Double>? = null,
)

/** One night, dated by wake-up day. Minutes are whole minutes. */
data class SleepNight(
    val date: LocalDate,
    val stamp: Stamp,
    val score: Int? = null,
    val asleepMinutes: Int? = null,
    val inBedMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val avgHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    val start: Instant? = null,
    val end: Instant? = null,
)

data class Recovery(
    val stamp: Stamp,
    val percent: Int? = null,
    val level: String? = null,
    val hoursToFullRecovery: Int? = null,
)

data class TrainingLoadDay(
    val date: LocalDate,
    val stamp: Stamp,
    val shortTermLoad: Int? = null,
    val longTermLoad: Int? = null,
    val loadRatio: Double? = null,
    val comment: String? = null,
)

data class FitnessSnapshot(
    val stamp: Stamp,
    val vo2max: Int? = null,
    /** Running level exactly as the server gives it (a decimal like 73.3 when it has one). */
    val runningLevel: Double? = null,
    val thresholdPaceSecPerKm: Int? = null,
    val prediction5kSec: Int? = null,
    val prediction10kSec: Int? = null,
    val predictionHalfSec: Int? = null,
    val predictionMarathonSec: Int? = null,
)

data class Activity(
    val id: String,
    val stamp: Stamp,
    val date: LocalDate,
    val sport: String,
    val start: Instant? = null,
    val durationSec: Int? = null,
    val distanceMeters: Double? = null,
    val avgPaceSecPerKm: Int? = null,
    val avgHr: Int? = null,
    val calories: Int? = null,
    val location: String? = null,
    val sportType: Int? = null,
)

/** One day of heart rate and stress figures from the dedicated COROS tools (not the daily summary). */
data class VitalsDay(
    val date: LocalDate,
    val stamp: Stamp,
    val avgHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    /** From queryRestingHeartRate: this is the figure COROS shows, which can differ by 1 bpm from the daily summary. */
    val restingHr: Int? = null,
    /** From queryStressLevel. */
    val avgStress: Int? = null,
)

/** Overnight HRV for the night that ended on [date] (wake-up day). */
data class HrvDay(
    val date: LocalDate,
    val stamp: Stamp,
    val avgMs: Int? = null,
    /** The server's own assessment text, unchanged ("Above normal", "Normal", ...). */
    val assessment: String? = null,
    val normalLowMs: Int? = null,
    val normalHighMs: Int? = null,
    val baselineMs: Int? = null,
)

/** Per-activity figures from getActivityDetail, cached by [id] (the labelId). A row with a null load means "asked, none given". */
data class ActivityDetail(
    val id: String,
    val stamp: Stamp,
    val trainingLoad: Int? = null,
    val aerobicTe: Double? = null,
    val anaerobicTe: Double? = null,
    val trainingFocus: String? = null,
)
