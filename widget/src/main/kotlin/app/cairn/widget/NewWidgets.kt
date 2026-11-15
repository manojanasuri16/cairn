package app.cairn.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign

// ---- W3 Training load (4x2, 4x3) ----

private val LoadWide = DpSize(250.dp, 100.dp)
private val LoadTall = DpSize(250.dp, 170.dp)

class TrainingLoadWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(LoadWide, LoadTall))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ui = WidgetEnvironment.trainingLoad().collectAsState(WidgetUi.Loading).value
            CairnGlanceTheme {
                when (ui) {
                    WidgetUi.Loading -> LoadingView()
                    is WidgetUi.Message -> MessageView(ui)
                    is WidgetUi.Content -> TrainingLoadBody(ui)
                }
            }
        }
    }
}

class TrainingLoadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrainingLoadWidget()
}

@Composable
private fun TrainingLoadBody(ui: WidgetUi.Content<TrainingLoadUi>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val scale = LocalFontScale
    val b = ui.body
    val tall = size.height >= 150.dp
    val sub = GlanceTheme.colors.onSurfaceVariant
    WidgetFrame(ui.description, bodyTap(context, WidgetKind.TrainingLoad), footer = { FooterView(ui.footer, ui.screen) }, padding = 14.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(96.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Load ratio", 11f, color = sub)
                Txt(b.ratioText, SizeRules.cappedSp(32f, scale, 1.15f), FontWeight.Medium)
                Txt(b.comment, 12f, FontWeight.Medium, maxLines = 2)
                if (tall) b.vo2Text?.let { v ->
                    Spacer(GlanceModifier.height(6.dp))
                    Txt(v + (b.vo2Trend?.let { " ($it)" } ?: ""), 12f, color = sub, maxLines = 2)
                }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (!tall) b.vo2Text?.let { v ->
                    Txt(v + (b.vo2Trend?.let { " ($it)" } ?: ""), 11f, color = sub, align = TextAlign.End, modifier = GlanceModifier.fillMaxWidth())
                }
                // Chart width = widget width minus padding (2 x 14dp), the ratio column (96dp) and the gap (12dp).
                val chartW = (size.width.value - 28f - 96f - 12f).coerceAtLeast(60f).dp
                LoadBars(b.bars, chartW, if (tall) 70.dp else 34.dp)
            }
        }
    }
}

@Composable
private fun LoadBars(bars: List<LoadBar>, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bmp = ChartBitmaps.bars(
        wPx = (width.value * density).toInt(), hPx = (height.value * density).toInt(),
        fractions = bars.map { it.fraction }, todayIdx = bars.indexOfFirst { it.isToday },
        color = GlanceTheme.colors.primary.getColor(context).toArgb(),
        dimColor = GlanceTheme.colors.primary.getColor(context).copy(alpha = 0.4f).toArgb(),
    )
    Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(width, height))
    Row(GlanceModifier.width(width)) {
        bars.forEach { Txt(it.letter, 9f, if (it.isToday) FontWeight.Bold else FontWeight.Normal, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight()) }
    }
}

// ---- W5 Sleep (2x2, 4x2) ----

private val SleepSquare = DpSize(110.dp, 110.dp)
private val SleepWide = DpSize(250.dp, 110.dp)

class SleepWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SleepSquare, SleepWide))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ui = WidgetEnvironment.sleep().collectAsState(WidgetUi.Loading).value
            CairnGlanceTheme {
                when (ui) {
                    WidgetUi.Loading -> LoadingView()
                    is WidgetUi.Message -> MessageView(ui)
                    is WidgetUi.Content -> SleepBody(ui)
                }
            }
        }
    }
}

class SleepWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SleepWidget()
}

@Composable
private fun stageColor(stage: SleepStage): Color {
    val context = LocalContext.current
    val c = GlanceTheme.colors
    return when (stage) {
        SleepStage.Deep -> c.primary
        SleepStage.Light -> c.secondary
        SleepStage.Rem -> c.tertiary
        SleepStage.Awake -> c.onSurfaceVariant
    }.getColor(context)
}

