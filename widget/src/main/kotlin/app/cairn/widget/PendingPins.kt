package app.cairn.widget

import android.content.Context

/** Minimal key-value port so the claim rules run in plain JVM tests. */
interface PinKv {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class SharedPrefsPinKv(context: Context) : PinKv {
    private val sp = context.applicationContext.getSharedPreferences("cairn_pending_pins", Context.MODE_PRIVATE)
    override fun get(key: String) = sp.getString(key, null)
    override fun put(key: String, value: String) { sp.edit().putString(key, value).apply() }
    override fun remove(key: String) { sp.edit().remove(key).apply() }
}

/** A customised widget waiting for the launcher to place it. Choices only, never health data. */
data class PendingPin(val kind: WidgetKind, val size: WidgetSize, val config: WidgetConfig, val createdMs: Long)

/**
 * Short-lived record written by the widget studio just before it asks the launcher to pin, claimed once by the new widget id
 * (pin callback, or the configure screen the launcher opens) so the widget appears already configured at the chosen size.
 */
class PendingPins(private val kv: PinKv) {
    private fun key(kind: WidgetKind) = "pending_${kind.name}"

    fun put(pin: PendingPin) = kv.put(key(pin.kind), "${pin.size.name}|${pin.createdMs}|${pin.config.encode()}")

    /**
     * Returns the record for [kind] once; a second call, or one after the TTL, returns null. When [onlySize] is given a record for
     * another size is left alone (the widget being placed is a different provider variant).
     */
    fun claim(kind: WidgetKind, now: Long, onlySize: WidgetSize? = null): PendingPin? {
        val raw = kv.get(key(kind)) ?: return null
        val parts = raw.split('|', limit = 3)
        val created = parts.getOrNull(1)?.toLongOrNull()
        val size = WidgetSize.parse(parts.getOrNull(0))
        if (parts.size < 3 || created == null || size == null || now - created !in 0..TTL_MS) { kv.remove(key(kind)); return null }
        if (onlySize != null && onlySize != size) return null
        kv.remove(key(kind))
        return PendingPin(kind, size, WidgetConfig.decode(parts[2], kind), created)
    }

    /** Remembers that the pin callback already applied the choices to [appWidgetId], so the configure screen can step aside. */
    fun markClaimed(appWidgetId: Int, now: Long) = kv.put("claimed_$appWidgetId", now.toString())

    fun wasClaimedRecently(appWidgetId: Int, now: Long): Boolean {
        val at = kv.get("claimed_$appWidgetId")?.toLongOrNull() ?: return false
        return now - at in 0..CLAIMED_WINDOW_MS
    }

    companion object {
        const val TTL_MS = 10 * 60_000L
        const val CLAIMED_WINDOW_MS = 2 * 60_000L
    }
}
