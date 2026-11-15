package app.cairn.source.corosmcp

enum class Region(val label: String, val host: String) {
    US("US", "https://mcpus.coros.com"),
    EU("EU", "https://mcpeu.coros.com"),
    CN("CN", "https://mcpcn.coros.com"),
}

open class CorosException(message: String) : Exception(message)

/** The session is gone (no token, or refresh rejected): the user must sign in again. Retrying cannot help. */
class CorosAuthException(message: String) : CorosException(message)

/** Transport failure (offline, timeout, DNS). Safe to retry with backoff. */
class CorosNetworkException(message: String) : CorosException(message)

/** The loopback wait for the browser redirect ran out; the COROS authorize state has likely expired too. */
class SignInTimeoutException : CorosException(SignInMessages.TIMEOUT)

/** User-facing sign-in strings shared by the app and tests. */
object SignInMessages {
    const val HINT = "Finish signing in within a couple of minutes."
    const val TIMEOUT = "Sign-in timed out. Please try again and finish signing in within a couple of minutes."
}

/** The browser callback carried an OAuth `error=` code. */
class AuthorizationException(val code: String) : CorosException("Authorization failed: $code")

/**
 * Region fallback policy (pure, no I/O).
 *
 * Sign-in is interactive, so we never probe regions in parallel. We start at the cached region
 * (else US) and advance US -> EU -> CN (CN last) only when the callback error code clearly says
 * the account is not on that server. Everything else (user cancel/access_denied, timeout, network,
 * loopback or state problems, unknown codes) is surfaced; the user may then tap "Try another
 * region", which calls [next] explicitly.
 */
object RegionPolicy {
    val ORDER: List<Region> = listOf(Region.US, Region.EU, Region.CN)

    private val WRONG_REGION_CODES = setOf(
        "account_not_found", "user_not_found", "unknown_account", "unknown_user", "invalid_user", "no_such_user", "wrong_region",
    )

    fun start(cached: Region?): Region = cached ?: ORDER.first()

    /** Next region after [current] in [ORDER], or null when exhausted. */
    fun next(current: Region): Region? = ORDER.getOrNull(ORDER.indexOf(current) + 1)

    /** True only for failures that clearly mean "account is not on this region's server". */
    fun isWrongRegion(e: Throwable): Boolean =
        e is AuthorizationException && e.code.lowercase() in WRONG_REGION_CODES

}
