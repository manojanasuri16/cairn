package app.cairn.widget.matrix

import android.graphics.Rect
import app.cairn.core.designsystem.Contrast
import app.cairn.widget.FooterRules
import app.cairn.widget.Layouts
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetSizes
import app.cairn.widget.WidgetStyle
import app.cairn.widget.WidgetUi
import app.cairn.widget.OverviewId

data class Violation(val rule: String, val detail: String, val rect: Rect? = null)

fun interface Invariant { fun check(r: RenderedWidget): List<Violation> }

/** Style-independent rules of docs/widget-quality.md 2.6. Ids are used in failure output and in known-failures.txt. */
object Invariants {
    const val PARCEL_LIMIT = 120_000
    const val BITMAP_LIMIT = 7_800_000L
    const val MIN_TARGET_DP = 40f
    const val MIN_HERO_EFFECTIVE_DP = 14f
    const val DEAD_BAND_PERCENT = 40
    const val RENDER_BUDGET_MS = 2_000L

    private fun f(x: Float) = String.format(java.util.Locale.ROOT, "%.1f", x)

    private fun isValue(r: RenderedWidget, n: TextNode): Boolean = r.expected.values.any { v -> v.isNotEmpty() && (n.text == v || n.text.contains(v)) }

    val all: List<Pair<String, Invariant>> = listOf(
        "I1.noThrow" to Invariant { r ->
            val e = r.error
            when {
                e != null -> listOf(Violation("I1.noThrow", "render threw ${e::class.java.simpleName}"))
                r.root != null && glanceError(r.root) -> listOf(Violation("I1.noThrow", "Glance error layout was chosen"))
                else -> emptyList()
            }
        },
        "I2.footer" to Invariant { r ->
            if (r.error != null || !r.expected.isContent) return@Invariant emptyList()
            val d = r.density
            val footer = r.clickables.firstOrNull { it.description?.let { s -> s.contains("Opens Cairn") || s.contains("Opens COROS") } == true }
                ?: return@Invariant listOf(Violation("I2.footer", "no footer view (Opens Cairn / Opens COROS) in the tree"))
            val out = mutableListOf<Violation>()
            if (footer.rect.height() / d < 24f) out += Violation("I2.footer", "footer ${f(footer.rect.height() / d)} dp high, needs >= 24", footer.rect)
            if (footer.rect.left < -1 || footer.rect.right > r.cellPx.first + 1 || footer.rect.bottom > r.cellPx.second + 1) out += Violation("I2.footer", "footer outside the root", footer.rect)
            val needsLabel = r.case.data == DataState.Syncing || r.case.data == DataState.SyncError || (r.expected.tier != null && r.expected.tier != app.cairn.core.model.Freshness.Fresh)
            if (needsLabel && r.texts.none { Rect.intersects(it.rect, footer.rect) && it.rect.top >= footer.rect.top - 2 })
                out += Violation("I2.footer", "age/sync label missing (state ${r.case.data}, tier ${r.expected.tier})", footer.rect)
            out
        },
        "I3.syncTarget" to Invariant { r ->
            if (r.error != null || !r.expected.isContent) return@Invariant emptyList()
            val d = r.density
            val (declW, declH) = r.declared ?: (0 to 0)
            val ew = declW + WidgetSizes.MARGIN_DP; val eh = declH + WidgetSizes.MARGIN_DP
            val allowedHidden = !FooterRules.showSyncIcon(r.case.tweaks.hideSyncIcon, ew, eh)
            val sync = r.clickables.firstOrNull { it.description?.let { s -> s.startsWith("Sync now") || s.startsWith("Syncing") } == true }
            when {
                sync == null && allowedHidden -> emptyList()
                sync == null -> listOf(Violation("I3.syncTarget", "no sync button although the hide rule does not apply"))
                allowedHidden -> emptyList()
                sync.rect.width() / d < MIN_TARGET_DP - 0.5f || sync.rect.height() / d < (if (eh < FooterRules.STRIP_H) FooterRules.STRIP_HEIGHT_DP else MIN_TARGET_DP) - 0.5f ->
                    listOf(Violation("I3.syncTarget", "sync target ${f(sync.rect.width() / d)} x ${f(sync.rect.height() / d)} dp, needs >= 40 x 40", sync.rect))
                else -> emptyList()
            }
        },
        "I4.textFits" to Invariant { r ->
            val out = mutableListOf<Violation>()
            val d = r.density
            for (n in r.texts) {
                val rect = n.rect
                if (n.view.width <= 0 || n.view.height <= 0) { out += Violation("I4.textFits", "\"${shown(n)}\" has a ${n.view.width} x ${n.view.height} px text view", rect); continue }
                val value = isValue(r, n)
                if (value && n.ellipsized) out += Violation("I4.textFits", "value \"${shown(n)}\" is ellipsized", rect)
                if (value && n.neededWidthPx > n.availWidthPx + 1f && n.maxLines == 1)
                    out += Violation("I4.textFits", "value \"${shown(n)}\" needs ${f(n.neededWidthPx / d)} dp, has ${f(n.availWidthPx / d)} dp", rect)
                if (value && n.neededLines > n.maxLines) out += Violation("I4.textFits", "value \"${shown(n)}\" needs ${n.neededLines} lines, max ${n.maxLines}", rect)
                if (n.neededHeightPx > n.viewHeightPx + r.density)
                    out += Violation("I4.textFits", "\"${shown(n)}\" needs ${f(n.neededHeightPx / d)} dp of height, has ${f(n.viewHeightPx / d)} dp (clipped)", rect)
                // A label may shorten but never to a stub such as "S..."
                if (!value && n.ellipsized && n.text.length > 3 && n.availWidthPx < 2.5f * n.textSizePx)
                    out += Violation("I4.textFits", "label \"${shown(n)}\" ellipsized to a stub (${f(n.availWidthPx / d)} dp wide)", rect)
            }
            out
        },
        "I5.noOverlap" to Invariant { r ->
            val out = mutableListOf<Violation>()
            val t = r.texts
            for (i in t.indices) for (j in i + 1 until t.size) {
                val a = t[i].ink; val b = t[j].ink
                val ix = minOf(a.right, b.right) - maxOf(a.left, b.left); val iy = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                if (ix > 2 && iy > 2) out += Violation("I5.noOverlap", "\"${shown(t[i])}\" overlaps \"${shown(t[j])}\" by ${f(ix / r.density)} x ${f(iy / r.density)} dp", Rect(a).also { it.union(b) })
            }
            out
        },
        "I6.inBounds" to Invariant { r ->
            val out = mutableListOf<Violation>()
            val root = Rect(0, 0, r.cellPx.first, r.cellPx.second)
            for ((v, rect, parent) in r.allRects) {
                if (rect.width() <= 0 || rect.height() <= 0) continue
                val tol = 2
                if (rect.left < root.left - tol || rect.top < root.top - tol || rect.right > root.right + tol || rect.bottom > root.bottom + tol)
                    out += Violation("I6.inBounds", "${v.javaClass.simpleName} ${f(rect.width() / r.density)} x ${f(rect.height() / r.density)} dp leaves the widget", rect)
                else if (rect.left < parent.left - tol || rect.top < parent.top - tol || rect.right > parent.right + tol || rect.bottom > parent.bottom + tol)
                    out += Violation("I6.inBounds", "${v.javaClass.simpleName} exceeds its parent by ${f(maxOf(parent.left - rect.left, parent.top - rect.top, rect.right - parent.right, rect.bottom - parent.bottom) / r.density)} dp", rect)
            }
            out.distinctBy { it.detail }.take(6)
        },
        "I7.hero" to Invariant { r ->
            val hero = r.expected.hero
            if (r.error != null || !r.expected.isContent || hero == null) return@Invariant emptyList()
            val d = r.density
            val node = r.texts.filter { it.text == hero }.maxByOrNull { it.textSizePx }
                ?: r.texts.filter { it.text.contains(hero) }.maxByOrNull { it.textSizePx }
                ?: return@Invariant listOf(Violation("I7.hero", "hero \"$hero\" is not rendered"))
            val out = mutableListOf<Violation>()
            if (node.ellipsized || node.neededWidthPx > node.availWidthPx + 1f) out += Violation("I7.hero", "hero \"$hero\" needs ${f(node.neededWidthPx / d)} dp, has ${f(node.availWidthPx / d)} dp", node.rect)
            val minHero = if (r.case.kind == WidgetKind.Overview) app.cairn.widget.OverviewLayout.TILE_MIN_SP else MIN_HERO_EFFECTIVE_DP
            if (node.textSizePx / d < minHero - 0.5f) out += Violation("I7.hero", "hero \"$hero\" drawn at ${f(node.textSizePx / d)} dp, minimum $minHero", node.rect)
            // A numeral inside a ring must sit inside the ring's inner circle (A20).
            val ring = r.images.filter { !it.fillsRoot && Math.abs(it.rect.width() - it.rect.height()) <= 2 && it.rect.width() / d in 40f..220f && it.rect.contains(node.rect.centerX(), node.rect.centerY()) }
                .minByOrNull { it.rect.width() }
            if (ring != null) {
                val ringDp = ring.rect.width() / d
                val inner = ringDp - 2 * Layouts.ringStroke(ringDp.toInt())
                if (node.neededWidthPx / d > inner) out += Violation("I7.hero", "ring numeral \"$hero\" needs ${f(node.neededWidthPx / d)} dp, ring interior is ${f(inner)} dp", node.rect)
            }
            out
        },
        "I8.plannedRendered" to Invariant { r ->
            val p = r.plan ?: return@Invariant emptyList()
            if (r.error != null || !r.expected.isContent) return@Invariant emptyList()
            val ui = r.ui as? WidgetUi.Content<*> ?: return@Invariant emptyList()
            val cfg = r.case.config().resolve(r.case.kind, app.cairn.widget.AppAppearance())
            // A value equal to the hero (steps "0" and stress "0") cannot be told apart from it, so such values are not counted.
            val heroText = r.expected.hero
            fun rendered(values: List<String>, alt: (String) -> String = { it }) = values.filter { it != heroText }.sumOf { v -> r.texts.count { it.text == v || it.text == alt(v) } }
            when (r.case.kind to p.style) {
                WidgetKind.Today to WidgetStyle.TodayRing, WidgetKind.Today to WidgetStyle.TodayStack -> {
                    val shown = (ui.body as app.cairn.widget.TodayUi).metrics.filter { cfg.shows(app.cairn.widget.Part.ofMetric(it.id)) }.map { it.value }
                    val want = minOf(p.count("r"), shown.filter { it != heroText }.size)
                    val got = rendered(shown, { app.cairn.widget.stripeValue(it) })
                    if (got != want) listOf(Violation("I8.plannedRendered", "plan has ${p.count("r")} rows for ${shown.size} metrics, $got rendered, expected $want")) else emptyList()
                }
                WidgetKind.Today to WidgetStyle.TodayStripe -> {
                    val b = ui.body as app.cairn.widget.TodayUi
                    val shown = b.metrics.filter { cfg.shows(app.cairn.widget.Part.ofMetric(it.id)) }.map { it.value }
                    val all = listOf(b.stepsText ?: "–") + shown
                    val want = minOf(p.count("c"), all.size)
                    val got = rendered(all.take(want), { app.cairn.widget.stripeValue(it) })
                    if (got < want) listOf(Violation("I8.plannedRendered", "plan has ${p.count("c")} chips, $got of $want values rendered")) else emptyList()
                }
                WidgetKind.LastActivity to WidgetStyle.ActivityGrid -> {
                    val want = minOf(p.count("t"), r.expected.tileValues.size)
                    val got = rendered(r.expected.tileValues)
                    if (got != want) listOf(Violation("I8.plannedRendered", "plan has ${p.count("t")} tiles for ${r.expected.tileValues.size} stats, $got rendered, expected $want")) else emptyList()
                }
                WidgetKind.Fitness to WidgetStyle.FitnessGrid -> {
                    val want = minOf(p.count("p"), r.expected.raceTimes.size)
                    val got = rendered(r.expected.raceTimes)
                    if (got != want) listOf(Violation("I8.plannedRendered", "plan has ${p.count("p")} tiles for ${r.expected.raceTimes.size} predictions, $got rendered, expected $want")) else emptyList()
                }
                else -> emptyList()
            } + overviewCount(r)
        },
        "I9.contrast" to Invariant { r ->
            if (r.error != null || r.case.wallpapers.isEmpty() || r.root == null) return@Invariant emptyList()
            val out = mutableListOf<Violation>()
            val seen = HashSet<String>()
            for (wp in r.case.wallpapers) {
                val bg = r.backgroundWithoutText(wp)
                for (n in r.texts) {
                    if (n.text.isBlank()) continue
                    val worst = worstContrast(n, bg)
                    val dp = n.textSizePx / r.density
                    val large = dp >= 24f || (n.bold && dp >= 18.66f)
                    val need = if (large) 3.0 else 4.5
                    if (worst.first < need && seen.add("${n.text}|${wp}"))
                        out += Violation("I9.contrast", "\"${shown(n)}\" ${String.format(java.util.Locale.ROOT, "%.2f", worst.first)}:1 (needs $need) text ${hex(worst.second)} on ${hex(worst.third)} over wallpaper ${hex(wp)}", n.rect)
                }
            }
            out.take(8)
        },
        "I16.chartBounds" to Invariant { r ->
            // Chart images (bars, markers, gauges) must stay inside their parent and must not be far taller than the row they sit in.
            r.allRects.filter { (v, rect, parent) -> v is android.widget.ImageView && rect.width() > 0 && rect.height() > 0 && !(rect.width() >= r.cellPx.first * 0.9f && rect.height() >= r.cellPx.second * 0.9f) &&
                (rect.left < parent.left - 2 || rect.right > parent.right + 2 || rect.top < parent.top - 2 || rect.bottom > parent.bottom + 2) }
                .take(3).map { (_, rect, parent) -> Violation("I16.chartBounds", "chart image ${f(rect.width() / r.density)} x ${f(rect.height() / r.density)} dp sticks out of its ${f(parent.width() / r.density)} x ${f(parent.height() / r.density)} dp parent", rect) }
        },
        "I17.bodyNotEmpty" to Invariant { r ->
            if (r.error != null || !r.expected.isContent) return@Invariant emptyList()
            val visible = r.texts.count { it.view.width > 0 && it.view.height > 0 && it.ink.left < r.cellPx.first && it.ink.right > 0 }
            if (visible == 0) listOf(Violation("I17.bodyNotEmpty", "no visible text in the body (empty box)")) else emptyList()
        },
        "I10.fillToBox" to Invariant { r ->
            if (r.error != null || !r.expected.isContent || r.root == null) return@Invariant emptyList()
            val d = r.density
            // The size lists stop where a layout rule stops changing (a Today of 200 dp is already the wide layout), so the stretch zone starts at least at a 4x3 cell.
            val biggest = WidgetSizes.byKind.getValue(r.case.kind).let { l -> maxOf(l.maxOf { it.first }, 290) to maxOf(l.maxOf { it.second }, 220) }
            if (r.case.cell.wDp > biggest.first + 40f || r.case.cell.hDp > biggest.second + 40f) return@Invariant emptyList() // A24: stretch is expected
            val bodyH = r.cellPx.second - (FooterRules.heightDp(r.cellPx.second / d) * d).toInt()
            if (bodyH <= 0) return@Invariant emptyList()
            val covered = (r.texts.map { it.ink } + r.images.filter { !it.fillsRoot }.map { it.rect }).filter { it.height() > 0 && it.width() > 0 }
            val intervals = covered.map { maxOf(0, it.top) to minOf(bodyH, it.bottom) }.filter { it.second > it.first }.sortedBy { it.first }
            var cursor = 0; var worst = 0
            for ((s, e) in intervals) { if (s > cursor) worst = maxOf(worst, s - cursor); cursor = maxOf(cursor, e) }
            worst = maxOf(worst, bodyH - cursor)
            val pct = 100 * worst / bodyH
            if (pct > DEAD_BAND_PERCENT && worst / d > 30f) listOf(Violation("I10.fillToBox", "dead band $pct % of the body (${f(worst / d)} of ${f(bodyH / d)} dp)")) else emptyList()
        },
        "I11.parcel" to Invariant { r ->
            if (r.error == null && r.parcelBytes > PARCEL_LIMIT) listOf(Violation("I11.parcel", "RemoteViews parcel ${r.parcelBytes} bytes, limit $PARCEL_LIMIT")) else emptyList()
        },
        "I11b.bitmaps" to Invariant { r ->
            if (r.bitmapBytes > BITMAP_LIMIT) listOf(Violation("I11.parcel", "bitmaps ${r.bitmapBytes / 1024} KB, limit ${BITMAP_LIMIT / 1024} KB")) else emptyList()
        },
        "I12.time" to Invariant { r ->
            if (r.renderMs > RENDER_BUDGET_MS) listOf(Violation("I12.time", "render took ${r.renderMs} ms, budget $RENDER_BUDGET_MS")) else emptyList()
        },
        "I13.taps" to Invariant { r ->
            if (r.error != null || !r.expected.isContent) return@Invariant emptyList()
            val d = r.density
            val body = r.clickables.filter { c -> c.description?.let { it.startsWith("Sync") || it.contains("Opens C") } != true }.maxByOrNull { it.rect.width().toLong() * it.rect.height() }
                ?: return@Invariant listOf(Violation("I13.taps", "no body tap target"))
            val bodyArea = r.cellPx.first.toLong() * (r.cellPx.second - FooterRules.heightDp(r.cellPx.second / d) * d)
            val cover = body.rect.width().toLong() * body.rect.height() / bodyArea.toFloat()
            if (cover < 0.6f) listOf(Violation("I13.taps", "body tap covers ${(cover * 100).toInt()} % of the body, needs >= 60", body.rect)) else emptyList()
        },
        "I14.a11y" to Invariant { r ->
            if (r.error != null || !r.expected.isContent) return@Invariant emptyList()
            val out = mutableListOf<Violation>()
            val desc = r.expected.description
            val root = r.allRects.map { it.first }.firstOrNull { it.contentDescription?.toString() == desc }
            if (desc.isNullOrBlank() || root == null) out += Violation("I14.a11y", "no view carries the widget's contentDescription")
            else r.expected.hero?.let { h -> if (h != "\u2013" && !desc.contains(h)) out += Violation("I14.a11y", "contentDescription lacks the hero value") }
            return@Invariant out
        },
        "I15.rtl" to Invariant { r ->
            if (!r.case.env.rtl || r.error != null) return@Invariant emptyList()
            val letters = r.expected.letters
            if (letters.size < 5) return@Invariant emptyList()
            val singles = r.texts.filter { it.text.length == 1 && it.text in letters }
            val rowCandidates = singles.groupBy { it.rect.centerY() / 6 }.values.filter { it.size == letters.size }
            val row = rowCandidates.firstOrNull() ?: return@Invariant emptyList()
            val order = row.sortedBy { it.rect.left }.map { it.text }
            if (order != letters) listOf(Violation("I15.rtl", "day letters read ${order.joinToString("")} left to right but the bars run ${letters.joinToString("")} (letters mirror, bars do not)")) else emptyList()
        },
    )

