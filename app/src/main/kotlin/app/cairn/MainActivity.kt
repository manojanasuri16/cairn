package app.cairn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.cairn.appdrop.AppDropUpdater
import app.cairn.ui.CairnRoot
import app.cairn.ui.NavRequest
import app.cairn.ui.NavRequests
import app.cairn.widget.EXTRA_COROS_MISSING
import app.cairn.widget.EXTRA_SCREEN
import app.cairn.widget.EXTRA_SIGNED_IN

class MainActivity : ComponentActivity() {
    private val coros: CorosViewModel by viewModels()

    override fun onResume() { super.onResume(); AppDropUpdater.check(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent?.getStringExtra(EXTRA_SCREEN)
        val missing = intent?.getBooleanExtra(EXTRA_COROS_MISSING, false) == true
        val signedIn = intent?.getBooleanExtra(EXTRA_SIGNED_IN, false) == true
        // A rotation or process restore replays the old intent; only a fresh launch shows the sign-in confirmation.
        setContent { CairnRoot(coros, screen, missing, signedInNotice = signedIn && savedInstanceState == null) }
    }

    /** singleTop: widget taps and the sign-in redirect arrive here while the app is open. Navigate instead of recreating, so Back still works. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NavRequests.flow.tryEmit(NavRequest(intent.getStringExtra(EXTRA_SCREEN), intent.getBooleanExtra(EXTRA_SIGNED_IN, false)))
    }
}
