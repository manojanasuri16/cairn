package app.cairn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.cairn.ui.CairnRoot
import app.cairn.widget.EXTRA_COROS_MISSING
import app.cairn.widget.EXTRA_SCREEN

class MainActivity : ComponentActivity() {
    private val coros: CorosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent?.getStringExtra(EXTRA_SCREEN)
        val missing = intent?.getBooleanExtra(EXTRA_COROS_MISSING, false) == true
        setContent { CairnRoot(coros, screen, missing) }
    }
}
