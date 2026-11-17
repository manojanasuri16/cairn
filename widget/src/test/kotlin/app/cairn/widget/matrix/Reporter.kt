package app.cairn.widget.matrix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File

/** One line of known-failures.txt: a case-id glob, an invariant id and an issue tag (audit id). */
data class KnownFailure(val glob: String, val rule: String, val issue: String) {
    private val regex = Regex(glob.split("*").joinToString(".*") { Regex.escape(it) })
    fun matches(caseId: String, rule: String) = this.rule == rule && regex.matches(caseId)
    fun matchesCase(caseId: String) = regex.matches(caseId)
}

object KnownFailures {
    fun parse(text: String): List<KnownFailure> = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { line ->
        val p = line.split(Regex("\\s+"), limit = 3)
        if (p.size >= 2) KnownFailure(p[0], p[1], p.getOrElse(2) { "" }) else null
    }

    fun load(): List<KnownFailure> {
        val res = KnownFailures::class.java.getResourceAsStream("/known-failures.txt")
        return if (res == null) emptyList() else parse(res.bufferedReader().readText())
    }
}

data class CaseResult(val case: MatrixCase, val violations: List<Violation>, val ms: Long, val png: String?)

/** Collects results, writes PNGs, failures.csv and the summary, and decides what is a real failure (not in known-failures.txt). */
class Reporter(private val known: List<KnownFailure>, private val dir: File) {
    val results = ArrayList<CaseResult>()
    private val pngPerGroup = HashMap<String, Int>()
    var pngCount = 0; private set

    init { dir.mkdirs(); dir.listFiles { f -> f.extension == "png" }?.forEach { it.delete() } }

    fun isKnown(caseId: String, rule: String) = known.any { it.matches(caseId, rule) }

    /** Dumps a PNG for a failing case when it is new, or among the first few of its (rule, kind.style) group. */
    fun record(r: RenderedWidget, violations: List<Violation>, ms: Long, dumpAll: Boolean): CaseResult {
        var png: String? = null
        if (violations.isNotEmpty() || dumpAll) {
            val unlisted = violations.any { !isKnown(r.case.id, it.rule) }
            val key = violations.firstOrNull()?.rule + "|" + r.case.kind + "." + r.case.style
            val n = pngPerGroup.merge(key, 1, Int::plus)!!
            if (dumpAll || (unlisted && pngCount < 300) || n <= 3) png = writePng(r, violations)
        }
        return CaseResult(r.case, violations, ms, png).also { results += it }
    }

    private fun writePng(r: RenderedWidget, violations: List<Violation>): String? {
        val root = r.root ?: return null
        return try {
            val wp = r.case.wallpapers.firstOrNull() ?: 0xFF808080.toInt()
            val bmp = r.bitmap(wp).copy(Bitmap.Config.ARGB_8888, true)
            val c = Canvas(bmp)
            val paint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f * r.density }
            violations.mapNotNull { it.rect }.forEach { c.drawRect(it, paint) }
            val label = Paint().apply { color = Color.RED; textSize = 9f * r.density; isAntiAlias = true }
            violations.map { it.rule }.distinct().forEachIndexed { i, s -> c.drawText(s, 2f, (i + 1) * 10f * r.density, label) }
            val f = File(dir, r.case.id + ".png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            pngCount++
            f.absolutePath
        } catch (t: Throwable) { null }
    }

    fun unlisted(): List<Pair<CaseResult, Violation>> = results.flatMap { r -> r.violations.filter { !isKnown(r.case.id, it.rule) }.map { r to it } }
    fun listed(): List<Pair<CaseResult, Violation>> = results.flatMap { r -> r.violations.filter { isKnown(r.case.id, it.rule) }.map { r to it } }

    /** Known failures whose glob matches a case that ran but no longer violates the rule. */
    fun nowFixed(): List<KnownFailure> = known.filter { k -> results.any { r -> k.matchesCase(r.case.id) } && results.none { r -> r.violations.any { k.matches(r.case.id, it.rule) } } }

    fun p95(): Long = results.map { it.ms }.sorted().let { if (it.isEmpty()) 0 else it[(it.size * 95 / 100).coerceAtMost(it.size - 1)] }

    fun write(label: String, totalMs: Long) {
        File(dir, "failures.csv").bufferedWriter().use { w ->
            w.write("caseId,rule,detail,png\n")
            for (r in results) for (v in r.violations) w.write("${r.case.id},${v.rule},\"${v.detail.replace("\"", "'")}\",${r.png ?: ""}\n")
        }
        val all = results.flatMap { r -> r.violations.map { r to it } }
        File(dir, "summary.txt").bufferedWriter().use { w ->
            w.write("matrix[$label] ${results.size} cases, ${results.count { it.violations.isNotEmpty() }} failing cases, ${all.size} violations, ${unlisted().size} unlisted, p95 ${p95()} ms, total ${totalMs / 1000} s\n\n")
            w.write("By rule (cases failing):\n")
            all.groupBy { it.second.rule }.toSortedMap().forEach { (rule, l) -> w.write("  $rule: ${l.map { it.first.case.id }.distinct().size} cases\n") }
            w.write("\nBy rule and widget:\n")
            all.groupBy { it.second.rule to it.first.case.kind }.toSortedMap(compareBy({ it.first }, { it.second.name })).forEach { (k, l) ->
                val ids = l.map { it.first }.distinctBy { it.case.id }
                w.write("  ${k.first} ${k.second}: ${ids.size} cases; e.g. ${ids.first().case.id}\n      ${l.first().second.detail}\n      png ${ids.firstOrNull { it.png != null }?.png ?: "-"}\n")
            }
            w.write("\nBy tier (failing cases / cases):\n")
            results.groupBy { it.case.tier }.forEach { (t, l) -> w.write("  $t: ${l.count { it.violations.isNotEmpty() }} / ${l.size}\n") }
        }
        // Grouped patterns, to paste into known-failures.txt: rule x kind.style x font scale.
        File(dir, "suggested-known-failures.txt").bufferedWriter().use { w ->
            all.map { (r, v) -> Triple(v.rule, "${r.case.kind}.${r.case.style}", r.case.env.fontScale) }.distinct().sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
                .forEach { (rule, ks, fs) -> w.write("$ks@*_fs$fs-*  $rule  TODO\n") }
        }
    }
}
