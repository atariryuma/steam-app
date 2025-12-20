package com.steamdeck.mobile.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.domain.emulator.DirectXWrapperType
import com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig
import com.steamdeck.mobile.domain.emulator.PerformancePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Winlator initialization ViewModel
 */
@HiltViewModel
class WinlatorInitViewModel @Inject constructor(
 private val winlatorEmulator: WinlatorEmulator
) : ViewModel() {

 private val _uiState = MutableStateFlow<WinlatorInitUiState>(WinlatorInitUiState.Idle)
 val uiState: StateFlow<WinlatorInitUiState> = _uiState.asStateFlow()

 /**
  * Winlator initialization
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

     // OPTIMIZATION: Pre-create default container if it doesn't exist
     createDefaultContainerInBackground()

     _uiState.value = WinlatorInitUiState.Completed
     return@launch
    }

    // 2. Initialize Winlator
    _uiState.value = WinlatorInitUiState.Initializing(
     progress = 0f,
     statusText = "Winlator initializationin..."
    )

    val initResult = winlatorEmulator.initialize { progress, status ->
     _uiState.value = WinlatorInitUiState.Initializing(
      progress = progress,
      statusText = status
     )
    }

    if (initResult.isSuccess) {
     // OPTIMIZATION: Pre-create default container in background
     // This ensures instant game launch on first run (no 60s wait)
     createDefaultContainerInBackground()

     _uiState.value = WinlatorInitUiState.Completed
    } else {
     val error = initResult.exceptionOrNull()
     _uiState.value = WinlatorInitUiState.Error(
      error?.message ?: "初期化 failed"
     )
    }
   } catch (e: Exception) {
    _uiState.value = WinlatorInitUiState.Error(
     e.message ?: "初期化in Error 発生しました"
    )
   }
  }
 }

 /**
  * Create default container in background (non-blocking)
  *
  * Best Practice (2025): Pre-create shared container on initialization
  * - Prevents 60s wait on first game launch
  * - Runs in background, doesn't block UI
  * - Silently fails if container already exists (idempotent)
  */
 private fun createDefaultContainerInBackground() {
  viewModelScope.launch {
   try {
    // Check if default container already exists
    val containers = winlatorEmulator.listContainers().getOrNull() ?: emptyList()
    val defaultExists = containers.any { it.id == "default_shared_container" }

    if (defaultExists) {
     android.util.Log.d("WinlatorInitVM", "Default container already exists, skipping creation")
     return@launch
    }

    android.util.Log.i("WinlatorInitVM", "Pre-creating default container in background...")

    // Create default container with optimal settings
    val config = EmulatorContainerConfig(
     name = "Default Container",
     screenWidth = 1280,
     screenHeight = 720,
     directXWrapper = DirectXWrapperType.DXVK,
     performancePreset = PerformancePreset.BALANCED,
     customEnvVars = emptyMap()
    )

    val result = winlatorEmulator.createContainer(config)

    if (result.isSuccess) {
     android.util.Log.i("WinlatorInitVM", "Default container created successfully in background")
    } else {
     // Silent failure - not critical, will be created on first game launch
     android.util.Log.w("WinlatorInitVM", "Failed to pre-create default container: ${result.exceptionOrNull()?.message}")
    }
   } catch (e: Exception) {
    // Silent failure - not critical
    android.util.Log.w("WinlatorInitVM", "Exception during container pre-creation", e)
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
  val statusText: String,
  val currentStep: Int = 0,
  val totalSteps: Int = 0,
  val elapsedTimeMs: Long = 0L
 ) : WinlatorInitUiState()

 /** Completed */
 @Immutable
 data object Completed : WinlatorInitUiState()

 /** Error */
 @Immutable
 data class Error(val message: String) : WinlatorInitUiState()
}
