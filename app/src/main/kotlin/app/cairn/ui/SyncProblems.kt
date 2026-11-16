package app.cairn.ui

/** What kind of problem the header banner is showing. Decides which action it offers. */
enum class ProblemKind { NotSignedIn, NeedsSignIn, Offline, Network, Server, Partial, CapReached, Other }

enum class BannerAction { SignIn, Retry, None }

/** Pure rules for the error banner: sign-in problems offer Sign in (a retry can never work), transient ones offer Retry. */
object SyncProblems {
    fun actionFor(kind: ProblemKind): BannerAction = when (kind) {
        ProblemKind.NotSignedIn, ProblemKind.NeedsSignIn -> BannerAction.SignIn
        ProblemKind.Network, ProblemKind.Server, ProblemKind.Partial -> BannerAction.Retry
        ProblemKind.Offline, ProblemKind.CapReached, ProblemKind.Other -> BannerAction.None
    }

    fun label(action: BannerAction, kind: ProblemKind): String? = when (action) {
        BannerAction.SignIn -> if (kind == ProblemKind.NeedsSignIn) "Sign in again" else "Sign in"
        BannerAction.Retry -> "Retry"
        BannerAction.None -> null
    }

    /** Signed out is certain only for [ProblemKind.NotSignedIn]; then the account screen can start the sign-in by itself. */
    fun startsSignIn(kind: ProblemKind): Boolean = kind == ProblemKind.NotSignedIn

    /** Kind of a failed sync run, from its stored outcome name and note. */
    fun fromOutcome(outcome: String?, note: String?): ProblemKind = when (outcome) {
        "NEEDS_SIGN_IN" -> ProblemKind.NeedsSignIn
        "NETWORK_ERROR" -> ProblemKind.Network
        "PARTIAL" -> ProblemKind.Partial
        "FAILED" -> ProblemKind.Server
        "SKIPPED" -> if (note?.contains("budget") == true) ProblemKind.CapReached else ProblemKind.Other
        else -> ProblemKind.Other
    }

    /** Kind of a manual sync that was stopped before it started. */
    fun fromGate(signedIn: Boolean, online: Boolean, message: String): ProblemKind = when {
        !signedIn -> ProblemKind.NotSignedIn
        !online -> ProblemKind.Offline
        message.contains("daily limit") -> ProblemKind.CapReached
        else -> ProblemKind.Other
    }
}

/** Where the app goes after a successful sign-in. */
object PostSignIn {
    /** True only for the false-to-true flip, so opening the account screen while already signed in never navigates away. */
    fun goHome(wasSignedIn: Boolean, nowSignedIn: Boolean): Boolean = !wasSignedIn && nowSignedIn

    /** Both the in-app flip and the redirect activity can report the same sign-in; show the confirmation once per few seconds. */
    fun shouldConfirm(lastShownMs: Long, nowMs: Long): Boolean = lastShownMs <= 0L || nowMs - lastShownMs !in 0..CONFIRM_WINDOW_MS

    const val CONFIRM_WINDOW_MS = 5_000L
    const val MESSAGE = "Signed in to COROS. Syncing now."
}
