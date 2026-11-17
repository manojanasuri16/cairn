package app.cairn.widget

import app.cairn.core.designsystem.FitMath
import app.cairn.core.designsystem.WidgetTokens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln

/** The stats the Overview widget can show. Order here is the picker's canonical order. */
enum class OverviewId(val label: String) {
    Steps("Steps"), Sleep("Sleep"), SleepScore("Sleep score"), Recovery("Recovery"), RestingHr("Resting HR"),
    Calories("Calories"), Stress("Stress"),
    /** Id kept as LoadRatio so placed widgets keep their choice; COROS calls it Intensity Trend and shows it as a percent. */
    LoadRatio("Intensity Trend"), Vo2("VO2max"), LastDistance("Last run"),
    HeartRate("Heart rate"), Hrv("HRV"), WeeklyLoad("Weekly load"), TodayRun("Today's run"),
    /** Added in 1.4.0; appended so the ids and order of every earlier stat stay as they were. */
    WeeklyMileage("Weekly mileage");

    companion object {
        val Default = listOf(Steps, Sleep, Recovery, RestingHr)
        const val MAX = 6
        fun parse(raw: String?): OverviewId? = entries.firstOrNull { it.name == raw }
    }
}

/** One stat: a number and its unit (set in small, light text beside it), an optional 0..1 progress and a 7-day trend. */
data class OverviewItem(
    val id: OverviewId,
    val number: String,
    val unit: String = "",
    val fraction: Float? = null,
    /** Load ratio, for colouring. */
    val level: Double? = null,
    val trend: List<Float> = emptyList(),
)

data class OverviewUi(val items: List<OverviewItem>) {
    fun find(id: OverviewId): OverviewItem? = items.firstOrNull { it.id == id }
}

/** Overview stats from the newer COROS tools, with their own data-age footer (null when there are none). */
data class OverviewExtras(val items: List<OverviewItem>, val footer: FooterUi?)

object OverviewMapper {
    /** "52 bpm" -> ("52", "bpm"), "98%" -> ("98", "%"), "7h 41m" stays whole. The number and unit are drawn together on one baseline. */
    fun splitUnit(text: String): Pair<String, String> {
        val t = text.trim()
        if (t.endsWith("%")) return t.dropLast(1).trim() to "%"
        val i = t.lastIndexOf(' ')
        if (i > 0) {
            val unit = t.substring(i + 1)
            if (unit.isNotEmpty() && unit.all { it.isLetter() || it == '/' }) return t.substring(0, i) to unit
        }
        return t to ""
    }

    /** "7 h 41 min" becomes "7h 41m": a tile is narrow and the header already says Sleep. */
    fun compactDuration(text: String): String = text.replace(" h ", "h ").replace(" min", "m")

