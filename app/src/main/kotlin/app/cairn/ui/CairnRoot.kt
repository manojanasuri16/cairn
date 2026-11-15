package app.cairn.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.cairn.CorosAccountScreen
import app.cairn.CorosViewModel
import app.cairn.FreshnessScreen
import app.cairn.BuildConfig
import app.cairn.ExplorerFeature
import app.cairn.InspectorScreen
import app.cairn.SyncDiagnosticsScreen
import app.cairn.core.designsystem.CairnMotion
import app.cairn.CairnApp
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.AppLook
import androidx.compose.ui.graphics.Color
import app.cairn.core.designsystem.LocalReducedMotion
import app.cairn.widget.Screen

object Routes {
    const val TODAY = "today"
    const val ACTIVITIES = "activities"
    const val FITNESS = "fitness"
    const val SETTINGS = "settings"
    const val INSPECTOR = "inspector"
    const val DIAGNOSTICS = "diagnostics"
    const val FRESHNESS = "freshness"
    const val ACCOUNT = "account"
    const val EXPLORER = "explorer"
    const val STUDIO = "studio"
    const val TAP_TARGETS = "tap_targets"
    const val TOOLS = "tools"

    val tabs = listOf(TODAY, ACTIVITIES, FITNESS)

    fun title(route: String?): String = when (route) {
        TODAY -> "Today"
        ACTIVITIES -> "Activities"
        FITNESS -> "Fitness"
        SETTINGS -> "Settings"
        INSPECTOR -> "Data sources"
        DIAGNOSTICS -> "Sync diagnostics"
        EXPLORER -> "Tool explorer"
        FRESHNESS -> "Keep data fresh"
        STUDIO -> "Add widget"
        TAP_TARGETS -> "Widget tap targets"
        TOOLS -> "Tools"
        ACCOUNT -> "COROS account"
        else -> "Cairn"
    }

    /** Where a widget tap (or other launcher extra) lands. Unknown values open Today. */
    fun forScreen(screen: String?): String = when (screen) {
        Screen.Activities.name -> ACTIVITIES
        Screen.Fitness.name -> FITNESS
        Screen.Account.name -> ACCOUNT
        Screen.Freshness.name -> FRESHNESS
        "Studio" -> STUDIO
        else -> TODAY
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.TODAY, "Today", Icons.Filled.Home),
    Tab(Routes.ACTIVITIES, "Activities", Icons.AutoMirrored.Filled.List),
    Tab(Routes.FITNESS, "Fitness", Icons.Filled.Favorite),
)

@Composable
fun CairnRoot(coros: CorosViewModel, startScreen: String?, corosMissing: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val look by (context.applicationContext as CairnApp).graph.appSettings.look.collectAsStateWithLifecycle(initialValue = AppLook())
    CairnTheme(look) {
        val onboarding: OnboardingViewModel = viewModel()
        val done by onboarding.done.collectAsStateWithLifecycle()
        when (done) {
            null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            false -> OnboardingScreen(coros, vm = onboarding)
            true -> CairnScaffold(coros, Routes.forScreen(startScreen), corosMissing)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CairnScaffold(coros: CorosViewModel, start: String, corosMissing: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: start
    val isTab = route in Routes.tabs
    val topRoute = route?.substringBefore('?')
    val reduced = LocalReducedMotion.current

    fun go(r: String) { nav.navigate(r) { launchSingleTop = true } }
    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun isSub(e: NavBackStackEntry) = e.destination.route !in Routes.tabs
    // Tabs cross-fade. Sub-screens slide a short distance on a critically damped spring. With animations removed, everything cross-fades.
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced || !isSub(targetState)) fadeIn(CairnMotion.settle()) else slideInHorizontally(CairnMotion.settle<IntOffset>()) { it / 5 } + fadeIn(CairnMotion.settle())
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduced || !isSub(targetState)) fadeOut(CairnMotion.settle()) else fadeOut(CairnMotion.settle())
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced || !isSub(initialState)) fadeIn(CairnMotion.settle()) else slideInHorizontally(CairnMotion.settle<IntOffset>()) { -it / 8 } + fadeIn(CairnMotion.settle())
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduced || !isSub(initialState)) fadeOut(CairnMotion.settle()) else slideOutHorizontally(CairnMotion.settle<IntOffset>()) { it / 5 } + fadeOut(CairnMotion.settle())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (isTab) {
                CenterAlignedTopAppBar(
                    title = { Text(Routes.title(route)) },
                    actions = { IconButton(onClick = { go(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") } },
                )
            } else {
                TopAppBar(
                    title = { Text(Routes.title(route.substringBefore('?'))) },
                    navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                )
            }
        },
        bottomBar = {
            if (isTab) NavigationBar {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { if (route != t.route) goTab(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        val openAccount = { go(Routes.ACCOUNT) }
        val openStudio: (app.cairn.widget.WidgetKind?) -> Unit = { k -> go(if (k == null) Routes.STUDIO else "${Routes.STUDIO}?kind=${k.name}") }
        NavHost(
            nav, startDestination = start, modifier = Modifier.fillMaxSize(),
            enterTransition = enter, exitTransition = exit, popEnterTransition = popEnter, popExitTransition = popExit,
        ) {
            composable(Routes.TODAY) { TodayScreen(openAccount, openStudio, m) }
            composable(Routes.ACTIVITIES) { ActivitiesScreen(openAccount, m) }
            composable(Routes.FITNESS) { FitnessScreen(openAccount, openStudio, m) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    coros,
                    SettingsNav({ go(Routes.INSPECTOR) }, { go(Routes.DIAGNOSTICS) }, { go(Routes.FRESHNESS) }, { go(Routes.ACCOUNT) }, { go(Routes.EXPLORER) }, { go(Routes.STUDIO) }, { go(Routes.TAP_TARGETS) }, { go(Routes.TOOLS) }),
                    m,
                )
            }
            composable(Routes.INSPECTOR) { InspectorScreen(m) }
            composable(Routes.DIAGNOSTICS) { SyncDiagnosticsScreen(m) }
            composable(Routes.FRESHNESS) { FreshnessScreen(corosMissing, m) }
            if (BuildConfig.DEBUG && ExplorerFeature.available) composable(Routes.EXPLORER) { ExplorerFeature.Screen(m) }
            composable(Routes.ACCOUNT) { CorosAccountScreen(coros, m) }
            composable("${Routes.STUDIO}?kind={kind}", arguments = listOf(androidx.navigation.navArgument("kind") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null })) { e ->
                WidgetStudioScreen(app.cairn.widget.WidgetKind.parse(e.arguments?.getString("kind")), m)
            }
            composable(Routes.TAP_TARGETS) { TapTargetsScreen(m) }
            composable(Routes.TOOLS) { ToolsScreen(SettingsNav({ go(Routes.INSPECTOR) }, { go(Routes.DIAGNOSTICS) }, { go(Routes.FRESHNESS) }, { go(Routes.ACCOUNT) }, { go(Routes.EXPLORER) }), m) }
        }
    }
}
