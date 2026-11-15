package app.cairn.core.common

import java.time.Duration
import java.time.Instant

/** Formats how long ago something was updated, e.g. "Updated 2h ago". */
object RelativeAge {
    fun format(updatedAt: Instant, now: Instant): String = format(Duration.between(updatedAt, now))

    fun format(age: Duration): String {
        val minutes = age.toMinutes()
        return when {
            minutes < 1 -> "Updated just now"
            minutes < 60 -> "Updated ${minutes}m ago"
            age.toHours() < 24 -> "Updated ${age.toHours()}h ago"
            else -> "Updated ${age.toDays()}d ago"
        }
    }
}
