package app.cairn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.cairn.sync.CorosRefreshPlanner
import app.cairn.widget.COROS_PACKAGE
import app.cairn.widget.EXTRA_COROS_MISSING
import app.cairn.widget.EXTRA_SCREEN
import app.cairn.widget.Screen

/** Opens COROS so it syncs the watch, and schedules two delayed Cairn refreshes. Falls back to Cairn when COROS is missing. */
class OpenCorosActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openCorosAndRefresh(this)
        finish()
    }
}

fun isCorosInstalled(context: android.content.Context): Boolean =
    context.packageManager.getLaunchIntentForPackage(COROS_PACKAGE) != null

fun openCorosAndRefresh(context: android.content.Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(COROS_PACKAGE)
    when (CorosRefreshPlanner.decide(launch != null)) {
        CorosRefreshPlanner.Launch.OpenCoros -> {
            (context.applicationContext as CairnApp).graph.syncScheduler.scheduleCorosRefreshes()
            context.startActivity(launch!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        CorosRefreshPlanner.Launch.ExplainInCairn ->
            context.startActivity(
                Intent(context, MainActivity::class.java).putExtra(EXTRA_SCREEN, Screen.Freshness.name)
                    .putExtra(EXTRA_COROS_MISSING, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
    }
}
