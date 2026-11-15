package app.cairn.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import app.cairn.core.designsystem.Contrast
import app.cairn.core.designsystem.Palette
import kotlin.math.ceil
import kotlin.math.floor

/** Shared pieces of the HRV, Heart rate, Stress and Weekly mileage widgets. */

@Composable
internal fun toPx(v: Dp): Int = (v.value * LocalContext.current.resources.displayMetrics.density).toInt().coerceAtLeast(1)

/** The colour of a semantic tone in the current palette. The word beside it carries the meaning; colour only backs it up. */
fun Palette.tone(t: Tone): Int = when (t) {
    Tone.Good -> good; Tone.Warn -> warn; Tone.Bad -> bad; Tone.Cool -> cool; Tone.Neutral -> accent
}

/** Number with its unit set small beside it on one baseline ("65 ms"). [unit] may be empty. */
@Composable
internal fun BigValue(number: String, unit: String, sp: Float, modifier: GlanceModifier = GlanceModifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Txt(number, sp, FontWeight.Bold, hero = true)
        if (unit.isNotEmpty()) { Spacer(GlanceModifier.width(3.dp)); Txt(unit, OverviewLayout.unitSp(sp), FontWeight.Normal, skin.p.inkSub) }
    }
}

/** Weekday letters under a chart, [today] in bold. */
@Composable
internal fun Letters(letters: List<String>, todayIdx: Int, width: Dp) {
    val p = skin.p
    Row(GlanceModifier.width(width)) {
        letters.forEachIndexed { i, l ->
            Txt(l, 9f, if (i == todayIdx) FontWeight.Bold else FontWeight.Normal, if (i == todayIdx) p.ink else p.inkSub, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
        }
    }
}

@Composable
internal fun ChartImage(bmp: android.graphics.Bitmap, width: Dp, height: Dp) =
    Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(width, height))

/** The first of [candidates] (longest first) that fits [availW] at [sp]; the last one otherwise, so a value is shortened, never cut. */
@Composable
internal fun fitLine(candidates: List<String>, availW: Float, sp: Float): String {
    val scale = LocalFontScale
    return candidates.firstOrNull { TextFit.fits(it, sp, scale, availW) } ?: candidates.last()
}

/** Axis of the HRV charts: the normal range, tonight and the baseline all sit inside it. Pure so it is unit-tested. */
object HrvAxis {
    data class Axis(val lo: Int, val hi: Int) {
        fun fraction(v: Int): Float = if (hi <= lo) 0f else ((v - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    }

    /** For the range bar: padded around everything on it. */
    fun bar(b: HrvUi): Axis {
        val values = listOfNotNull(b.lowMs, b.highMs, b.baselineMs, b.avgMs)
        val lo = floor(values.min() * 0.8).toInt().coerceAtLeast(0)
        val hi = ceil(values.max() * 1.2).toInt().coerceAtLeast(lo + 1)
        return Axis(lo, hi)
    }

    /** For the nights chart: bars start at zero so their heights are honest. */
    fun nights(b: HrvUi): Axis {
        val top = (b.nights.mapNotNull { it.ms } + listOfNotNull(b.highMs, b.avgMs)).max()
        return Axis(0, ceil(top * 1.15).toInt().coerceAtLeast(1))
    }
}

/** Resting heart rate sparkline fractions: the series scaled between its lowest and highest value (a flat series sits mid-height). */
fun sparkFractions(series: List<Int>): List<Float> {
    if (series.isEmpty()) return emptyList()
    val lo = series.min(); val hi = series.max()
    return series.map { if (hi == lo) 0.5f else (it - lo).toFloat() / (hi - lo) }
}

/** Heart rate axis of the min / average / max bar: rounded outwards to tens, with 10 bpm of room on each side. */
fun heartAxis(min: Int, max: Int): HrvAxis.Axis = HrvAxis.Axis(((min - 10).coerceAtLeast(30) / 10) * 10, ceil((max + 10) / 10.0).toInt() * 10)

internal fun softBand(color: Int) = Contrast.withAlpha(color, 0.32f)
