package app.cairn.widget.matrix

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.model.Freshness
import app.cairn.widget.FooterRules
import app.cairn.widget.FooterUi
import app.cairn.widget.Layouts
import app.cairn.widget.Metric
import app.cairn.widget.MetricId
import app.cairn.widget.Screen
import app.cairn.widget.TodayUi
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetStyle
import app.cairn.widget.WidgetUi
import app.cairn.core.designsystem.SizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each invariant must fire on a deliberately broken fake view tree, and stay quiet on a good one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvariantsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val W = 300; private val H = 240

    private fun case(env: Env = Env(1.0f, "en-GB", false, 320, false), wallpapers: List<Int> = emptyList(), hide: Boolean = false, data: DataState = DataState.Full) =
        MatrixCase(WidgetKind.Today, WidgetStyle.TodayRing, Cell(W.toFloat(), H.toFloat(), "fake"), env, LookSpec(app.cairn.core.designsystem.ThemeId.Clean, app.cairn.widget.Bg.Gradient, 100),
            data, ConfigTweaks(hideSyncIcon = hide), wallpapers, Tier.Core)

    private fun tv(text: String, px: Float, x: Int, y: Int, w: Int, h: Int, color: Int = Color.BLACK, lines: Int = 1, bold: Boolean = false) = TextView(ctx).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_PX, px); setTextColor(color); maxLines = lines; setIncludeFontPadding(false)
        if (bold) setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        layoutParams = FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.START).also { it.leftMargin = x; it.topMargin = y }
    }

    private fun clickable(desc: String?, x: Int, y: Int, w: Int, h: Int) = View(ctx).apply {
        contentDescription = desc; setOnClickListener { }
        layoutParams = FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.START).also { it.leftMargin = x; it.topMargin = y }
    }

    private fun density() = ctx.resources.displayMetrics.density

    private fun render(
        c: MatrixCase = case(), expected: Expected = Expected(true, "8,432", listOf("8,432"), description = "Today. 8,432"),
        plan: Layouts.Plan? = null, ui: WidgetUi<*>? = null, parcel: Int = 1000, ms: Long = 5, build: FrameLayout.() -> Unit,
    ): RenderedWidget {
        val root = FrameLayout(ctx).also { it.setBackgroundColor(Color.rgb(0x88, 0x88, 0x88)) }
        root.build()
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, W, H)
        return WidgetRenderer.inspect(c, root, W, H, density(), expected, plan, 250 to 110, parcel, ui, ms)
    }

    private fun FrameLayout.good() {
        val d = density()
        addView(clickable("Today. 8,432", 0, 0, W, H - (FooterRules.HEIGHT_DP * d).toInt()).also { it.contentDescription = "Today. 8,432" })
        addView(tv("8,432", 40f, 10, 10, 200, 60))
        addView(tv("Steps", 20f, 10, 90, 200, 40))
        addView(tv("52 bpm", 20f, 10, 140, 200, 40))
        addView(clickable("Updated 10 min ago. Opens Cairn", 0, H - (FooterRules.HEIGHT_DP * d).toInt(), 250, (FooterRules.HEIGHT_DP * d).toInt()))
        addView(clickable("Sync now. Updated", W - (48 * d).toInt(), H - (48 * d).toInt(), (48 * d).toInt(), (48 * d).toInt()))
    }

    private fun rules(r: RenderedWidget) = Invariants.check(r).map { it.rule }.toSet()

    @Test fun aGoodTreeHasNoViolationsInTheStructuralRules() {
        val v = Invariants.check(render { good() }).filter { it.rule in setOf("I1.noThrow", "I2.footer", "I3.syncTarget", "I4.textFits", "I5.noOverlap", "I6.inBounds", "I7.hero", "I11.parcel", "I12.time", "I14.a11y") }
        assertEquals(v.toString(), emptyList<Violation>(), v)
    }

    @Test fun i1ExceptionIsReported() {
        val r = WidgetRenderer.stub(case(), 0, 0, IllegalStateException("x"))
        assertTrue(rules(r).contains("I1.noThrow"))
    }

    @Test fun i2MissingFooterIsReported() {
        val r = render { addView(tv("8,432", 40f, 10, 10, 200, 60)) }
        assertTrue(rules(r).contains("I2.footer"))
    }

    @Test fun i2AgeLabelMissingWhenStale() {
        val r = render(expected = Expected(true, "8,432", listOf("8,432"), tier = Freshness.Stale, description = "Today. 8,432")) { good() }
        assertTrue(Invariants.check(r).any { it.rule == "I2.footer" && it.detail.contains("label") })
    }

    @Test fun i3SyncTargetTooSmallIsReported() {
        val d = density()
        val small = render { addView(clickable("Sync now. x", 0, 0, (40 * d).toInt(), (28 * d).toInt())) }
        assertTrue(Invariants.check(small).toString(), Invariants.check(small).any { it.rule == "I3.syncTarget" && it.detail.contains("40.0 x 28.0") })
        val big = render { good() }
        assertTrue(Invariants.check(big).none { it.rule == "I3.syncTarget" })
    }

    @Test fun i3MissingSyncButtonIsReported() {
        val r = render { addView(tv("8,432", 40f, 10, 10, 200, 60)) }
        assertTrue(Invariants.check(r).any { it.rule == "I3.syncTarget" })
    }

    @Test fun i4ValueTooWideIsReported() {
        val r = render(expected = Expected(true, "8,432", listOf("1234567890123456789"), description = "x")) { addView(tv("1234567890123456789", 30f, 0, 0, 80, 60)) }
        assertTrue(Invariants.check(r).any { it.rule == "I4.textFits" && it.detail.contains("needs") })
    }

    @Test fun i4ClippedHeightIsReported() {
        val r = render { addView(tv("52 bpm", 60f, 0, 0, 300, 20)) }
        assertTrue(Invariants.check(r).any { it.rule == "I4.textFits" && it.detail.contains("clipped") })
    }

    @Test fun i4ZeroSizeTextIsReported() {
        val r = render { addView(tv("52 bpm", 20f, 0, 0, 0, 0)) }
        assertTrue(Invariants.check(r).any { it.rule == "I4.textFits" })
    }

    @Test fun i5OverlappingTextIsReported() {
        val r = render { addView(tv("8,432", 40f, 10, 10, 200, 60)); addView(tv("52 bpm", 40f, 20, 30, 200, 60)) }
        assertTrue(Invariants.check(r).any { it.rule == "I5.noOverlap" })
    }

    @Test fun i6ChildOutsideParentIsReported() {
        val r = render { addView(tv("8,432", 20f, 380, 10, 200, 40)) }
        assertTrue(Invariants.check(r).any { it.rule == "I6.inBounds" })
    }

    @Test fun i7HeroMissingIsReported() {
        val r = render { addView(tv("Steps", 20f, 10, 90, 200, 40)) }
        assertTrue(Invariants.check(r).any { it.rule == "I7.hero" && it.detail.contains("not rendered") })
    }

    @Test fun i7HeroTooSmallIsReported() {
        val r = render { addView(tv("8,432", 8f, 10, 10, 200, 40)) }
        assertTrue(Invariants.check(r).any { it.rule == "I7.hero" && it.detail.contains("minimum") })
    }

    @Test fun i8PlannedRowsNotRenderedIsReported() {
        val body = TodayUi("8,432", 0.8f, listOf(Metric(MetricId.Sleep, "Sleep", "7 h 41 min"), Metric(MetricId.RestingHr, "Resting HR", "52 bpm"), Metric(MetricId.Recovery, "Recovery", "82%")))
        val ui = WidgetUi.Content(body, FooterUi("Updated 5m ago", Freshness.Fresh), "Today", Screen.Today)
        val plan = Layouts.Plan(WidgetStyle.TodayRing, SizeClass.Small, 12, false, 80, 20f, setOf("r1", "r2", "r3"), 1, 100f, 100f)
        val r = render(plan = plan, ui = ui) { addView(tv("52 bpm", 20f, 10, 90, 200, 40)) }
        assertTrue(Invariants.check(r).any { it.rule == "I8.plannedRendered" })
    }

    @Test fun i9LowContrastIsReported() {
        val r = render(c = case(wallpapers = listOf(0xFF888888.toInt()))) { addView(tv("52 bpm", 30f, 10, 10, 200, 50, color = Color.rgb(0x77, 0x77, 0x77))) }
        assertTrue(Invariants.check(r).any { it.rule == "I9.contrast" })
        val ok = render(c = case(wallpapers = listOf(0xFF888888.toInt()))) { addView(tv("52 bpm", 30f, 10, 10, 200, 50, color = Color.BLACK)) }
        assertTrue(Invariants.check(ok).none { it.rule == "I9.contrast" })
    }

    @Test fun i10DeadBandIsReported() {
        val r = render { addView(tv("8,432", 20f, 10, 10, 200, 30)) }
        assertTrue(Invariants.check(r).any { it.rule == "I10.fillToBox" })
        val ok = render { good(); addView(tv("fill", 20f, 10, 120, 200, 90)) }
        assertTrue(Invariants.check(ok).none { it.rule == "I10.fillToBox" })
    }

    @Test fun i11ParcelTooBigIsReported() {
        assertTrue(Invariants.check(render(parcel = 200_000) { good() }).any { it.rule == "I11.parcel" })
    }

    @Test fun i12SlowRenderIsReported() {
        assertTrue(Invariants.check(render(ms = 5_000) { good() }).any { it.rule == "I12.time" })
    }

    @Test fun i13BodyTapMissingIsReported() {
        val r = render { addView(tv("8,432", 40f, 10, 10, 200, 60)) }
        assertTrue(Invariants.check(r).any { it.rule == "I13.taps" })
    }

    @Test fun i14DescriptionMissingIsReported() {
        val r = render { addView(tv("8,432", 40f, 10, 10, 200, 60)) }
        assertTrue(Invariants.check(r).any { it.rule == "I14.a11y" })
    }

    @Test fun i15MirroredLettersWithUnmirroredBarsAreReported() {
        val letters = listOf("M", "T", "W", "T", "F", "S", "S")
        val exp = Expected(true, "8,432", listOf("8,432"), letters = letters, description = "x")
        val rtl = case(env = Env(1.0f, "ar-EG", true, 320, false))
        val mirrored = render(c = rtl, expected = exp) { letters.reversed().forEachIndexed { i, s -> addView(tv(s, 20f, 10 + i * 40, 200, 30, 30)) } }
        assertTrue(Invariants.check(mirrored).any { it.rule == "I15.rtl" })
        val same = render(c = rtl, expected = exp) { letters.forEachIndexed { i, s -> addView(tv(s, 20f, 10 + i * 40, 200, 30, 30)) } }
        assertTrue(Invariants.check(same).none { it.rule == "I15.rtl" })
    }

    @Test fun knownFailureGlobs() {
        val k = KnownFailures.parse("# c\nToday.TodayStripe@*_fs1.3-*  I4.textFits  A4\n")
        assertEquals(1, k.size)
        assertTrue(k[0].matches("Today.TodayStripe@aosp_2x2-110x110_fs1.3-en-GB_x", "I4.textFits"))
        assertTrue(!k[0].matches("Today.TodayStripe@aosp_2x2-110x110_fs1.0-en-GB_x", "I4.textFits"))
        assertTrue(!k[0].matches("Today.TodayStripe@aosp_2x2-110x110_fs1.3-en-GB_x", "I5.noOverlap"))
    }
}
