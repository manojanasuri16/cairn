package app.cairn.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The sizes the widget studio offers. Each maps to a launcher provider whose default cell size matches. */
enum class WidgetSize(val label: String, val cells: String, val cw: Int, val ch: Int, val preview: DpSize) {
    Small("Small", "2x2", 2, 2, DpSize(160.dp, 160.dp)),
    Wide("Wide", "4x2", 4, 2, DpSize(320.dp, 160.dp)),
    Large("Large", "4x3", 4, 3, DpSize(320.dp, 240.dp));

    /** Launcher-reported size of this many cells: 70 dp per cell minus 30 dp (4x2 is 250 x 110 dp, within the provider's minimum). */
    val cellDp: DpSize get() = DpSize(cellDpOf(cw).dp, cellDpOf(ch).dp)

    /** Typical size a launcher gives a placed widget of this many cells (74 dp pitch minus 24 dp): 4x2 is 272 x 124 dp. Used for the pin dialog preview. */
    val pinDp: DpSize get() = DpSize((74 * cw - 24).dp, (74 * ch - 24).dp)

    companion object {
        fun cellDpOf(cells: Int): Int = 70 * cells - 30
        /** Inverse estimate: how many cells a launcher thinks [dp] covers. */
        fun cellsOf(dp: Float): Int = ((dp + 30f) / 70f).toInt().coerceAtLeast(1)
        fun parse(raw: String?): WidgetSize? = entries.firstOrNull { it.name == raw }
    }
}

/** Which receiver (launcher provider) serves a kind at a size, and the reverse. Pure, so the choice is testable. */
object WidgetVariants {
    /** The size each of the original receivers has always provided. They are never renamed or removed. */
    fun defaultSize(kind: WidgetKind): WidgetSize = when (kind) {
        WidgetKind.Today, WidgetKind.Recovery, WidgetKind.Sleep, WidgetKind.Hrv, WidgetKind.HeartRate, WidgetKind.Stress -> WidgetSize.Small
        WidgetKind.LastActivity, WidgetKind.TrainingLoad, WidgetKind.Fitness, WidgetKind.Overview, WidgetKind.WeeklyMileage -> WidgetSize.Wide
    }

    private fun original(kind: WidgetKind) = when (kind) {
        WidgetKind.Today -> "TodayWidgetReceiver"; WidgetKind.Recovery -> "RecoveryWidgetReceiver"
        WidgetKind.LastActivity -> "LastActivityWidgetReceiver"; WidgetKind.TrainingLoad -> "TrainingLoadWidgetReceiver"
        WidgetKind.Sleep -> "SleepWidgetReceiver"; WidgetKind.Fitness -> "FitnessWidgetReceiver"; WidgetKind.Overview -> "OverviewWidgetReceiver"
        WidgetKind.Hrv -> "HrvWidgetReceiver"; WidgetKind.HeartRate -> "HeartRateWidgetReceiver"
        WidgetKind.Stress -> "StressWidgetReceiver"; WidgetKind.WeeklyMileage -> "WeeklyMileageWidgetReceiver"
    }

    /** Simple class name of the receiver whose default cell size is [size]. */
    fun receiverName(kind: WidgetKind, size: WidgetSize): String =
        if (size == defaultSize(kind)) original(kind) else "${kind.name}${size.name}WidgetReceiver"

    /** Every provider receiver (11 kinds x 3 sizes = 33). */
    fun allReceivers(): List<Class<*>> = WidgetKind.entries.flatMap { k -> WidgetSize.entries.map { receiverClass(k, it) } }.distinct()

