@file:OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)

package app.cairn.widget.matrix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Parcel
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.model.DistanceUnit
import app.cairn.widget.CairnWidget
import app.cairn.widget.Layouts
import app.cairn.widget.LocalSyncState
import app.cairn.widget.OverviewId
import app.cairn.widget.OverviewLayout
import app.cairn.widget.PlanRecorder
import app.cairn.widget.UnitsPref
import app.cairn.widget.WidgetCatalog
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetSizes
import app.cairn.widget.WidgetThumbnails
import app.cairn.widget.WidgetUi
import app.cairn.widget.AppAppearance
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import java.util.Locale

data class TextNode(
    val view: TextView, val text: String, val rect: Rect, val textSizePx: Float, val color: Int,
    val maxLines: Int, val availWidthPx: Int, val lineCount: Int, val ellipsized: Boolean,
    /** Widest unwrapped line (px) the text needs, and the number of lines it needs when wrapped to the available width. */
    val neededWidthPx: Float, val neededLines: Int, val neededHeightPx: Int, val viewHeightPx: Int, val bold: Boolean,
    /** Where the glyphs really are (alignment and gravity applied), clipped to the view. */
    val ink: Rect = rect,
)

data class ImageNode(val view: ImageView, val rect: Rect, val bitmap: Bitmap?, val fillsRoot: Boolean)
data class ClickNode(val view: View, val rect: Rect, val description: String?)

