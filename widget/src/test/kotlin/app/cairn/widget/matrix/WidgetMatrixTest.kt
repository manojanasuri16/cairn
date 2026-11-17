package app.cairn.widget.matrix

import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import app.cairn.widget.TextFit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget matrix (docs/widget-quality.md section 2): renders cases through the real GlanceRemoteViews at the exact launcher cell size,
 * lays them out, and checks the invariants. Smoke runs in the normal unit-test task; `-PwidgetMatrix=full` widens it; `-PwidgetMatrix=off` skips it.
 * A failure fails the test unless it is listed in src/test/resources/known-failures.txt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetMatrixTest {
    /** Native fonts must be loaded, or every text-width rule would give false passes. */
    private fun calibrate() {
        WidgetRenderer.applyEnv(Env(1.0f, "en-GB", false, 320, false))
        val d = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val paint = Paint().apply { textSize = 10f * d }
        val dp = paint.measureText("0000000000") / d
        val est = TextFit.widthDp("0000000000", 10f, 1f)
        assertTrue("harness not calibrated: native font measures $dp dp, estimate $est dp", dp > 30f && dp in est * 0.6f..est * 1.2f)
    }

    private fun run(label: String, cases: List<MatrixCase>, render: (MatrixCase) -> Pair<RenderedWidget, List<Violation>>) {
        val known = KnownFailures.load()
        val rep = Reporter(known, MatrixConfig.reportDir)
        val t0 = System.currentTimeMillis()
        for (c in cases) {
            val s = System.nanoTime()
            val (r, v) = render(c)
            rep.record(r, v, (System.nanoTime() - s) / 1_000_000, MatrixConfig.dumpAll)
        }
        val total = System.currentTimeMillis() - t0
        rep.write(label, total)
        val unlisted = rep.unlisted()
        val line = "matrix[$label] ${cases.size} cases, ${rep.results.count { it.violations.isNotEmpty() }} failing, ${rep.listed().size} known violations, ${unlisted.size} unlisted, p95 ${rep.p95()} ms, total ${total / 1000} s, report ${MatrixConfig.reportDir}"
        println(line)
        rep.nowFixed().forEach { println("NOTE now fixed, remove from known-failures: ${it.glob} ${it.rule} ${it.issue}") }
        if (unlisted.isNotEmpty()) {
            val msg = unlisted.take(50).joinToString("\n") { (r, v) -> "FAIL ${v.rule} ${r.case.id}: ${v.detail} -> ${r.png ?: "(no png)"}" }
            throw AssertionError("$line\n$msg" + if (unlisted.size > 50) "\n... ${unlisted.size - 50} more, see ${MatrixConfig.reportDir}/failures.csv" else "")
        }
    }

    @Test fun matrix() {
        assumeFalse(MatrixConfig.off)
        calibrate()
        val cases = WidgetMatrix.cases()
        run(MatrixConfig.mode, cases) { c ->
            val r = WidgetRenderer.renderCase(c)
            r to Invariants.check(r)
        }
    }

    @Test fun parcelOfTheWholeResponsiveMap() {
        assumeFalse(MatrixConfig.off)
        calibrate()
        val cases = WidgetMatrix.cases(parcel = true)
        run(MatrixConfig.mode + "-parcel", cases) { c ->
            val (bytes, bitmaps) = WidgetRenderer.renderResponsiveMap(c)
            val v = mutableListOf<Violation>()
            if (bytes > 400_000) v += Violation("I11.parcel", "responsive map parcel $bytes bytes, limit 400000")
            if (bitmaps > 7_800_000L) v += Violation("I11.parcel", "responsive map bitmaps $bitmaps bytes, limit 7800000")
            WidgetRenderer.stub(c, bytes, bitmaps) to v
        }
    }
}
