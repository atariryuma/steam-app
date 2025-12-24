package com.steamdeck.mobile.presentation.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Creates a StateFlow with the given initial value.
 * Reduces boilerplate in ViewModels.
 *
 * Usage:
 * ```
 * class MyViewModel : ViewModel() {
 *     private val (_state, state) = stateFlow(MyState.Loading)
 * }
 * ```
 */
fun <T> stateFlow(initial: T): Pair<MutableStateFlow<T>, StateFlow<T>> {
    val mutable = MutableStateFlow(initial)
    return mutable to mutable.asStateFlow()
}

/**
 * Launches a coroutine with automatic error handling.
 * Reduces try-catch boilerplate in ViewModels.
 *
 * Usage:
 * ```
 * viewModel.launchCatching(
 *     block = { someUseCase() },
 *     onSuccess = { result -> _state.value = Success(result) },
 *     onError = { error -> _state.value = Error(error.message) }
 * )
 * ```
 */
inline fun <T> ViewModel.launchCatching(
    tag: String = "ViewModel",
    crossinline block: suspend CoroutineScope.() -> T,
    crossinline onSuccess: (T) -> Unit,
    crossinline onError: (Exception) -> Unit = { e ->
        AppLogger.e(tag, "Error in launchCatching", e)
    }
) {
    viewModelScope.launch {
        try {
            val result = block()
            onSuccess(result)
        } catch (e: Exception) {
            onError(e)
        }
    }
}

/**
 * Launches a coroutine with automatic error handling (no success callback).
 * Useful for fire-and-forget operations.
 *
 * Usage:
 * ```
 * viewModel.launchCatching(
 *     block = { someUseCase() },
 *     onError = { error -> _state.value = Error(error.message) }
 * )
 * ```
 */
inline fun ViewModel.launchCatching(
    tag: String = "ViewModel",
    crossinline block: suspend CoroutineScope.() -> Unit,
    crossinline onError: (Exception) -> Unit = { e ->
        AppLogger.e(tag, "Error in launchCatching", e)
    }
) {
    viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            onError(e)
        }
    }
}
