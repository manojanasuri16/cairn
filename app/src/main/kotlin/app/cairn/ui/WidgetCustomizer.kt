package app.cairn.ui

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.cairn.core.designsystem.AccentOption
import app.cairn.core.designsystem.Spacing
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.model.DistanceUnit
import app.cairn.widget.AppAppearance
import app.cairn.widget.Bg
import app.cairn.widget.Corners
import app.cairn.widget.OverviewId
import app.cairn.widget.Part
import app.cairn.widget.ResolvedConfig
import app.cairn.widget.TapTarget
import app.cairn.widget.UnitsPref
import app.cairn.widget.WeeklyGoal
import app.cairn.widget.WidgetCatalog
import app.cairn.widget.WidgetConfig
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetSize
import app.cairn.widget.WidgetStyle

/** The five customisation sections, one shown at a time. */
enum class CustomSection(val label: String, val icon: ImageVector, val hint: String) {
    Style("Style", AppIcons.Style, "How the widget is laid out."),
    Colour("Colour", AppIcons.Palette, "The palette and background behind the numbers."),
    Content("Content", AppIcons.Content, "What the widget shows."),
    Size("Size", AppIcons.Size, "How much room it takes on the home screen."),
    Tap("Tap", AppIcons.Touch, "What happens when you tap the widget."),
}

/** One plain-language line per style. */
fun styleHint(s: WidgetStyle): String = when (s) {
    WidgetStyle.TodayRing -> "Steps ring with your numbers beside it"
    WidgetStyle.TodayStack -> "Big step count with a progress bar"
    WidgetStyle.TodayStripe -> "One compact row of icons"
    WidgetStyle.RecoveryRing -> "A ring that fills as you recover"
    WidgetStyle.RecoveryGauge -> "A half-circle gauge"
    WidgetStyle.RecoveryPill -> "A big number and a status pill"
    WidgetStyle.ActivityHero -> "Distance first, details below"
    WidgetStyle.ActivityGrid -> "Distance, time, pace and heart rate in tiles"
    WidgetStyle.ActivityLine -> "Everything on a single line"
    WidgetStyle.LoadBars -> "Seven bars, one per day"
    WidgetStyle.LoadDial -> "A dial for your load ratio"
    WidgetStyle.LoadTrend -> "A line over the last week"
    WidgetStyle.SleepBar -> "Sleep stages as one coloured bar"
    WidgetStyle.SleepRing -> "Your sleep score as a ring"
    WidgetStyle.SleepTimeline -> "When you slept, hour by hour"
    WidgetStyle.FitnessGrid -> "Race predictions in tiles"
    WidgetStyle.FitnessVo2 -> "VO2max up front"
    WidgetStyle.FitnessLadder -> "Paces listed like a ladder"
    WidgetStyle.OverviewGrid -> "Your chosen stats in a tidy grid"
    WidgetStyle.HrvRange -> "A bar with your normal range shaded and a marker for last night"
    WidgetStyle.HrvNights -> "Seven nights as bars, the normal range as a stripe"
    WidgetStyle.HrvPill -> "A big number and COROS's word for it"
    WidgetStyle.HeartSpark -> "Resting heart rate with a 7-day line"
    WidgetStyle.HeartRange -> "Today's lowest, average and highest on one bar"
    WidgetStyle.HeartPair -> "Daily average and resting heart rate side by side"
    WidgetStyle.StressPill -> "Today's number with its level word"
    WidgetStyle.StressBars -> "Seven days of bars, coloured by level"
    WidgetStyle.StressGauge -> "A half-circle gauge with the number inside"
    WidgetStyle.MileageBars -> "Distance per day, Monday to Sunday"
    WidgetStyle.MileageGoal -> "A ring that fills towards your weekly goal"
    WidgetStyle.MileageTrend -> "The week's total with a line building up"
}

private fun partHint(p: Part): String = when (p) {
    Part.Rings -> "Small rings and trend lines inside the tiles"
    Part.Comment -> "A short sentence about your load"
    Part.NormalRange -> "The normal range COROS gives for you"
    Part.Baseline -> "Your baseline HRV"
    Part.SevenDayAvg -> "The average of the last seven days"
    Part.DailyAvg -> "Today's average with the lowest and highest"
    Part.ActivityCount -> "How many activities made up the total"
    Part.VsLastWeek -> "The change from last Monday-to-Sunday week"
    else -> "Show or hide ${p.label.lowercase()}"
}

/** Renders the real widget (Glance RemoteViews, synthetic data) at [size]; re-renders whenever the choices change. */
@Composable
fun LivePreview(kind: WidgetKind, resolved: ResolvedConfig, size: DpSize) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<android.widget.RemoteViews?>(null) }
    LaunchedEffect(resolved, size) { views = runCatching { WidgetCatalog.previewViews(context, kind, resolved, size) }.getOrNull() }
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame -> views?.let { v -> frame.removeAllViews(); runCatching { frame.addView(v.apply(context.applicationContext, frame)) } } },
        modifier = Modifier.size(size.width, size.height).semantics { contentDescription = "Live preview of the ${kind.label} widget" },
    )
}

