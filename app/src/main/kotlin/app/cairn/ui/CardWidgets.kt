package app.cairn.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetStyle

/** Every card in the app that can be turned into a widget. One id per card, so the mapping below is exhaustive. */
enum class CardId {
    TodayHeader, TodayActivity, HeartRate, RestingHr, Stress, Sleep, Hrv, WeeklyLoad, TrainingStatus, Recovery, RunningFitness, Last7Days,
    ActivitiesMonth, ActivityRow,
    FitTrainingStatus, FitRunning, FitRacePredictions, FitVo2,
}

/** The widget a card stands for, and the style to preselect in the editor (null: the kind's default). */
data class WidgetLink(val kind: WidgetKind, val style: WidgetStyle? = null)

/** Single source for "which widget does this card become". Pure and exhaustive. */
object CardWidgetMap {
    fun link(card: CardId): WidgetLink = when (card) {
        CardId.TodayHeader -> WidgetLink(WidgetKind.Today, WidgetStyle.TodayRing)
        CardId.TodayActivity -> WidgetLink(WidgetKind.LastActivity, WidgetStyle.ActivityHero)
        CardId.HeartRate -> WidgetLink(WidgetKind.HeartRate, WidgetStyle.HeartRange)
        CardId.RestingHr -> WidgetLink(WidgetKind.HeartRate, WidgetStyle.HeartSpark)
        CardId.Stress -> WidgetLink(WidgetKind.Stress, WidgetStyle.StressPill)
        CardId.Sleep -> WidgetLink(WidgetKind.Sleep, WidgetStyle.SleepBar)
        CardId.Hrv -> WidgetLink(WidgetKind.Hrv, WidgetStyle.HrvRange)
        CardId.WeeklyLoad -> WidgetLink(WidgetKind.TrainingLoad, WidgetStyle.LoadBars)
        CardId.TrainingStatus -> WidgetLink(WidgetKind.TrainingLoad, WidgetStyle.LoadDial)
        CardId.Recovery -> WidgetLink(WidgetKind.Recovery, WidgetStyle.RecoveryRing)
        CardId.RunningFitness -> WidgetLink(WidgetKind.Fitness, WidgetStyle.FitnessVo2)
        CardId.Last7Days -> WidgetLink(WidgetKind.Overview, WidgetStyle.OverviewGrid)
        CardId.ActivitiesMonth -> WidgetLink(WidgetKind.WeeklyMileage, WidgetStyle.MileageBars)
        CardId.ActivityRow -> WidgetLink(WidgetKind.LastActivity, WidgetStyle.ActivityHero)
        CardId.FitTrainingStatus -> WidgetLink(WidgetKind.TrainingLoad, WidgetStyle.LoadTrend)
        CardId.FitRunning -> WidgetLink(WidgetKind.Fitness, WidgetStyle.FitnessVo2)
        CardId.FitRacePredictions -> WidgetLink(WidgetKind.Fitness, WidgetStyle.FitnessGrid)
        CardId.FitVo2 -> WidgetLink(WidgetKind.Fitness, WidgetStyle.FitnessVo2)
    }
}

/** Provided once by the scaffold; opens the shared "Add to home screen / Open in COROS" sheet. */
class WidgetActions(val open: (WidgetLink) -> Unit)
val LocalWidgetActions = staticCompositionLocalOf<WidgetActions?> { null }

/**
 * The one way a card gets the long-press behaviour. Taps keep their meaning ([onClick], or nothing), a long press gives a haptic
 * and opens the actions sheet, and TalkBack gets a custom action with the same label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.widgetActions(card: CardId, onClick: (() -> Unit)? = null, onClickLabel: String? = null): Modifier {
    val actions = LocalWidgetActions.current
    val haptic = LocalHapticFeedback.current
    val link = CardWidgetMap.link(card)
    if (actions == null) return if (onClick != null) this.clickable(onClickLabel = onClickLabel, onClick = onClick) else this
    return this.combinedClickable(
        onClick = onClick ?: {},
        onClickLabel = onClickLabel,
        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); actions.open(link) },
        onLongClickLabel = "Add to home screen",
    )
}
