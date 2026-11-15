package app.cairn.source.healthconnect

import java.time.Instant

/** Minimal, health-value-free view of one record: only metadata. */
data class RecordMeta(val lastModified: Instant, val originPackage: String)

/** What the inspector shows per record type. */
data class RecordTypeSummary(
    val count: Int,
    val latestModified: Instant?,
    val origins: Set<String>,
)

object RecordAggregator {
    fun summarize(records: List<RecordMeta>): RecordTypeSummary = RecordTypeSummary(
        count = records.size,
        latestModified = records.maxOfOrNull { it.lastModified },
        origins = records.mapTo(sortedSetOf()) { it.originPackage },
    )
}

enum class SourceKind { Coros, Other, None }

fun RecordTypeSummary.sourceKind(corosPackage: String): SourceKind = when {
    corosPackage in origins -> SourceKind.Coros
    origins.isNotEmpty() -> SourceKind.Other
    else -> SourceKind.None
}
