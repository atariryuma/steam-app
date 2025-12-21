package com.steamdeck.mobile.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.steam.SteamSetupManager
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.usecase.DeleteGameUseCase
import com.steamdeck.mobile.domain.usecase.GetGameByIdUseCase
import com.steamdeck.mobile.domain.usecase.LaunchGameUseCase
import com.steamdeck.mobile.domain.usecase.ScanInstalledGamesUseCase
import com.steamdeck.mobile.domain.usecase.ToggleFavoriteUseCase
import com.steamdeck.mobile.domain.usecase.TriggerGameDownloadUseCase
import com.steamdeck.mobile.presentation.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Game Detail Screen ViewModel
 *
 * 2025 Best Practice:
 * - DataResult<T> for type-safe error handling
 * - AppLogger for centralized logging
 * - Structured concurrency with proper cancellation
 */
@HiltViewModel
class GameDetailViewModel @Inject constructor(
 @ApplicationContext private val context: Context,
 private val getGameByIdUseCase: GetGameByIdUseCase,
 private val launchGameUseCase: LaunchGameUseCase,
 private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
 private val deleteGameUseCase: DeleteGameUseCase,
 private val steamLauncher: SteamLauncher,
 private val steamSetupManager: SteamSetupManager,
 private val scanInstalledGamesUseCase: ScanInstalledGamesUseCase,
 private val triggerGameDownloadUseCase: TriggerGameDownloadUseCase,
 private val gameRepository: GameRepository
) : ViewModel() {

 companion object {
  private const val TAG = "GameDetailVM"
 }

 private val _uiState = MutableStateFlow<GameDetailUiState>(GameDetailUiState.Loading)
 val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

 private val _launchState = MutableStateFlow<LaunchState>(LaunchState.Idle)
 val launchState: StateFlow<LaunchState> = _launchState.asStateFlow()

 private val _steamLaunchState = MutableStateFlow<SteamLaunchState>(SteamLaunchState.Idle)
 val steamLaunchState: StateFlow<SteamLaunchState> = _steamLaunchState.asStateFlow()

 private val _isSteamInstalled = MutableStateFlow(false)
 val isSteamInstalled: StateFlow<Boolean> = _isSteamInstalled.asStateFlow()

 private val _isScanning = MutableStateFlow(false)
 val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

 init {
  checkSteamInstallation()
 }

 /**
  * Load game details
  */
 fun loadGame(gameId: Long) {
  viewModelScope.launch {
   try {
    val game = getGameByIdUseCase(gameId)
    _uiState.value = if (game != null) {
     GameDetailUiState.Success(game)
    } else {
     GameDetailUiState.Error("Game not found")
    }
   } catch (e: Exception) {
    _uiState.value = GameDetailUiState.Error(e.message ?: "Unknown error")
   }
  }
 }

 /**
  * Launch game
  */
 fun launchGame(gameId: Long) {
  viewModelScope.launch {
   _launchState.value = LaunchState.Launching
   when (val result = launchGameUseCase(gameId)) {
    is DataResult.Success -> {
     _launchState.value = LaunchState.Running(result.data)
     AppLogger.i(TAG, "Game launched: PID ${result.data}")
    }
    is DataResult.Error -> {
     val errorMessage = result.error.toUserMessage(context)
     _launchState.value = LaunchState.Error(errorMessage)
     AppLogger.e(TAG, "Launch failed: $errorMessage")
    }
    is DataResult.Loading -> {
     // Loading handled by Launching state
    }
   }
  }
 }

 /**
  * Toggle favorite status
  */
 fun toggleFavorite(gameId: Long, isFavorite: Boolean) {
  viewModelScope.launch {
   when (val result = toggleFavoriteUseCase(gameId, isFavorite)) {
    is DataResult.Success -> {
     // Update UI state
     val currentState = _uiState.value
     if (currentState is GameDetailUiState.Success) {
      _uiState.value = GameDetailUiState.Success(
       currentState.game.copy(isFavorite = isFavorite)
      )
     }
     AppLogger.d(TAG, "Favorite toggled: $isFavorite")
    }
    is DataResult.Error -> {
     AppLogger.e(TAG, "Failed to toggle favorite: ${result.error.message}")
    }
    is DataResult.Loading -> {}
   }
  }
 }

 /**
  * Delete game
  */
 fun deleteGame(game: Game) {
  viewModelScope.launch {
   when (val result = deleteGameUseCase(game)) {
    is DataResult.Success -> {
     _uiState.value = GameDetailUiState.Deleted
     AppLogger.i(TAG, "Game deleted: ${game.name}")
    }
    is DataResult.Error -> {
     val errorMessage = result.error.toUserMessage(context)
     _uiState.value = GameDetailUiState.Error(errorMessage)
     AppLogger.e(TAG, "Delete failed: $errorMessage")
    }
    is DataResult.Loading -> {}
   }
  }
 }

 /**
  * Check Steam installation status
  */
 fun checkSteamInstallation() {
  viewModelScope.launch {
   try {
    val isInstalled = steamSetupManager.isSteamInstalled()
    _isSteamInstalled.value = isInstalled
    AppLogger.d(TAG, "Steam installation status: $isInstalled")
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to check Steam installation", e)
    _isSteamInstalled.value = false
   }
  }
 }

 /**
  * Launch game via Steam Client
  */
 fun launchGameViaSteam(gameId: Long) {
  viewModelScope.launch {
   try {
    _steamLaunchState.value = SteamLaunchState.Launching

    // Get current game information
    val currentState = _uiState.value
    if (currentState !is GameDetailUiState.Success) {
     _steamLaunchState.value = SteamLaunchState.Error("Failed to get game information")
     return@launch
    }

    val game = currentState.game

    // Check Container ID and Steam App ID
    if (game.winlatorContainerId == null) {
     _steamLaunchState.value = SteamLaunchState.Error(
      "Winlator container is not configured. Please create a container in Settings."
     )
     return@launch
    }

    if (game.steamAppId == null) {
     _steamLaunchState.value = SteamLaunchState.Error(
      "This game has no Steam App ID"
     )
     return@launch
    }

    // Launch via Steam Client
    val result = steamLauncher.launchGameViaSteam(
     containerId = game.winlatorContainerId.toString(),
     appId = game.steamAppId
    )

    result
     .onSuccess {
      _steamLaunchState.value = SteamLaunchState.Running(game.steamAppId.toInt())
      AppLogger.i(TAG, "Game launched via Steam: appId=${game.steamAppId}")
     }
     .onFailure { error ->
      _steamLaunchState.value = SteamLaunchState.Error(
       error.message ?: "Failed to launch via Steam"
      )
      AppLogger.e(TAG, "Failed to launch game via Steam", error)
     }

   } catch (e: Exception) {
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: "An unexpected error occurred during Steam launch"
    )
    AppLogger.e(TAG, "Exception during Steam launch", e)
   }
  }
 }

 /**
  * Open Steam Client
  */
 fun openSteamClient(gameId: Long) {
  viewModelScope.launch {
   try {
    _steamLaunchState.value = SteamLaunchState.Launching

    // Get current game information
    val currentState = _uiState.value
    if (currentState !is GameDetailUiState.Success) {
     _steamLaunchState.value = SteamLaunchState.Error("Failed to get game information")
     return@launch
    }

    val game = currentState.game

    // Check Container ID
    if (game.winlatorContainerId == null) {
     _steamLaunchState.value = SteamLaunchState.Error(
      "Winlator container is not configured. Please create a container in Settings."
     )
     return@launch
    }

    // Launch Steam Big Picture mode
    val result = steamLauncher.launchSteamBigPicture(game.winlatorContainerId.toString())

    result
     .onSuccess {
      _steamLaunchState.value = SteamLaunchState.Running(0)
      AppLogger.i(TAG, "Steam Big Picture opened successfully")
     }
     .onFailure { error ->
      _steamLaunchState.value = SteamLaunchState.Error(
       error.message ?: "Steam Big Picture launch failed"
      )
      AppLogger.e(TAG, "Failed to open Steam Big Picture", error)
     }

   } catch (e: Exception) {
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: "An unexpected error occurred while launching Steam Big Picture"
    )
    AppLogger.e(TAG, "Exception while opening Steam Client", e)
   }
  }
 }

 /**
  * Reset Steam launch state
  */
 fun resetSteamLaunchState() {
  _steamLaunchState.value = SteamLaunchState.Idle
 }

 /**
  * Open game installation page using Steam Deep Link
  *
  * Uses the steam://install/<appId> protocol of the Android Steam app
  * to enable game download/installation in the official Steam app
  */
 fun openSteamInstallPage(gameId: Long) {
  viewModelScope.launch {
   try {
    // Get current game information
    val currentState = _uiState.value
    if (currentState !is GameDetailUiState.Success) {
     AppLogger.e(TAG, "Cannot open Steam install page: game info not loaded")
     return@launch
    }

    val game = currentState.game

    // Check Steam App ID
    if (game.steamAppId == null) {
     _steamLaunchState.value = SteamLaunchState.Error(
      "This game has no Steam App ID"
     )
     return@launch
    }

    // Open with Steam Deep Link
    val result = steamLauncher.openSteamInstallPage(game.steamAppId)

    result
     .onSuccess {
      AppLogger.i(TAG, "Opened Steam install page for: ${game.name} (appId=${game.steamAppId})")
     }
     .onFailure { error ->
      _steamLaunchState.value = SteamLaunchState.Error(
       error.message ?: "Failed to open Steam install page"
      )
      AppLogger.e(TAG, "Failed to open Steam install page", error)
     }

   } catch (e: Exception) {
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: "An unexpected error occurred"
    )
    AppLogger.e(TAG, "Exception while opening Steam install page", e)
   }
  }
 }

 /**
  * Scan for installed games and update executable file paths
  */
 fun scanForInstalledGame(gameId: Long) {
  viewModelScope.launch {
   try {
    _isScanning.value = true
    AppLogger.i(TAG, "Scanning for installed game: gameId=$gameId")

    when (val result = scanInstalledGamesUseCase(gameId)) {
     is DataResult.Success -> {
      if (result.data) {
       AppLogger.i(TAG, "Game found and updated")
       // Reload game information
       loadGame(gameId)
      } else {
       AppLogger.i(TAG, "Game not found (not installed yet)")
       // Bug fix: Notify user when game is not found
       _steamLaunchState.value = SteamLaunchState.NotInstalled(
        "Game not found. Please make sure it's downloaded via Steam app."
       )
      }
     }
     is DataResult.Error -> {
      val errorMessage = result.error.toUserMessage(context)
      AppLogger.e(TAG, "Scan failed: $errorMessage")
      // Bug fix: Notify user of scan errors
      _steamLaunchState.value = SteamLaunchState.Error(errorMessage)
     }
     is DataResult.Loading -> {
      // Handled by _isScanning
     }
    }

   } catch (e: Exception) {
    AppLogger.e(TAG, "Exception during scan", e)
    // Bug fix: Notify user of exceptions
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: "An unexpected error occurred while scanning"
    )
   } finally {
    _isScanning.value = false
   }
  }
 }

 /**
  * Trigger game download via Steam Client
  *
  * NEW 2025: Automatic download/install through Wine Steam client
  * - Starts FileObserver monitoring service
  * - Launches steam.exe -applaunch <appId> (auto-triggers download)
  * - Monitors installation progress in real-time
  */
 fun triggerGameDownload(gameId: Long) {
  viewModelScope.launch {
   try {
    _steamLaunchState.value = SteamLaunchState.InitiatingDownload
    AppLogger.i(TAG, "Triggering game download for gameId=$gameId")

    when (val result = triggerGameDownloadUseCase(gameId)) {
     is DataResult.Success -> {
      _steamLaunchState.value = SteamLaunchState.Downloading(0)
      AppLogger.i(TAG, "Download initiated successfully")

      // Start observing installation progress
      observeInstallationProgress(gameId)
     }
     is DataResult.Error -> {
      val errorMessage = result.error.toUserMessage(context)
      _steamLaunchState.value = SteamLaunchState.Error(errorMessage)
      AppLogger.e(TAG, "Failed to trigger download: $errorMessage")
     }
     is DataResult.Loading -> {
      // Handled by InitiatingDownload state
     }
    }

   } catch (e: Exception) {
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: "An unexpected error occurred while starting download"
    )
    AppLogger.e(TAG, "Exception during download trigger", e)
   }
  }
 }

 /**
  * Observe installation progress in real-time
  *
  * NEW 2025: Monitors game installation status via Flow
  * Updates UI with download/install progress
  */
 private fun observeInstallationProgress(gameId: Long) {
  viewModelScope.launch {
   try {
    gameRepository.observeGame(gameId).collect { game ->
     if (game == null) {
      AppLogger.w(TAG, "Game not found during progress monitoring")
      return@collect
     }

     AppLogger.d(TAG, "Installation status: ${game.installationStatus}, progress: ${game.installProgress}%")

     when (game.installationStatus) {
      InstallationStatus.DOWNLOADING -> {
       _steamLaunchState.value = SteamLaunchState.Downloading(game.installProgress)
      }
      InstallationStatus.INSTALLING -> {
       _steamLaunchState.value = SteamLaunchState.Installing(game.installProgress)
      }
      InstallationStatus.INSTALLED -> {
       _steamLaunchState.value = SteamLaunchState.InstallComplete(game.name)
       AppLogger.i(TAG, "Game installation complete: ${game.name}")

       // Reload game information
       loadGame(gameId)
      }
      InstallationStatus.VALIDATION_FAILED -> {
       _steamLaunchState.value = SteamLaunchState.ValidationFailed(
        listOf("Installation validation failed. Please check game files.")
       )
      }
      else -> {
       // Other statuses (NOT_INSTALLED, UPDATE_REQUIRED, etc.)
       // No UI update needed
      }
     }
    }
   } catch (e: Exception) {
    AppLogger.e(TAG, "Exception during installation progress monitoring", e)
   }
  }
 }

}

