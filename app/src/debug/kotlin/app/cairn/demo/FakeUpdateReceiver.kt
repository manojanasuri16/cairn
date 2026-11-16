package app.cairn.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cairn.appdrop.AppDropUpdater
import org.json.JSONObject

/**
 * Debug only. Shows a fake "update available" banner without the network:
 * `adb shell am broadcast -n app.cairn/.demo.FakeUpdateReceiver -a app.cairn.FAKE_UPDATE [--es version 1.5.0] [--es notes '"Faster widgets"'] [--ez apk true]`
 * Quote multi-word notes as `--es notes '"two words"'` (adb shell re-splits on spaces; unquoted, only the first word arrives).
 * Clear it with `--ez clear true`. It writes the updater's cache (as a fresh check) so resume does not erase it.
 */
class FakeUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.cairn.FAKE_UPDATE") return
        val p = context.getSharedPreferences("appdrop_updater", Context.MODE_PRIVATE)
        val e = p.edit().putLong("last_check", System.currentTimeMillis()).remove("skipped_version").remove("dismissed_until")
        if (intent.getBooleanExtra("clear", false)) e.remove("cached_info")
        else {
            val notes = intent.getStringExtra("notes") ?: "Update banner, faster widget previews and fixes."
            val j = JSONObject()
                .put("versionName", intent.getStringExtra("version") ?: "1.5.0")
                .put("notesSummary", notes).put("notes", notes)
                .put("storeUrl", "https://appdrop-store.vercel.app/apps/cairn")
                .put("sizeBytes", 12_000_000L)
            if (intent.getBooleanExtra("apk", true)) j.put("downloadUrl", "https://appdrop-store.vercel.app/d/cairn/fake")
            e.putString("cached_info", j.toString()).putString("cached_server_newer", "true")
        }
        e.commit()
        AppDropUpdater.refresh(context)
    }
}
