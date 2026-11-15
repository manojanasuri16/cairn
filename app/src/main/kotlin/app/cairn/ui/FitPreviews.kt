package app.cairn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cairn.core.designsystem.CairnTheme

private val longValues = listOf(MetricUi("5K", "25:06"), MetricUi("10K", "52:33"), MetricUi("Half marathon", "1:58:07"), MetricUi("Marathon", "4:09:57"), MetricUi("Calories", "1,234 kcal"))

@Composable
private fun FitSample() = CairnTheme {
    Column(Modifier.padding(16.dp)) {
        FitText("12.34 km", MaterialTheme.typography.displayMedium)
        AdaptiveTiles(longValues)
    }
}

@Preview(name = "Narrow 320", widthDp = 320, showBackground = true) @Composable private fun Narrow() = FitSample()
@Preview(name = "Wide tablet", widthDp = 840, showBackground = true) @Composable private fun Wide() = FitSample()
@Preview(name = "Font 200%", widthDp = 360, fontScale = 2f, showBackground = true) @Composable private fun Big() = FitSample()
