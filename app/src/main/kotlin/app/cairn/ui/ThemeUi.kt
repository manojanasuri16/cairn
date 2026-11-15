package app.cairn.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cairn.core.designsystem.Accent
import app.cairn.core.designsystem.AppLook
import app.cairn.core.designsystem.Palette
import app.cairn.core.designsystem.Palettes
import app.cairn.core.designsystem.Spacing
import app.cairn.core.designsystem.ThemeId
import app.cairn.core.designsystem.ThemeMode
import app.cairn.widget.AppAppearance
import app.cairn.widget.Bg

/** Widget style palette in the current brightness. */
@Composable
fun rememberPalette(theme: ThemeId): Palette {
    val dark = isSystemInDarkTheme()
    return remember(theme, dark) { Palettes.fixed(theme, dark) }
}

private fun Palette.brush() = Brush.linearGradient(listOf(Color(start), Color(center), Color(end)))

/** One widget style swatch: its gradient and name in AA ink; selection is a border plus a check and semantics, not colour alone. */
@Composable
fun ThemeSwatch(theme: ThemeId, selected: Boolean, onClick: () -> Unit) {
    val p = rememberPalette(theme)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.width(104.dp).height(64.dp).clip(shape).background(p.brush())
            .border(if (selected) BorderStroke(3.dp, Color(p.ink)) else BorderStroke(1.dp, Color(p.ink).copy(alpha = 0.25f)), shape)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = theme.label + if (selected) ", selected" else "" }
            .padding(Spacing.xs),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(if (selected) "✓ ${theme.label}" else theme.label, style = MaterialTheme.typography.labelLarge, color = Color(p.ink))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StylePicker(selected: ThemeId, onSelect: (ThemeId) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ThemeId.entries.forEach { ThemeSwatch(it, it == selected) { onSelect(it) } }
    }
}

/** Accent picker: a round hero-gradient chip per accent with its name below, like Daymark. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(look: AppLook, onLook: (AppLook) -> Unit) {
    val dark = when (look.mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Accent.entries.forEach { a ->
            val (s, e) = if (dark) a.heroDark else a.heroLight
            val sel = a == look.accent && !look.dynamic
            Column(
                Modifier.widthIn(min = 64.dp).clickable { onLook(look.copy(accent = a, dynamic = false)) }
                    .semantics { role = Role.RadioButton; selected = sel; contentDescription = a.label + if (sel) ", selected" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(s), Color(e))))
                        .border(if (sel) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (sel) Text("✓", color = Color.White) }
                Text(a.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** Accent, light/dark/system and the optional Material You switch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeCard(look: AppLook, onLook: (AppLook) -> Unit) {
    SectionCard {
        SectionTitle("Theme")
        AccentPicker(look, onLook)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ThemeMode.entries.forEach { m ->
                FilterChip(selected = look.mode == m, onClick = { onLook(look.copy(mode = m)) }, label = { Text(m.label) }, modifier = Modifier.semantics { role = Role.RadioButton })
            }
            FilterChip(selected = look.dynamic, onClick = { onLook(look.copy(dynamic = !look.dynamic)) }, label = { Text("Material You colours") })
        }
    }
}

/** A tiny mock widget drawn with the style and background mode, for the settings preview strip. */
@Composable
private fun MiniWidget(p: Palette, bg: Bg, opacity: Int, label: String, modifier: Modifier = Modifier) {
    val alpha = if (bg == Bg.Transparent) 0f else opacity / 100f
    val shape = RoundedCornerShape(20.dp)
    val fill = if (bg == Bg.Gradient) Brush.linearGradient(listOf(Color(p.start).copy(alpha = alpha), Color(p.center).copy(alpha = alpha), Color(p.end).copy(alpha = alpha)))
    else Brush.linearGradient(listOf(Color(p.start).copy(alpha = alpha), Color(p.start).copy(alpha = alpha)))
    val onWall = bg == Bg.Transparent || opacity < 55
    Box(modifier.height(84.dp).clip(shape).background(Color(0xFF8A8F98)).background(fill, shape).padding(Spacing.xs)) {
        val c = if (onWall) Color.White else Color(p.ink)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                listOf(10, 18, 8, 24, 14, 28).forEach { h -> Box(Modifier.width(6.dp).height(h.dp).background(Color(p.accent), RoundedCornerShape(2.dp))) }
            }
        }
    }
}

/** App-wide default for new widgets: style, background and opacity, on a preview strip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetAppearanceCard(appearance: AppAppearance, onStyle: (ThemeId) -> Unit, onChange: (Bg, Int) -> Unit) {
    val p = rememberPalette(appearance.theme)
    SectionCard {
        SectionTitle("Widget appearance")
        Text("New widgets start with this style and background. You can change each widget on its own when you place or edit it.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MiniWidget(p, appearance.bg, appearance.opacity, "8,432", Modifier.weight(1f))
            MiniWidget(p, appearance.bg, appearance.opacity, "82%", Modifier.weight(1f))
            MiniWidget(p, appearance.bg, appearance.opacity, "7h 41m", Modifier.weight(1f))
        }
        StylePicker(appearance.theme, onStyle)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Bg.entries.forEach { b ->
                FilterChip(selected = appearance.bg == b, onClick = { onChange(b, appearance.opacity) }, label = { Text(b.label) }, modifier = Modifier.semantics { role = Role.RadioButton })
            }
        }
        if (appearance.bg != Bg.Transparent) {
            Text("Opacity ${appearance.opacity}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = appearance.opacity.toFloat(), onValueChange = { onChange(appearance.bg, it.toInt()) }, valueRange = 0f..100f,
                modifier = Modifier.semantics { contentDescription = "Widget opacity ${appearance.opacity} percent" },
            )
        }
    }
}
