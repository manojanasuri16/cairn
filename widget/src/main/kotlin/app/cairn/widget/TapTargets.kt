package app.cairn.widget

/** Widgets whose body tap can be pointed at a COROS screen. */
enum class WidgetKind(val label: String, val cairnScreen: Screen) {
    Today("Today", Screen.Today),
    Recovery("Recovery", Screen.Today),
    LastActivity("Last activity", Screen.Activities),
    TrainingLoad("Training load", Screen.Fitness),
    Sleep("Sleep", Screen.Today),
    Fitness("Fitness", Screen.Fitness);

    companion object {
        fun parse(raw: String?): WidgetKind? = entries.firstOrNull { it.name == raw }
    }
}

/** Where a widget body tap goes. */
sealed interface TapTarget {
    /** COROS's own launch intent. */
    data object CorosHome : TapTarget
    /** The matching Cairn screen. */
    data object OpenCairn : TapTarget
    /** One COROS activity, by class name (the package is always [COROS_PACKAGE]). */
    data class CorosScreen(val className: String) : TapTarget
}

/** Persistence mapping and defaults. Stores class names only, nothing personal. */
object TapTargets {
    /** Known-good COROS screens per widget. Empty until supplied; add entries here, e.g. `WidgetKind.Sleep to TapTarget.CorosScreen("com.example.SleepActivity")`. */
    val KNOWN_DEFAULTS: Map<WidgetKind, TapTarget> = emptyMap()

    fun default(kind: WidgetKind): TapTarget = KNOWN_DEFAULTS[kind] ?: TapTarget.CorosHome

    fun storageKey(kind: WidgetKind): String = "tap_target_${kind.name}"

    private const val HOME = "coros_home"
    private const val CAIRN = "cairn"
    private const val SCREEN = "screen:"
    private val CLASS_NAME = Regex("[A-Za-z0-9_.$]{1,200}")

    fun encode(t: TapTarget): String = when (t) {
        TapTarget.CorosHome -> HOME
        TapTarget.OpenCairn -> CAIRN
        is TapTarget.CorosScreen -> SCREEN + t.className
    }

    /** Unknown, empty or malformed values fall back to the widget's default. */
    fun decode(raw: String?, kind: WidgetKind): TapTarget = when {
        raw == null -> default(kind)
        raw == HOME -> TapTarget.CorosHome
        raw == CAIRN -> TapTarget.OpenCairn
        raw.startsWith(SCREEN) -> raw.removePrefix(SCREEN).takeIf { CLASS_NAME.matches(it) }?.let { TapTarget.CorosScreen(it) } ?: default(kind)
        else -> default(kind)
    }
}

/** What PackageManager reports about one COROS activity. */
data class ActivityCandidate(
    val className: String,
    val label: String?,
    val exported: Boolean,
    val enabled: Boolean,
    val permission: String?,
)

data class CorosScreenOption(val className: String, val label: String?) {
    val simpleName: String get() = className.substringAfterLast('.')
    val title: String get() = label ?: simpleName
}

object CorosScreens {
    /** Only activities other apps may start: exported, enabled, no required permission. Sorted, de-duplicated. */
    fun launchable(all: List<ActivityCandidate>): List<CorosScreenOption> = all
        .filter { it.exported && it.enabled && it.permission.isNullOrBlank() && it.className.isNotBlank() }
        .distinctBy { it.className }
        .map { c -> CorosScreenOption(c.className, c.label?.trim()?.takeIf { it.isNotEmpty() && it != c.className }) }
        .sortedWith(compareBy({ it.title.lowercase() }, { it.className }))

    /** Case-insensitive match on label or class name; every whitespace-separated word must match. */
    fun filter(options: List<CorosScreenOption>, query: String): List<CorosScreenOption> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return options
        return options.filter { o ->
            val hay = (o.title + " " + o.className).lowercase()
            words.all { it in hay }
        }
    }
}

/** One attempt in the launch fallback chain. */
sealed interface LaunchStep {
    data class Screen(val className: String) : LaunchStep
    data object CorosLaunch : LaunchStep
    data object OpenCairn : LaunchStep
    /** Cairn's Keep data fresh screen with an explanation that COROS could not be opened. */
    data object Explain : LaunchStep

    /** True when COROS ends up in front, so the delayed refreshes should be scheduled. */
    val opensCoros: Boolean get() = this is Screen || this == CorosLaunch
}

object TapLaunchPlanner {
    /** Ordered attempts: chosen screen, COROS launch intent, then Cairn's explanation. */
    fun chain(target: TapTarget, corosInstalled: Boolean): List<LaunchStep> = when (target) {
        TapTarget.OpenCairn -> listOf(LaunchStep.OpenCairn, LaunchStep.Explain)
        TapTarget.CorosHome -> if (corosInstalled) listOf(LaunchStep.CorosLaunch, LaunchStep.Explain) else listOf(LaunchStep.Explain)
        is TapTarget.CorosScreen ->
            if (corosInstalled) listOf(LaunchStep.Screen(target.className), LaunchStep.CorosLaunch, LaunchStep.Explain) else listOf(LaunchStep.Explain)
    }

    /** Runs [chain] until one step reports success; any exception counts as failure. Returns the step that worked, or null. */
    fun execute(chain: List<LaunchStep>, run: (LaunchStep) -> Boolean): LaunchStep? =
        chain.firstOrNull { step -> try { run(step) } catch (_: Exception) { false } }
}