    fun check(r: RenderedWidget): List<Violation> {
        val out = mutableListOf<Violation>()
        for ((id, inv) in all) {
            try { out += inv.check(r) } catch (t: Throwable) { out += Violation(id, "invariant crashed: ${t::class.java.simpleName}") }
        }
        return out
    }

    private fun overviewCount(r: RenderedWidget): List<Violation> {
        val shown = r.overviewShown ?: return emptyList()
        val vals = r.expected.values
        if (r.case.kind != WidgetKind.Overview) return emptyList()
        val labels = OverviewId.entries.flatMap { listOf(it.label, app.cairn.widget.Layouts.shortLabel(it.label)) }.toSet()
        val got = r.texts.count { it.text in labels }
        return if (got != shown && vals.isNotEmpty()) listOf(Violation("I8.plannedRendered", "Overview plans $shown tiles, $got labels rendered")) else emptyList()
    }

    private fun glanceError(root: android.view.View): Boolean {
        fun walk(v: android.view.View): Boolean {
            val name = if (v.id != android.view.View.NO_ID) runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull() else null
            if (name != null && name.contains("error", ignoreCase = true) && name.contains("glance", ignoreCase = true)) return true
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) if (walk(v.getChildAt(i))) return true
            return false
        }
        return walk(root)
    }

    private fun shown(n: TextNode) = n.text.take(24)
    private fun hex(c: Int) = String.format("#%08X", c)

    /** Worst WCAG ratio of [n]'s colour (blended over the sampled pixel) over a 5 x 3 grid of its ink rect. */
    internal fun worstContrast(n: TextNode, bg: android.graphics.Bitmap): Triple<Double, Int, Int> {
        var worst = Triple(99.0, n.color, 0)
        val rect = Rect(n.ink)
        if (!rect.intersect(0, 0, bg.width, bg.height)) return worst
        for (gx in 0 until 5) for (gy in 0 until 3) {
            val x = (rect.left + (rect.width() - 1) * (gx + 0.5f) / 5f).toInt().coerceIn(0, bg.width - 1)
            val y = (rect.top + (rect.height() - 1) * (gy + 0.5f) / 3f).toInt().coerceIn(0, bg.height - 1)
            val px = bg.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(n.color) / 255f
            val fg = Contrast.blend(n.color or (0xFF shl 24), px or (0xFF shl 24), alpha)
            val ratio = Contrast.ratio(fg, px or (0xFF shl 24))
            if (ratio < worst.first) worst = Triple(ratio, n.color, px)
        }
        return worst
    }
}
