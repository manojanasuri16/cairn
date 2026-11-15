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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.FontWeight

class LastActivityWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ui = WidgetEnvironment.lastActivity().collectAsState(WidgetUi.Loading).value
            CairnGlanceTheme {
                when (ui) {
                    WidgetUi.Loading -> LoadingView()
                    is WidgetUi.Message -> MessageView(ui)
                    is WidgetUi.Content -> ActivityBody(ui)
                }
            }
        }
    }
}

class LastActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastActivityWidget()
}

@Composable
private fun ActivityBody(ui: WidgetUi.Content<ActivityUi>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val scale = LocalFontScale
    val b = ui.body
    val layout = SizeRules.lastActivity(size.width.value, size.height.value, scale)
    val oneLine = layout.mode == SizeRules.ActivityMode.OneLine
    val sub = GlanceTheme.colors.onSurfaceVariant
    WidgetFrame(
        ui.description, bodyTap(context, WidgetKind.LastActivity),
        footer = { FooterView(ui.footer, ui.screen, compact = oneLine || size.width < 150.dp) },
        padding = if (oneLine) 8.dp else 14.dp,
    ) {
        if (oneLine) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Txt("${b.sport} \u00b7 ${b.date}", 13f, FontWeight.Medium, modifier = GlanceModifier.defaultWeight())
                Txt(listOfNotNull(b.distance, b.duration).joinToString(" \u00b7 "), 13f, color = sub)
            }
        } else {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Txt("${b.sport} \u00b7 ${b.date}", 12f, color = sub)
                Txt(b.distance ?: b.duration ?: "\u2013", SizeRules.cappedSp(30f, scale, 1.15f), FontWeight.Medium)
                // Priority: duration (when distance is the hero), pace, average heart rate.
                val stats = listOfNotNull(b.distance?.let { b.duration }, b.pace, b.avgHr).take(layout.maxStats)
                stats.chunked(layout.statsPerRow).forEach { row ->
                    Row(GlanceModifier.fillMaxWidth()) {
                        row.forEach { Txt(it, 13f, modifier = GlanceModifier.defaultWeight()) }
                    }
                }
            }
        }
    }
}
