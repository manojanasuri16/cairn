package app.cairn.core.model

import java.time.Duration

/** How trustworthy a displayed value is, based on the age of the last successful update. */
enum class Freshness {
    Fresh,
    Aging,
    Stale;

    companion object {
        val DEFAULT_FRESH_LIMIT: Duration = Duration.ofHours(3)
        val DEFAULT_AGING_LIMIT: Duration = Duration.ofHours(12)

        /** Fresh below [freshLimit], Aging below [agingLimit], Stale otherwise. Negative ages count as Fresh. */
        fun fromAge(
            age: Duration,
            freshLimit: Duration = DEFAULT_FRESH_LIMIT,
            agingLimit: Duration = DEFAULT_AGING_LIMIT,
        ): Freshness {
            require(freshLimit <= agingLimit) { "freshLimit must not exceed agingLimit" }
            return when {
                age < freshLimit -> Fresh
                age < agingLimit -> Aging
                else -> Stale
            }
        }
    }
}
