package com.steamdeck.mobile.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig
import com.steamdeck.mobile.domain.emulator.WindowsEmulator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Wine/Winlator integration testing.
 *
 * Provides essential diagnostic tests for Wine environment:
 * 1. Availability check
 * 2. Initialization
 * 3. Container creation test
 *
 * Uses data-driven approach with string resources for maintainability.
 */
@HiltViewModel
class WineTestViewModel @Inject constructor(
 @ApplicationContext private val context: Context,
 private val emulator: WindowsEmulator
) : ViewModel() {

 private val _uiState = MutableStateFlow<WineTestUiState>(WineTestUiState.Idle)
 val uiState: StateFlow<WineTestUiState> = _uiState.asStateFlow()

 companion object {
  private const val TAG = "WineTestViewModel"
 }

 /**
  * Check if emulator is available and properly configured.
  */
 fun checkWineAvailability() {
  viewModelScope.launch {
   val checkingMessage = context.getString(
    com.steamdeck.mobile.R.string.wine_test_checking_availability,
    emulator.name
   )
   _uiState.value = WineTestUiState.Testing(checkingMessage)

   emulator.isAvailable().fold(
    onSuccess = { isAvailable ->
     if (isAvailable) {
      val info = emulator.getEmulatorInfo()

      // Build success message using string resources
      val successHeader = context.getString(
       com.steamdeck.mobile.R.string.wine_test_available_success,
       info.name,
       info.version
      )

      val details = context.getString(
       com.steamdeck.mobile.R.string.wine_test_available_details,
       info.backend,
       info.wineVersion ?: "N/A",
       info.translationLayer ?: "N/A",
       info.graphicsBackend ?: "N/A"
      )

      val capabilities = context.getString(com.steamdeck.mobile.R.string.wine_test_available_capabilities)
      val capabilitiesList = info.capabilities.joinToString("\n") { "• $it" }

      val ready = context.getString(com.steamdeck.mobile.R.string.wine_test_available_ready)

      _uiState.value = WineTestUiState.Success(
       "$successHeader\n\n$details\n\n$capabilities\n$capabilitiesList\n\n$ready"
      )
     } else {
      val notAvailableHeader = context.getString(
       com.steamdeck.mobile.R.string.wine_test_not_available,
       emulator.name
      )
      val components = context.getString(com.steamdeck.mobile.R.string.wine_test_required_components)

      _uiState.value = WineTestUiState.Error("$notAvailableHeader\n\n$components")
     }
    },
    onFailure = { error ->
     val errorMessage = context.getString(
      com.steamdeck.mobile.R.string.wine_test_error_checking,
      error.message
     )
     _uiState.value = WineTestUiState.Error(errorMessage)
    }
   )
  }
 }

 /**
  * Initialize the emulator environment.
  */
 fun initializeEmulator() {
  viewModelScope.launch {
   val initializingMessage = context.getString(
    com.steamdeck.mobile.R.string.wine_test_initializing,
    emulator.name
   )
   _uiState.value = WineTestUiState.Testing(initializingMessage)

   emulator.initialize { progress, message ->
    _uiState.value = WineTestUiState.Testing(
     "$message (${(progress * 100).toInt()}%)"
    )
   }.fold(
    onSuccess = {
     val successHeader = context.getString(
      com.steamdeck.mobile.R.string.wine_test_init_success,
      emulator.name
     )
     val nextSteps = context.getString(com.steamdeck.mobile.R.string.wine_test_init_next_steps)

     _uiState.value = WineTestUiState.Success("$successHeader\n\n$nextSteps")
    },
    onFailure = { error ->
     val failedMessage = context.getString(
      com.steamdeck.mobile.R.string.wine_test_init_failed,
      error.message
     )
     val note = context.getString(com.steamdeck.mobile.R.string.wine_test_init_note)

     _uiState.value = WineTestUiState.Error("$failedMessage\n\n$note")
    }
   )
  }
 }

 /**
  * Test container creation.
  */
 fun testCreateContainer() {
  viewModelScope.launch {
   val creatingMessage = context.getString(com.steamdeck.mobile.R.string.wine_test_creating_container)
   _uiState.value = WineTestUiState.Testing(creatingMessage)

   val config = EmulatorContainerConfig(
    name = "TestContainer_${System.currentTimeMillis()}"
   )

   emulator.createContainer(config).fold(
    onSuccess = { container ->
     val successHeader = context.getString(com.steamdeck.mobile.R.string.wine_test_container_success)

     val details = context.getString(
      com.steamdeck.mobile.R.string.wine_test_container_details,
      container.id,
      container.name,
      container.rootPath,
      container.sizeBytes / 1024,
      container.isInitialized().toString()
     )

     val ready = context.getString(com.steamdeck.mobile.R.string.wine_test_container_ready)

     _uiState.value = WineTestUiState.Success("$successHeader\n\n$details\n\n$ready")
    },
    onFailure = { error ->
     val errorMessage = context.getString(
      com.steamdeck.mobile.R.string.wine_test_container_error,
      error.message
     )
     _uiState.value = WineTestUiState.Error(errorMessage)
    }
   )
  }
 }

}

/**
 * UI state for Wine testing.
 */
@Immutable
sealed class WineTestUiState {
 /**
  * Initial idle state.
  */
 @Immutable
 data object Idle : WineTestUiState()

 /**
  * Test is running.
  */
 @Immutable
 data class Testing(val message: String) : WineTestUiState()

 /**
  * Test completed successfully.
  */
 @Immutable
 data class Success(val message: String) : WineTestUiState()

 /**
  * Test failed with error.
  */
 @Immutable
 data class Error(val message: String) : WineTestUiState()
}
