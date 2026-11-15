@file:OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class, androidx.glance.ExperimentalGlanceApi::class)

package app.cairn.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Common shell for the six widgets: reads this widget's stored configuration (inheriting the app-wide appearance),
 * feeds the data flow (with the per-widget units override) and renders the chosen style variant. The same
 * [Render] drives the real widget, the launcher picker preview and the live preview in the configuration screen.
 */
abstract class CairnWidget<T>(val kind: WidgetKind) : GlanceAppWidget() {
    /**
     * Responsive with a fixed grid of breakpoints (as the Daymark app does): all size variants are composed up front into one
     * RemoteViews and the launcher picks by cell size, so an update pushed while the app is open cannot strand a widget on a
     * small layout the way re-measured Exact sizes did.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.dp(kind))

    protected abstract fun flow(unit: DistanceUnit?): Flow<WidgetUi<T>>

    /** Synthetic data for previews. Never real values. */
    abstract fun sample(): WidgetUi.Content<T>

    /** Draws the variant in `skin.cfg.style`. Called inside [CairnGlanceTheme]. */
    @Composable
    protected abstract fun Body(ui: WidgetUi.Content<T>)

    /** Theme used by this widget's picker preview, so the picker shows a variety of colours. */
    open val pickerTheme: ThemeId = ThemeId.Clean

    @Composable
    fun Render(ui: WidgetUi<T>, cfg: ResolvedConfig) {
        CairnGlanceTheme(cfg) {
            when (ui) {
                WidgetUi.Loading -> LoadingView()
                is WidgetUi.Message -> MessageView(ui)
                is WidgetUi.Content -> Body(ui)
            }
        }
    }

    @Composable
    fun RenderSample(cfg: ResolvedConfig) = Render(sample(), cfg)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        WidgetLog.options(context, appWidgetId, "provide $kind")
        provideContent {
            val settings = WidgetEnvironment.settings
            val stored by remember { settings?.config(appWidgetId, kind) ?: flowOf(WidgetConfig()) }.collectAsState(WidgetConfig())
            val app by remember { settings?.appearance ?: flowOf(AppAppearance()) }.collectAsState(AppAppearance())
            val cfg = stored.resolve(kind, app)
            WidgetLog.d("provide kind=$kind id=$appWidgetId declaredSizes=${WidgetSizes.byKind[kind]} stored=[style=${stored.style} theme=${stored.theme} metrics=${stored.metrics?.map { it.name }}] resolved=[style=${cfg.style} theme=${cfg.theme} metrics=${cfg.metrics.map { it.name }} hidden=${cfg.hidden.map { it.name }}]")
            val unit = when (cfg.units) { UnitsPref.Km -> DistanceUnit.Km; UnitsPref.Mi -> DistanceUnit.Mi; UnitsPref.Inherit -> null }
            val ui by remember(unit) { flow(unit) }.collectAsState(WidgetUi.Loading)
            val sync by remember { WidgetEnvironment.sources?.syncState ?: flowOf(null) }.collectAsState(null)
            androidx.compose.runtime.CompositionLocalProvider(LocalSyncState provides sync) { Render(ui, cfg) }
        }
    }

    /** Android 15+ generated preview in the launcher picker. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { RenderSample(WidgetConfig(theme = pickerTheme).resolve(kind, AppAppearance())) }
    }
}

object WidgetCatalog {

    /** Which widget a launcher provider (receiver class name) belongs to. */
    fun kindOf(receiverClassName: String?): WidgetKind? = WidgetVariants.kindOf(receiverClassName)

    fun widget(kind: WidgetKind): CairnWidget<*> = when (kind) {
        WidgetKind.Today -> TodayWidget()
        WidgetKind.Recovery -> RecoveryWidget()
        WidgetKind.LastActivity -> LastActivityWidget()
        WidgetKind.TrainingLoad -> TrainingLoadWidget()
        WidgetKind.Sleep -> SleepWidget()
        WidgetKind.Fitness -> FitnessWidget()
        WidgetKind.Overview -> OverviewWidget()
    }

    /** Redraws one placed widget right now (used by Done in the configuration screen). */
    suspend fun updateOne(context: Context, kind: WidgetKind, appWidgetId: Int) {
        val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        widget(kind).update(context, id)
    }

    /** Default size of the preview for [kind] in the configuration screen, in dp. */
    fun previewSize(kind: WidgetKind): DpSize = when (kind) {
        WidgetKind.Today, WidgetKind.Recovery -> DpSize(170.dp, 170.dp)
        WidgetKind.Sleep, WidgetKind.LastActivity -> DpSize(300.dp, 140.dp)
        WidgetKind.TrainingLoad, WidgetKind.Fitness -> DpSize(300.dp, 160.dp)
        WidgetKind.Overview -> DpSize(300.dp, 170.dp)
    }

    /** The widget exactly as the launcher will draw it, built from synthetic data; used for the live preview. */
    suspend fun previewViews(context: Context, kind: WidgetKind, cfg: ResolvedConfig, size: DpSize = previewSize(kind)): RemoteViews {
        val w = widget(kind)
        // Compose at the size the launcher would select for this cell (the largest listed size that fits), so the preview and the
        // placed widget apply the same layout rules; the frame then stretches it over the full cell.
        val (pw, ph) = WidgetSizes.pick(WidgetSizes.byKind.getValue(kind), size.width.value, size.height.value)
        return GlanceRemoteViews().compose(context, DpSize(pw.dp, ph.dp)) { w.RenderSample(cfg) }.remoteViews
    }
}

/** Calls the preview setter so launchers on Android 15+ show the real coloured widgets in the picker. Rate limited by the system; failures are harmless. */
suspend fun registerWidgetPreviews(context: Context) {
    val mgr = GlanceAppWidgetManager(context)
    runCatching { mgr.setWidgetPreviews(TodayWidgetReceiver::class) }
    runCatching { mgr.setWidgetPreviews(RecoveryWidgetReceiver::class) }
    runCatching { mgr.setWidgetPreviews(LastActivityWidgetReceiver::class) }
    runCatching { mgr.setWidgetPreviews(TrainingLoadWidgetReceiver::class) }
    runCatching { mgr.setWidgetPreviews(SleepWidgetReceiver::class) }
    runCatching { mgr.setWidgetPreviews(FitnessWidgetReceiver::class) }
    runCatching { mgr.setWidgetPreviews(OverviewWidgetReceiver::class) }
}
