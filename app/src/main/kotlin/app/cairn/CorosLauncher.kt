package app.cairn

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import app.cairn.widget.ActivityCandidate
import app.cairn.widget.COROS_PACKAGE
import app.cairn.widget.CorosScreenOption
import app.cairn.widget.CorosScreens
import app.cairn.widget.EXTRA_COROS_MISSING
import app.cairn.widget.EXTRA_SCREEN
import app.cairn.widget.LaunchStep
import app.cairn.widget.Screen
import app.cairn.widget.TapLaunchPlanner
import app.cairn.widget.TapTarget

/**
 * Lists and launches COROS screens. Never throws and never logs: failures fall through the chain
 * chosen screen -> COROS launch intent -> Cairn's "Keep data fresh" explanation.
 */
class CorosLauncher(private val context: Context) {
    private val pm: PackageManager get() = context.packageManager

    /** Activities of the COROS app that other apps are allowed to start. Empty when COROS is missing or the query fails. */
    fun discover(): List<CorosScreenOption> = runCatching {
        val info = pm.getPackageInfo(COROS_PACKAGE, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        val appEnabled = info.applicationInfo?.enabled != false
        if (!appEnabled) return@runCatching emptyList()
        CorosScreens.launchable((info.activities ?: emptyArray()).map(::candidate))
    }.getOrDefault(emptyList())

    private fun candidate(a: ActivityInfo) = ActivityCandidate(
        className = a.name,
        label = runCatching { a.loadLabel(pm).toString() }.getOrNull(),
        exported = a.exported,
        enabled = a.enabled,
        permission = a.permission,
    )

    fun isInstalled(): Boolean = runCatching { pm.getLaunchIntentForPackage(COROS_PACKAGE) != null }.getOrDefault(false)

    /**
     * Opens [target]. Returns the step that actually worked (null only if even Cairn could not be opened).
     * When COROS came to the front and [scheduleRefresh] is set, the delayed Cairn refreshes are scheduled.
     */
    fun launch(target: TapTarget, cairnScreen: Screen, scheduleRefresh: Boolean = true): LaunchStep? {
        val worked = TapLaunchPlanner.execute(TapLaunchPlanner.chain(target, isInstalled())) { step -> run(step, cairnScreen) }
        if (worked?.opensCoros == true && scheduleRefresh) {
            runCatching { (context.applicationContext as CairnApp).graph.syncScheduler.scheduleCorosRefreshes() }
        }
        return worked
    }

    private fun run(step: LaunchStep, cairnScreen: Screen): Boolean {
        val intent: Intent = when (step) {
            is LaunchStep.Screen -> Intent().setClassName(COROS_PACKAGE, step.className)
            LaunchStep.CorosLaunch -> pm.getLaunchIntentForPackage(COROS_PACKAGE) ?: return false
            LaunchStep.OpenCairn -> cairnIntent(cairnScreen, missing = false)
            LaunchStep.Explain -> cairnIntent(Screen.Freshness, missing = true)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: SecurityException) { false } catch (_: android.content.ActivityNotFoundException) { false } catch (_: Exception) { false }
    }

    private fun cairnIntent(screen: Screen, missing: Boolean) =
        Intent(context, MainActivity::class.java).putExtra(EXTRA_SCREEN, screen.name).apply { if (missing) putExtra(EXTRA_COROS_MISSING, true) }
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
