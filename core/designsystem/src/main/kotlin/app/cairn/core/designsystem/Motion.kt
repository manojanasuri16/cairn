package app.cairn.core.designsystem

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The one spacing grid: multiples of 4 dp. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** Critically damped by default (no overshoot). A slight bounce is reserved for results of a flick. */
object CairnMotion {
    const val STIFFNESS = 320f
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = STIFFNESS)
    fun <T> flick(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = STIFFNESS)

    /** Springs normally, snaps when the user removed animations. */
    fun <T> settle(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else settle()
}

val LocalReducedMotion = compositionLocalOf { false }

/** True when "Remove animations" (animator duration scale 0) is on. Slides and springs become cross-fades. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}
