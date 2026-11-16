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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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

/** A request from outside Compose (MainActivity.onNewIntent) to go somewhere while the app is open. */
data class NavRequest(val screen: String?, val signedIn: Boolean)

object NavRequests {
    val flow = kotlinx.coroutines.flow.MutableSharedFlow<NavRequest>(extraBufferCapacity = 4)
}

object Routes {
    const val WIDGETS = "widgets"
    const val DEFAULT_LOOK = "widgets/look"
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

    /** Bottom navigation, in order. Widgets is the home and the start destination. */
    val tabs = listOf(WIDGETS, TODAY, ACTIVITIES, FITNESS)
    const val START = WIDGETS

    /** The deep target to push on top of [START], or null when the request is the home itself. Back always returns to the home. */
    fun deepTarget(screen: String?): String? = forScreen(screen).takeIf { it != START }

    fun title(route: String?): String = when (route) {
        WIDGETS -> "Widgets"
        DEFAULT_LOOK -> "Default look"
        TODAY -> "Today"
        ACTIVITIES -> "Activities"
        FITNESS -> "Fitness"
        SETTINGS -> "Settings"
        INSPECTOR -> "Data sources"
        DIAGNOSTICS -> "Sync diagnostics"
        EXPLORER -> "Explorer"
        FRESHNESS -> "Keep data fresh"
        STUDIO -> "Add widget"
        TAP_TARGETS -> "Widget tap targets"
        TOOLS -> "Tools"
        ACCOUNT -> "COROS account"
        else -> "Cairn"
    }

    /** Where a widget tap (or other launcher extra) lands. No extra, Home, Studio and unknown values open the Widgets home. */
    fun forScreen(screen: String?): String = when (screen) {
        Screen.Today.name -> TODAY
        Screen.Activities.name -> ACTIVITIES
        Screen.Fitness.name -> FITNESS
        Screen.Account.name -> ACCOUNT
        Screen.Freshness.name -> FRESHNESS
        else -> WIDGETS
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.WIDGETS, "Widgets", AppIcons.Widgets),
    Tab(Routes.TODAY, "Today", Icons.Filled.Home),
    Tab(Routes.ACTIVITIES, "Activities", Icons.AutoMirrored.Filled.List),
    Tab(Routes.FITNESS, "Fitness", Icons.Filled.Favorite),
)