/**
 * Preview on top, then a segmented row of icon + label sections (one shown at a time), then rarely used items under
 * "More options" (collapsed). Defaults are sensible, so the caller can offer Done straight away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetCustomizer(
    kind: WidgetKind,
    cfg: WidgetConfig,
    onCfg: (WidgetConfig) -> Unit,
    appearance: AppAppearance,
    size: WidgetSize,
    onSize: (WidgetSize) -> Unit,
    sizeNote: String,
    tapTarget: TapTarget,
    onEditTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** The app-wide distance unit, for the weekly goal's default and label. */
    appUnit: DistanceUnit = DistanceUnit.Km,
) {
    val resolved = cfg.resolve(kind, appearance)
    var section by rememberSaveable { mutableStateOf(CustomSection.Style) }
    var more by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        // The preview sits on a neutral "wallpaper" so transparency reads.
        Box(Modifier.fillMaxWidth().background(Color(0xFF6B7280)).padding(Spacing.sm), contentAlignment = Alignment.Center) { LivePreview(kind, resolved, size.preview) }
        SectionTabs(section) { section = it }
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(section.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (section) {
                CustomSection.Style -> WidgetStyle.forKind(kind).forEach { s ->
                    OptionRow(s.label, styleHint(s), resolved.style == s) { onCfg(cfg.copy(style = s)) }
                }
                CustomSection.Colour -> {
                    FilterChip(selected = cfg.theme == null, onClick = { onCfg(cfg.copy(theme = null)) }, label = { Text("Match the app (${appearance.theme.label})") }, modifier = Modifier.semantics { role = Role.RadioButton })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ThemeId.entries.forEach { t -> ThemeSwatch(t, cfg.theme == t) { onCfg(cfg.copy(theme = t)) } }
                    }
                    Text("Background", style = MaterialTheme.typography.titleSmall)
                    Text("Transparent lets your wallpaper show through.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        FilterChip(selected = cfg.bg == null, onClick = { onCfg(cfg.copy(bg = null, opacity = null)) }, label = { Text("App default") })
                        Bg.entries.forEach { b -> FilterChip(selected = cfg.bg == b, onClick = { onCfg(cfg.copy(bg = b)) }, label = { Text(b.label) }, modifier = Modifier.semantics { role = Role.RadioButton }) }
                    }
                    if (resolved.bg != Bg.Transparent) {
                        Text("Opacity ${resolved.opacity}%", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = resolved.opacity.toFloat(), onValueChange = { onCfg(cfg.copy(opacity = it.toInt())) }, valueRange = 0f..100f,
                            modifier = Modifier.semantics { contentDescription = "Opacity"; stateDescription = "${resolved.opacity} percent" },
                        )
                    }
                }
                CustomSection.Content -> {
                    if (kind == WidgetKind.Overview) OverviewStats(cfg, resolved, onCfg)
                    if (kind == WidgetKind.WeeklyMileage) WeeklyGoalEditor(cfg, appUnit, onCfg)
                    val parts = Part.forKind(kind)
                    if (parts.isEmpty() && kind != WidgetKind.Overview) Text("Nothing to switch on or off for this widget.", style = MaterialTheme.typography.bodyMedium)
                    parts.forEach { part ->
                        val on = cfg.shows(part)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { onCfg(cfg.copy(hidden = if (on) cfg.hidden + part else cfg.hidden - part)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(end = Spacing.sm))
                            Column { Text(part.label, style = MaterialTheme.typography.bodyLarge); Text(partHint(part), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                CustomSection.Size -> {
                    WidgetSize.entries.forEach { s -> OptionRow("${s.label} (${s.cells})", sizeHint(s), size == s, icon = AppIcons.Size) { onSize(s) } }
                    Text(sizeNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CustomSection.Tap -> {
                    Text(tapTarget.describe(), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onEditTap) { Text("Change what a tap opens") }
                    Text("This applies to every ${kind.label} widget. Tapping also refreshes Cairn in the background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = { more = !more }) { Icon(AppIcons.Expand, null, Modifier.size(18.dp)); Text(if (more) "Fewer options" else "More options", Modifier.padding(start = Spacing.xs)) }
            if (more) MoreOptions(kind, cfg, onCfg)
        }
    }
}

fun sizeHint(s: WidgetSize) = when (s) {
    WidgetSize.Small -> "A compact square: the essentials"
    WidgetSize.Wide -> "Twice as wide: room for more numbers"
    WidgetSize.Large -> "Wide and tall: the full picture"
}

@Composable
private fun SectionTabs(selected: CustomSection, onSelect: (CustomSection) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        CustomSection.entries.forEach { s ->
            val on = s == selected
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp)
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab) { onSelect(s) }.semantics { this.selected = on }.padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(s.icon, null, Modifier.size(22.dp), tint = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.label, style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, hint: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(AppIcons.Check, contentDescription = "Selected", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewStats(cfg: WidgetConfig, resolved: ResolvedConfig, set: (WidgetConfig) -> Unit) {
    val picked = resolved.metrics
    Text("Stats (1 to 6, in this order)", style = MaterialTheme.typography.titleSmall)
    picked.forEachIndexed { i, id ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}. ${id.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(enabled = i > 0, onClick = { set(cfg.copy(metrics = picked.toMutableList().also { java.util.Collections.swap(it, i, i - 1) })) }, modifier = Modifier.semantics { contentDescription = "Move ${id.label} up" }) { Text("▲") }
            TextButton(enabled = i < picked.lastIndex, onClick = { set(cfg.copy(metrics = picked.toMutableList().also { java.util.Collections.swap(it, i, i + 1) })) }, modifier = Modifier.semantics { contentDescription = "Move ${id.label} down" }) { Text("▼") }
            TextButton(enabled = picked.size > 1, onClick = { set(cfg.copy(metrics = picked - id)) }, modifier = Modifier.semantics { contentDescription = "Remove ${id.label}" }) { Icon(AppIcons.Close, null, Modifier.size(18.dp)) }
        }
    }
    Text(if (picked.size >= OverviewId.MAX) "Six stats is the most." else "Add a stat", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OverviewId.entries.filter { it !in picked }.forEach { id ->
            FilterChip(selected = false, enabled = picked.size < OverviewId.MAX, onClick = { set(cfg.copy(metrics = picked + id)) }, label = { Text(id.label) })
        }
    }
}

/** Weekly mileage: an optional goal for the Goal ring (30 km or 20 mi to start with). Without one the ring style shows bars. */
@Composable
private fun WeeklyGoalEditor(cfg: WidgetConfig, appUnit: DistanceUnit, set: (WidgetConfig) -> Unit) {
    val unit = when (cfg.units) { UnitsPref.Km -> DistanceUnit.Km; UnitsPref.Mi -> DistanceUnit.Mi; UnitsPref.Inherit -> appUnit }
    val label = if (unit == DistanceUnit.Mi) "mi" else "km"
    val goal = cfg.weeklyGoal
    val max = if (unit == DistanceUnit.Mi) WeeklyGoal.MAX_MI else WeeklyGoal.MAX_KM
    ToggleRow("Weekly goal", "The Goal ring fills towards it. Without a goal the Goal ring style shows bars instead.", goal != null) {
        set(cfg.copy(weeklyGoal = if (it) WeeklyGoal.default(unit) else null))
    }
    if (goal != null) {
        val shown = goal.inUnit(unit).coerceIn(5, max)
        Text("Goal: $shown $label a week", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = shown.toFloat(), onValueChange = { set(cfg.copy(weeklyGoal = WeeklyGoal(((it / 5f).toInt() * 5).coerceIn(5, max), unit))) },
            valueRange = 5f..max.toFloat(), steps = max / 5 - 2,
            modifier = Modifier.semantics { contentDescription = "Weekly goal"; stateDescription = "$shown $label" },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreOptions(kind: WidgetKind, cfg: WidgetConfig, set: (WidgetConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Corner roundness", style = MaterialTheme.typography.titleSmall)
        Text("System matches your launcher; Extra round is softer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Corners.entries.forEach { c -> FilterChip(selected = cfg.corners == c, onClick = { set(cfg.copy(corners = c)) }, label = { Text(c.label) }, modifier = Modifier.semantics { role = Role.RadioButton }) }
        }
        Text("Accent colour", style = MaterialTheme.typography.titleSmall)
        Text("Overrides the palette's highlight colour.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val dark = isSystemInDarkTheme()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AccentOption.entries.forEach { a ->
                FilterChip(
                    selected = cfg.accent == a, onClick = { set(cfg.copy(accent = a)) }, label = { Text(a.label) },
                    leadingIcon = { if (a != AccentOption.Theme) Box(Modifier.size(14.dp).background(Color(if (dark) a.dark else a.light), CircleShape)) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
        }
        if (kind == WidgetKind.LastActivity || kind == WidgetKind.Fitness || kind == WidgetKind.WeeklyMileage) {
            Text("Units for this widget", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                UnitsPref.entries.forEach { u -> FilterChip(selected = cfg.units == u, onClick = { set(cfg.copy(units = u)) }, label = { Text(u.label) }, modifier = Modifier.semantics { role = Role.RadioButton }) }
            }
        }
        ToggleRow("Compact footer", "A tighter strip with the freshness dot and sync button.", cfg.compactFooter) { set(cfg.copy(compactFooter = it)) }
        ToggleRow("Hide sync button on tiny layouts", "Only applies to very small widgets where space is short. The freshness dot always stays.", cfg.hideSyncIcon) { set(cfg.copy(hideSyncIcon = it)) }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = on, onCheckedChange = onChange)
    }
}
