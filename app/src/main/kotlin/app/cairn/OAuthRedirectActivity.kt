package app.cairn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cairn.core.designsystem.CairnTheme
import app.cairn.source.corosmcp.FailureReason
import app.cairn.source.corosmcp.RegionPolicy
import app.cairn.source.corosmcp.SignInMethod
import app.cairn.source.corosmcp.SignInState
import app.cairn.widget.EXTRA_SCREEN
import app.cairn.widget.EXTRA_SIGNED_IN
import app.cairn.widget.Screen

/**
 * Receives `app.cairn.oauth://callback?...` from the HTTPS bounce page. Does no work of its own: the coordinator
 * (application scope) validates state and exchanges the code, so a finished/destroyed activity cannot lose the result.
 */
class OAuthRedirectActivity : ComponentActivity() {
    private val graph get() = (application as CairnApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) graph.signIn.handleRedirect(intent?.dataString)
        setContent {
            CairnTheme {
                Surface(Modifier.fillMaxSize()) { RedirectBody(onOpenApp = ::openApp, onRestart = ::restart) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        graph.signIn.handleRedirect(intent.dataString)
    }

    /** Signed in: land on the Widgets home with a one-time confirmation. Otherwise (Back to Cairn after a failure): the account screen, on top of the home. */
    private fun openApp(signedIn: Boolean) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_SCREEN, if (signedIn) Screen.Home.name else Screen.Account.name)
                .putExtra(EXTRA_SIGNED_IN, signedIn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }

    private fun restart() {
        graph.signIn.start(SignInMethod.BOUNCE, RegionPolicy.start(graph.regionStore.get()))
        finish()
    }

    @Composable
    private fun RedirectBody(onOpenApp: (Boolean) -> Unit, onRestart: () -> Unit) {
        val s by graph.signIn.state.collectAsState()
        LaunchedEffect(s) { if (s is SignInState.SignedIn) onOpenApp(true) }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val st = s) {
                is SignInState.Failed -> {
                    Text(st.reason.message, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onRestart) { Text(if (st.reason == FailureReason.EXPIRED) "Sign in" else "Try again") }
                    TextButton(onClick = { onOpenApp(false) }) { Text("Back to Cairn") }
                }
                else -> {
                    CircularProgressIndicator()
                    Text("Finishing sign-in...", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
