package app.cairn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cairn.appdrop.AppDropUpdater
import app.cairn.appdrop.UpdateInfo
import app.cairn.appdrop.UpdateState
import app.cairn.core.designsystem.CairnMotion
import app.cairn.core.designsystem.LocalReducedMotion
import app.cairn.core.designsystem.Spacing
import app.cairn.widget.FooterUi

/** What the banner draws, decoupled from the updater so it is unit-testable. */
sealed interface UpdateBannerUi {
    data class Offer(val title: String, val summary: String, val canInstallInApp: Boolean, val versionName: String, val error: String? = null) : UpdateBannerUi
    data class Progress(val percent: Int) : UpdateBannerUi
    object Ready : UpdateBannerUi
}

object UpdateBannerMapper {
    fun map(state: UpdateState, appName: String = "Cairn"): UpdateBannerUi? = when (state) {
        is UpdateState.Available -> offer(state.info, appName, null)
        is UpdateState.Error -> state.info?.let { offer(it, appName, state.message) }
        is UpdateState.Downloading -> UpdateBannerUi.Progress(state.percent)
        UpdateState.ReadyToInstall -> UpdateBannerUi.Ready
        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> null
    }

    private fun offer(i: UpdateInfo, appName: String, error: String?) =
        UpdateBannerUi.Offer("$appName ${i.versionName} available", i.notesSummary, !i.downloadUrl.isNullOrBlank(), i.versionName, error)
}

/** Compose view of the updater's observable state. */
@Composable
fun rememberUpdateState(): State<UpdateState> {
    val st = remember { mutableStateOf(AppDropUpdater.state.currentState()) }
    DisposableEffect(Unit) {
        st.value = AppDropUpdater.state.currentState()
        val off = AppDropUpdater.state.addListener { st.value = it }
        onDispose { off() }
    }
    return st
}

/** Sync status line, then the update banner under it (Today, Activities, Fitness). */
@Composable
fun StatusAndUpdate(footer: FooterUi?) {
    Column { SyncStatusBar(footer); UpdateBanner() }
}

/**
 * Cairn's update card, ONE slim row: icon, title + one-line notes, compact Update, overflow menu, close X.
 * Dusk tonal container, 16 dp corners like other cards. Never modal, never blocks content.
 * "Update" opens the AppDrop store page; the menu has Install now (APK only), Later and Skip this version.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ui = UpdateBannerMapper.map(rememberUpdateState().value)
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = ui != null, modifier = modifier,
        enter = if (reduced) fadeIn(tween(0)) else fadeIn(CairnMotion.settle()) + expandVertically(CairnMotion.settle()),
        exit = if (reduced) fadeOut(tween(0)) else fadeOut(CairnMotion.settle()) + shrinkVertically(CairnMotion.settle()),
    ) {
        val shown = remember { mutableStateOf<UpdateBannerUi?>(null) }
        if (ui != null) shown.value = ui
        val cur = shown.value ?: return@AnimatedVisibility
        var menu by remember { mutableStateOf(false) }
        Card(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
        ) {
            val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
            val small = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp)
            val lines = if (stacked) 2 else 1
            val icon: @Composable () -> Unit = { Icon(AppIcons.SystemUpdate, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
            val texts: @Composable (Modifier) -> Unit = { mod ->
                Column(mod) {
                    when (cur) {
                        is UpdateBannerUi.Offer -> {
                            Text(cur.title, style = MaterialTheme.typography.titleSmall, maxLines = lines, overflow = TextOverflow.Ellipsis)
                            val second = cur.error ?: cur.summary
                            if (second.isNotBlank()) Text(second, style = MaterialTheme.typography.bodySmall, maxLines = lines, overflow = TextOverflow.Ellipsis,
                                color = if (cur.error != null) MaterialTheme.colorScheme.error else LocalContentColor.current)
                        }
                        is UpdateBannerUi.Progress -> {
                            Text("Downloading ${cur.percent}%", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            LinearProgressIndicator(progress = { cur.percent / 100f }, Modifier.fillMaxWidth().padding(top = Spacing.xxs))
                        }
                        UpdateBannerUi.Ready -> Text("Update ready to install", style = MaterialTheme.typography.titleSmall, maxLines = lines, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val primary: @Composable () -> Unit = {
                when (cur) {
                    is UpdateBannerUi.Offer ->
                        if (cur.error != null) Button(onClick = { AppDropUpdater.startUpdate(context) }, Modifier.heightIn(min = 32.dp), contentPadding = small) { Text("Retry", maxLines = 1, softWrap = false) }
                        else Button(onClick = { AppDropUpdater.openStore(context) }, Modifier.heightIn(min = 32.dp), contentPadding = small) { Text("Update", maxLines = 1, softWrap = false) }
                    UpdateBannerUi.Ready -> Button(onClick = { AppDropUpdater.startUpdate(context) }, Modifier.heightIn(min = 32.dp), contentPadding = small) { Text("Install", maxLines = 1, softWrap = false) }
                    is UpdateBannerUi.Progress -> Unit
                }
            }
            val actions: @Composable () -> Unit = {
                if (cur is UpdateBannerUi.Offer) {
                    Box {
                        IconButton(onClick = { menu = true }, Modifier.size(40.dp)) { Icon(AppIcons.More, "More options", Modifier.size(24.dp)) }
                        DropdownMenu(menu, { menu = false }) {
                            if (cur.canInstallInApp) DropdownMenuItem({ Text("Install now") }, { menu = false; AppDropUpdater.startUpdate(context) })
                            DropdownMenuItem({ Text("Later") }, { menu = false; AppDropUpdater.dismissForNow(context) })
                            DropdownMenuItem({ Text("Skip this version") }, { menu = false; AppDropUpdater.skip(context, cur.versionName) })
                        }
                    }
                    IconButton(onClick = { AppDropUpdater.dismissForNow(context) }, Modifier.size(40.dp)) { Icon(AppIcons.Close, "Later", Modifier.size(24.dp)) }
                }
            }
            if (stacked) {
                // Large font: row 1 = icon + full-width text (2 lines each), row 2 = Update left, overflow + close right.
                Column(Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.xxs, top = Spacing.xs, bottom = Spacing.xxs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(end = Spacing.xs)) { icon(); texts(Modifier.weight(1f)) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { primary(); Spacer(Modifier.weight(1f)); actions() }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Spacing.sm, end = Spacing.xxs, top = Spacing.xs, bottom = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) { icon(); texts(Modifier.weight(1f).fillMaxWidth()); primary(); actions() }
            }
        }
    }
}
