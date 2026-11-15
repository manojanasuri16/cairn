package app.cairn.widget

import android.content.Context
import android.content.Intent

const val COROS_PACKAGE = "com.yf.smart.coros.dist"
const val EXTRA_SCREEN = "cairn.screen"

fun cairnIntent(context: Context, screen: Screen): Intent =
    Intent(Intent.ACTION_MAIN).setClassName(context.packageName, "app.cairn.MainActivity")
        .putExtra(EXTRA_SCREEN, screen.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** Stale footer opens COROS when installed (so it can sync), otherwise Cairn. */
fun footerIntent(context: Context, footer: FooterUi?, screen: Screen): Intent =
    (if (footer?.opensCoros == true) context.packageManager.getLaunchIntentForPackage(COROS_PACKAGE) else null)
        ?: cairnIntent(context, screen)
