package app.cairn.core.model

import java.time.Duration

enum class DistanceUnit {
    Km, Mi;

    companion object {
        fun parse(raw: String?): DistanceUnit = entries.firstOrNull { it.name == raw } ?: Km
    }
}

/** How values are shown: distance unit plus the data-age tier limits. Shared by the app and the widgets. */
data class DisplayPrefs(
    val unit: DistanceUnit = DistanceUnit.Km,
    val freshHours: Int = DEFAULT_FRESH,
    val agingHours: Int = DEFAULT_AGING,
) {
    val freshLimit: Duration get() = Duration.ofHours(freshHours.toLong())
    val agingLimit: Duration get() = Duration.ofHours(agingHours.toLong())

    companion object {
        val FRESH_CHOICES = listOf(1, 3, 6)
        val AGING_CHOICES = listOf(6, 12, 24)
        const val DEFAULT_FRESH = 3
        const val DEFAULT_AGING = 12

        /** Normalises stored values; the aging limit never drops below the fresh limit. */
        fun of(unit: String?, fresh: Int?, aging: Int?): DisplayPrefs {
            val f = if (fresh in FRESH_CHOICES) fresh!! else DEFAULT_FRESH
            val a = (if (aging in AGING_CHOICES) aging!! else DEFAULT_AGING).coerceAtLeast(f)
            return DisplayPrefs(DistanceUnit.parse(unit), f, a)
        }
    }
}
