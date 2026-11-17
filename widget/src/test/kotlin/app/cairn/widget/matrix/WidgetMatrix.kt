package app.cairn.widget.matrix

import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.ThemeId
import app.cairn.widget.Bg
import app.cairn.widget.Corners
import app.cairn.widget.OverviewId
import app.cairn.widget.Part
import app.cairn.widget.UnitsPref
import app.cairn.widget.WeeklyGoal
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetSize
import app.cairn.widget.WidgetSizes
import app.cairn.widget.WidgetStyle
import app.cairn.widget.WidgetVariants
import java.io.File
import java.util.Random

/** Which tiers run: -PwidgetMatrix=smoke (default, in the normal unit-test task) | full | off. */
object MatrixConfig {
    val mode: String get() = System.getProperty("widgetMatrix", "smoke")
    val full: Boolean get() = mode == "full"
    val off: Boolean get() = mode == "off"
    val shard: Pair<Int, Int> get() = System.getProperty("matrixShard", "0/1").split("/").let { it[0].toInt() to it[1].toInt() }
    /** Regex on the case id (-PmatrixFilter=Overview) to run a slice. */
    val filter: Regex? get() = System.getProperty("matrixFilter", "").takeIf { it.isNotEmpty() }?.toRegex()
    val dumpAll: Boolean get() = System.getProperty("matrixDumpAll", "false") == "true"
    val reportDir: File get() = File(System.getProperty("matrixReportDir", "build/reports/widget-matrix"))
    val resourcesDir: File get() = File(System.getProperty("matrixResourcesDir", "src/test/resources"))
}

/**
 * Case generator of docs/widget-quality.md 2.2 to 2.4. Tiers: Core (every kind x style x registered size x font scale), Cells (launcher profiles),
 * States (every data state and locale), Pairwise (seeded greedy pairwise cover of everything else), Parcel (whole Responsive map).
 * Smoke keeps the product small; full widens fonts, styles, profiles and the pairwise budget.
 */