class RenderedWidget(
    val case: MatrixCase,
    val declared: Pair<Int, Int>?,
    val root: View?,
    val density: Float,
    val texts: List<TextNode>,
    val images: List<ImageNode>,
    val clickables: List<ClickNode>,
    val allRects: List<Triple<View, Rect, Rect>>,
    val plan: Layouts.Plan?,
    val expected: Expected,
    val ui: WidgetUi<*>?,
    val parcelBytes: Int,
    val bitmapBytes: Long,
    val renderMs: Long,
    val error: Throwable?,
    val cellPx: Pair<Int, Int>,
    val overviewShown: Int? = null,
    val wallpaperBitmaps: MutableMap<Int, Bitmap> = mutableMapOf(),
) {
    fun dp(px: Int) = px / density

    /** The render drawn over [wallpaper] with every text view hidden (the background under the text); roots are drawn at device px. */
    fun backgroundWithoutText(wallpaper: Int): Bitmap = wallpaperBitmaps.getOrPut(wallpaper) { draw(wallpaper, hideText = true) }

    fun bitmap(wallpaper: Int): Bitmap = draw(wallpaper, hideText = false)

    private fun draw(wallpaper: Int, hideText: Boolean): Bitmap {
        val r = root ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val bmp = Bitmap.createBitmap(r.width.coerceAtLeast(1), r.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(wallpaper)
        val hidden = if (hideText) texts.map { it.view to it.view.visibility } else emptyList()
        hidden.forEach { it.first.visibility = View.INVISIBLE }
        WidgetThumbnails.drawClipped(r, c)
        hidden.forEach { it.first.visibility = it.second }
        return bmp
    }
}

object WidgetRenderer {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var appliedKey: String? = null

    /** Applies locale, direction, night mode, density and font scale to the Robolectric environment (only when it changed). */
    fun applyEnv(env: Env) {
        val key = env.groupKey
        if (key == appliedKey) return
        RuntimeEnvironment.setQualifiers(env.qualifiers)
        RuntimeEnvironment.setFontScale(env.fontScale)
        Locale.setDefault(localeOf(env))
        appliedKey = key
    }

    fun localeOf(env: Env): Locale = Locale.forLanguageTag(env.locale)

    private fun unitOf(c: MatrixCase): DistanceUnit = when (c.tweaks.units) {
        UnitsPref.Mi -> DistanceUnit.Mi
        else -> DistanceUnit.Km
    }

    /** The size Glance composes at: Overview is SizeMode.Exact (the launcher's own size), every other kind picks from its Responsive list. */
    private fun declaredFor(c: MatrixCase): Pair<Int, Int> =
        if (c.kind == WidgetKind.Overview) Math.round(c.cell.wDp) to Math.round(c.cell.hDp)
        else WidgetSizes.pick(WidgetSizes.byKind.getValue(c.kind), c.cell.wDp, c.cell.hDp)

    /** The RemoteViews the launcher would be handed for [c] (one size), and what the plan recorded while composing it. */
    @Suppress("UNCHECKED_CAST")
    private fun compose(c: MatrixCase, size: Pair<Int, Int>, ui: WidgetUi<*>): Pair<RemoteViews, Layouts.Plan?> {
        val widget = WidgetCatalog.widget(c.kind) as CairnWidget<Any?>
        val cfg = c.config().resolve(c.kind, AppAppearance())
        var plan: Layouts.Plan? = null
        PlanRecorder.sink = { k, p -> if (k == c.kind) plan = p }
        try {
            val rv = runBlocking {
                GlanceRemoteViews().compose(context, DpSize(size.first.dp, size.second.dp)) {
                    CompositionLocalProvider(LocalSyncState provides Fixtures.sync(c.data)) { widget.Render(ui as WidgetUi<Any?>, cfg) }
                }.remoteViews
            }
            return rv to plan
        } finally {
            PlanRecorder.sink = null
        }
    }

    fun renderCase(c: MatrixCase): RenderedWidget {
        applyEnv(c.env)
        val density = context.resources.displayMetrics.density
        val wPx = Math.round(c.cell.wDp * density); val hPx = Math.round(c.cell.hDp * density)
        val locale = localeOf(c.env)
        val ui = Fixtures.ui(c.kind, c.data, locale, unitOf(c))
        var expected = Fixtures.expected(ui, locale, c.style)
        val t0 = System.nanoTime()
        val declared = declaredFor(c)
        try {
            val (rv, plan) = compose(c, declared, ui)
            val frame = FrameLayout(context)
            val v = rv.apply(context, frame)
            frame.addView(v, FrameLayout.LayoutParams(wPx, hPx))
            frame.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, wPx, hPx)
            var shown: Int? = null
            if (c.kind == WidgetKind.Overview && ui is WidgetUi.Content<*>) {
                val cfg = c.config().resolve(c.kind, AppAppearance())
                // Overview is SizeMode.Exact: the plan is evaluated at the launcher's own size (+ MARGIN), as OverviewWidget.Body does.
                val ew = declared.first + WidgetSizes.MARGIN_DP; val eh = declared.second + WidgetSizes.MARGIN_DP
                val pad = Layouts.pad(app.cairn.core.designsystem.SizeClass.of(ew, eh), eh)
                val ov = ui.body as app.cairn.widget.OverviewUi
                shown = OverviewLayout.planFor(cfg.metrics.map { ov.find(it) ?: app.cairn.widget.OverviewItem(it, "\u2013") }, ew - 2f * pad, Layouts.availDp(ew, eh, c.env.fontScale), c.env.fontScale).shown
                val vals = cfg.metrics.take(shown).mapNotNull { ov.find(it)?.number }
                expected = expected.copy(values = vals, hero = vals.firstOrNull())
            }
            val nodes = Walker(frame, density, wPx, hPx).walk()
            // Robolectric writes bitmap pixels into the parcel; on a device they travel as ashmem blobs, so only the rest counts as parcel bytes.
            val parcel = measureParcel(rv).let { (bytes, _) -> maxOf(0, bytes - nodes.bitmapBytes.toInt()) to 0L }
            val ms = (System.nanoTime() - t0) / 1_000_000
            return RenderedWidget(
                c, declared, frame, density, nodes.texts, nodes.images, nodes.clicks, nodes.rects, plan, expected, ui, parcel.first, nodes.bitmapBytes, ms, null,
                wPx to hPx, shown,
            )
        } catch (t: Throwable) {
            return RenderedWidget(c, declared, null, density, emptyList(), emptyList(), emptyList(), emptyList(), null, expected, ui, -1, 0, (System.nanoTime() - t0) / 1_000_000, t, wPx to hPx)
        }
    }

    /** A placeholder result for tiers that measure the whole map and have no view tree of their own. */
    fun stub(c: MatrixCase, parcelBytes: Int, bitmapBytes: Long, error: Throwable? = null) = RenderedWidget(
        c, null, null, 1f, emptyList(), emptyList(), emptyList(), emptyList(), null, Expected(false, null, emptyList()), null, parcelBytes, bitmapBytes, 0, error, 0 to 0,
    )

    /** Walks an already laid-out [frame] into a [RenderedWidget]. Used by renderCase and by the invariant self-tests (fake trees). */
    fun inspect(
        c: MatrixCase, frame: View, wPx: Int, hPx: Int, density: Float, expected: Expected, plan: Layouts.Plan? = null,
        declared: Pair<Int, Int>? = null, parcelBytes: Int = 0, ui: WidgetUi<*>? = null, ms: Long = 0,
    ): RenderedWidget {
        val nodes = Walker(frame, density, wPx, hPx).walk()
        return RenderedWidget(c, declared, frame, density, nodes.texts, nodes.images, nodes.clicks, nodes.rects, plan, expected, ui, parcelBytes, nodes.bitmapBytes, ms, null, wPx to hPx)
    }

    /** Parcel.dataSize of one RemoteViews (what binder carries; bitmaps are counted separately). */
    fun measureParcel(rv: RemoteViews): Pair<Int, Long> {
        val p = Parcel.obtain()
        return try { rv.writeToParcel(p, 0); p.dataSize() to 0L } catch (t: Throwable) { -1 to 0L } finally { p.recycle() }
    }

    /** The responsive map of every declared size of [c]'s kind, as the launcher receives it (parcel bytes, bitmap bytes). */
    @Suppress("NewApi")
    fun renderResponsiveMap(c: MatrixCase): Pair<Int, Long> {
        applyEnv(c.env)
        val ui = Fixtures.ui(c.kind, c.data, localeOf(c.env), unitOf(c))
        val map = HashMap<android.util.SizeF, RemoteViews>()
        val bitmaps = HashSet<Bitmap>()
        var uses = 0L // every use of a bitmap in a tree (Robolectric writes the pixels of each use into the parcel; a device sends ashmem blobs)
        var bytes = 0L
        for (s in WidgetSizes.byKind.getValue(c.kind)) {
            val (rv, _) = compose(c, s, ui)
            map[android.util.SizeF(s.first.toFloat(), s.second.toFloat())] = rv
            val frame = FrameLayout(context); val v = rv.apply(context, frame); frame.addView(v)
            val perTree = HashSet<Bitmap>(); collectBitmaps(v, perTree); uses += perTree.sumOf { it.allocationByteCount.toLong() }; bitmaps += perTree
        }
        bitmaps.forEach { bytes += it.allocationByteCount }
        val parcel = measureParcel(RemoteViews(map))
        return maxOf(0, parcel.first - uses.toInt()) to bytes
    }

    private fun collectBitmaps(v: View, out: MutableSet<Bitmap>) {
        if (v is ImageView) (v.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { out += it }
        if (v is ViewGroup) for (i in 0 until v.childCount) collectBitmaps(v.getChildAt(i), out)
    }

    private class Nodes(val texts: List<TextNode>, val images: List<ImageNode>, val clicks: List<ClickNode>, val rects: List<Triple<View, Rect, Rect>>, val bitmapBytes: Long)

    private class Walker(val root: View, val density: Float, val w: Int, val h: Int) {
        val texts = mutableListOf<TextNode>(); val images = mutableListOf<ImageNode>(); val clicks = mutableListOf<ClickNode>()
        val rects = mutableListOf<Triple<View, Rect, Rect>>()
        val bitmaps = HashSet<Bitmap>()

        fun walk(): Nodes {
            visit(root, 0, 0, Rect(0, 0, w, h))
            // Glance puts the semantics (contentDescription) on a wrapper beside the clickable view: pair them by identical bounds.
            val described = rects.filter { (v, _, _) -> !v.contentDescription.isNullOrEmpty() }
            val paired = clicks.map { c ->
                if (c.description != null) c else {
                    val m = described.firstOrNull { (v, r, _) -> v !== c.view && Math.abs(r.left - c.rect.left) <= 2 && Math.abs(r.top - c.rect.top) <= 2 && Math.abs(r.right - c.rect.right) <= 2 && Math.abs(r.bottom - c.rect.bottom) <= 2 }
                    ClickNode(c.view, c.rect, m?.first?.contentDescription?.toString())
                }
            }
            return Nodes(texts, images, paired, rects, bitmaps.sumOf { it.allocationByteCount.toLong() })
        }

        private fun visit(v: View, ox: Int, oy: Int, parentRect: Rect) {
            if (v.visibility == View.GONE) return
            val r = Rect(ox + v.left, oy + v.top, ox + v.right, oy + v.bottom)
            if (v !== root) rects += Triple(v, r, parentRect)
            if (v.visibility == View.VISIBLE) {
                if (v is TextView && v.text.isNotEmpty()) texts += textNode(v, r)
                if (v is ImageView) {
                    val bmp = (v.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    bmp?.let { bitmaps += it }
                    images += ImageNode(v, r, bmp, r.width() >= w * 0.9f && r.height() >= h * 0.9f)
                }
                if (v.hasOnClickListeners()) clicks += ClickNode(v, r, v.contentDescription?.toString())
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i), r.left - v.scrollX, r.top - v.scrollY, r)
        }

        private fun textNode(tv: TextView, r: Rect): TextNode {
            val text = tv.text.toString()
            val avail = (tv.width - tv.paddingLeft - tv.paddingRight).coerceAtLeast(0)
            val paint = TextPaint(tv.paint)
            val maxLines = tv.maxLines.let { if (it < 0 || it == Int.MAX_VALUE) Int.MAX_VALUE else it }
            val layout = tv.layout
            val ellipsized = layout != null && (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
            val unlimited = StaticLayout.Builder.obtain(text, 0, text.length, paint, avail.coerceAtLeast(1) + 2).setMaxLines(Int.MAX_VALUE).setIncludePad(tv.includeFontPadding).build()
            var needed = 0f
            for (i in 0 until unlimited.lineCount) needed = maxOf(needed, unlimited.getLineWidth(i))
            val desired = Layout.getDesiredWidth(text, paint)
            // A value on one line: what it needs is its whole width. Wrapped text: the widest wrapped line.
            val neededWidth = if (maxLines == 1) desired else needed
            val neededHeight = unlimited.height.let { full -> if (unlimited.lineCount > maxLines) unlimited.getLineBottom(maxLines - 1) else full }
            var ink = Rect(r)
            if (layout != null && layout.lineCount > 0) {
                var l = Float.MAX_VALUE; var rt = 0f
                for (i in 0 until layout.lineCount) { l = minOf(l, layout.getLineLeft(i)); rt = maxOf(rt, layout.getLineRight(i)) }
                val x0 = r.left + tv.compoundPaddingLeft; val y0 = r.top + tv.extendedPaddingTop
                ink = Rect(x0 + l.toInt(), y0, x0 + rt.toInt(), y0 + layout.height)
                if (!ink.intersect(r)) ink = Rect(r.left, r.top, r.left, r.top)
            }
            return TextNode(
                tv, text, r, tv.textSize, tv.currentTextColor, maxLines, avail, layout?.lineCount ?: 0, ellipsized,
                neededWidth, unlimited.lineCount, neededHeight + tv.paddingTop + tv.paddingBottom, tv.height, tv.typeface?.isBold == true, ink,
            )
        }
    }
}
