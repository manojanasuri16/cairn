package app.cairn.source.corosmcp.parse

import app.cairn.core.model.Activity
import app.cairn.core.model.ActivityDetail
import app.cairn.core.model.HrvDay
import app.cairn.core.model.VitalsDay
import app.cairn.core.model.DailySummary
import app.cairn.core.model.DataSource
import app.cairn.core.model.FitnessSnapshot
import app.cairn.core.model.Recovery
import app.cairn.core.model.SleepNight
import app.cairn.core.model.SourcedValue
import app.cairn.core.model.Stamp
import app.cairn.core.model.TrainingLoadDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Defensive, label-driven parsers for the plain-text MCP tool replies. They look up "Label: value" pairs,
 * ignore unknown lines, and report problems as warnings. Every value is stamped with [fetchedAt] as
 * `sourceUpdatedAt` (the server gives no other timestamp).
 */
private fun stamp(fetchedAt: Instant) = Stamp(DataSource.COROS_MCP, fetchedAt)

private fun <T> guard(reply: ToolReply, empty: T, parse: (String) -> ParseResult<T>): ParseResult<T> =
    if (reply.isError) ParseResult(empty, listOf("tool returned an error: ${reply.text.lineSequence().firstOrNull().orEmpty().take(120)}"))
    else parse(reply.text)

private fun emptyWarnings(text: String): List<String>? = when {
    text.isBlank() -> listOf("empty reply")
    text.trimStart().startsWith("No data", ignoreCase = true) -> listOf("no data: ${text.lineSequence().first().take(120)}")
    else -> null
}

/** Splits [text] into blocks that start at lines matched by [header]; returns (header match, following lines). */
private fun blocks(text: String, header: Regex): List<Pair<MatchResult, List<String>>> {
    val out = ArrayList<Pair<MatchResult, MutableList<String>>>()
    for (line in text.lines()) {
        val m = header.matchEntire(line.trim())
        if (m != null) out += m to mutableListOf() else out.lastOrNull()?.second?.add(line)
    }
    return out
}

data class DailyHealth(val days: List<DailySummary>, val sleep: List<SleepNight>, val hrvBaselineMs: Int?)

/** `queryDailyHealthData`: header line, then `--- YYYYMMDD ---` blocks. */
object DailyHealthParser {
    private val DAY = Regex("""-{3}\s*(\d{8})\s*-{3}""")
    private val FMT = DateTimeFormatter.BASIC_ISO_DATE
    private val SLEEP_HR = Regex("""Sleep HR:(.*)""", RegexOption.IGNORE_CASE)

