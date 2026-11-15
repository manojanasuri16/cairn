package app.cairn.widget

// Thin extra receivers: one widget per launcher-picker size, all reusing the same GlanceAppWidget. Existing receivers keep their names.
class TodayWideWidgetReceiver : CairnReceiver() { override val glanceAppWidget = TodayWidget() }
class TodayLargeWidgetReceiver : CairnReceiver() { override val glanceAppWidget = TodayWidget() }
class RecoveryWideWidgetReceiver : CairnReceiver() { override val glanceAppWidget = RecoveryWidget() }
class RecoveryLargeWidgetReceiver : CairnReceiver() { override val glanceAppWidget = RecoveryWidget() }
class LastActivitySmallWidgetReceiver : CairnReceiver() { override val glanceAppWidget = LastActivityWidget() }
class LastActivityLargeWidgetReceiver : CairnReceiver() { override val glanceAppWidget = LastActivityWidget() }
class TrainingLoadSmallWidgetReceiver : CairnReceiver() { override val glanceAppWidget = TrainingLoadWidget() }
class TrainingLoadLargeWidgetReceiver : CairnReceiver() { override val glanceAppWidget = TrainingLoadWidget() }
class SleepWideWidgetReceiver : CairnReceiver() { override val glanceAppWidget = SleepWidget() }
class SleepLargeWidgetReceiver : CairnReceiver() { override val glanceAppWidget = SleepWidget() }
class FitnessSmallWidgetReceiver : CairnReceiver() { override val glanceAppWidget = FitnessWidget() }
class FitnessLargeWidgetReceiver : CairnReceiver() { override val glanceAppWidget = FitnessWidget() }
class OverviewSmallWidgetReceiver : CairnReceiver() { override val glanceAppWidget = OverviewWidget() }
class OverviewLargeWidgetReceiver : CairnReceiver() { override val glanceAppWidget = OverviewWidget() }
