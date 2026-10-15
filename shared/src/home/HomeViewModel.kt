package dev.justinsmith.interval.home

import androidx.lifecycle.ViewModel
import dev.justinsmith.interval.Greeting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HomeUiState(
    val greeting: String,
    val showDetails: Boolean = false,
)

class HomeViewModel(greeting: Greeting) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(greeting = greeting.greet()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun toggleDetails() {
        _state.update { it.copy(showDetails = !it.showDetails) }
    }
}