    /** Builds the Overview data from the per-widget results; each stat shows only when its source has data. */
    fun build(
        today: WidgetUi<TodayUi>, sleep: WidgetUi<SleepUi>, recovery: WidgetUi<RecoveryUi>,
        load: WidgetUi<TrainingLoadUi>, fitness: WidgetUi<FitnessUi>, activity: WidgetUi<ActivityUi>,
        extras: OverviewExtras = OverviewExtras(emptyList(), null),
    ): WidgetUi<OverviewUi> {
        val items = mutableListOf<OverviewItem>()
        val footers = mutableListOf<FooterUi>()
        (today as? WidgetUi.Content)?.let { c ->
            footers += c.footer
            c.body.stepsText?.let { items += OverviewItem(OverviewId.Steps, it, "steps", c.body.stepsFraction) }
        }
        (sleep as? WidgetUi.Content)?.let { c ->
            footers += c.footer
            items += OverviewItem(OverviewId.Sleep, compactDuration(c.body.durationText), "", (c.body.minutes / 480f).coerceIn(0f, 1f))
            c.body.score?.let { items += OverviewItem(OverviewId.SleepScore, it.toString(), "", (it / 100f).coerceIn(0f, 1f)) }
        }
        (recovery as? WidgetUi.Content)?.let { c ->
            footers += c.footer
            items += OverviewItem(OverviewId.Recovery, c.body.percentText.removeSuffix("%"), "%", c.body.fraction)
        }
        (today as? WidgetUi.Content)?.body?.metrics?.forEach { m ->
            val id = when (m.id) { MetricId.RestingHr -> OverviewId.RestingHr; MetricId.Calories -> OverviewId.Calories; MetricId.Stress -> OverviewId.Stress; else -> null } ?: return@forEach
            val (n, u) = splitUnit(m.value)
            items += OverviewItem(id, n, u)
        }
        (load as? WidgetUi.Content)?.let { c ->
            footers += c.footer
            if (c.body.ratio != null) items += OverviewItem(OverviewId.LoadRatio, c.body.ratioText, "", null, c.body.ratio, c.body.bars.map { it.fraction })
        }
        (fitness as? WidgetUi.Content)?.let { c ->
            footers += c.footer
            c.body.vo2Text?.let { items += OverviewItem(OverviewId.Vo2, it) }
        }
        (activity as? WidgetUi.Content)?.let { c ->
            footers += c.footer
            c.body.distance?.let { val (n, u) = splitUnit(it); items += OverviewItem(OverviewId.LastDistance, n, u) }
        }
        extras.footer?.let { footers += it; items += extras.items }
        if (footers.isEmpty()) return (today as? WidgetUi.Message) ?: (listOf(sleep, recovery, load, fitness, activity).firstOrNull { it is WidgetUi.Message } as? WidgetUi.Message) ?: WidgetUi.Loading
        val ordered = items.sortedBy { it.id.ordinal }
        // The data-age line reports the oldest source, so a stale tile never hides behind a fresh one.
        val footer = footers.maxByOrNull { it.tier.ordinal } ?: footers.first()
        val desc = "Overview. " + ordered.joinToString(". ") { "${it.id.label} ${it.number} ${it.unit}".trim() } + ". " + footer.text
        return WidgetUi.Content(OverviewUi(ordered), footer, desc, Screen.Today)
    }
}

/** Where the chosen stats go in the box, from their number and the box's aspect ratio. */
enum class OverviewPattern { Single, Grid, BigLeft, BigTop }

data class OverviewPlan(
    /** How many of the chosen stats are shown (fewer only when the box is too small for even a compact tile). */
    val shown: Int,
    val pattern: OverviewPattern,
    val cols: Int,
    val rows: Int,
    /** Width and height in dp of the smallest tile, which every tile is at least as large as. */
    val tileW: Float,
    val tileH: Float,
)

object OverviewLayout {
    const val GAP = 6f
    const val MIN_TILE_W = 56f
    const val MIN_TILE_H = 52f

    /** Pattern for [n] stats in a box of [w] x [h] dp (content area): 1 hero, 2 side by side, 3 one big + two small, 4 as 2x2, 5-6 as 3x2 or 2x3. */
    fun pattern(n: Int, w: Float, h: Float): Triple<OverviewPattern, Int, Int> {
        val aspect = w / h.coerceAtLeast(1f)
        return when {
            n <= 1 -> Triple(OverviewPattern.Single, 1, 1)
            n == 2 -> if (aspect >= 1f) Triple(OverviewPattern.Grid, 2, 1) else Triple(OverviewPattern.Grid, 1, 2)
            n == 3 -> if (aspect >= 1f) Triple(OverviewPattern.BigLeft, 2, 2) else Triple(OverviewPattern.BigTop, 2, 2)
            else -> bestGrid(n, w, h)
        }
    }

    /** The columns x rows whose tiles come closest to square for this box, so a stat never gets a long thin strip when a squarer one exists. */
    fun bestGrid(n: Int, w: Float, h: Float): Triple<OverviewPattern, Int, Int> {
        var best = Triple(OverviewPattern.Grid, 1, n); var bestScore = Float.MAX_VALUE
        for (cols in 1..n) {
            val perRow = FitMath.rows(n, cols)
            val eff = perRow.max(); val rows = perRow.size
            // A last row that is mostly empty wastes the box: it must hold at least half of a full row.
            if (rows > 1 && perRow.min() * 2 < eff) continue
            val tw = (w - GAP * (eff - 1)) / eff
            val th = (h - GAP * (rows - 1)) / rows
            if (tw <= 0f || th <= 0f) continue
            val score = abs(ln(tw / th))
            if (score < bestScore) { bestScore = score; best = Triple(OverviewPattern.Grid, eff, rows) }
        }
        return best
    }