@Composable
private fun SleepBody(ui: WidgetUi.Content<SleepUi>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val scale = LocalFontScale
    val b = ui.body
    val wide = size.width >= 200.dp
    val sub = GlanceTheme.colors.onSurfaceVariant
    WidgetFrame(ui.description, bodyTap(context, WidgetKind.Sleep), footer = { FooterView(ui.footer, ui.screen, compact = !wide) }, padding = if (wide) 14.dp else 10.dp) {
        if (!wide) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Txt("Sleep", 11f, color = sub)
                Txt(b.durationText, SizeRules.cappedSp(28f, scale, 1.15f), FontWeight.Medium)
                b.scoreText?.let { Txt(it, 12f, FontWeight.Medium) }
                b.window?.let { Txt(it, 12f, color = sub) }
            }
        } else {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Txt(b.durationText, SizeRules.cappedSp(28f, scale, 1.15f), FontWeight.Medium, modifier = GlanceModifier.defaultWeight())
                    Column(horizontalAlignment = Alignment.End) {
                        b.scoreText?.let { Txt(it, 12f, FontWeight.Medium) }
                        b.window?.let { Txt(it, 12f, color = sub) }
                    }
                }
                val stages = b.stages
                if (stages != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    val density = context.resources.displayMetrics.density
                    val w = (size.width.value - 28f).coerceAtLeast(60f)
                    val colors = stages.map { stageColor(it.stage).toArgb() }
                    val bmp = ChartBitmaps.stageBar((w * density).toInt(), (8 * density).toInt(), stages.map { it.minutes }, colors)
                    Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(w.dp, 8.dp))
                    Spacer(GlanceModifier.height(4.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        stages.forEach { s ->
                            Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                                Box(GlanceModifier.size(6.dp).background(ColorProviderOf(stageColor(s.stage))).cornerRadius(2.dp)) {}
                                Spacer(GlanceModifier.width(3.dp))
                                Txt(Fmt.duration(s.minutes), SizeRules.cappedSp(10f, scale), color = sub)
                            }
                        }
                    }
                    b.stageNote?.let { Txt(it, 10f, color = sub, maxLines = 1) }
                } else {
                    Txt("No stage breakdown", 11f, color = sub)
                }
            }
        }
    }
}

private fun ColorProviderOf(c: Color) = androidx.glance.unit.ColorProvider(c)

// ---- W6 Fitness / race (4x2) ----

private val FitnessWide = DpSize(250.dp, 110.dp)

class FitnessWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(FitnessWide))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ui = WidgetEnvironment.fitness().collectAsState(WidgetUi.Loading).value
            CairnGlanceTheme {
                when (ui) {
                    WidgetUi.Loading -> LoadingView()
                    is WidgetUi.Message -> MessageView(ui)
                    is WidgetUi.Content -> FitnessBody(ui)
                }
            }
        }
    }
}

class FitnessWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FitnessWidget()
}

@Composable
private fun FitnessBody(ui: WidgetUi.Content<FitnessUi>) {
    val context = LocalContext.current
    val scale = LocalFontScale
    val b = ui.body
    val sub = GlanceTheme.colors.onSurfaceVariant
    WidgetFrame(ui.description, bodyTap(context, WidgetKind.Fitness), footer = { FooterView(ui.footer, ui.screen) }, padding = 14.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(96.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("VO2max", 11f, color = sub)
                Txt(b.vo2Text ?: "–", SizeRules.cappedSp(32f, scale, 1.15f), FontWeight.Medium)
                b.thresholdPace?.let { Txt("Threshold", 10f, color = sub); Txt(it, 12f, FontWeight.Medium) }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (b.predictions.isEmpty()) Txt("No race predictions yet", 12f, color = sub)
                b.predictions.chunked(2).forEach { pair ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        pair.forEach { p ->
                            Column(GlanceModifier.defaultWeight()) {
                                Txt(p.label, 10f, color = sub)
                                Txt(p.time, 14f, FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
