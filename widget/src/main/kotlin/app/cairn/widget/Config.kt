package app.cairn.widget

import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.ThemeId

enum class Bg(val label: String) { Gradient("Gradient"), Solid("Solid"), Transparent("Transparent");
    companion object { fun parse(raw: String?): Bg? = entries.firstOrNull { it.name == raw } } }

enum class Corners(val label: String) { System("System"), ExtraRound("Extra round");
    companion object { fun parse(raw: String?): Corners = entries.firstOrNull { it.name == raw } ?: System } }

enum class UnitsPref(val label: String) { Inherit("Use app setting"), Km("Kilometres"), Mi("Miles");
    companion object { fun parse(raw: String?): UnitsPref = entries.firstOrNull { it.name == raw } ?: Inherit } }

/** A style variant of one widget. The first entry per kind is the default (the best-looking one). */
enum class WidgetStyle(val kind: WidgetKind, val label: String) {
    TodayRing(WidgetKind.Today, "Ring"),
    TodayStack(WidgetKind.Today, "Stack"),
    TodayStripe(WidgetKind.Today, "Compact stripe"),
    RecoveryRing(WidgetKind.Recovery, "Ring"),
    RecoveryGauge(WidgetKind.Recovery, "Gauge arc"),
    RecoveryPill(WidgetKind.Recovery, "Number and pill"),
    ActivityHero(WidgetKind.LastActivity, "Hero distance"),
    ActivityGrid(WidgetKind.LastActivity, "Stat grid"),
    ActivityLine(WidgetKind.LastActivity, "One-liner"),
    LoadBars(WidgetKind.TrainingLoad, "Bars"),
    LoadDial(WidgetKind.TrainingLoad, "Ratio dial"),
    LoadTrend(WidgetKind.TrainingLoad, "Trend line"),
    SleepBar(WidgetKind.Sleep, "Stage bar"),
    SleepRing(WidgetKind.Sleep, "Score ring"),
    SleepTimeline(WidgetKind.Sleep, "Timeline"),
    FitnessGrid(WidgetKind.Fitness, "Race grid"),
    FitnessVo2(WidgetKind.Fitness, "VO2max hero"),
    FitnessLadder(WidgetKind.Fitness, "Pace ladder"),
    OverviewGrid(WidgetKind.Overview, "Grid");

    companion object {
        fun forKind(kind: WidgetKind): List<WidgetStyle> = entries.filter { it.kind == kind }
        fun default(kind: WidgetKind): WidgetStyle = forKind(kind).first()
        fun parse(raw: String?, kind: WidgetKind): WidgetStyle =
            entries.firstOrNull { it.name == raw && it.kind == kind } ?: default(kind)
    }
}

/** Secondary content a user can switch off per widget. The data-age footer is not here on purpose: it is always shown. */
enum class Part(val label: String) {
    Sleep("Sleep"), RestingHr("Resting heart rate"), Recovery("Recovery"), Calories("Calories"), Stress("Stress"),
    TimeToFull("Time to full"),
    Duration("Duration"), Pace("Pace"), AvgHr("Heart rate"),
    Vo2("VO2max"), Comment("Load comment"),
    Score("Score"), Window("Time window"), Stages("Stage legend"),
    Threshold("Threshold pace"), Predictions("Race predictions"),
    Rings("Progress rings and trends");

    companion object {
        fun forKind(kind: WidgetKind): List<Part> = when (kind) {
            WidgetKind.Today -> listOf(Sleep, RestingHr, Recovery, Calories, Stress)
            WidgetKind.Recovery -> listOf(TimeToFull)
            WidgetKind.LastActivity -> listOf(Duration, Pace, AvgHr)
            WidgetKind.TrainingLoad -> listOf(Comment, Vo2)
            WidgetKind.Sleep -> listOf(Score, Window, Stages)
            WidgetKind.Fitness -> listOf(Threshold, Predictions)
            WidgetKind.Overview -> listOf(Rings)
        }

        fun ofMetric(id: MetricId): Part = when (id) {
            MetricId.Sleep -> Sleep; MetricId.RestingHr -> RestingHr; MetricId.Recovery -> Recovery
            MetricId.Calories -> Calories; MetricId.Stress -> Stress
        }
    }
}

