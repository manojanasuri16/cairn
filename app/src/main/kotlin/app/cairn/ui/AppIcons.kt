package app.cairn.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small self-contained icon set (24 dp Material outlines as path data), so no icon library has to ship. */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black),
    ).build()

    val Sync = icon("sync", "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z")
    val Clock = icon("clock", "M12,2C6.5,2 2,6.5 2,12s4.5,10 10,10 10,-4.5 10,-10S17.5,2 12,2zM12,20c-4.4,0 -8,-3.6 -8,-8s3.6,-8 8,-8 8,3.6 8,8 -3.6,8 -8,8zM12.5,7H11v6l5.2,3.2 0.8,-1.3 -4.5,-2.7V7z")
    val Check = icon("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val Close = icon("close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val Expand = icon("expand", "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z")
    val Warning = icon("warning", "M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z")
    val Palette = icon("palette", "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5L16,16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z")
    val Style = icon("style", "M3,13h8L11,3L3,3v10zM3,21h8v-6L3,15v6zM13,21h8L21,11h-8v10zM13,3v6h8L21,3h-8z")
    val Content = icon("content", "M3,13h2v-2L3,11v2zM3,17h2v-2L3,15v2zM3,9h2L5,7L3,7v2zM7,13h14v-2L7,11v2zM7,17h14v-2L7,15v2zM7,7v2h14L21,7L7,7z")
    val Size = icon("size", "M21,11V3h-8l3.29,3.29 -10,10L3,13v8h8l-3.29,-3.29 10,-10z")
    val Touch = icon("touch", "M9,11.24V7.5C9,6.12 10.12,5 11.5,5S14,6.12 14,7.5v3.74c1.21,-0.81 2,-2.18 2,-3.74C16,5.01 13.99,3 11.5,3S7,5.01 7,7.5c0,1.56 0.79,2.93 2,3.74zM18.84,15.87l-4.54,-2.26c-0.17,-0.07 -0.35,-0.11 -0.54,-0.11H13v-6c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,6.67 10,7.5v10.74l-3.43,-0.72c-0.08,-0.01 -0.15,-0.03 -0.24,-0.03 -0.31,0 -0.59,0.13 -0.79,0.33l-0.79,0.8 4.94,4.94c0.27,0.27 0.65,0.44 1.06,0.44h6.79c0.75,0 1.33,-0.55 1.44,-1.28l0.75,-5.27c0.01,-0.07 0.02,-0.14 0.02,-0.2 0,-0.62 -0.38,-1.16 -0.91,-1.38z")
    val Open = icon("open", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    val Link = icon("link", "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z")
    val Bell = icon("bell", "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z")
    val Ruler = icon("ruler", "M21,6H3c-1.1,0 -2,0.9 -2,2v8c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM21,16H3V8h2v4h2V8h2v4h2V8h2v4h2V8h2v4h2V8h2v8z")
    val Widgets = icon("widgets", "M13,13v8h8v-8h-8zM3,21h8v-8H3v8zM3,3v8h8V3H3zM16.66,1.69L11,7.34 16.66,13l5.66,-5.66 -5.66,-5.65z")
    val Build = icon("build", "M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.6,4.7C0.4,7.1 0.9,10.1 2.9,12.1c1.9,1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z")
    val Delete = icon("delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    val ChevronRight = icon("chevron", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    val SystemUpdate = icon("system_update", "M17,1.01L7,1c-1.1,0 -2,0.9 -2,2v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3c0,-1.1 -0.9,-1.99 -2,-1.99zM17,19H7V5h10v14zM16,13h-3V8h-2v5H8l4,4 4,-4z")
    val More = icon("more", "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
    val Add = icon("add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")

    /** Falls back to a plain disc if a path ever fails to parse, so a bad icon can never crash a screen. */
    private fun safe(name: String, path: String): ImageVector =
        runCatching { icon(name, path) }.getOrElse { icon(name, "M12,4a8,8 0 1,0 0.001,0z") }

    private val RunIcon = safe("run", "M13.49,5.48c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM9.89,19.38l1,-4.4 2.1,2v6h2v-7.5l-2.1,-2 0.6,-3c1.3,1.5 3.3,2.5 5.5,2.5v-2c-1.9,0 -3.5,-1 -4.3,-2.4l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1 -0.3,0 -0.5,0.1 -0.8,0.1l-5.2,2.2v4.7h2v-3.4l1.8,-0.7 -1.6,8.1 -4.9,-1 -0.4,2 7,1.4z")
    private val WalkIcon = safe("walk", "M13.5,5.5c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM9.8,8.9L7,23h2.1l1.8,-8 2.1,2v6h2v-7.5l-2.1,-2 0.6,-3C14.8,12 16.8,13 19,13v-2c-1.9,0 -3.5,-1 -4.3,-2.4l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1 -0.3,0 -0.5,0.1 -0.8,0.1L6,8.3V13h2V9.6l1.8,-0.7")
    private val CycleIcon = safe("cycle", "M15.5,5.5c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM5,12c-2.8,0 -5,2.2 -5,5s2.2,5 5,5 5,-2.2 5,-5 -2.2,-5 -5,-5zM5,20.5c-1.9,0 -3.5,-1.6 -3.5,-3.5s1.6,-3.5 3.5,-3.5 3.5,1.6 3.5,3.5 -1.6,3.5 -3.5,3.5zM10.8,10.5l2.4,-2.4 0.8,0.8c1.3,1.3 3,2.1 5.1,2.1V9c-1.5,0 -2.7,-0.6 -3.6,-1.5l-1.9,-1.9c-0.5,-0.4 -1,-0.6 -1.6,-0.6s-1.1,0.2 -1.4,0.6L7.8,8.4c-0.4,0.4 -0.6,0.9 -0.6,1.4 0,0.6 0.2,1.1 0.6,1.4L11,14v5h2v-6.2l-2.2,-2.3zM19,12c-2.8,0 -5,2.2 -5,5s2.2,5 5,5 5,-2.2 5,-5 -2.2,-5 -5,-5zM19,20.5c-1.9,0 -3.5,-1.6 -3.5,-3.5s1.6,-3.5 3.5,-3.5 3.5,1.6 3.5,3.5 -1.6,3.5 -3.5,3.5z")
    private val SwimIcon = safe("swim", "M16,3.5a2,2 0 1,0 0.001,0zM2,15c2,0 2,-2 4,-2s2,2 4,2 2,-2 4,-2 2,2 4,2 2,-2 4,-2v3c-2,0 -2,2 -4,2s-2,-2 -4,-2 -2,2 -4,2 -2,-2 -4,-2 -2,2 -4,2zM5,10l7,-3 3,4 -2,1.5 -2,-2.5 -4,1.5z")
    private val StrengthIcon = safe("strength", "M20.57,14.86L22,13.43 20.57,12 17,15.57 8.43,7 12,3.43 10.57,2 9.14,3.43 7.71,2 5.57,4.14 4.14,2.71 2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57 3.43,12 7,8.43 15.57,17 12,20.57 13.43,22l1.43,-1.43L16.29,22l2.14,-2.14 1.43,1.43 1.43,-1.43 -1.43,-1.43L22,16.29z")
    private val CardioIcon = safe("cardio", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
    private val YogaIcon = safe("yoga", "M12,2a2,2 0 1,0 0.001,0zM3,9h18v2h-7v4l3,7h-2.4l-2.6,-6 -2.6,6H7l3,-7v-4H3z")
    private val SnowIcon = safe("snow", "M11,2h2v20h-2zM2,11h20v2H2zM4.9,3.5l15.6,15.6 -1.4,1.4L3.5,4.9zM19.1,3.5l1.4,1.4L4.9,20.5 3.5,19.1z")
    private val BallIcon = safe("ball", "M12,2a10,10 0 1,0 0.001,0zM12,4a8,8 0 1,1 -0.001,0zM11,6h2v12h-2z")
    private val WorkoutIcon = safe("workout", "M12,2a2.2,2.2 0 1,0 0.001,0zM7,8h10v2h-3.5v12h-3V10H7z")

    /** One icon per sport family; the same mapping everywhere an activity is shown. */
    fun sport(kind: app.cairn.ui.SportKind): ImageVector = when (kind) {
        app.cairn.ui.SportKind.Run -> RunIcon
        app.cairn.ui.SportKind.Walk -> WalkIcon
        app.cairn.ui.SportKind.Cycle -> CycleIcon
        app.cairn.ui.SportKind.Swim, app.cairn.ui.SportKind.Water -> SwimIcon
        app.cairn.ui.SportKind.Strength -> StrengthIcon
        app.cairn.ui.SportKind.Cardio -> CardioIcon
        app.cairn.ui.SportKind.Yoga -> YogaIcon
        app.cairn.ui.SportKind.Snow -> SnowIcon
        app.cairn.ui.SportKind.Ball -> BallIcon
        app.cairn.ui.SportKind.Workout -> WorkoutIcon
    }
}
