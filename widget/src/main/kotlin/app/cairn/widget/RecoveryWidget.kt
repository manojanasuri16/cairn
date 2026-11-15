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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign


class RecoveryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ui = WidgetEnvironment.recovery().collectAsState(WidgetUi.Loading).value
            CairnGlanceTheme {
                when (ui) {
                    WidgetUi.Loading -> LoadingView()
                    is WidgetUi.Message -> MessageView(ui)
                    is WidgetUi.Content -> RecoveryBody(ui)
                }
            }
        }
    }
}

class RecoveryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecoveryWidget()
}

@Composable
private fun RecoveryBody(ui: WidgetUi.Content<RecoveryUi>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val scale = LocalFontScale
    val b = ui.body
    val wide = size.width >= 150.dp
    WidgetFrame(ui.description, bodyTap(context, WidgetKind.Recovery), footer = { FooterView(ui.footer, ui.screen, compact = !wide) }, padding = 10.dp) {
        val ring: @Composable () -> Unit = {
            Ring(b.fraction, if (wide) 80.dp else 76.dp, 8.dp) {
                Txt(b.percentText, SizeRules.cappedSp(if (wide) 24f else 22f, scale), FontWeight.Medium)
            }
        }
        if (wide) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                ring()
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(b.level, 16f, FontWeight.Medium, maxLines = 2)
                    b.timeToFull?.let { Txt(it, 12f, color = GlanceTheme.colors.onSurfaceVariant, maxLines = 2) }
                }
            }
        } else {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                ring()
                Txt(b.level, SizeRules.cappedSp(11f, scale, 1.15f), FontWeight.Medium, align = TextAlign.Center)
            }
        }
    }
}