    fun parse(reply: ToolReply, fetchedAt: Instant) = guard(reply, DailyHealth(emptyList(), emptyList(), null)) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<DailyHealth> {
        val warnings = mutableListOf<String>()
        emptyWarnings(text)?.let { return ParseResult(DailyHealth(emptyList(), emptyList(), null), it) }
        val st = stamp(fetchedAt)
        fun <T> sv(v: T?) = v?.let { SourcedValue(it, st.source, st.sourceUpdatedAt) }
        val lines = text.lines()
        val headerEnd = lines.indexOfFirst { DAY.matches(it.trim()) }.let { if (it < 0) lines.size else it }
        val headerLabels = Txt.labels(lines.take(headerEnd))
        val resting = Txt.int(headerLabels["resting hr"])
        val hrv = Txt.int(headerLabels["hrv baseline"])
        lines.filter { it.trim().startsWith("---") && !DAY.matches(it.trim()) }.forEach { warnings += "unrecognized day header: ${it.trim().take(40)}" }

        val days = mutableListOf<DailySummary>()
        val sleep = mutableListOf<SleepNight>()
        for ((m, body) in blocks(text, DAY)) {
            val date = try { LocalDate.parse(m.groupValues[1], FMT) } catch (e: DateTimeParseException) {
                warnings += "bad day header date ${m.groupValues[1]}"; continue
            }
            // Stop at the next "---" line (an unrecognized header) so its lines don't leak into this day.
            val blockLines = body.takeWhile { !it.trim().startsWith("---") }
            val l = Txt.labels(blockLines)
            fun intOf(key: String): Int? = l[key]?.let { v -> Txt.int(v).also { if (it == null) warnings += "$date: cannot read '$key' from '${v.take(30)}'" } }
            days += DailySummary(
                date = date,
                steps = sv(intOf("steps")),
                calories = sv(intOf("calories")),
                exerciseMinutes = sv(Txt.minutes(l["exercise"]) ?: intOf("exercise")),
                floors = sv(intOf("floors")),
                avgStress = sv(intOf("stress")),
            )
            val total = Txt.minutes(l["total"])
            val awake = Txt.minutes(l["awake"])
            val hr = blockLines.firstNotNullOfOrNull { SLEEP_HR.find(it)?.groupValues?.get(1) }
            fun hrPart(k: String) = hr?.let { Regex("""$k\s+(\d+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toInt() }
            if (total != null || awake != null || hr != null || l["deep"] != null) {
                sleep += SleepNight(
                    date = date, stamp = st,
                    // "Total" includes awake time in real replies (stages sum to it), so asleep = total - awake.
                    inBedMinutes = total,
                    asleepMinutes = if (total != null && awake != null) (total - awake).coerceAtLeast(0) else null,
                    deepMinutes = Txt.minutes(l["deep"]), lightMinutes = Txt.minutes(l["light"]), remMinutes = Txt.minutes(l["rem"]),
                    awakeMinutes = awake,
                    avgHr = hrPart("Avg"), minHr = hrPart("Min"), maxHr = hrPart("Max"),
                )
            }
        }
        if (days.isEmpty()) warnings += "no day blocks recognized"
        // The header's resting HR is a current baseline: attach it to the newest day only.
        val newest = days.maxByOrNull { it.date }
        val out = days.map { if (it === newest && resting != null) it.copy(restingHr = sv(resting)) else it }
        return ParseResult(DailyHealth(out, sleep, hrv), warnings)
    }
}

/** `querySleepOverview`: `YYYY-MM-DD` blocks of labelled lines. */
object SleepOverviewParser {
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val WINDOW = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})\s*[-–—]\s*(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})""")

    fun parse(reply: ToolReply, fetchedAt: Instant, zone: ZoneId = ZoneId.systemDefault()) =
        guard(reply, emptyList<SleepNight>()) { parse(it, fetchedAt, zone) }

    fun parse(text: String, fetchedAt: Instant, zone: ZoneId = ZoneId.systemDefault()): ParseResult<List<SleepNight>> {
        emptyWarnings(text)?.let { return ParseResult(emptyList(), it) }
        val warnings = mutableListOf<String>()
        val out = mutableListOf<SleepNight>()
        for ((m, body) in blocks(text, DATE)) {
            val date = try { LocalDate.parse(m.value) } catch (e: DateTimeParseException) { warnings += "bad date ${m.value}"; continue }
            val l = Txt.labels(body)
            if (l.isEmpty()) { warnings += "$date: no fields"; continue }
            val score = l["sleep score"]?.let { v -> Txt.int(v).also { if (it == null) warnings += "$date: cannot read sleep score" } }
            val asleep = Txt.minutes(l["main sleep (asleep)"]) ?: Txt.minutes(l["daily sleep"])
            val inBed = Txt.minutes(l["main sleep period (incl. awake)"])
            val awake = Txt.minutes(l["awake time"])
            // Stage minutes are derived from the ratios (percent of the period incl. awake): approximate.
            fun fromRatio(k: String): Int? = if (inBed == null) null else Txt.int(l[k])?.let { Math.round(it * inBed / 100.0).toInt() }
            val win = l["main sleep window"]?.let { WINDOW.find(it) }
            fun inst(s: String): Instant? = try { LocalDateTime.parse(s).atZone(zone).toInstant() } catch (e: DateTimeParseException) { null }
            out += SleepNight(
                date = date, stamp = stamp(fetchedAt),
                score = score, asleepMinutes = asleep, inBedMinutes = inBed,
                deepMinutes = fromRatio("deep sleep ratio"), lightMinutes = fromRatio("light sleep ratio"),
                remMinutes = fromRatio("rem ratio"), awakeMinutes = awake,
                start = win?.let { inst(it.groupValues[1]) }, end = win?.let { inst(it.groupValues[2]) },
            )
        }
        if (out.isEmpty()) warnings += "no sleep records recognized"
        return ParseResult(out, warnings)
    }
}

/** `queryRecoveryStatus`: a handful of labels. Value is null when none of them could be read. */
object RecoveryParser {
    fun parse(reply: ToolReply, fetchedAt: Instant) = guard<Recovery?>(reply, null) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<Recovery?> {
        emptyWarnings(text)?.let { return ParseResult(null, it) }
        val warnings = mutableListOf<String>()
        val l = Txt.labels(text.lines())
        val pct = l["recovery"]?.let { v -> Txt.int(v).also { if (it == null) warnings += "cannot read recovery percent" } }
        val level = l["level"]?.takeIf { it.isNotBlank() }
        val hours = l["estimated full recovery"]?.let { v ->
            Txt.minutes(v)?.let { (it + 30) / 60 }.also { if (it == null) warnings += "cannot read full-recovery time" }
        }
        if (pct == null && level == null && hours == null) return ParseResult(null, warnings + "no recovery fields recognized")
        if (pct == null) warnings += "recovery percent missing"
        return ParseResult(Recovery(stamp(fetchedAt), pct, level, hours), warnings)
    }
}

/** `queryTrainingLoadAssessment`: `YYYY-MM-DD` blocks. */
object TrainingLoadParser {
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    fun parse(reply: ToolReply, fetchedAt: Instant) = guard(reply, emptyList<TrainingLoadDay>()) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<List<TrainingLoadDay>> {
        emptyWarnings(text)?.let { return ParseResult(emptyList(), it) }
        val warnings = mutableListOf<String>()
        val out = mutableListOf<TrainingLoadDay>()
        for ((m, body) in blocks(text, DATE)) {
            val date = try { LocalDate.parse(m.value) } catch (e: DateTimeParseException) { warnings += "bad date ${m.value}"; continue }
            val l = Txt.labels(body)
            fun i(k: String) = l[k]?.let { v -> Txt.int(v).also { if (it == null) warnings += "$date: cannot read '$k'" } }
            val ratio = l["load ratio"]?.let { v -> Txt.double(v).also { if (it == null) warnings += "$date: cannot read 'load ratio'" } }
            val day = TrainingLoadDay(date, stamp(fetchedAt), i("short-term load"), i("long-term load"), ratio, l["comment"]?.takeIf { it.isNotBlank() })
            if (day.shortTermLoad == null && day.longTermLoad == null && day.loadRatio == null && day.comment == null) {
                warnings += "$date: no usable fields"
            } else out += day
        }
        if (out.isEmpty()) warnings += "no training load days recognized"
        return ParseResult(out, warnings)
    }
}

/** `queryFitnessAssessmentOverview`: labels. Paces and predictions become seconds. */
object FitnessParser {
    fun parse(reply: ToolReply, fetchedAt: Instant) = guard<FitnessSnapshot?>(reply, null) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<FitnessSnapshot?> {
        emptyWarnings(text)?.let { return ParseResult(null, it) }
        val warnings = mutableListOf<String>()
        val l = Txt.labels(text.lines())
        fun i(k: String) = l[k]?.let { v -> Txt.int(v).also { if (it == null) warnings += "cannot read '$k'" } }
        fun t(k: String) = l[k]?.let { v -> Txt.clockSeconds(v).also { if (it == null) warnings += "cannot read '$k'" } }
        val snap = FitnessSnapshot(
            stamp = stamp(fetchedAt),
            vo2max = i("vo2max"),
            runningLevel = l["running level"]?.let { v -> Txt.double(v).also { if (it == null) warnings += "cannot read 'running level'" } },
            thresholdPaceSecPerKm = l["threshold pace"]?.let { v -> Txt.paceSecPerKm(v).also { if (it == null) warnings += "cannot read 'threshold pace'" } },
            prediction5kSec = t("5 km prediction"), prediction10kSec = t("10 km prediction"),
            predictionHalfSec = t("half marathon prediction"), predictionMarathonSec = t("marathon prediction"),
        )
        val any = listOf(snap.vo2max, snap.runningLevel, snap.thresholdPaceSecPerKm, snap.prediction5kSec, snap.prediction10kSec,
            snap.predictionHalfSec, snap.predictionMarathonSec).any { it != null }
        return if (any) ParseResult(snap, warnings) else ParseResult(null, warnings + "no fitness fields recognized")
    }
}

/** `querySportRecords`: numbered entries "N. Sport — YYYY-MM-DD" followed by indented label lines. */
object SportRecordsParser {
    private val ENTRY = Regex("""\d+\.\s+(.+?)\s+[—–-]\s+(\d{4}-\d{2}-\d{2})""")
    private val COUNT = Regex("""\((\d+)\s+records?\)""", RegexOption.IGNORE_CASE)
    private val START_TS = Regex("""startTimestamp\s*=\s*(\d+)""")

    fun parse(reply: ToolReply, fetchedAt: Instant) = guard(reply, emptyList<Activity>()) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<List<Activity>> {
        emptyWarnings(text)?.let { return ParseResult(emptyList(), it) }
        val warnings = mutableListOf<String>()
        val out = mutableListOf<Activity>()
        for ((idx, pair) in blocks(text, ENTRY).withIndex()) {
            val (m, body) = pair
            val sport = m.groupValues[1]
            val date = try { LocalDate.parse(m.groupValues[2]) } catch (e: DateTimeParseException) { warnings += "entry ${idx + 1}: bad date"; continue }
            val l = Txt.labels(body)
            val startTs = START_TS.find(body.joinToString("\n"))?.groupValues?.get(1)
            // Unverified: assume epoch seconds (10 digits) or millis (13 digits); anything else is ignored.
            val start = when (startTs?.length) {
                10 -> Instant.ofEpochSecond(startTs.toLong())
                13 -> Instant.ofEpochMilli(startTs.toLong())
                else -> null
            }
            val dur = l["duration"]?.let { v -> (Txt.clockSeconds(v) ?: Txt.minutes(v)?.times(60)).also { if (it == null) warnings += "$sport $date: cannot read duration" } }
            val dist = l["distance"]?.let { v -> Txt.distanceMeters(v).also { if (it == null) warnings += "$sport $date: cannot read distance" } }
            val id = l["labelid"]?.takeIf { it.isNotBlank() } ?: "$date-$sport-${startTs ?: (idx + 1)}"
            out += Activity(
                id = id, stamp = stamp(fetchedAt), date = date, sport = sport, start = start,
                durationSec = dur, distanceMeters = dist,
                avgPaceSecPerKm = l["average pace"]?.let { Txt.paceSecPerKm(it) },
                avgHr = Txt.int(l["avg hr"]), calories = Txt.int(l["calories"]),
                location = l["location"]?.takeIf { it.isNotBlank() },
                sportType = Txt.int(l["sporttype"]),
            )
        }
        val declared = COUNT.find(text)?.groupValues?.get(1)?.toInt()
        if (declared != null && declared != out.size) warnings += "header says $declared records, parsed ${out.size}"
        if (out.isEmpty()) warnings += "no sport records recognized"
        return ParseResult(out, warnings)
    }
}

private val DAY_LINE = Regex("""(\d{4}-\d{2}-\d{2}):?""")

/** `queryRestingHeartRate`: lines `YYYY-MM-DD: 51 bpm`. This is the figure the COROS app shows. */
object RestingHrParser {
    private val ROW = Regex("""(\d{4}-\d{2}-\d{2})\s*:\s*(\d+)\s*bpm""", RegexOption.IGNORE_CASE)

    fun parse(reply: ToolReply, fetchedAt: Instant) = guard(reply, emptyList<VitalsDay>()) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<List<VitalsDay>> {
        emptyWarnings(text)?.let { return ParseResult(emptyList(), it) }
        val out = text.lines().mapNotNull { l ->
            ROW.find(l)?.let { m -> runCatching { VitalsDay(LocalDate.parse(m.groupValues[1]), stamp(fetchedAt), restingHr = m.groupValues[2].toInt()) }.getOrNull() }
        }
        return ParseResult(out, if (out.isEmpty()) listOf("no resting heart rate rows recognized") else emptyList())
    }
}

/** `queryAvgHeartRate`: lines `YYYY-MM-DD: 70 bpm (Min: 44, Max: 154)`. Daily average, not a latest reading. */
object AvgHeartRateParser {
    private val ROW = Regex("""(\d{4}-\d{2}-\d{2})\s*:\s*(\d+)\s*bpm(.*)""", RegexOption.IGNORE_CASE)
    private val MIN = Regex("""Min\w*\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val MAX = Regex("""Max\w*\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(reply: ToolReply, fetchedAt: Instant) = guard(reply, emptyList<VitalsDay>()) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<List<VitalsDay>> {
        emptyWarnings(text)?.let { return ParseResult(emptyList(), it) }
        val out = text.lines().mapNotNull { l ->
            val m = ROW.find(l) ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull() ?: return@mapNotNull null
            VitalsDay(
                date, stamp(fetchedAt), avgHr = m.groupValues[2].toInt(),
                minHr = MIN.find(m.groupValues[3])?.groupValues?.get(1)?.toInt(), maxHr = MAX.find(m.groupValues[3])?.groupValues?.get(1)?.toInt(),
            )
        }
        return ParseResult(out, if (out.isEmpty()) listOf("no average heart rate rows recognized") else emptyList())
    }
}

/** `queryStressLevel`: `YYYY-MM-DD:` blocks with `Average Stress: N`. The zone breakdown ("No data") is ignored. */
object StressParser {
    fun parse(reply: ToolReply, fetchedAt: Instant) = guard(reply, emptyList<VitalsDay>()) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<List<VitalsDay>> {
        emptyWarnings(text)?.let { return ParseResult(emptyList(), it) }
        val out = mutableListOf<VitalsDay>()
        for ((m, body) in blocks(text, DAY_LINE)) {
            val date = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull() ?: continue
            val v = Txt.int(Txt.labels(body)["average stress"]) ?: continue
            out += VitalsDay(date, stamp(fetchedAt), avgStress = v)
        }
        return ParseResult(out, if (out.isEmpty()) listOf("no stress rows recognized") else emptyList())
    }
}

/** `querySleepHrv`: only the "HRV Assessment" summary; the (huge) raw time series after it is never read. */
object SleepHrvParser {
    private val RANGE = Regex("""(\d+)\s*[-\u2013\u2014]\s*(\d+)""")
    private val DASH = Regex("""\s[\u2014\u2013-]\s""")

    fun parse(reply: ToolReply, fetchedAt: Instant) = guard(reply, emptyList<HrvDay>()) { parse(it, fetchedAt) }

    fun parse(text: String, fetchedAt: Instant): ParseResult<List<HrvDay>> {
        emptyWarnings(text)?.let { return ParseResult(emptyList(), it) }
        val lines = text.lines()
        val end = lines.indexOfFirst { it.trim().startsWith("Sleep HRV Time Series", ignoreCase = true) }.let { if (it < 0) lines.size else it }
        val summary = lines.take(end).joinToString("\n")
        val out = mutableListOf<HrvDay>()
        for ((m, body) in blocks(summary, DAY_LINE)) {
            val date = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull() ?: continue
            val l = Txt.labels(body)
            val avgRaw = l["hrv avg"]
            val avg = Txt.int(avgRaw)
            val assessment = avgRaw?.split(DASH, limit = 2)?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            val range = l["normal range"]?.let { RANGE.find(it) }
            val day = HrvDay(
                date, stamp(fetchedAt), avg, assessment,
                range?.groupValues?.get(1)?.toInt(), range?.groupValues?.get(2)?.toInt(), Txt.int(l["baseline"]),
            )
            if (avg != null || assessment != null) out += day
        }
        return ParseResult(out, if (out.isEmpty()) listOf("no HRV assessment rows recognized") else emptyList())
    }
}

/** `getActivityDetail`: labelled lines. Always returns a row for [id] so an unreadable reply is not fetched again and again. */
object ActivityDetailParser {
    fun parse(reply: ToolReply, id: String, fetchedAt: Instant): ParseResult<ActivityDetail> {
        if (reply.isError) return ParseResult(ActivityDetail(id, stamp(fetchedAt)), listOf("tool returned an error"))
        val l = Txt.labels(reply.text.lines())
        val warnings = mutableListOf<String>()
        val load = l["training load"]?.let { v -> Txt.int(v).also { if (it == null) warnings += "cannot read training load" } }
        if (load == null) warnings += "no training load in activity detail"
        return ParseResult(
            ActivityDetail(
                id, stamp(fetchedAt), load, Txt.double(l["aerobic te"]), Txt.double(l["anaerobic te"]),
                l["training focus"]?.takeIf { it.isNotBlank() },
            ),
            warnings,
        )
    }
}
