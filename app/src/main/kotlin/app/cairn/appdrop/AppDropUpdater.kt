// AppDrop in-app updater + feedback. Single drop-in file, no third-party dependencies
// (only the Android SDK, org.json, and androidx.core's FileProvider, which virtually every app has).
//
// The `package appdrop` line is a placeholder: `appdrop add-updater` rewrites it to
// "<your.package>.appdrop". If you copy the file by hand, change it yourself.
//
// Setup (see docs/android-updates.md):
//   Application.onCreate()  ->  AppDropUpdater.init(this, "https://appdrop-store.vercel.app", "<slug>")
//   MainActivity.onResume() ->  AppDropUpdater.check(this)            // dialog (default)
// Own banner instead of the dialog (draw it in your app's design language):
//   AppDropUpdater.setDialogEnabled(false); AppDropUpdater.refresh(context)   // on start + resume
//   AppDropUpdater.state.addListener { s -> ... }  /  AppDropUpdater.state.currentState()
package app.cairn.appdrop

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


/** One published release that is newer than the installed app. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long?,
    /** One line, markdown stripped, at most ~200 chars. */
    val notesSummary: String,
    /** ISO-8601 timestamp from the API, if any. */
    val publishedAt: String?,
    /** AppDrop store page for the app (opened by [AppDropUpdater.openStore]). */
    val storeUrl: String,
    /** Direct APK URL; null when the release has no APK (then only the store page is possible). */
    val downloadUrl: String?,
    val sizeBytes: Long?,
    val sha256: String? = null,
    /** Several plain-text lines of release notes (the dialog uses these). */
    val notes: String = "",
)

sealed interface UpdateState {
    /** Nothing to show (never checked, or the update was skipped / dismissed). */
    object Idle : UpdateState
    object Checking : UpdateState
    object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    /** APK downloaded and verified; waiting for the installer (permission or foreground). */
    object ReadyToInstall : UpdateState
    /** [info] is set when an update is known, so the UI can offer a retry. */
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/** Pure, JVM-testable decisions. No Android types. */
internal object AppDropLogic {
    sealed interface Event {
        object CheckStarted : Event
        data class Resolved(val next: UpdateState) : Event
        data class Skipped(val versionName: String) : Event
        object Dismissed : Event
        data class Progress(val percent: Int) : Event
        object Downloaded : Event
        data class Failed(val message: String, val info: UpdateInfo?) : Event
    }

    /** "v1.10.0-beta.1" -> [1, 10, 0]; null when there is no leading number. */
    fun parseVersion(v: String): List<Long>? {
        var s = v.trim().removePrefix("v").removePrefix("V")
        val cut = s.indexOfFirst { it == '-' || it == '+' }
        if (cut >= 0) s = s.substring(0, cut)
        val parts = s.split('.', '_')
        val nums = ArrayList<Long>()
        for (p in parts) nums.add(p.toLongOrNull() ?: break)
        return nums.takeIf { it.isNotEmpty() }
    }

    private fun suffix(v: String): String {
        val s = v.trim().removePrefix("v").removePrefix("V")
        val cut = s.indexOfFirst { it == '-' }
        return if (cut >= 0) s.substring(cut + 1) else ""
    }

