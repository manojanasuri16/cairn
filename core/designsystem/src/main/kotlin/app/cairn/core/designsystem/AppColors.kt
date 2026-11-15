package app.cairn.core.designsystem

/** One accent = primary family plus hero gradient, for light and dark. Same values as the Daymark app. Surfaces stay neutral for every accent. */
enum class Accent(
    val id: String, val label: String,
    val light: Long, val dark: Long,
    val containerLight: Long, val containerDark: Long,
    val onContainerLight: Long, val onContainerDark: Long,
    val heroLight: Pair<Long, Long>, val heroDark: Pair<Long, Long>,
) {
    DUSK("dusk", "Dusk", 0xFF4C55C7, 0xFFB4BAFF, 0xFFE0E2FF, 0xFF2E3478, 0xFF0E1257, 0xFFE0E2FF, 0xFF5A63DA to 0xFF2E3590, 0xFF2D3375 to 0xFF171A3C),
    OCEAN("ocean", "Ocean", 0xFF1F6FB2, 0xFF9CCBFF, 0xFFD4E6FA, 0xFF0E4170, 0xFF001D36, 0xFFD4E6FA, 0xFF2A80C6 to 0xFF164A7E, 0xFF164272 to 0xFF0B2340),
    SUNSET("sunset", "Sunset", 0xFFC2532F, 0xFFFFB59E, 0xFFFFDBD0, 0xFF702410, 0xFF3A0B00, 0xFFFFDBD0, 0xFFE27350 to 0xFFA03A24, 0xFF7A2E1C to 0xFF3A150C),
    ROSE("rose", "Rose", 0xFFAE426D, 0xFFFFB0CB, 0xFFFFD9E4, 0xFF6B1E3D, 0xFF3E0021, 0xFFFFD9E4, 0xFFCA578B to 0xFF7E2650, 0xFF6E2346 to 0xFF351021),
    VIOLET("violet", "Violet", 0xFF7A4BC2, 0xFFD3B8FF, 0xFFEBDDFF, 0xFF45247A, 0xFF2A0067, 0xFFEBDDFF, 0xFF8C5ED8 to 0xFF4D2A8C, 0xFF4A2A80 to 0xFF22123E),
    AMBER("amber", "Amber", 0xFF8F5B00, 0xFFFFC96B, 0xFFFFE2B5, 0xFF573600, 0xFF2D1A00, 0xFFFFE2B5, 0xFFD08A1E to 0xFF8A5200, 0xFF6B4300 to 0xFF2E1C00),
    GRAPHITE("graphite", "Graphite", 0xFF3D4450, 0xFFC6CCD6, 0xFFE1E4EA, 0xFF353B45, 0xFF151A22, 0xFFE1E4EA, 0xFF525B6B to 0xFF262B33, 0xFF2E333C to 0xFF15181D),
    FOREST("forest", "Forest", 0xFF2B7A67, 0xFF7FD3B9, 0xFFCDEDE2, 0xFF14503F, 0xFF0B2E25, 0xFFCDEDE2, 0xFF2F8A72 to 0xFF1D5E4E, 0xFF1C5446 to 0xFF10302A);

    companion object {
        val DEFAULT = DUSK
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Semantic colours for neutral cards. */
data class CardColors(val good: Int, val warn: Int, val bad: Int, val cool: Int, val deep: Int, val light: Int, val rem: Int, val awake: Int)

/** Role colours (ARGB ints) so the pairs the screens use are contrast-tested on the JVM. */
data class AppRoles(
    val primary: Int, val onPrimary: Int, val container: Int, val onContainer: Int,
    val background: Int, val card: Int, val onSurface: Int, val surfaceVariant: Int, val onSurfaceVariant: Int,
    val containerLowest: Int, val containerLow: Int, val containerHigh: Int, val containerHighest: Int,
    val outline: Int, val outlineVariant: Int, val error: Int,
)

object AppColors {
    fun roles(a: Accent, dark: Boolean): AppRoles = if (dark) AppRoles(
        a.dark.toInt(), a.onContainerLight.toInt(), a.containerDark.toInt(), a.onContainerDark.toInt(),
        0xFF0F1012.toInt(), 0xFF191A1E.toInt(), 0xFFE4E4E8.toInt(), 0xFF2B2D33.toInt(), 0xFFAAADB6.toInt(),
        0xFF0B0C0E.toInt(), 0xFF16171A.toInt(), 0xFF212328.toInt(), 0xFF2B2D33.toInt(),
        0xFF5B5E66.toInt(), 0xFF30323A.toInt(), 0xFFF08A80.toInt(),
    ) else AppRoles(
        a.light.toInt(), 0xFFFFFFFF.toInt(), a.containerLight.toInt(), a.onContainerLight.toInt(),
        0xFFF7F7F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF1A1B1E.toInt(), 0xFFE6E6E9.toInt(), 0xFF5E6068.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFF0F0F2.toInt(), 0xFFE9E9EC.toInt(),
        0xFFC3C4CA.toInt(), 0xFFE0E0E4.toInt(), 0xFFC4453B.toInt(),
    )

    /** Softened Daymark tones (clean / partial / relapse) plus four harmonious sleep-stage hues, readable on the neutral cards. */
    fun card(dark: Boolean): CardColors = if (dark) CardColors(
        good = 0xFF4CC38F.toInt(), warn = 0xFFE8B964.toInt(), bad = 0xFFF07C72.toInt(), cool = 0xFF7CC4F5.toInt(),
        deep = 0xFF9AA3FF.toInt(), light = 0xFF7CC4F5.toInt(), rem = 0xFFF0A0CB.toInt(), awake = 0xFFF2B87A.toInt(),
    ) else CardColors(
        good = 0xFF2F8F68.toInt(), warn = 0xFFB07A14.toInt(), bad = 0xFFC9473C.toInt(), cool = 0xFF2A7FB8.toInt(),
        deep = 0xFF4C55C7.toInt(), light = 0xFF2A7FB8.toInt(), rem = 0xFFAE426D.toInt(), awake = 0xFFB8641A.toInt(),
    )

    /** The accent's hero gradient with white text, fitted so text keeps AA; [accentTint] is its light tint for rings on the gradient. */
    fun hero(a: Accent, dark: Boolean): Palette {
        val (s, e) = if (dark) a.heroDark else a.heroLight
        return Palettes.hero(s.toInt(), e.toInt(), dark, Contrast.blend(0xFFFFFFFF.toInt(), (if (dark) a.dark else a.light).toInt(), 0.7f))
    }
}
