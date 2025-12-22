package com.steamdeck.mobile.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.domain.emulator.EmulatorContainer
import com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Container Management ViewModel
 */
@HiltViewModel
class ContainerViewModel @Inject constructor(
 private val winlatorEmulator: WinlatorEmulator
) : ViewModel() {

 private val _uiState = MutableStateFlow<ContainerUiState>(ContainerUiState.Loading)
 val uiState: StateFlow<ContainerUiState> = _uiState.asStateFlow()

 /**
  * Load container list
  */
 fun loadContainers() {
  viewModelScope.launch {
   _uiState.value = ContainerUiState.Loading

   val result = winlatorEmulator.listContainers()

   _uiState.value = if (result.isSuccess) {
    ContainerUiState.Success(result.getOrThrow())
   } else {
    ContainerUiState.Error(
     result.exceptionOrNull()?.message ?: "Failed to load containers"
    )
   }
  }
 }

 /**
  * Create new container
  */
 fun createContainer(name: String) {
  viewModelScope.launch {
   _uiState.value = ContainerUiState.Creating("Initializing container...")

   val config = EmulatorContainerConfig(name = name)
   val result = winlatorEmulator.createContainer(config)

   if (result.isSuccess) {
    // Reload containers after creation
    loadContainers()
   } else {
    _uiState.value = ContainerUiState.Error(
     result.exceptionOrNull()?.message ?: "Failed to create container"
    )
   }
  }
 }

 /**
  * Delete container
  */
 fun deleteContainer(containerId: String) {
  viewModelScope.launch {
   val result = winlatorEmulator.deleteContainer(containerId)

   if (result.isSuccess) {
    // Reload containers after deletion
    loadContainers()
   } else {
    _uiState.value = ContainerUiState.Error(
     result.exceptionOrNull()?.message ?: "Failed to delete container"
    )
   }
  }
 }
}

/**
 * Container management UI state
 */
@Immutable
sealed class ContainerUiState {
 /** Loading */
 @Immutable
 data object Loading : ContainerUiState()

 /** Creating container */
 @Immutable
 data class Creating(val message: String) : ContainerUiState()

 /** Success */
 @Immutable
 data class Success(val containers: List<EmulatorContainer>) : ContainerUiState()

 /** Error */
 @Immutable
 data class Error(val message: String) : ContainerUiState()
}