    /** <0, 0, >0 like compareTo; null when either side is not a dotted-number version. */
    fun compareVersions(a: String, b: String): Int? {
        val x = parseVersion(a) ?: return null
        val y = parseVersion(b) ?: return null
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0L }.compareTo(y.getOrElse(i) { 0L })
            if (d != 0) return d
        }
        val xp = suffix(a); val yp = suffix(b)
        return when {
            xp == yp -> 0
            xp.isEmpty() -> 1
            yp.isEmpty() -> -1
            else -> xp.compareTo(yp)
        }
    }

    /** True/false when decidable (versionName first, then versionCode), null otherwise. */
    fun isNewer(installedName: String, installedCode: Long?, latestName: String, latestCode: Long?): Boolean? {
        compareVersions(latestName, installedName)?.let { return it > 0 }
        if (installedCode != null && latestCode != null) return latestCode > installedCode
        return null
    }

    fun shouldCheck(nowMs: Long, lastCheckMs: Long, force: Boolean, intervalMs: Long): Boolean =
        force || lastCheckMs <= 0L || nowMs - lastCheckMs >= intervalMs || nowMs < lastCheckMs

    /** What the banner should show for a cached/fresh release. */
    fun resolve(
        info: UpdateInfo?, installedName: String, installedCode: Long?, serverSaysNewer: Boolean?,
        skippedVersion: String?, dismissedUntilMs: Long, nowMs: Long, force: Boolean, everChecked: Boolean,
    ): UpdateState {
        if (info == null) return if (everChecked) UpdateState.UpToDate else UpdateState.Idle
        val newer = isNewer(installedName, installedCode, info.versionName, info.versionCode) ?: serverSaysNewer ?: false
        if (!newer) return UpdateState.UpToDate
        if (!force && (skippedVersion == info.versionName || nowMs < dismissedUntilMs)) return UpdateState.Idle
        return UpdateState.Available(info)
    }

    fun reduce(current: UpdateState, e: Event): UpdateState = when (e) {
        Event.CheckStarted -> if (current is UpdateState.Idle || current is UpdateState.UpToDate || current is UpdateState.Error) UpdateState.Checking else current
        is Event.Resolved ->
            if (current is UpdateState.Downloading || current is UpdateState.ReadyToInstall) current else e.next
        is Event.Skipped -> if (current is UpdateState.Available && current.info.versionName == e.versionName) UpdateState.Idle else current
        Event.Dismissed -> if (current is UpdateState.Available) UpdateState.Idle else current
        is Event.Progress -> UpdateState.Downloading(e.percent.coerceIn(0, 100))
        Event.Downloaded -> UpdateState.ReadyToInstall
        is Event.Failed -> UpdateState.Error(e.message, e.info)
    }

    /** First non-empty line of release notes, markdown stripped, trimmed to [max] chars. */
    fun summarize(md: String, max: Int = 200): String {
        val line = plainLines(md).firstOrNull { !it.startsWith("•") || it.length > 2 }.orEmpty().removePrefix("• ")
        if (line.length <= max) return line
        val cut = line.take(max - 1)
        val sp = cut.lastIndexOf(' ')
        return (if (sp > max / 2) cut.take(sp) else cut).trimEnd(',', ';', ':', ' ') + "…"
    }

    fun plainLines(md: String): List<String> =
        md.lineSequence()
            .map { line ->
                line.trim()
                    .replace(Regex("^#{1,6}\\s*"), "")
                    .replace(Regex("^[-*+]\\s+"), "• ")
                    .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
                    .replace(Regex("(\\*\\*|__|`)"), "")
            }
            .filter { it.isNotEmpty() && !it.startsWith("```") }
            .toList()
}

/** Tiny observable (no coroutines). Listeners run on the main thread. */
class UpdateStateStore internal constructor() {
    private val listeners = CopyOnWriteArrayList<(UpdateState) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var value: UpdateState = UpdateState.Idle

    fun currentState(): UpdateState = value