@Composable
fun CairnRoot(coros: CorosViewModel, startScreen: String?, corosMissing: Boolean, signedInNotice: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val look by (context.applicationContext as CairnApp).graph.appSettings.look.collectAsStateWithLifecycle(initialValue = AppLook())
    CairnTheme(look) {
        val onboarding: OnboardingViewModel = viewModel()
        val done by onboarding.done.collectAsStateWithLifecycle()
        when (done) {
            null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            false -> OnboardingScreen(coros, vm = onboarding)
            true -> CairnScaffold(coros, startScreen, corosMissing, signedInNotice)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CairnScaffold(coros: CorosViewModel, startScreen: String?, corosMissing: Boolean, signedInNotice: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.START
    val isTab = route in Routes.tabs
    val reduced = LocalReducedMotion.current

    val context = androidx.compose.ui.platform.LocalContext.current
    val graph = (context.applicationContext as CairnApp).graph
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    var sheetLink by remember { androidx.compose.runtime.mutableStateOf<WidgetLink?>(null) }
    var lastConfirmMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    fun go(r: String) { nav.navigate(r) { launchSingleTop = true } }
    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    /** Opens [r] from a request or a deep link: tabs switch, anything else is pushed on top of the home. */
    fun open(r: String) { if (r == Routes.WIDGETS) goTab(r) else if (r in Routes.tabs) goTab(r) else go(r) }
    /** Leaves the current screen; with nothing underneath (it was the first thing shown) go home. */
    fun back() { if (!nav.popBackStack()) nav.navigate(Routes.WIDGETS) { popUpTo(0) } }
    fun signedInHome() {
        if (!nav.popBackStack(Routes.WIDGETS, false)) nav.navigate(Routes.WIDGETS) { popUpTo(0) }
        val now = System.currentTimeMillis()
        if (PostSignIn.shouldConfirm(lastConfirmMs, now)) { lastConfirmMs = now; scope.launch { snackbar.showSnackbar(PostSignIn.MESSAGE) } }
    }
    fun addToHome(l: WidgetLink) = go("${Routes.STUDIO}?kind=${l.kind.name}&style=${l.style?.name.orEmpty()}")

    // Deep link from a widget tap: pushed above the home so Back always works.
    LaunchedEffect(Unit) {
        Routes.deepTarget(startScreen)?.let { open(it) }
        if (signedInNotice) { lastConfirmMs = System.currentTimeMillis(); snackbar.showSnackbar(PostSignIn.MESSAGE) }
    }
    LaunchedEffect(Unit) { NavRequests.flow.collect { r -> if (r.signedIn) signedInHome() else open(Routes.forScreen(r.screen)) } }
    // Signing in from anywhere (account screen, Settings, banner) ends on the home with a confirmation; stale errors go away.
    val corosState by coros.state.collectAsStateWithLifecycle()
    var wasSignedIn by remember { androidx.compose.runtime.mutableStateOf(corosState.signedIn) }
    LaunchedEffect(corosState.signedIn) {
        if (PostSignIn.goHome(wasSignedIn, corosState.signedIn)) { SyncStatusViewModel.clearAll(); signedInHome() }
        wasSignedIn = corosState.signedIn
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
                    actions = {
                        if (route == Routes.WIDGETS) WidgetsMenu({ go(Routes.DEFAULT_LOOK) }, { go(Routes.TAP_TARGETS) })
                        IconButton(onClick = { go(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(Routes.title(route.substringBefore('?'))) },
                    navigationIcon = { IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                )
            }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
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
        androidx.compose.runtime.CompositionLocalProvider(
            LocalWidgetActions provides WidgetActions { link ->
                sheetLink = link
                scope.launch { graph.appSettings.setCardHintSeen() }
            },
            LocalOpenAccount provides { signIn -> go(if (signIn) "${Routes.ACCOUNT}?signIn=true" else Routes.ACCOUNT) },
        ) {
        NavHost(
            nav, startDestination = Routes.START, modifier = Modifier.fillMaxSize(),
            enterTransition = enter, exitTransition = exit, popEnterTransition = popEnter, popExitTransition = popExit,
        ) {
            composable(Routes.WIDGETS) {
                WidgetsHomeScreen({ k, sz -> go("${Routes.STUDIO}?kind=${k.name}&size=${sz.name}") }, m)
            }
            composable(Routes.DEFAULT_LOOK) { DefaultLookScreen(m) }
            composable(Routes.TODAY) { TodayScreen(openAccount, m) }
            composable(Routes.ACTIVITIES) { ActivitiesScreen(openAccount, m) }
            composable(Routes.FITNESS) { FitnessScreen(openAccount, m) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    coros,
                    SettingsNav({ go(Routes.INSPECTOR) }, { go(Routes.DIAGNOSTICS) }, { go(Routes.FRESHNESS) }, { go(Routes.ACCOUNT) }, { go(Routes.EXPLORER) }, onTools = { go(Routes.TOOLS) }),
                    m,
                )
            }
            composable(Routes.INSPECTOR) { InspectorScreen(m) }
            composable(Routes.DIAGNOSTICS) { SyncDiagnosticsScreen(m) }
            composable(Routes.FRESHNESS) { FreshnessScreen(corosMissing, m) }
            if (BuildConfig.DEBUG && ExplorerFeature.available) composable(Routes.EXPLORER) { ExplorerFeature.Screen(m) }
            composable(
                "${Routes.ACCOUNT}?signIn={signIn}",
                arguments = listOf(androidx.navigation.navArgument("signIn") { type = androidx.navigation.NavType.BoolType; defaultValue = false }),
            ) { e -> CorosAccountScreen(coros, m, autoSignIn = e.arguments?.getBoolean("signIn") == true, onDone = ::back) }
            composable(
                "${Routes.STUDIO}?kind={kind}&style={style}&size={size}",
                arguments = listOf("kind", "style", "size").map { n -> androidx.navigation.navArgument(n) { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null } },
            ) { e ->
                val kind = app.cairn.widget.WidgetKind.parse(e.arguments?.getString("kind")) ?: app.cairn.widget.WidgetKind.Today
                WidgetStudioScreen(kind, app.cairn.widget.WidgetStyle.entries.firstOrNull { it.name == e.arguments?.getString("style") && it.kind == kind }, app.cairn.widget.WidgetSize.parse(e.arguments?.getString("size")), m)
            }
            composable(Routes.TAP_TARGETS) { TapTargetsScreen(m) }
            composable(Routes.TOOLS) { ToolsScreen(SettingsNav({ go(Routes.INSPECTOR) }, { go(Routes.DIAGNOSTICS) }, { go(Routes.FRESHNESS) }, { go(Routes.ACCOUNT) }, { go(Routes.EXPLORER) }), m) }
        }
        }
    }
    sheetLink?.let { l -> CardActionsSheet(l, onAddToHome = ::addToHome, onDismiss = { sheetLink = null }) }
}

/** Overflow menu of the Widgets home: the defaults that apply to every widget. */
@Composable
private fun WidgetsMenu(onDefaultLook: () -> Unit, onTapTargets: () -> Unit) {
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Widget options") }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("Default look") }, onClick = { open = false; onDefaultLook() })
            androidx.compose.material3.DropdownMenuItem(text = { Text("Tap targets") }, onClick = { open = false; onTapTargets() })
        }
    }
}
