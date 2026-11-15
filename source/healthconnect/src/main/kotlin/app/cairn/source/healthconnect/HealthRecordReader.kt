package app.cairn.source.healthconnect

import java.time.Instant

/** A record type the inspector knows about. [key] is the stable id; [label] is for display. */
data class RecordTypeInfo(val key: String, val label: String)

/** Small read-only seam so the inspector can be tested with a fake. */
interface HealthRecordReader {
    val types: List<RecordTypeInfo>

    /** Permissions (read) currently granted, as permission strings. */
    suspend fun grantedPermissions(): Set<String>

    /** Metadata of all records of [type] modified/occurring in [from, to), across all pages. */
    suspend fun readMeta(type: RecordTypeInfo, from: Instant, to: Instant): List<RecordMeta>
}

data class TypeRow(val type: RecordTypeInfo, val summary: RecordTypeSummary?, val error: Boolean = false)

class HealthInspector(private val reader: HealthRecordReader) {
    suspend fun inspect(now: Instant, days: Long = 30): List<TypeRow> {
        val from = now.minusSeconds(days * 86_400)
        return reader.types.map { t ->
            try {
                TypeRow(t, RecordAggregator.summarize(reader.readMeta(t, from, now)))
            } catch (e: Exception) {
                // Missing permission or unsupported type: show as unavailable, never crash.
                TypeRow(t, null, error = true)
            }
        }
    }
}