    fun receiverClass(kind: WidgetKind, size: WidgetSize): Class<*> = when (receiverName(kind, size)) {
        "TodayWidgetReceiver" -> TodayWidgetReceiver::class.java
        "RecoveryWidgetReceiver" -> RecoveryWidgetReceiver::class.java
        "LastActivityWidgetReceiver" -> LastActivityWidgetReceiver::class.java
        "TrainingLoadWidgetReceiver" -> TrainingLoadWidgetReceiver::class.java
        "SleepWidgetReceiver" -> SleepWidgetReceiver::class.java
        "FitnessWidgetReceiver" -> FitnessWidgetReceiver::class.java
        "OverviewWidgetReceiver" -> OverviewWidgetReceiver::class.java
        "TodayWideWidgetReceiver" -> TodayWideWidgetReceiver::class.java
        "TodayLargeWidgetReceiver" -> TodayLargeWidgetReceiver::class.java
        "RecoveryWideWidgetReceiver" -> RecoveryWideWidgetReceiver::class.java
        "RecoveryLargeWidgetReceiver" -> RecoveryLargeWidgetReceiver::class.java
        "LastActivitySmallWidgetReceiver" -> LastActivitySmallWidgetReceiver::class.java
        "LastActivityLargeWidgetReceiver" -> LastActivityLargeWidgetReceiver::class.java
        "TrainingLoadSmallWidgetReceiver" -> TrainingLoadSmallWidgetReceiver::class.java
        "TrainingLoadLargeWidgetReceiver" -> TrainingLoadLargeWidgetReceiver::class.java
        "SleepWideWidgetReceiver" -> SleepWideWidgetReceiver::class.java
        "SleepLargeWidgetReceiver" -> SleepLargeWidgetReceiver::class.java
        "FitnessSmallWidgetReceiver" -> FitnessSmallWidgetReceiver::class.java
        "FitnessLargeWidgetReceiver" -> FitnessLargeWidgetReceiver::class.java
        "OverviewSmallWidgetReceiver" -> OverviewSmallWidgetReceiver::class.java
        "HrvWidgetReceiver" -> HrvWidgetReceiver::class.java
        "HrvWideWidgetReceiver" -> HrvWideWidgetReceiver::class.java
        "HrvLargeWidgetReceiver" -> HrvLargeWidgetReceiver::class.java
        "HeartRateWidgetReceiver" -> HeartRateWidgetReceiver::class.java
        "HeartRateWideWidgetReceiver" -> HeartRateWideWidgetReceiver::class.java
        "HeartRateLargeWidgetReceiver" -> HeartRateLargeWidgetReceiver::class.java
        "StressWidgetReceiver" -> StressWidgetReceiver::class.java
        "StressWideWidgetReceiver" -> StressWideWidgetReceiver::class.java
        "StressLargeWidgetReceiver" -> StressLargeWidgetReceiver::class.java
        "WeeklyMileageWidgetReceiver" -> WeeklyMileageWidgetReceiver::class.java
        "WeeklyMileageSmallWidgetReceiver" -> WeeklyMileageSmallWidgetReceiver::class.java
        "WeeklyMileageLargeWidgetReceiver" -> WeeklyMileageLargeWidgetReceiver::class.java
        else -> OverviewLargeWidgetReceiver::class.java
    }

    /** Which widget a launcher provider (receiver simple or full class name) belongs to; any size variant maps to its kind. */
    fun kindOf(receiverClassName: String?): WidgetKind? {
        val n = receiverClassName?.substringAfterLast('.') ?: return null
        if (!n.endsWith("WidgetReceiver")) return null
        val stem = n.removeSuffix("WidgetReceiver")
        // Longest kind name first so "TrainingLoad..." never matches a shorter prefix.
        return WidgetKind.entries.sortedByDescending { it.name.length }.firstOrNull { k ->
            stem == k.name || WidgetSize.entries.any { stem == k.name + it.name }
        }
    }

    /** The size a placed provider was created for; the original receivers report their default. */
    fun sizeOf(receiverClassName: String?): WidgetSize? {
        val kind = kindOf(receiverClassName) ?: return null
        val stem = receiverClassName!!.substringAfterLast('.').removeSuffix("WidgetReceiver")
        return WidgetSize.entries.firstOrNull { stem == kind.name + it.name } ?: defaultSize(kind)
    }

    /** All variants of [kind] with their picker labels, e.g. "Today (2x2)". */
    fun pickerLabel(kind: WidgetKind, size: WidgetSize) = "${kind.label} (${size.cells})"

    /** The size to start the studio on: the kind's natural size. */
    fun suggested(kind: WidgetKind): WidgetSize = defaultSize(kind)
}
