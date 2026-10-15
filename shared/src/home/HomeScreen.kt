package dev.justinsmith.interval.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.compose_multiplatform
import dev.justinsmith.interval.ui.IntervalTheme
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.currentKoinScope

/** Resolves the screen's ViewModel, scoped to its back-stack entry, and binds it to [HomeScreen]. */
@Composable
fun HomeRoute() {
    val scope = currentKoinScope()
    val viewModel = viewModel { HomeViewModel(scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, onToggleDetails = viewModel::toggleDetails)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggleDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Interval", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onToggleDetails) {
                Text(if (state.showDetails) "Hide details" else "Show details")
            }
            AnimatedVisibility(state.showDetails) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), contentDescription = null)
                    Text(state.greeting)
                }
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    IntervalTheme {
        HomeScreen(state = HomeUiState(greeting = "Hello, Preview!", showDetails = true), onToggleDetails = {})
    }
}