    /** Returns a function that removes the listener. Does not emit the current value; read [currentState]. */
    fun addListener(listener: (UpdateState) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    internal fun update(f: (UpdateState) -> UpdateState) {
        val next: UpdateState
        synchronized(this) {
            next = f(value)
            if (next == value) return
            value = next
        }
        if (Looper.myLooper() == Looper.getMainLooper()) notify(next) else main.post { notify(next) }
    }

    private fun notify(s: UpdateState) { for (l in listeners) runCatching { l(s) } }
}

object AppDropUpdater {
    private const val TAG = "AppDrop"
    private const val PREFS = "appdrop_updater"
    private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000
    private const val DISMISS_MS = 24L * 60 * 60 * 1000
    private const val TIMEOUT_MS = 10_000
    private const val MIME_APK = "application/vnd.android.package-archive"

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "AppDrop-io").apply { isDaemon = true } }
    private val poller: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "AppDrop-dl").apply { isDaemon = true } }
    private val checking = AtomicBoolean(false)

    private var appContext: Context? = null
    private var baseUrl = ""
    private var slug = ""
    private var betaToken: String? = null
    @Volatile private var dialogEnabled = true

    /** Observable update state for your own banner. Listeners run on the main thread. */
    val state = UpdateStateStore()

    // ───────────────────────── public API ─────────────────────────

    /** Call once from Application.onCreate(). [baseUrl] is e.g. https://appdrop-store.vercel.app */
    fun init(context: Context, baseUrl: String, slug: String) {
        this.appContext = context.applicationContext
        this.baseUrl = baseUrl.trim().trimEnd('/')
        this.slug = slug.trim()
        // Offline-safe: show the cached release immediately if it is still newer than what is installed.
        runCatching {
            val ctx = context.applicationContext
            val p = prefs(ctx)
            if (p.getString("pending_apk", null) != null && File(p.getString("pending_apk", "")!!).isFile && cachedInfo(ctx) != null &&
                isStillNewer(ctx, cachedInfo(ctx)!!)) {
                state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Downloaded) }
            } else {
                state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Resolved(resolveCached(ctx, false))) }
            }
        }
    }

    /** Optional: beta invite token so pre-releases count as "latest" for this user. */
    fun setBetaToken(token: String?) {
        betaToken = token?.takeIf { it.isNotBlank() }
    }

    /** false = [check] only updates [state] and never shows the dialog (you draw your own banner). Default true. */
    fun setDialogEnabled(enabled: Boolean) { dialogEnabled = enabled }

    /**
     * Updates [state]: re-reads the cache immediately, then asks the server at most once per 12 hours
     * (unless [force], which also ignores skip/dismiss). Never throws, never needs an Activity.
     */
    fun refresh(context: Context, force: Boolean = false) {
        refreshInternal(context, force, null)
    }

    /** Hides the banner for this version until a newer one is published. [force] refreshes ignore it. */
    fun skip(context: Context, versionName: String) {
        prefs(context).edit().putString("skipped_version", versionName).apply()
        state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Skipped(versionName)) }
    }

    /** Hides the banner for 24 hours. */
    fun dismissForNow(context: Context) {
        prefs(context).edit().putLong("dismissed_until", System.currentTimeMillis() + DISMISS_MS).apply()
        state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Dismissed) }
    }

    /** Opens the AppDrop store page ([UpdateInfo.storeUrl]) in the browser. */
    fun openStore(context: Context) {
        try {
            val url = when (val s = state.currentState()) {
                is UpdateState.Available -> s.info.storeUrl
                is UpdateState.Error -> s.info?.storeUrl
                else -> cachedInfo(context.applicationContext)?.storeUrl
            } ?: "$baseUrl/apps/$slug"
            openUrl(context, url)
        } catch (t: Throwable) {
            Log.w(TAG, "openStore failed", t)
        }
    }

    /** Downloads the APK (DownloadManager), verifies SHA-256, opens the installer. Reports via [state]. */
    fun startUpdate(context: Context) {
        val ctx = context.applicationContext
        try {
            val cur = state.currentState()
            if (cur is UpdateState.Downloading) return
            val info = when (cur) {
                is UpdateState.Available -> cur.info
                is UpdateState.Error -> cur.info
                else -> null
            } ?: cachedInfo(ctx)
            if (info == null) { openStore(ctx); return }
            // A verified APK is already waiting (permission was missing earlier): just install it.
            val p = prefs(ctx)
            val pending = p.getString("pending_apk", null)?.let { File(it) }
            if (pending != null && pending.isFile && p.getString("pending_version", null) == info.versionName) {
                state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Downloaded) }
                installApk(context, pending, info.versionName)
                return
            }
            if (info.downloadUrl.isNullOrBlank()) { openStore(ctx); return }
            startDownload(ctx, info)
        } catch (t: Throwable) {
            Log.w(TAG, "startUpdate failed", t)
            state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Failed("Couldn't start the update.", null)) }
        }
    }

    /**
     * Dialog flow (default): checks, and if there is an update shows an "Update available" dialog.
     * Cheap to call from onResume: it runs at most once per 12 hours unless [force] is true
     * (use force = true for a manual "Check for updates" menu item; it also reports "up to date").
     * With [setDialogEnabled]`(false)` it only updates [state].
     */
    fun check(activity: Activity, force: Boolean = false) {
        try {
            val ctx = appContext ?: activity.applicationContext.also { appContext = it }
            if (baseUrl.isEmpty() || slug.isEmpty()) {
                Log.w(TAG, "check() called before init()")
                return
            }
            // Came back from the "install unknown apps" settings screen with a download waiting?
            if (dialogEnabled && resumePendingInstall(activity)) return
            refreshInternal(ctx, force) { networked, result ->
                if (!dialogEnabled || activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return@refreshInternal
                when {
                    result is UpdateState.Error && force -> toast(activity, "Couldn't check for updates. Are you online?")
                    result is UpdateState.Available && networked -> showUpdateDialog(activity, result.info)
                    force && result is UpdateState.UpToDate -> toast(activity, "You're up to date.")
                }
            }
        } catch (t: Throwable) {
            checking.set(false)
            Log.w(TAG, "check() failed", t)
        }
    }

    // ───────────────────────── refresh ─────────────────────────

    private fun refreshInternal(context: Context, force: Boolean, onDone: ((networked: Boolean, result: UpdateState) -> Unit)?) {
        try {
            val ctx = context.applicationContext.also { appContext = it }
            if (baseUrl.isEmpty() || slug.isEmpty()) {
                Log.w(TAG, "refresh() called before init()")
                return
            }
            val prefs = prefs(ctx)
            val now = System.currentTimeMillis()
            if (!AppDropLogic.shouldCheck(now, prefs.getLong("last_check", 0L), force, CHECK_INTERVAL_MS)) {
                val r = resolveCached(ctx, false)
                state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Resolved(r)) }
                onDone?.let { cb -> main.post { cb(false, r) } }
                return
            }
            if (!checking.compareAndSet(false, true)) return
            state.update { AppDropLogic.reduce(it, AppDropLogic.Event.CheckStarted) }
            val current = installedVersion(ctx)
            val device = deviceId(ctx)
            val beta = betaToken
            io.execute {
                var failed = false
                var serverNewer: Boolean? = null
                try {
                    val json = fetchLatest(current, device, beta)
                    prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
                    serverNewer = if (json.isNull("updateAvailable")) null else json.optBoolean("updateAvailable")
                    val info = parseRelease(json)
                    // Always replace the cache: no release / no newer release clears it.
                    prefs.edit().apply { if (info == null) remove("cached_info") else putString("cached_info", infoToJson(info).toString()) }
                        .putString("cached_server_newer", serverNewer?.toString().orEmpty()).apply()
                } catch (t: Throwable) {
                    failed = true
                    Log.w(TAG, "Update check failed: ${t.message}")
                } finally {
                    checking.set(false)
                }
                val resolved = resolveCached(ctx, force)
                // A failed check keeps whatever the cache says (offline still shows a known update);
                // only a manual check reports the failure.
                val next = if (failed && force && resolved !is UpdateState.Available)
                    UpdateState.Error("Couldn't check for updates. Are you online?", null) else resolved
                main.post {
                    state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Resolved(next)) }
                    try { onDone?.invoke(!failed, next) } catch (t: Throwable) { Log.w(TAG, "Could not show update UI", t) }
                }
            }
        } catch (t: Throwable) {
            checking.set(false)
            Log.w(TAG, "refresh() failed", t)
        }
    }

    private fun resolveCached(ctx: Context, force: Boolean): UpdateState {
        val p = prefs(ctx)
        val sn = p.getString("cached_server_newer", "").orEmpty().let { if (it.isEmpty()) null else it.toBoolean() }
        return AppDropLogic.resolve(
            cachedInfo(ctx), installedVersion(ctx), installedVersionCode(ctx), sn,
            p.getString("skipped_version", null), p.getLong("dismissed_until", 0L), System.currentTimeMillis(),
            force, everChecked = p.getLong("last_check", 0L) > 0L,
        )
    }

    private fun isStillNewer(ctx: Context, info: UpdateInfo): Boolean =
        AppDropLogic.isNewer(installedVersion(ctx), installedVersionCode(ctx), info.versionName, info.versionCode) ?: false

    private fun infoToJson(i: UpdateInfo) = JSONObject().apply {
        put("versionName", i.versionName); i.versionCode?.let { put("versionCode", it) }
        put("notesSummary", i.notesSummary); put("notes", i.notes); i.publishedAt?.let { put("publishedAt", it) }
        put("storeUrl", i.storeUrl); i.downloadUrl?.let { put("downloadUrl", it) }
        i.sizeBytes?.let { put("sizeBytes", it) }; i.sha256?.let { put("sha256", it) }
    }

    private fun cachedInfo(ctx: Context): UpdateInfo? = try {
        prefs(ctx).getString("cached_info", null)?.let { s ->
            val j = JSONObject(s)
            UpdateInfo(
                versionName = j.getString("versionName"),
                versionCode = if (j.has("versionCode")) j.getLong("versionCode") else null,
                notesSummary = j.optString("notesSummary"), publishedAt = j.optString("publishedAt").ifBlank { null },
                storeUrl = j.getString("storeUrl"), downloadUrl = j.optString("downloadUrl").ifBlank { null },
                sizeBytes = if (j.has("sizeBytes")) j.getLong("sizeBytes") else null,
                sha256 = j.optString("sha256").ifBlank { null }, notes = j.optString("notes"),
            )
        }
    } catch (t: Throwable) { null }

    /** Opens the app's feedback form on the AppDrop web page in the browser. */
    fun openFeedback(activity: Activity) {
        try {
            openUrl(activity, "$baseUrl/apps/$slug#feedback")
        } catch (t: Throwable) {
            Log.w(TAG, "openFeedback failed", t)
        }
    }

    /**
     * Posts feedback straight to AppDrop (build your own in-app form around this).
     * [callback] runs on the main thread: (true, null) on success, else (false, message).
     */
    fun sendFeedback(
        context: Context,
        message: String,
        name: String? = null,
        contact: String? = null,
        callback: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        val ctx = context.applicationContext
        val version = installedVersion(ctx)
        io.execute {
            var error: String? = null
            try {
                val body = JSONObject().apply {
                    put("message", message)
                    if (!name.isNullOrBlank()) put("name", name)
                    if (!contact.isNullOrBlank()) put("contact", contact)
                    if (version.isNotEmpty()) put("version", version)
                    put("source", "app")
                }
                val conn = open("$baseUrl/api/public/apps/$slug/feedback")
                try {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    if (code != 200) {
                        val text = (if (code >= 400) conn.errorStream else conn.inputStream)
                            ?.bufferedReader()?.use { it.readText() }.orEmpty()
                        error = when (code) {
                            429 -> "Too many messages. Please try again in a few minutes."
                            else -> runCatching { JSONObject(text).optString("error") }.getOrNull()
                                ?.takeIf { it.isNotBlank() } ?: "Server error ($code)"
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "sendFeedback failed: ${t.message}")
                error = "Couldn't send feedback. Are you online?"
            }
            main.post { callback?.invoke(error == null, error) }
        }
    }

    // ───────────────────────── networking ─────────────────────────

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AppDropUpdater/1 (Android ${Build.VERSION.SDK_INT})")
        }

    private fun fetchLatest(current: String, device: String, beta: String?): JSONObject {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = buildString {
            append("$baseUrl/api/public/apps/${enc(slug)}/latest?current=${enc(current)}&device=${enc(device)}")
            if (beta != null) append("&beta=${enc(beta)}")
        }
        val conn = open(url)
        try {
            val code = conn.responseCode
            if (code != 200) error("HTTP $code")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject): UpdateInfo? {
        val latest = json.optJSONObject("latest") ?: return null
        val version = latest.optString("version").ifBlank { return null }
        val apk = latest.optJSONObject("apk")
        val url = apk?.optString("url").orEmpty()
        val notes = latest.optString("notes")
        val lines = AppDropLogic.plainLines(notes)
        return UpdateInfo(
            versionName = version,
            versionCode = if (latest.has("versionCode") && !latest.isNull("versionCode")) latest.optLong("versionCode") else null,
            notesSummary = AppDropLogic.summarize(notes),
            publishedAt = latest.optString("publishedAt").ifBlank { null },
            storeUrl = json.optString("pageUrl").ifBlank { "$baseUrl/apps/$slug" },
            downloadUrl = url.ifEmpty { null },
            sizeBytes = apk?.optLong("size", 0L)?.takeIf { it > 0 },
            sha256 = if (apk == null || apk.isNull("sha256")) null else apk.optString("sha256").lowercase(Locale.ROOT).ifBlank { null },
            notes = lines.take(6).joinToString("\n"),
        )
    }

    // ───────────────────────── dialog ─────────────────────────

    private fun showUpdateDialog(activity: Activity, r: UpdateInfo) {
        val sizeText = r.sizeBytes?.let { " (%.1f MB)".format(Locale.US, it / 1_048_576.0) }.orEmpty()
        val message = "Version ${r.versionName} is ready$sizeText." + if (r.notes.isNotEmpty()) "\n\n${r.notes}" else ""
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(message)
            .setPositiveButton("Update") { _, _ -> startUpdate(activity) }
            .setNegativeButton("Later", null)
            .setNeutralButton("Skip this version") { _, _ -> skip(activity, r.versionName) }
            .show()
    }

    // ───────────────────────── download + install ─────────────────────────

    private fun startDownload(ctx: Context, r: UpdateInfo) {
        try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return downloadFailed(ctx, r, "Couldn't start the download.")
            // One APK at a time: clear out older downloads from earlier updates.
            downloadDir(ctx)?.listFiles()?.forEach { it.delete() }
            prefs(ctx).edit().remove("pending_apk").remove("pending_version").apply()

            val fileName = "${slug}-${r.versionName}.apk".replace(Regex("[^A-Za-z0-9._-]"), "_")
            val request = DownloadManager.Request(Uri.parse(r.downloadUrl))
                .setTitle("$slug ${r.versionName} (update)")
                .setDescription("Downloading version ${r.versionName}")
                .setMimeType(MIME_APK)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "appdrop/$fileName")
            val id = dm.enqueue(request)
            state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Progress(0)) }
            pollDownload(ctx, dm, id, r, System.currentTimeMillis())
        } catch (t: Throwable) {
            Log.w(TAG, "Download failed to start", t)
            downloadFailed(ctx, r, "Couldn't start the download.")
        }
    }

    /** Polls DownloadManager every 500 ms (no receiver, no Activity reference). Gives up after 30 minutes. */
    private fun pollDownload(ctx: Context, dm: DownloadManager, id: Long, r: UpdateInfo, startedMs: Long) {
        poller.schedule({
            try {
                var status = -1; var done = 0L; var total = 0L; var local: String? = null
                dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (c.moveToFirst()) {
                        status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        local = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    }
                }
                when {
                    status == DownloadManager.STATUS_SUCCESSFUL -> onDownloadFinished(ctx, local, r)
                    status == DownloadManager.STATUS_FAILED || status == -1 -> downloadFailed(ctx, r, "The download failed.")
                    System.currentTimeMillis() - startedMs > 30L * 60 * 1000 -> downloadFailed(ctx, r, "The download timed out.")
                    else -> {
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Progress(pct)) }
                        pollDownload(ctx, dm, id, r, startedMs)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Download poll failed", t)
                downloadFailed(ctx, r, "The download failed.")
            }
        }, 500, TimeUnit.MILLISECONDS)
    }

    private fun onDownloadFinished(ctx: Context, localUri: String?, r: UpdateInfo) {
        val apk = localUri?.let { Uri.parse(it).path }?.let { File(it) }?.takeIf { it.isFile }
        if (apk == null) return downloadFailed(ctx, r, "The download failed.")
        if (r.sha256 != null) {
            val actual = sha256Of(apk)
            if (!actual.equals(r.sha256, ignoreCase = true)) {
                Log.e(TAG, "SHA-256 mismatch: expected ${r.sha256}, got $actual")
                apk.delete()
                return downloadFailed(ctx, r, "Update download is corrupted (checksum mismatch). Please try again later.", browser = false)
            }
        }
        state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Downloaded) }
        main.post { try { installApk(ctx, apk, r.versionName) } catch (t: Throwable) { Log.w(TAG, "Install step failed", t) } }
    }

    private fun downloadFailed(ctx: Context, r: UpdateInfo, message: String, browser: Boolean = true) {
        state.update { AppDropLogic.reduce(it, AppDropLogic.Event.Failed(message, r)) }
        // Dialog users have no banner to show the error in: fall back to the browser as before.
        if (browser && dialogEnabled) main.post { runCatching { openUrl(ctx, r.downloadUrl ?: r.storeUrl) } }
        else if (dialogEnabled) main.post { toast(ctx, message, long = true) }
    }

    private fun installApk(context: Context, apk: File, version: String) {
        val ctx = context.applicationContext
        // Always remember the verified APK until the new version is actually running: Android 10+
        // silently blocks starting the installer while the app is in the background, so a later
        // startUpdate() (banner "Install") or check() must be able to pick the install up again.
        prefs(ctx).edit().putString("pending_apk", apk.absolutePath).putString("pending_version", version).apply()
        val activity = context as? Activity
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            if (activity != null && !activity.isFinishing) {
                AlertDialog.Builder(activity)
                    .setTitle("Allow installing updates")
                    .setMessage("To install the update, Android needs your permission for this app to install apps. Turn on \"Allow from this source\", then come back.")
                    .setPositiveButton("Open settings") { _, _ ->
                        try { activity.startActivity(settings) } catch (t: Throwable) { Log.w(TAG, "Could not open install settings", t) }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                try { ctx.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (t: Throwable) { Log.w(TAG, "Could not open install settings", t) }
            }
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.appdrop.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, MIME_APK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    /** Dialog mode: if an APK was downloaded but the install permission was missing, install it now that it may be granted. */
    private fun resumePendingInstall(activity: Activity): Boolean {
        val ctx = activity.applicationContext
        val p = prefs(ctx)
        val path = p.getString("pending_apk", null) ?: return false
        val version = p.getString("pending_version", null) ?: ""
        val apk = File(path)
        // Already updated (or the file is gone)? Clean up and carry on with a normal check.
        if (!apk.isFile || (version.isNotEmpty() && (AppDropLogic.compareVersions(installedVersion(ctx), version) ?: 0) >= 0)) {
            p.edit().remove("pending_apk").remove("pending_version").apply()
            if (apk.isFile) apk.delete()
            return false
        }
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) return false
        if (!activity.hasWindowFocus()) return false
        return try {
            installApk(activity, apk, version)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Could not resume install", t)
            false
        }
    }

    // ───────────────────────── helpers ─────────────────────────

    private fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Random per-install id; the server stores only a hash and uses it for anonymous "active devices by version". */
    private fun deviceId(context: Context): String {
        val p = prefs(context)
        return p.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("device_id", it).apply()
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(context: Context) = context.packageManager.let { pm ->
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        else pm.getPackageInfo(context.packageName, 0)
    }

    private fun installedVersion(context: Context): String = try {
        packageInfo(context).versionName.orEmpty()
    } catch (t: Throwable) {
        ""
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(context: Context): Long? = try {
        val i = packageInfo(context)
        if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else i.versionCode.toLong()
    } catch (t: Throwable) {
        null
    }

    private fun downloadDir(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "appdrop") }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun toast(context: Context, text: String, long: Boolean = false) {
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
