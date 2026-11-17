package app.cairn.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The family of a workout, for its icon. Never a bare initial: anything unknown is [Workout]. */
enum class SportKind(val label: String) {
    Run("Run"), Walk("Walk"), Cycle("Cycle"), Swim("Swim"), Water("Water sport"), Strength("Strength"), Cardio("Cardio"),
    Yoga("Yoga"), Snow("Snow sport"), Ball("Ball sport"), Workout("Workout");

    companion object {
        private val keywords = listOf(
            Strength to listOf("strength", "gym", "weight", "lift", "crossfit", "kettlebell", "resistance"),
            Yoga to listOf("yoga", "pilates", "stretch", "mobility", "meditat"),
            Cardio to listOf("cardio", "hiit", "elliptical", "stair", "aerobic", "jump rope", "dance"),
            Swim to listOf("swim", "pool", "open water"),
            Water to listOf("row", "paddle", "kayak", "canoe", "surf", "sup", "sail"),
            Snow to listOf("ski", "snowboard", "snow"),
            Cycle to listOf("bike", "cycl", "ride", "spin"),
            Walk to listOf("walk", "hike", "trek", "climb"),
            Run to listOf("run", "jog", "treadmill", "trail"),
            Ball to listOf("tennis", "badminton", "squash", "padel", "football", "soccer", "basketball", "golf", "ball", "volley", "table"),
        )

        /** By name keywords first (case-insensitive), then by the COROS sport type family, else [Workout]. */
        fun of(sport: String?, sportType: Int?): SportKind {
            val n = sport?.lowercase().orEmpty().replace('_', ' ').replace('-', ' ')
            keywords.firstOrNull { (_, words) -> words.any { it in n } }?.let { return it.first }
            return when (sportType) {
                in 100..103 -> Run
                104, 105 -> Walk
                in 200..299 -> Cycle
                in 300..399 -> Swim
                in 400..499 -> Strength
                else -> Workout
            }
        }
    }
}

/** An accent-tinted round badge with the sport's icon. Decorative: the sport is also spoken in the card text. */
@Composable
fun SportBadge(kind: SportKind, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.defaultMinSize(size, size).clearAndSetSemantics { }) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(AppIcons.sport(kind), null, Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
