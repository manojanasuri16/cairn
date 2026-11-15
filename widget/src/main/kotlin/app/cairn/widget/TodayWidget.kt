package app.cairn.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

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
    val layout = SizeRules.today(size.width.value, size.height.value, scale)
    val compact = layout.mode != SizeRules.TodayMode.Side
    WidgetFrame(
        description = ui.description,
        bodyAction = bodyTap(context, WidgetKind.Today),
        footer = { FooterView(ui.footer, ui.screen, compact = size.width < 150.dp) },
        padding = if (compact) 10.dp else 14.dp,
    ) {
        val ringSize = layout.ringDp.dp
        val hero: @Composable () -> Unit = {
            Ring(body.stepsFraction, ringSize, if (layout.ringDp < 64) 6.dp else if (layout.ringDp < 80) 7.dp else 8.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val digits = body.stepsText ?: "\u2013"
                    Txt(digits, SizeRules.cappedSp(if (layout.ringDp < 64) 16f else if (layout.ringDp < 80) 18f else 20f, scale), FontWeight.Medium)
                    Txt(if (body.stepsText == null) "no steps" else "steps", SizeRules.cappedSp(10f, scale), color = GlanceTheme.colors.onSurfaceVariant)
                }
            }
        }
        val shown = body.metrics.take(layout.rows)
        val metricRows: @Composable () -> Unit = {
            if (shown.isEmpty()) Txt("No other data yet", 12f, color = GlanceTheme.colors.onSurfaceVariant)
            shown.forEach { m ->
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(m.label, 12f, color = GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.defaultWeight())
                    Txt(m.value, 13f, FontWeight.Medium)
                }
            }
        }
        when (layout.mode) {
            SizeRules.TodayMode.RingOnly ->
                Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) { hero() }
            SizeRules.TodayMode.Stacked ->
                Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                    hero()
                    Spacer(GlanceModifier.height(6.dp))
                    metricRows()
                }
            SizeRules.TodayMode.Side ->
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    hero()
                    Spacer(GlanceModifier.width(16.dp))
                    Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) { metricRows() }
                }
        }
    }
}
