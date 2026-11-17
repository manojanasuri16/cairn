package app.cairn.ui

import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.ThemeId
import app.cairn.widget.Bg
import app.cairn.widget.Corners
import app.cairn.widget.WidgetConfig
import kotlin.random.Random

/** A curated, complete look: the five colour and shape fields. Style, content, units and footer are never touched by a look. */
data class WidgetLook(
    val id: String,
    val name: String,
    val theme: ThemeId,
    val bg: Bg,
    val opacity: Int,
    val corners: Corners,
    val accent: AccentOption,
)

object WidgetLooks {
    val curated: List<WidgetLook> = listOf(
        WidgetLook("paper", "Paper", ThemeId.Clean, Bg.Solid, 100, Corners.System, AccentOption.Theme),
        WidgetLook("minimal", "Minimal", ThemeId.Minimal, Bg.Solid, 100, Corners.System, AccentOption.Theme),
        WidgetLook("night-run", "Night run", ThemeId.Midnight, Bg.Gradient, 100, Corners.System, AccentOption.Lime),
        WidgetLook("dusk", "Dusk", ThemeId.Dusk, Bg.Gradient, 100, Corners.ExtraRound, AccentOption.Theme),
        WidgetLook("forest", "Forest", ThemeId.Forest, Bg.Gradient, 100, Corners.System, AccentOption.Theme),
        WidgetLook("sunrise", "Sunrise", ThemeId.Sunrise, Bg.Gradient, 100, Corners.ExtraRound, AccentOption.Coral),
        WidgetLook("deep-ocean", "Deep ocean", ThemeId.Ocean, Bg.Gradient, 100, Corners.System, AccentOption.Sky),
        WidgetLook("glass", "Glass", ThemeId.Minimal, Bg.Transparent, 0, Corners.ExtraRound, AccentOption.Theme),
    )

    /** Extra pairings only "Surprise me" draws from; each keeps its theme's own tested accent. */
    val extras: List<WidgetLook> = listOf(
        WidgetLook("dawn", "Dawn", ThemeId.Dawn, Bg.Gradient, 100, Corners.System, AccentOption.Theme),
        WidgetLook("violet", "Violet", ThemeId.Violet, Bg.Gradient, 100, Corners.ExtraRound, AccentOption.Theme),
        WidgetLook("berry", "Berry", ThemeId.Berry, Bg.Gradient, 100, Corners.System, AccentOption.Theme),
        WidgetLook("dawn-solid", "Dawn solid", ThemeId.Dawn, Bg.Solid, 100, Corners.System, AccentOption.Theme),
    )

    fun byId(id: String?): WidgetLook? = curated.firstOrNull { it.id == id }

    /** The curated look this config shows exactly, or null ("Custom"). */
    fun of(cfg: WidgetConfig): WidgetLook? = curated.firstOrNull {
        cfg.theme == it.theme && cfg.bg == it.bg && cfg.opacity == it.opacity && cfg.corners == it.corners && cfg.accent == it.accent
    }

    /** A random look that is not [current] and not Glass (it depends on the wallpaper). */
    fun surprise(current: WidgetLook?, random: Random): WidgetLook =
        (curated + extras).filter { it.id != "glass" && it.id != current?.id }.let { it[random.nextInt(it.size)] }
}

/** Only the five look fields change; style, hidden parts, units, footer, metrics and goal are kept. */
fun WidgetConfig.withLook(l: WidgetLook): WidgetConfig =
    copy(theme = l.theme, bg = l.bg, opacity = l.opacity, corners = l.corners, accent = l.accent)

/** Back to the app-wide default look (inherit); the style and content choices are kept apart from the style, which returns to the default. */
fun WidgetConfig.resetLook(): WidgetConfig =
    copy(theme = null, bg = null, opacity = null, corners = Corners.System, accent = AccentOption.Theme, style = null)

/** One-level undo for the editor: "back to how it was when I opened it". */
object EditorUndo {
    fun canUndo(initial: WidgetConfig, current: WidgetConfig) = initial != current
}

/**
 * Applies a look to every placed widget and remembers the exact previous encoded strings so it can be undone.
 * [load] and [save] are injected so this stays a plain Kotlin class (unit-testable).
 */
class LookApplier<K>(
    private val ids: () -> List<Pair<Int, K>>,
    private val load: suspend (Int, K) -> WidgetConfig,
    private val save: suspend (Int, WidgetConfig) -> Unit,
    private val refresh: suspend () -> Unit,
    private val decode: (String, K) -> WidgetConfig,
) {
    private var snapshot: Map<Int, Pair<K, String>>? = null
    val canUndo: Boolean get() = snapshot != null

    suspend fun applyAll(look: WidgetLook): Int {
        val placed = ids()
        snapshot = placed.associate { (id, k) -> id to (k to load(id, k).encode()) }
        placed.forEach { (id, k) -> save(id, load(id, k).withLook(look)) }
        refresh()
        return placed.size
    }

    suspend fun undo(): Int {
        val snap = snapshot ?: return 0
        snap.forEach { (id, p) -> save(id, decode(p.second, p.first)) }
        snapshot = null
        refresh()
        return snap.size
    }
}
