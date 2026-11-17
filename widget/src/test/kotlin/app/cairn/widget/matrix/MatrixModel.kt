package app.cairn.widget.matrix

import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.ThemeId
import app.cairn.widget.Bg
import app.cairn.widget.Corners
import app.cairn.widget.Part
import app.cairn.widget.OverviewId
import app.cairn.widget.UnitsPref
import app.cairn.widget.WeeklyGoal
import app.cairn.widget.WidgetConfig
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetStyle

/** Sampling tiers of docs/widget-quality.md 2.4. */
enum class Tier { Core, Cells, States, Looks, Pairwise, Parcel, Full }

/** A real cell the launcher lays the RemoteViews out in, in dp. [source] says where the number came from. */
data class Cell(val wDp: Float, val hDp: Float, val source: String) {
    val tag: String get() = "$source-${wDp.toInt()}x${hDp.toInt()}"
}

/** Everything the device environment contributes: font scale, locale, direction and density. */
data class Env(val fontScale: Float, val locale: String, val rtl: Boolean, val densityDpi: Int, val dark: Boolean) {
    /** Robolectric qualifier string (absolute: it replaces the previous one). */
    val qualifiers: String
        get() {
            val parts = locale.split("-")
            val loc = if (parts.size == 2) "${parts[0]}-r${parts[1]}" else parts[0]
            val dir = if (rtl) "ldrtl" else "ldltr"
            val dpi = when (densityDpi) { 320 -> "xhdpi"; 420 -> "420dpi"; 560 -> "560dpi"; else -> "xhdpi" }
            return "$loc-$dir-${if (dark) "night" else "notnight"}-$dpi"
        }
    val tag: String get() = "fs$fontScale-$locale${if (rtl) "-rtl" else ""}-${densityDpi}dpi"
    val groupKey: String get() = "$qualifiers|$fontScale"
}

/** One of the 11 looks (10 themes plus "on wallpaper") with background and opacity. */
data class LookSpec(
    val theme: ThemeId, val bg: Bg, val opacity: Int, val accent: AccentOption = AccentOption.Theme, val corners: Corners = Corners.System,
    val onWallpaperLook: Boolean = false,
) {
    val id: String get() = (if (onWallpaperLook) "OnWallpaper" else theme.name) + "-" + bg.name + (if (bg == Bg.Solid || bg == Bg.Gradient) opacity.toString() else "") +
        (if (accent != AccentOption.Theme) "-" + accent.name else "") + (if (corners != Corners.System) "-XR" else "")
}

enum class DataState {
    Full, NoData, Partial, Fresh, Aging, Stale, SignedOut, NeedsSignIn, Syncing, SyncError, Huge, Zero, LongText, HcOnly, Loading;
}

data class ConfigTweaks(
    val hidden: Set<Part> = emptySet(), val units: UnitsPref = UnitsPref.Inherit, val compactFooter: Boolean = false,
    val hideSyncIcon: Boolean = false, val metrics: List<OverviewId>? = null, val weeklyGoal: WeeklyGoal? = null,
) {
    val tag: String
        get() = buildList {
            if (hidden.isNotEmpty()) add("h" + hidden.joinToString("+") { it.name.take(3) })
            if (units != UnitsPref.Inherit) add(units.name)
            if (compactFooter) add("cf")
            if (hideSyncIcon) add("hs")
            metrics?.let { add("m" + it.size) }
            weeklyGoal?.let { add("g" + it.encode()) }
        }.ifEmpty { listOf("default") }.joinToString("+")
}

data class MatrixCase(
    val kind: WidgetKind, val style: WidgetStyle, val cell: Cell, val env: Env, val look: LookSpec,
    val data: DataState, val tweaks: ConfigTweaks = ConfigTweaks(),
    val wallpapers: List<Int> = listOf(0xFF808080.toInt()),
    /** Where the cell came from, for the tier summaries ("core", "cells", ...). */
    val tier: Tier = Tier.Core,
) {
    /** Stable, file-system-safe id. */
    val id: String
        get() = "${kind.name}.${style.name}@${cell.tag.replace(':', '_')}_${env.tag}_${look.id}${if (env.dark) "-D" else "-L"}_${data.name}_${tweaks.tag}".replace(Regex("[^A-Za-z0-9._@+=-]"), "_")

    fun config(): WidgetConfig = WidgetConfig(
        theme = look.theme, style = style, bg = look.bg, opacity = look.opacity, corners = look.corners, accent = look.accent,
        hidden = tweaks.hidden, units = tweaks.units, compactFooter = tweaks.compactFooter, hideSyncIcon = tweaks.hideSyncIcon,
        metrics = tweaks.metrics, weeklyGoal = tweaks.weeklyGoal,
    )
}

data class LauncherProfile(val name: String, val wPitch: Int, val wInset: Int, val hPitch: Int, val hInset: Int) {
    fun w(n: Int) = (wPitch * n - wInset).toFloat()
    fun h(n: Int) = (hPitch * n - hInset).toFloat()
}

data class ProviderLimits(val minW: Float, val minH: Float, val maxW: Float, val maxH: Float)
