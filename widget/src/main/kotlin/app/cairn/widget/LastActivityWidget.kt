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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.FontWeight

private val Line = DpSize(240.dp, 40.dp)
private val Card = DpSize(240.dp, 100.dp)

class LastActivityWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Line, Card))

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
    val b = ui.body
    val oneLine = size.height < 80.dp
    val sub = GlanceTheme.colors.onSurfaceVariant
    WidgetFrame(
        ui.description, bodyTap(context, WidgetKind.LastActivity),
        footer = { FooterView(ui.footer, ui.screen, compact = oneLine) },
        padding = if (oneLine) 8.dp else 14.dp,
    ) {
        if (oneLine) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Txt("${b.sport} · ${b.date}", 13f, FontWeight.Medium, modifier = GlanceModifier.defaultWeight())
                Txt(listOfNotNull(b.distance, b.duration).joinToString(" · "), 13f, color = sub)
            }
        } else {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Txt("${b.sport} · ${b.date}", 12f, color = sub)
                Txt(b.distance ?: b.duration ?: "–", SizeRules.cappedSp(32f, LocalFontScale, 1.15f), FontWeight.Medium)
                Row(GlanceModifier.fillMaxWidth()) {
                    val rest = listOfNotNull(b.distance?.let { b.duration }, b.pace, b.avgHr)
                    rest.forEach { Txt(it, 13f, modifier = GlanceModifier.defaultWeight()) }
                }
            }
        }
    }
}
