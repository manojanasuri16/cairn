package app.cairn.core.model

import java.time.Duration
import java.time.Instant

/** Where a value came from. */
enum class DataSource { COROS_MCP, HEALTH_CONNECT }

/**
 * Provenance of a value: [source] plus [sourceUpdatedAt], the instant the data was fetched from / was
 * valid at the source (not when we last looked at our cache).
 */
data class Stamp(val source: DataSource, val sourceUpdatedAt: Instant) {
    fun freshness(now: Instant, freshLimit: Duration = Freshness.DEFAULT_FRESH_LIMIT, agingLimit: Duration = Freshness.DEFAULT_AGING_LIMIT): Freshness =
        Freshness.fromAge(Duration.between(sourceUpdatedAt, now), freshLimit, agingLimit)
}

/** A single value with its provenance. */
data class SourcedValue<out T>(val value: T, val source: DataSource, val sourceUpdatedAt: Instant) {
    val stamp: Stamp get() = Stamp(source, sourceUpdatedAt)
    fun freshness(now: Instant, freshLimit: Duration = Freshness.DEFAULT_FRESH_LIMIT, agingLimit: Duration = Freshness.DEFAULT_AGING_LIMIT): Freshness =
        stamp.freshness(now, freshLimit, agingLimit)
    fun <R> map(f: (T) -> R): SourcedValue<R> = SourcedValue(f(value), source, sourceUpdatedAt)
}

/** A [SourcedValue] resolved against "now", ready for display. */
data class FreshValue<out T>(val value: T, val source: DataSource, val sourceUpdatedAt: Instant, val freshness: Freshness)

fun <T> SourcedValue<T>.withFreshness(now: Instant): FreshValue<T> = FreshValue(value, source, sourceUpdatedAt, freshness(now))

/** Picks the value with the newest [SourcedValue.sourceUpdatedAt]; on a tie [preferred] wins. */
fun <T> newest(a: SourcedValue<T>?, b: SourcedValue<T>?, preferred: DataSource = DataSource.COROS_MCP): SourcedValue<T>? = when {
    a == null -> b
    b == null -> a
    a.sourceUpdatedAt > b.sourceUpdatedAt -> a
    b.sourceUpdatedAt > a.sourceUpdatedAt -> b
    a.source == preferred -> a
    else -> b
}