object WidgetMatrix {
    const val SEED = 20261117L
    private val fontsAll = listOf(0.85f, 1.0f, 1.3f, 1.5f, 2.0f)
    private val cleanLook = LookSpec(ThemeId.Clean, Bg.Gradient, 100)
    private val defaultEnv = Env(1.0f, "en-GB", false, 320, false)
    private val overviewOrder = listOf(OverviewId.Steps, OverviewId.Sleep, OverviewId.Recovery, OverviewId.RestingHr, OverviewId.Calories, OverviewId.LoadRatio)
    private val gray = 0xFF808080.toInt()
    private val wallpapers3 = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt())

    val profiles: List<LauncherProfile> = listOf(
        LauncherProfile("aosp", 70, 30, 70, 30), LauncherProfile("pixel5x5", 74, 24, 74, 24), LauncherProfile("oneui4x5", 86, 16, 104, 16),
        LauncherProfile("nova6x8", 62, 8, 72, 8), LauncherProfile("tablet", 136, 16, 116, 16), LauncherProfile("foldInner", 104, 16, 112, 16),
    )

    val looks: List<LookSpec> = ThemeId.entries.map { LookSpec(it, Bg.Gradient, 100) } + LookSpec(ThemeId.Clean, Bg.Transparent, 0, onWallpaperLook = true)
    private val bgs = listOf(Bg.Gradient to 100, Bg.Solid to 100, Bg.Solid to 85, Bg.Solid to 70, Bg.Solid to 55, Bg.Solid to 30, Bg.Transparent to 0)
    private val locales = listOf("en-GB" to false, "de-DE" to false, "fr-FR" to false, "ar-EG" to true, "hi-IN" to false)

    private fun xmlName(kind: WidgetKind) = when (kind) {
        WidgetKind.Today -> "today"; WidgetKind.Recovery -> "recovery"; WidgetKind.LastActivity -> "activity"; WidgetKind.TrainingLoad -> "load"
        WidgetKind.Sleep -> "sleep"; WidgetKind.Fitness -> "fitness"; WidgetKind.Overview -> "overview"; WidgetKind.Hrv -> "hrv"
        WidgetKind.HeartRate -> "heart"; WidgetKind.Stress -> "stress"; WidgetKind.WeeklyMileage -> "mileage"
    }

    private val limitsCache = HashMap<WidgetKind, List<ProviderLimits>>()

    /** Resize limits parsed from each provider XML (one per size variant) of the kind: a launcher can produce a cell only inside ONE variant's limits. */
    fun variantLimits(kind: WidgetKind): List<ProviderLimits> = limitsCache.getOrPut(kind) {
        val dir = File(MatrixConfig.resourcesDir.absoluteFile.parentFile.parentFile, "main/res/xml")
        val files = dir.listFiles { f -> f.name.startsWith("widget_${xmlName(kind)}_") && f.name.endsWith("_info.xml") }.orEmpty()
        fun attr(text: String, name: String) = Regex("""android:$name="(\d+)dp"""").find(text)?.groupValues?.get(1)?.toFloat()
        files.map { f ->
            val t = f.readText()
            // A widget is placed at minWidth x minHeight and resizes down to min(minResize, min) (Android ignores a minResize above the min).
            ProviderLimits(
                minW = minOf(attr(t, "minResizeWidth") ?: 1000f, attr(t, "minWidth") ?: 1000f), minH = minOf(attr(t, "minResizeHeight") ?: 1000f, attr(t, "minHeight") ?: 1000f),
                maxW = attr(t, "maxResizeWidth") ?: 1000f, maxH = attr(t, "maxResizeHeight") ?: 1000f,
            )
        }.ifEmpty { listOf(ProviderLimits(40f, 40f, 1000f, 1000f)) }
    }

    fun cellAllowed(kind: WidgetKind, w: Float, h: Float): Boolean = variantLimits(kind).any { w >= it.minW && h >= it.minH && w <= it.maxW * 1.2f && h <= it.maxH * 1.2f }

    /** Cells 2x1..5x5, portrait and landscape, that the provider lets a launcher produce (up to 1.2 x its max, as launchers overshoot). */
    fun profileCells(kind: WidgetKind, p: LauncherProfile): List<Cell> {
        val out = mutableListOf<Cell>()
        for (nw in 2..5) for (nh in 1..5) for (land in listOf(false, true)) {
            val w = p.w(nw); val h = if (land) Math.round(p.h(nh) * 0.8f).toFloat() else p.h(nh)
            if (!cellAllowed(kind, w, h)) continue
            out += Cell(w, h, "${p.name}:${nw}x$nh:${if (land) "l" else "p"}")
        }
        return out.distinctBy { it.wDp to it.hDp }
    }

    private fun declaredCells(kind: WidgetKind) = WidgetSizes.byKind.getValue(kind).map { Cell(it.first.toFloat(), it.second.toFloat(), "declared") }

    private fun case(kind: WidgetKind, style: WidgetStyle, cell: Cell, env: Env = defaultEnv, look: LookSpec = cleanLook, data: DataState = DataState.Full,
                     tweaks: ConfigTweaks = ConfigTweaks(), wallpapers: List<Int> = emptyList(), tier: Tier) = MatrixCase(kind, style, cell, env, look, data, tweaks, wallpapers, tier)

    private fun styles(kind: WidgetKind, all: Boolean) = if (all) WidgetStyle.forKind(kind) else listOf(WidgetStyle.default(kind))

    /** T0: every kind x style x registered size x font scale, Clean light, full data. Overview additionally 1..6 stats. */
    fun core(): List<MatrixCase> {
        val fonts = if (MatrixConfig.full) fontsAll else listOf(1.0f, 2.0f)
        val out = mutableListOf<MatrixCase>()
        for (kind in WidgetKind.entries) for (style in WidgetStyle.forKind(kind)) for (cell in declaredCells(kind)) for (fs in fonts)
            out += case(kind, style, cell, defaultEnv.copy(fontScale = fs), wallpapers = if (fs == 1.0f) listOf(gray) else emptyList(), tier = Tier.Core)
        for (cell in declaredCells(WidgetKind.Overview)) for (n in 1..6) for (fs in listOf(1.0f, 1.5f))
            out += case(WidgetKind.Overview, WidgetStyle.OverviewGrid, cell, defaultEnv.copy(fontScale = fs), tweaks = ConfigTweaks(metrics = overviewOrder.take(n)), tier = Tier.Core)
        return out
    }

    /** T1: launcher profile cells. Default style at font 1.0 and 1.5; the other styles on aosp and pixel5x5 at 1.0 (full only). */
    fun cells(): List<MatrixCase> {
        val out = mutableListOf<MatrixCase>()
        val profs = if (MatrixConfig.full) profiles else profiles.take(1)
        for (kind in WidgetKind.entries) for (p in profs) for (cell in profileCells(kind, p)) {
            for (fs in listOf(1.0f, 1.5f)) out += case(kind, WidgetStyle.default(kind), cell, defaultEnv.copy(fontScale = fs), tier = Tier.Cells)
            if (MatrixConfig.full && p.name == "aosp") for (st in WidgetStyle.forKind(kind).drop(1)) out += case(kind, st, cell, tier = Tier.Cells)
        }
        return out
    }

    /** Owner's phone (dark, Clean, ~411 dp wide): gallery previews at pinDp plus the odd launcher sizes for 2x2, 4x2 and 4x3. Provider limits are NOT applied (A1 needs 2x2 for wide kinds). */
    fun owner(): List<MatrixCase> {
        val sizes = mutableListOf<Cell>()
        for (w in listOf(110f, 140f, 176f)) for (h in listOf(110f, 160f, 223f)) sizes += Cell(w, h, "owner2x2")
        for (w in listOf(250f, 300f, 340f)) for (h in listOf(110f, 130f, 146f)) sizes += Cell(w, h, "owner4x2")
        for (w in listOf(250f, 300f, 340f)) for (h in listOf(180f, 220f, 260f)) sizes += Cell(w, h, "owner4x3")
        for (sz in WidgetSize.entries) sizes += Cell(sz.pinDp.width.value, sz.pinDp.height.value, "ownerPin${sz.cells}")
        val env = defaultEnv.copy(dark = true)
        return WidgetKind.entries.flatMap { k -> sizes.flatMap { c -> WidgetStyle.forKind(k).map { st -> case(k, st, c, env, tier = Tier.Cells, wallpapers = listOf(gray)) } } }
            .filter { MatrixConfig.full || it.style == WidgetStyle.default(it.kind) || it.cell.source == "owner2x2" && false }
    }

    /** T2: every data state at the natural placed size and a large one; locales (de-DE decimal comma, RTL) on full and huge values. */
    fun states(): List<MatrixCase> {
        val out = mutableListOf<MatrixCase>()
        val fonts = if (MatrixConfig.full) listOf(1.0f, 2.0f) else listOf(1.0f)
        for (kind in WidgetKind.entries) {
            val natural = WidgetVariants.defaultSize(kind).pinDp.let { Cell(it.width.value, it.height.value, "pin") }
            val large = WidgetSize.Large.pinDp.let { Cell(it.width.value, it.height.value, "pinL") }
            // "Small (2x2)" of the wide kinds (A1) is a real placement: its provider minWidth is 110 dp although minResizeWidth is 250 dp.
            val small = WidgetSize.Small.pinDp.let { Cell(it.width.value, it.height.value, "pinS") }
            val wide = WidgetVariants.defaultSize(kind) == WidgetSize.Wide
            val wanted = listOf(natural, if (wide) small else large)
            val cellsHere = wanted.distinctBy { it.wDp to it.hDp }.filter { fits(kind, it) }.ifEmpty { listOf(natural) }
            for (style in styles(kind, MatrixConfig.full)) for (cell in cellsHere) for (fs in fonts) {
                for (st in DataState.entries) out += case(kind, style, cell, defaultEnv.copy(fontScale = fs), data = st, wallpapers = if (st == DataState.Full) listOf(gray) else emptyList(), tier = Tier.States)
                for (st in listOf(DataState.Full, DataState.Huge)) {
                    for ((loc, rtl) in locales.drop(1).let { if (MatrixConfig.full) it else it.filter { l -> l.first == "de-DE" || l.first == "ar-EG" } })
                        out += case(kind, style, cell, defaultEnv.copy(fontScale = fs, locale = loc, rtl = rtl), data = st, tier = Tier.States)
                    for (u in listOf(UnitsPref.Km, UnitsPref.Mi)) out += case(kind, style, cell, defaultEnv.copy(fontScale = fs), data = st, tweaks = ConfigTweaks(units = u), tier = Tier.States)
                }
            }
        }
        return out
    }

    /** Looks: all 11 looks x light/dark, and every background/opacity, at the natural size, with the contrast sampled over black, white and grey. */
    fun looks(): List<MatrixCase> {
        val out = mutableListOf<MatrixCase>()
        for (kind in WidgetKind.entries) {
            val natural = WidgetVariants.defaultSize(kind).pinDp.let { Cell(it.width.value, it.height.value, "pin") }
            for (dark in listOf(false, true)) {
                val env = defaultEnv.copy(dark = dark)
                for (look in looks) out += case(kind, WidgetStyle.default(kind), natural, env, look, wallpapers = if (look.bg == Bg.Transparent) wallpapers3 else listOf(gray), tier = Tier.Looks)
                for ((bg, op) in bgs) out += case(kind, WidgetStyle.default(kind), natural, env, LookSpec(ThemeId.Clean, bg, op, onWallpaperLook = bg == Bg.Transparent), wallpapers = wallpapers3, tier = Tier.Looks)
            }
        }
        return out
    }

    private fun fits(kind: WidgetKind, c: Cell): Boolean = cellAllowed(kind, c.wDp, c.hDp)

    /** Seeded greedy pairwise cover over look, theme, background, accent, corners, font, density, locale, data, cell, toggles and style. */
    fun pairwise(seed: Long = SEED, maxCases: Int = if (MatrixConfig.full) 1500 else 220): List<MatrixCase> {
        val rng = Random(seed)
        val styleList = WidgetStyle.entries
        val dims = listOf(styleList.size, looks.size, 2, bgs.size, AccentOption.entries.size, 2, fontsAll.size, 3, locales.size, DataState.entries.size, 3, 2, 2, profiles.size)
        val covered = HashSet<Int>()
        fun pairs(v: IntArray): List<Int> {
            val out = ArrayList<Int>(dims.size * dims.size / 2)
            for (i in dims.indices) for (j in i + 1 until dims.size) out += ((i * 16 + j) * 64 + v[i]) * 64 + v[j]
            return out
        }
        val result = mutableListOf<MatrixCase>()
        var stall = 0
        while (result.size < maxCases && stall < 20) {
            var best: IntArray? = null; var bestScore = -1
            repeat(40) {
                val v = IntArray(dims.size) { rng.nextInt(dims[it]) }
                val score = pairs(v).count { it !in covered }
                if (score > bestScore) { bestScore = score; best = v }
            }
            val v = best!!
            if (bestScore == 0) { stall++; continue }
            covered += pairs(v)
            val style = styleList[v[0]]; val kind = style.kind
            val look = looks[v[1]]
            val (bg, op) = bgs[v[3]]
            val l = if (bg == Bg.Transparent) LookSpec(look.theme, Bg.Transparent, 0, AccentOption.entries[v[4]], if (v[5] == 1) Corners.ExtraRound else Corners.System, true)
            else LookSpec(look.theme, bg, op, AccentOption.entries[v[4]], if (v[5] == 1) Corners.ExtraRound else Corners.System, look.onWallpaperLook)
            val (loc, rtl) = locales[v[8]]
            val env = Env(fontsAll[v[6]], loc, rtl, listOf(320, 420, 560)[v[7]], dark = v[2] == 1)
            val prof = profiles[v[13]]
            val cells = profileCells(kind, prof).ifEmpty { profileCells(kind, profiles[0]) }.ifEmpty { declaredCells(kind) }
            val cell = cells[rng.nextInt(cells.size)]
            val parts = Part.forKind(kind)
            val hidden = if (parts.isNotEmpty() && rng.nextInt(10) < 4) parts.filter { rng.nextBoolean() }.toSet() else emptySet()
            val tweaks = ConfigTweaks(
                hidden = hidden, units = UnitsPref.entries[v[10]], compactFooter = v[11] == 1, hideSyncIcon = v[12] == 1,
                metrics = if (kind == WidgetKind.Overview) overviewOrder.take(1 + rng.nextInt(6)) else null,
                weeklyGoal = if (kind == WidgetKind.WeeklyMileage) listOf(null, WeeklyGoal(30, app.cairn.core.model.DistanceUnit.Km), WeeklyGoal(20, app.cairn.core.model.DistanceUnit.Mi))[rng.nextInt(3)] else null,
            )
            val wp = if (l.bg == Bg.Transparent || l.opacity < 55) wallpapers3 else listOf(gray)
            result += case(kind, style, cell, env, l, DataState.entries[v[9]], tweaks, wp, Tier.Pairwise)
        }
        return result
    }

    /** T4: whole Responsive map per kind x style (parcel and bitmap size). Cases carry the largest registered size as their cell. */
    fun parcel(): List<MatrixCase> {
        val out = mutableListOf<MatrixCase>()
        val fonts = if (MatrixConfig.full) listOf(1.0f, 2.0f) else listOf(1.0f)
        for (kind in WidgetKind.entries) for (style in styles(kind, MatrixConfig.full)) for (fs in fonts) for (st in listOf(DataState.Full, DataState.Huge))
            out += case(kind, style, declaredCells(kind).maxBy { it.wDp * it.hDp }, Env(fs, "en-GB", false, 560, false), data = st, tier = Tier.Parcel)
        return out
    }

    /** Every case of the selected tiers, ordered so cases sharing a device environment are adjacent (cheap qualifier switches). */
    fun cases(parcel: Boolean = false): List<MatrixCase> {
        val all = if (parcel) parcel() else (core() + cells() + owner() + states() + looks() + pairwise())
        val (i, n) = MatrixConfig.shard
        return all.distinctBy { it.id }.filter { MatrixConfig.filter?.containsMatchIn(it.id) ?: true }.sortedBy { it.env.groupKey }.filterIndexed { idx, _ -> idx % n == i }
    }
}
