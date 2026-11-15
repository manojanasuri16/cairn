package app.cairn

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

object ExplorerFeature {
    const val available = true
    @Composable fun Screen(modifier: Modifier = Modifier) = ExplorerScreen(viewModel(), modifier)
}
