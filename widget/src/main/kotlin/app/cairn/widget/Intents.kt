package app.cairn.widget

import android.content.Context
import android.content.Intent

const val COROS_PACKAGE = "com.yf.smart.coros.dist"
const val EXTRA_SCREEN = "cairn.screen"

fun cairnIntent(context: Context, screen: Screen): Intent =
    Intent(Intent.ACTION_MAIN).setClassName(context.packageName, "app.cairn.MainActivity")
        .putExtra(EXTRA_SCREEN, screen.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

const val EXTRA_COROS_MISSING = "cairn.coros_missing"
const val OPEN_COROS_CLASS = "app.cairn.OpenCorosActivity"

/** Trampoline that opens COROS (or Cairn if it is missing) and schedules the delayed refreshes. */
fun openCorosIntent(context: Context): Intent =
    Intent(Intent.ACTION_MAIN).setClassName(context.packageName, OPEN_COROS_CLASS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** Stale footer opens COROS via the trampoline (which also schedules refreshes), otherwise Cairn. */
fun footerIntent(context: Context, footer: FooterUi?, screen: Screen): Intent =
    if (footer?.opensCoros == true) openCorosIntent(context) else cairnIntent(context, screen)
