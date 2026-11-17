package app.cairn.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import app.cairn.core.designsystem.Contrast
import app.cairn.core.model.Freshness
import app.cairn.sync.Indicator
import app.cairn.sync.IndicatorKind
import app.cairn.sync.SyncIndicators
import app.cairn.sync.SyncState
import java.time.Duration
import java.time.Instant

/** The persisted sync state as the widget host last saw it (null in previews); drives the Syncing and error states. */
val LocalSyncState = compositionLocalOf<SyncState?> { null }

/** Pure decisions about the footer, so they can be tested without rendering. */
object FooterRules {
    /** Footer row height: the 40 x 40 dp sync button (the tap target) fills it. */
    const val HEIGHT_DP = 40f
    const val BUTTON_W_DP = 40f
    /** A strip under [STRIP_H] dp tall (a 4x1) cannot spare a 40 dp footer: it keeps the earlier 28 dp row (a 40 x 28 dp button). */
    const val STRIP_H = 90f
    const val STRIP_HEIGHT_DP = 28f
    fun heightDp(cellH: Float): Float = if (cellH < STRIP_H) STRIP_HEIGHT_DP else HEIGHT_DP

    /** The icon may be hidden only on very tiny cells, and only when the user asked for that. */
    fun showSyncIcon(hideRequested: Boolean, w: Float, h: Float): Boolean = !(hideRequested && w < 130f && h < 130f)

    fun indicator(footer: FooterUi, state: SyncState?, now: Instant): Indicator =
        SyncIndicators.derive(footer.tier, Duration.ofMillis(footer.ageMs), state, now)

    /** Text beside the dot: nothing when fresh, a very short age when old, a word while syncing or after a failure. */
    fun label(ind: Indicator, widthDp: Float): String? = when (ind.kind) {
        IndicatorKind.Fresh -> null
        IndicatorKind.Aging, IndicatorKind.Stale -> ind.shortAge
        IndicatorKind.Syncing -> "Syncing…"
        IndicatorKind.Error -> if (widthDp >= 200f) "Couldn't sync, tap to retry" else "Couldn't sync"
    }

    fun iconRes(kind: IndicatorKind): Int = if (kind == IndicatorKind.Syncing) R.drawable.ic_w_sync_busy else R.drawable.ic_w_sync
}

/** Runs the user-requested sync without opening any app, then redraws the widgets (which show Syncing, then the new age). */
class SyncActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        runCatching { WidgetEnvironment.requestSync?.invoke() }
        runCatching { GlanceWidgetUpdater(context).updateAll() }
        // No network and no result must not leave "Syncing…" up: redraw once the syncing window has passed.
        WidgetTicks.afterSyncTap(context)
    }
}

/**
 * Quiet freshness indicator: a coloured dot (green fresh, amber getting old, red stale, neutral while syncing), a very short
 * age only when the data is not fresh, and the small sync button. The exact age is in the TalkBack description.
 */
@Composable
fun FooterView(footer: FooterUi, screen: Screen, compact: Boolean) {
    val context = LocalContext.current
    val p = skin.p
    val (w, h) = widgetSize()
    val ind = FooterRules.indicator(footer, LocalSyncState.current, Instant.now())
    val dot = when (ind.kind) {
        IndicatorKind.Fresh -> p.good; IndicatorKind.Aging -> p.warn; IndicatorKind.Stale, IndicatorKind.Error -> p.bad
        IndicatorKind.Syncing -> p.inkSub
    }
    val textColor = when (ind.kind) {
        IndicatorKind.Aging -> p.warnText
        IndicatorKind.Stale, IndicatorKind.Error -> p.stale
        else -> p.inkSub
    }
    val label = FooterRules.label(ind, w)
    val showIcon = FooterRules.showSyncIcon(skin.cfg.hideSyncIcon, w, h)
    val rowH = FooterRules.heightDp(h)
    val iconTint = if (ind.kind == IndicatorKind.Error) p.stale else p.inkSub
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(rowH.dp).padding(start = 14.dp, end = if (showIcon) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight().height(rowH.dp)
                .semantics { contentDescription = ind.description + if (footer.opensCoros) ". Opens COROS to sync" else ". Opens Cairn" }
                .clickable(actionStartActivity(footerIntent(context, footer, screen))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(8.dp).background(cp(dot)).cornerRadius(4.dp)) {}
            if (label != null) {
                Spacer(GlanceModifier.width(6.dp))
                Txt(label, if (compact) 10f else 11f, if (ind.kind == IndicatorKind.Fresh) FontWeight.Medium else FontWeight.Bold, textColor)
            }
        }
        if (showIcon) {
            Box(
                GlanceModifier.size(FooterRules.BUTTON_W_DP.dp, rowH.dp)
                    .semantics { contentDescription = if (ind.kind == IndicatorKind.Syncing) "Syncing" else "Sync now. ${ind.description}" }
                    .clickable(actionRunCallback<SyncActionCallback>()),
                contentAlignment = Alignment.Center,
            ) { Icon(FooterRules.iconRes(ind.kind), 16.dp, iconTint) }
        }
    }
}