    /** The stats to draw: the first [shown] of the user's list, in the user's order. Stats are never skipped to make room. */
    fun <T> firstN(list: List<T>, shown: Int): List<T> = list.take(shown)

    /**
     * The plan the widget really uses for a launcher cell of [cellW] x [cellH]: Responsive picks the largest listed size that fits
     * the cell, and the content area is that size less padding and the footer. The configuration preview uses the same rule.
     */
    fun planForCell(count: Int, cellW: Float, cellH: Float, fontScale: Float, sizes: List<Pair<Int, Int>>, items: List<OverviewItem>? = null): OverviewPlan {
        val (sw, sh) = WidgetSizes.pick(sizes, cellW, cellH)
        val ew = sw + WidgetSizes.MARGIN_DP; val eh = sh + WidgetSizes.MARGIN_DP
        val pad = Layouts.pad(app.cairn.core.designsystem.SizeClass.of(ew, eh), eh)
        // With the stats' real text the plan also drops tiles whose number or title would not fit (what the widget itself does).
        return if (items != null) planFor(items, ew - 2f * pad, Layouts.availDp(ew, eh, fontScale), fontScale) else plan(count, ew - 2f * pad, Layouts.availDp(ew, eh, fontScale))
    }

    /** Smallest tile of a pattern: its width and height. Rows split evenly, so every tile fills its cell exactly. */
    fun tileSize(n: Int, pattern: OverviewPattern, cols: Int, rows: Int, w: Float, h: Float): Pair<Float, Float> = when (pattern) {
        OverviewPattern.Single -> w to h
        OverviewPattern.BigLeft -> (w - GAP) / 2f to (h - GAP) / 2f
        OverviewPattern.BigTop -> (w - GAP) / 2f to (h - GAP) / 2f
        OverviewPattern.Grid -> {
            val perRow = FitMath.rows(n, cols)
            val widest = perRow.max()
            val rowCount = perRow.size
            ((w - GAP * (widest - 1)) / widest) to ((h - GAP * (rowCount - 1)) / rowCount)
        }
    }

    /** Preferred pattern first, then the other arrangements that can hold [k] stats (a single row, a single column, 2 columns). */
    fun candidates(k: Int, w: Float, h: Float): List<Triple<OverviewPattern, Int, Int>> {
        if (k <= 1) return listOf(Triple(OverviewPattern.Single, 1, 1))
        val list = mutableListOf(pattern(k, w, h))
        list += Triple(OverviewPattern.Grid, k, 1)
        list += Triple(OverviewPattern.Grid, 1, k)
        if (k >= 3) list += Triple(OverviewPattern.Grid, 2, 2)
        if (k >= 5) list += Triple(OverviewPattern.Grid, 3, 2)
        return list.distinct()
    }

    /** The arrangement for [n] stats: the most stats, in the best pattern, whose smallest tile is still readable. */
    fun plan(n: Int, w: Float, h: Float): OverviewPlan {
        for (k in n.coerceIn(1, OverviewId.MAX) downTo 1) {
            for ((p, c, r) in candidates(k, w, h)) {
                val (tw, th) = tileSize(k, p, c, r, w, h)
                if (k == 1 || (tw >= MIN_TILE_W && th >= MIN_TILE_H)) return OverviewPlan(k, p, c, r, tw, th)
            }
        }
        return OverviewPlan(1, OverviewPattern.Single, 1, 1, w, h)
    }

