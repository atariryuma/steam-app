package com.steamdeck.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Winlator初期化のViewModel
 */
@HiltViewModel
class WinlatorInitViewModel @Inject constructor(
    private val winlatorEmulator: WinlatorEmulator
) : ViewModel() {

    private val _uiState = MutableStateFlow<WinlatorInitUiState>(WinlatorInitUiState.Idle)
    val uiState: StateFlow<WinlatorInitUiState> = _uiState.asStateFlow()

    /**
     * Winlatorを初期化
     */
    fun initialize() {
        viewModelScope.launch {
            try {
                // 1. Check if already initialized
                _uiState.value = WinlatorInitUiState.CheckingAvailability

                val availableResult = winlatorEmulator.isAvailable()
                if (availableResult.isSuccess && availableResult.getOrNull() == true) {
                    _uiState.value = WinlatorInitUiState.AlreadyInitialized
                    // Wait a moment then complete
                    kotlinx.coroutines.delay(500)
                    _uiState.value = WinlatorInitUiState.Completed
                    return@launch
                }

                // 2. Initialize Winlator
                _uiState.value = WinlatorInitUiState.Initializing(
                    progress = 0f,
                    statusText = "Winlatorを初期化中..."
                )

                val initResult = winlatorEmulator.initialize { progress, status ->
                    _uiState.value = WinlatorInitUiState.Initializing(
                        progress = progress,
                        statusText = status
                    )
                }

                if (initResult.isSuccess) {
                    _uiState.value = WinlatorInitUiState.Completed
                } else {
                    val error = initResult.exceptionOrNull()
                    _uiState.value = WinlatorInitUiState.Error(
                        error?.message ?: "初期化に失敗しました"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WinlatorInitUiState.Error(
                    e.message ?: "初期化中にエラーが発生しました"
                )
            }
        }
    }
}

/**
 * Winlator initialization UI state
 */
@Immutable
sealed class WinlatorInitUiState {
    /** Idle state */
    @Immutable
    data object Idle : WinlatorInitUiState()

    /** Checking availability */
    @Immutable
    data object CheckingAvailability : WinlatorInitUiState()

    /** Already initialized */
    @Immutable
    data object AlreadyInitialized : WinlatorInitUiState()

    /** Initializing */
    @Immutable
    data class Initializing(
        val progress: Float,
        val statusText: String
    ) : WinlatorInitUiState()

    /** Completed */
    @Immutable
    data object Completed : WinlatorInitUiState()

    /** Error */
    @Immutable
    data class Error(val message: String) : WinlatorInitUiState()
}
