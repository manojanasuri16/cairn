package app.cairn.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.text.FontWeight

private val Small = DpSize(100.dp, 100.dp)
private val Wide = DpSize(240.dp, 100.dp)
private val Tall = DpSize(240.dp, 170.dp)

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Small, Wide, Tall))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ui = WidgetEnvironment.today().collectAsState(WidgetUi.Loading).value
            CairnGlanceTheme { TodayContent(ui) }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(ui: WidgetUi<TodayUi>) {
    when (ui) {
        WidgetUi.Loading -> LoadingView()
        is WidgetUi.Message -> MessageView(ui)
        is WidgetUi.Content -> TodayBody(ui)
    }
}

@Composable
private fun TodayBody(ui: WidgetUi.Content<TodayUi>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val scale = LocalFontScale
    val body = ui.body
    val compact = size.width < 180.dp
    val tall = size.height >= 150.dp
    WidgetFrame(
        description = ui.description,
        bodyAction = bodyTap(context, WidgetKind.Today),
        footer = { FooterView(ui.footer, ui.screen, compact = compact) },
        padding = if (compact) 10.dp else 14.dp,
    ) {
        val ringSize = if (compact) 72.dp else if (tall) 92.dp else 68.dp
        val hero: @Composable () -> Unit = {
            Ring(body.stepsFraction, ringSize, if (compact) 7.dp else 8.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val digits = body.stepsText ?: "–"
                    Txt(digits, SizeRules.cappedSp(if (compact) 18f else 20f, scale), FontWeight.Medium)
                    Txt(if (body.stepsText == null) "no steps" else "steps", SizeRules.cappedSp(10f, scale), color = GlanceTheme.colors.onSurfaceVariant)
                }
            }
        }
        if (compact) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) { hero() }
        } else {
            val allowed = if (tall) MetricId.entries else listOf(MetricId.Sleep, MetricId.RestingHr, MetricId.Recovery)
            val rows = SizeRules.visibleRows(size.height.value - 28f - 18f * scale, scale)
            val shown = body.metrics.filter { it.id in allowed }.take(rows)
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                hero()
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    if (shown.isEmpty()) Txt("No other data yet", 12f, color = GlanceTheme.colors.onSurfaceVariant)
                    shown.forEach { m ->
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Txt(m.label, 12f, color = GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.defaultWeight())
                            Txt(m.value, 14f, FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