    /** Value size and unit size for a tile: the largest number (within [14, max by height]) whose number and unit fit the width. */
    fun valueSizes(number: String, unit: String, tileW: Float, tileH: Float, fontScale: Float, ringReserve: Float = 0f, maxSp: Float = 56f): Pair<Float, Float> {
        val availW = tileW - 2f * PAD - ringReserve
        val maxByHeight = (tileH * 0.36f / (1.2f * Layouts.ts(fontScale))).coerceIn(14f, maxSp)
        val floor = TextFit.minSp(fontScale, if (tileW > 150f) 14f else TILE_MIN_SP, Layouts.HERO_CAP)
        var sp = maxByHeight
        fun width(s: Float) = 1.1f * (TextFit.widthDp(number, s, fontScale, Layouts.HERO_CAP) + (if (unit.isEmpty()) 0f else 16f + TextFit.widthDp(unit, unitSp(s), fontScale, Layouts.HERO_CAP)))
        while (sp > floor && width(sp) > availW) sp -= 1f
        sp = maxOf(sp, minOf(floor, maxByHeight))
        if (width(sp) <= availW) return sp to unitSp(sp)
        // Still too wide at the floor: the unit is dropped (the header already names the stat).
        return sp to 0f
    }

    /** True when [number] and [unit] at [sp] fit [availW] dp (with the same safety margin valueSizes uses). */
    fun valueFits(number: String, unit: String, sp: Float, availW: Float, fontScale: Float): Boolean =
        1.1f * (TextFit.widthDp(number, sp, fontScale, Layouts.HERO_CAP) + (if (unit.isEmpty()) 0f else 16f + TextFit.widthDp(unit, unitSp(sp), fontScale, Layouts.HERO_CAP))) <= availW

    fun unitSp(valueSp: Float): Float = maxOf(10f, valueSp * 0.42f)

    /** Ring diameter beside the value, or 0 when there is no room: the ring never squeezes the value below its floor. */
    fun ringSize(number: String, unit: String, tileW: Float, tileH: Float, fontScale: Float): Float {
        val d = minOf(tileH - 2f * PAD - 16f, tileW * 0.36f)
        if (d < 32f) return 0f
        val (sp, u) = valueSizes(number, unit, tileW, tileH, fontScale, ringReserve = d + 6f)
        if (u == 0f && unit.isNotEmpty()) return 0f
        val floor = TextFit.minSp(fontScale, 14f, Layouts.HERO_CAP)
        val fits = TextFit.widthDp(number, sp, fontScale, Layouts.HERO_CAP) + (if (unit.isEmpty()) 0f else 16f + TextFit.widthDp(unit, unitSp(sp), fontScale, Layouts.HERO_CAP)) <= tileW - 2f * PAD - d - 6f
        return if (fits || sp > floor) d else 0f
    }

    const val PAD = 4f
    /** Smallest effective number size in a multi-stat tile; a lone stat keeps 14. */
    const val TILE_MIN_SP = 11f

    /** The plan the widget draws: the largest count of [items] (in order) whose tiles hold their number and title (fewer tiles beat cut ones). */
    fun planFor(items: List<OverviewItem>, w: Float, h: Float, fontScale: Float): OverviewPlan {
        var last = plan(items.size, w, h)
        for (k in items.size downTo 1) {
            val cand = plan(k, w, h)
            last = cand
            if (valuesFit(items, cand, fontScale)) return cand
        }
        return last
    }

    /** True when every tile of [plan] can hold its stat: the number at the legible floor, and the title (full or short form) beside its icon. */
    fun valuesFit(items: List<OverviewItem>, plan: OverviewPlan, fontScale: Float): Boolean {
        val floor = TextFit.minSp(fontScale, TILE_MIN_SP, Layouts.HERO_CAP)
        return items.take(plan.shown).all { (
            TextFit.widthDp(it.number, floor, fontScale, Layouts.HERO_CAP) <= plan.tileW - 2f * PAD &&
                (plan.pattern == OverviewPattern.Single || TextFit.fits(Layouts.shortLabel(it.id.label), 10f, fontScale, titleRoom(plan.tileW)))) }
    }

    /** Width left for a tile's title: a narrow tile (under 70 dp) drops the icon so the name keeps the whole line. */
    fun titleRoom(tileW: Float): Float = tileW - 2f * PAD - (if (tileW >= ICON_MIN_TILE_W) 16f else 0f) - 2f
    const val ICON_MIN_TILE_W = 70f

    /** Title for a tile: the full label when it fits beside the icon, else its short form. */
    fun title(id: OverviewId, tileW: Float, fontScale: Float): String =
        if (TextFit.fits(id.label, 10f, fontScale, titleRoom(tileW) + 6f)) id.label else Layouts.shortLabel(id.label)
}