/**
 * Game detail screen UI state
 */
@Immutable
sealed class GameDetailUiState {
 /** Loading */
 @Immutable
 data object Loading : GameDetailUiState()

 /** Success */
 @Immutable
 data class Success(val game: Game) : GameDetailUiState()

 /** Deleted */
 @Immutable
 data object Deleted : GameDetailUiState()

 /** Error */
 @Immutable
 data class Error(val message: String) : GameDetailUiState()
}

/**
 * Game launch state
 */
@Immutable
sealed class LaunchState {
 /** Idle state */
 @Immutable
 data object Idle : LaunchState()

 /** Launching */
 @Immutable
 data object Launching : LaunchState()

 /** Running */
 @Immutable
 data class Running(val processId: Int) : LaunchState()

 /** Error */
 @Immutable
 data class Error(val message: String) : LaunchState()
}

/**
 * Steam Client launch state
 *
 * Added 2025: Download/Install progress tracking
 */
@Immutable
sealed class SteamLaunchState {
 /** Idle state */
 @Immutable
 data object Idle : SteamLaunchState()

 /** Checking installation */
 @Immutable
 data object CheckingInstallation : SteamLaunchState()

 /** Initiating download */
 @Immutable
 data object InitiatingDownload : SteamLaunchState()

 /** Downloading game files */
 @Immutable
 data class Downloading(val progress: Int) : SteamLaunchState()

 /** Installing/extracting game files */
 @Immutable
 data class Installing(val progress: Int) : SteamLaunchState()

 /** Installation complete */
 @Immutable
 data class InstallComplete(val gameName: String) : SteamLaunchState()

 /** Validation failed (missing DLLs, corrupt files, etc.) */
 @Immutable
 data class ValidationFailed(val errors: List<String>) : SteamLaunchState()

 /** Launching */
 @Immutable
 data object Launching : SteamLaunchState()

 /** Running */
 @Immutable
 data class Running(val processId: Int) : SteamLaunchState()

 /** Error */
 @Immutable
 data class Error(val message: String) : SteamLaunchState()

 /** Not installed */
 @Immutable
 data class NotInstalled(val message: String) : SteamLaunchState()
}