/** App-wide default look that new (and unconfigured) widgets inherit. */
data class AppAppearance(val theme: ThemeId = ThemeId.Default, val bg: Bg = Bg.Gradient, val opacity: Int = 100)

/**
 * Per-widget choices as stored. Null theme, background and opacity mean "inherit the app-wide default", so a
 * widget placed before this existed (no stored value) simply follows the app default and keeps working.
 */
data class WidgetConfig(
    val theme: ThemeId? = null,
    val style: WidgetStyle? = null,
    val bg: Bg? = null,
    val opacity: Int? = null,
    val corners: Corners = Corners.System,
    val accent: AccentOption = AccentOption.Theme,
    val hidden: Set<Part> = emptySet(),
    val units: UnitsPref = UnitsPref.Inherit,
    /** Compact footer style. The footer itself can never be hidden. */
    val compactFooter: Boolean = false,
    /** Overview only: the chosen stats in order (1 to 6). Null means the default four. */
    val metrics: List<OverviewId>? = null,
) {
    fun shows(part: Part) = part !in hidden

    fun resolve(kind: WidgetKind, app: AppAppearance): ResolvedConfig {
        val b = bg ?: app.bg
        val op = (opacity ?: app.opacity).coerceIn(0, 100)
        return ResolvedConfig(
            kind = kind,
            theme = theme ?: app.theme,
            style = style?.takeIf { it.kind == kind } ?: WidgetStyle.default(kind),
            bg = b,
            opacity = if (b == Bg.Transparent) 0 else op,
            corners = corners, accent = accent,
            hidden = hidden.filter { it in Part.forKind(kind) }.toSet(),
            units = units, compactFooter = compactFooter,
            metrics = (metrics?.distinct()?.take(OverviewId.MAX)?.takeIf { it.isNotEmpty() }) ?: OverviewId.Default,
        )
    }

    fun encode(): String = buildList {
        theme?.let { add("theme=${it.name}") }
        style?.let { add("style=${it.name}") }
        bg?.let { add("bg=${it.name}") }
        opacity?.let { add("op=$it") }
        if (corners != Corners.System) add("corner=${corners.name}")
        if (accent != AccentOption.Theme) add("accent=${accent.name}")
        if (hidden.isNotEmpty()) add("hide=" + hidden.joinToString(",") { it.name })
        if (units != UnitsPref.Inherit) add("units=${units.name}")
        if (compactFooter) add("cf=1")
        metrics?.let { add("metrics=" + it.joinToString(",") { m -> m.name }) }
    }.joinToString(";")

    companion object {
        /** Unknown keys and malformed values are ignored, so older or newer data never breaks a widget. */
        fun decode(raw: String?, kind: WidgetKind): WidgetConfig {
            if (raw.isNullOrBlank()) return WidgetConfig()
            val m = raw.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return WidgetConfig(
                theme = ThemeId.parse(m["theme"]),
                style = WidgetStyle.entries.firstOrNull { it.name == m["style"] && it.kind == kind },
                bg = Bg.parse(m["bg"]),
                opacity = m["op"]?.toIntOrNull()?.coerceIn(0, 100),
                corners = Corners.parse(m["corner"]),
                accent = AccentOption.parse(m["accent"]),
                hidden = m["hide"]?.split(',')?.mapNotNull { n -> Part.entries.firstOrNull { it.name == n } }?.toSet() ?: emptySet(),
                units = UnitsPref.parse(m["units"]),
                compactFooter = m["cf"] == "1",
                metrics = m["metrics"]?.split(',')?.mapNotNull { OverviewId.parse(it) }?.distinct()?.take(OverviewId.MAX)?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

/** Everything a widget needs to draw, with inheritance applied. */
data class ResolvedConfig(
    val kind: WidgetKind,
    val theme: ThemeId,
    val style: WidgetStyle,
    val bg: Bg,
    /** 0..100; 0 for a transparent widget. */
    val opacity: Int,
    val corners: Corners,
    val accent: AccentOption,
    val hidden: Set<Part>,
    val units: UnitsPref,
    val compactFooter: Boolean,
    val metrics: List<OverviewId> = OverviewId.Default,
) {
    fun shows(part: Part) = part !in hidden

    /** The card is see-through enough that text must be legible on an unknown wallpaper. */
    val onWallpaper: Boolean get() = bg == Bg.Transparent || opacity < 55
}
