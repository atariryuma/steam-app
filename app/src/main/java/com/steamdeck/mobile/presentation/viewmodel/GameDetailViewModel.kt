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
import com.steamdeck.mobile.domain.usecase.LaunchGameUseCase
import com.steamdeck.mobile.domain.usecase.LaunchOrDownloadGameUseCase
import com.steamdeck.mobile.domain.usecase.LaunchOrDownloadResult
import com.steamdeck.mobile.domain.usecase.ScanInstalledGamesUseCase
import com.steamdeck.mobile.domain.usecase.ToggleFavoriteUseCase
import com.steamdeck.mobile.presentation.util.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.steamdeck.mobile.core.winlator.WinlatorEngine
import com.steamdeck.mobile.core.xserver.XServer
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
 private val launchGameUseCase: LaunchGameUseCase,
 private val launchOrDownloadGameUseCase: LaunchOrDownloadGameUseCase,
 private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
 private val deleteGameUseCase: DeleteGameUseCase,
 private val openSteamClientUseCase: com.steamdeck.mobile.domain.usecase.OpenSteamClientUseCase,
 private val triggerGameDownloadUseCase: com.steamdeck.mobile.domain.usecase.TriggerGameDownloadUseCase,
 private val steamLauncher: SteamLauncher,
 private val steamSetupManager: SteamSetupManager,
 private val scanInstalledGamesUseCase: ScanInstalledGamesUseCase,
 private val gameRepository: GameRepository,
 private val winlatorEngine: WinlatorEngine,
 private val controllerInputRouter: com.steamdeck.mobile.core.input.ControllerInputRouter,
 private val gameControllerManager: com.steamdeck.mobile.core.input.GameControllerManager,
 private val xServerManager: com.steamdeck.mobile.core.xserver.XServerManager
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

 /**
  * Controller connection state
  *
  * LIFECYCLE CLARIFICATION (2025-12-26):
  * - This Flow is managed by GameControllerManager (application-level singleton)
  * - Survives ViewModel destruction (hot StateFlow, not tied to viewModelScope)
  * - Hardware callbacks (USB/Bluetooth) cleaned up by GameControllerManager.cleanup()
  *   in Application.onTerminate() or Activity.onDestroy()
  * - No manual cleanup required in ViewModel.onCleared()
  *
  * TECHNICAL DETAILS:
  * - StateFlow is hot: Emits latest value immediately on collection
  * - Multiple ViewModels can observe same Flow without resource leaks
  * - GameControllerManager uses InputDevice.getDeviceIds() (no native resources)
  *
  * @see com.steamdeck.mobile.core.input.GameControllerManager
  */
 val connectedControllers: StateFlow<List<com.steamdeck.mobile.core.input.GameController>> =
  gameControllerManager.connectedControllers

 /**
  * XServer instance for game display
  *
  * CRITICAL (2025-12-27): Use XServerManager's shared instance
  * - XServerManager creates XServer when Steam/game launches
  * - This StateFlow provides UI access to the active XServer
  * - Returns null if XServer not yet started
  *
  * LIFECYCLE:
  * - XServer started by XServerManager.startXServer() during game launch
  * - Survives ViewModel destruction (managed by XServerManager singleton)
  * - UI observes this Flow to get XServer instance for rendering
  *
  * @see com.steamdeck.mobile.core.xserver.XServerManager
  */
 private val _xServer = MutableStateFlow<XServer?>(null)
 val xServer: StateFlow<XServer?> = _xServer.asStateFlow()

 // FIXED: Track process monitoring job to prevent memory leaks
 // REMOVED (2025-12-26): XServer lifecycle managed by Screen, not ViewModel
 // Old bug: ViewModel held XServer reference across configuration changes → GPU memory leak
 // New: Screen creates and owns XServer, passes to launch methods
 private var processMonitoringJob: Job? = null

 // FIXED (2025-12-26): Dedicated lock object to prevent deadlock
 // Bug: synchronized(this) exposes ViewModel to external locking
 // Impact: Potential deadlock if framework code locks ViewModel instance
 // Fix: Use dedicated lock object instead of ViewModel instance
 private val monitoringJobLock = Any()

 init {
  checkSteamInstallation()
 }

 override fun onCleared() {
  super.onCleared()

  // FIXED (2025-12-26): Cancel all jobs including loadGameJob
  loadGameJob?.cancel()
  loadGameJob = null

  processMonitoringJob?.cancel()
  processMonitoringJob = null

  installProgressMonitoringJob?.cancel()
  installProgressMonitoringJob = null

  // FIXED (2025-12-26): XServer cleanup removed - managed by Screen's DisposableEffect
  // XServer lifecycle is now tied to Composition, not ViewModel
  // This prevents GPU memory leaks during configuration changes

  // Clean up WinlatorEngine resources
  winlatorEngine.cleanup()

  AppLogger.d(TAG, "ViewModel cleared: all jobs cancelled and WinlatorEngine resources cleaned up")
 }

 // FIXED (2025-12-26): Track loadGame job to prevent concurrent calls
 private var loadGameJob: Job? = null

 /**
  * Load game details and ensure Steam client is running
  *
  * FIXED (2025-12-26): Prevents concurrent loadGame calls and restarts Flow monitoring if needed
  */
 fun loadGame(gameId: Long) {
  // FIXED (2025-12-26): Cancel previous loadGame call to prevent duplicate operations
  loadGameJob?.cancel()

  loadGameJob = viewModelScope.launch {
   try {
    // CRITICAL (2025-12-27): Update XServer reference from XServerManager
    // This provides UI with access to the active XServer for game rendering
    _xServer.value = xServerManager.getXServer()
    if (_xServer.value != null) {
     AppLogger.d(TAG, "XServer instance acquired from XServerManager")
    }

    val game = gameRepository.getGameById(gameId)
    _uiState.value = if (game != null) {
     // Auto-launch Steam client if not running (for download/install monitoring)
     ensureSteamClientRunning(gameId)

     // FIXED (2025-12-26): Restart Flow monitoring if game is downloading/installing
     // This ensures monitoring continues even after screen navigation or app restart
     if (game.installationStatus in listOf(
       InstallationStatus.DOWNLOADING,
       InstallationStatus.INSTALLING
      )) {
      AppLogger.i(TAG, "Restarting installation progress monitoring for gameId=$gameId (status=${game.installationStatus})")
      // Note: XServer will be null here, so auto-launch won't work
      // But monitoring will continue and update UI states
      observeInstallationProgressWithAutoLaunch(gameId, null, null)
     }

     GameDetailUiState.Success(game)
    } else {
     // FIXED (2025-12-26): Use strings.xml for error message
     GameDetailUiState.Error(context.getString(com.steamdeck.mobile.R.string.error_game_not_found))
    }
   } catch (e: Exception) {
    // FIXED (2025-12-26): Use strings.xml for error message
    _uiState.value = GameDetailUiState.Error(
     e.message ?: context.getString(com.steamdeck.mobile.R.string.error_unknown)
    )
   }
  }
 }

 /**
  * Ensure Steam client is running in background (for download monitoring)
  * Only launches if Steam is installed and not already running
  */
 private suspend fun ensureSteamClientRunning(gameId: Long) {
  try {
   // Check if Steam is installed
   if (!steamSetupManager.isSteamInstalled()) {
    AppLogger.d(TAG, "Steam not installed, skipping auto-launch")
    return
   }

   // Check if Steam process is already running
   val isSteamRunning = steamLauncher.isSteamRunning()
   if (isSteamRunning) {
    AppLogger.d(TAG, "Steam already running, no need to launch")
    return
   }

   // Launch Steam client in background
   AppLogger.i(TAG, "Auto-launching Steam client for download monitoring")
   when (val result = openSteamClientUseCase(gameId)) {
    is DataResult.Success -> {
     AppLogger.i(TAG, "Steam client auto-launched successfully")
    }
    is DataResult.Error -> {
     // Non-fatal: Log warning but continue
     AppLogger.w(TAG, "Failed to auto-launch Steam client: ${result.error.message}")
    }
    is DataResult.Loading -> {}
   }
  } catch (e: Exception) {
   // Non-fatal: Log warning but continue
   AppLogger.w(TAG, "Exception while ensuring Steam client running", e)
  }
 }

 /**
  * Launch game with XServer integrated mode (FIXED: Flow-based monitoring prevents memory leaks)
  *
  * XServer Integrated Mode (2025-12-22):
  * - Displays only game windows without Windows desktop background
  * - Uses GLRenderer window filtering (forceFullscreenWMClass, unviewableWMClasses)
  * - Removes Wine virtual desktop mode for seamless Android-like UX
  *
  * @param gameId Game ID to launch
  * @param xServer XServer instance for graphics rendering
  * @param xServerView XServerView for window filtering configuration
  */
 fun launchGame(gameId: Long, xServer: XServer, xServerView: com.steamdeck.mobile.presentation.widget.XServerView) {
  viewModelScope.launch {
   AppLogger.i(TAG, ">>> ViewModel: Starting game launch for gameId=$gameId")

   // FIXED (2025-12-26): XServer reference NOT stored in ViewModel
   // XServer is owned by Screen's Composition and passed as parameter
   // This prevents GPU memory leaks during configuration changes

   // Configure XServer integrated mode (hide desktop, show only game window)
   val currentState = _uiState.value
   if (currentState is GameDetailUiState.Success) {
    val game = currentState.game
    val gameExecutableName = java.io.File(game.executablePath).nameWithoutExtension

    val renderer = xServerView.renderer
    renderer.setUnviewableWMClasses("explorer.exe", "progman", "shell_traywnd")
    renderer.setForceFullscreenWMClass(gameExecutableName)

    AppLogger.d(TAG, ">>> XServer integrated mode configured: hide desktop, fullscreen $gameExecutableName")
   }

   _launchState.value = LaunchState.Launching
   AppLogger.d(TAG, ">>> ViewModel: State changed to Launching")

   when (val result = launchGameUseCase(gameId)) {
    is DataResult.Success -> {
     val launchInfo = result.data
     _launchState.value = LaunchState.Running(launchInfo.processId)
     AppLogger.i(TAG, ">>> ViewModel: Launch SUCCESS, PID=${launchInfo.processId}")

     // FIXED (2025-12-26): Start controller routing in viewModelScope
     // Previous bug: LaunchGameUseCase started routing in new CoroutineScope(Dispatchers.IO)
     // This caused resource leak as the scope was never cancelled
     startControllerRouting()

     // FIXED (2025-12-26): Properly collect monitoringFlow to enable play time tracking
     // Without collect(), the Flow never executes and play time is NOT recorded
     // FIXED (2025-12-26): Cancel AND join to prevent race condition
     processMonitoringJob?.cancel()
     processMonitoringJob?.join() // Wait for cancellation to complete
     processMonitoringJob = viewModelScope.launch {
      try {
       launchInfo.monitoringFlow.collect {
        // Flow handles all play time tracking internally (5min checkpoints + final save)
        // This collect {} block MUST exist for the Flow to execute
       }
       // Flow completed normally (game process ended)
       AppLogger.i(TAG, ">>> Game process monitoring completed for PID=${launchInfo.processId}")
       _launchState.value = LaunchState.Idle
      } catch (e: Exception) {
       if (e is kotlinx.coroutines.CancellationException) {
        // ViewModel cancelled - expected during cleanup
        AppLogger.d(TAG, ">>> Game monitoring cancelled (ViewModel cleared)")
       } else {
        // Unexpected error
        AppLogger.e(TAG, ">>> Game monitoring error", e)
        _launchState.value = LaunchState.Error(
         e.message ?: context.getString(com.steamdeck.mobile.R.string.error_unknown)
        )
       }
      }
     }
    }
    is DataResult.Error -> {
     val errorMessage = result.error.toUserMessage(context)
     _launchState.value = LaunchState.Error(errorMessage)
     AppLogger.e(TAG, ">>> ViewModel: Launch FAILED - $errorMessage")

     // FIXED (2025-12-26): Stop controller routing on launch failure
     // No need to check if routing was started - stopRouting() is idempotent
     controllerInputRouter.stopRouting()
    }
    is DataResult.Loading -> {
     // Loading handled by Launching state
    }
   }
  }
 }

 /**
  * Cancel game launch (timeout or user cancellation)
  *
  * FIXED (2025-12-26): Use strings.xml for error messages
  */
 fun cancelLaunch() {
  AppLogger.w(TAG, ">>> Launch cancelled by user or timeout")
  _launchState.value = LaunchState.Error(
   context.getString(com.steamdeck.mobile.R.string.error_launch_timeout)
  )
 }

 /**
  * Stop currently running game
  *
  * FIXED (2025-12-26): Use strings.xml for error messages
  */
 fun stopGame() {
  viewModelScope.launch {
   AppLogger.i(TAG, ">>> Stopping game...")

   // Stop controller input routing
   controllerInputRouter.stopRouting()
   AppLogger.i(TAG, ">>> Controller routing stopped")

   val result = winlatorEngine.stopGame()
   if (result.isSuccess) {
    _launchState.value = LaunchState.Idle
    AppLogger.i(TAG, ">>> Game stopped successfully")
   } else {
    AppLogger.e(TAG, ">>> Failed to stop game: ${result.exceptionOrNull()?.message}")
    _launchState.value = LaunchState.Error(
     context.getString(com.steamdeck.mobile.R.string.error_unknown)
    )
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
    // FIXED (2025-12-25): Container ID is String type - no conversion needed
    val result = steamLauncher.launchSteam(
     containerId = game.winlatorContainerId ?: "default_shared_container",
     mode = com.steamdeck.mobile.core.steam.SteamLauncher.SteamLaunchMode.GAME_LAUNCH,
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
  * Open Steam Client (2025-12-25 REFACTORED)
  *
  * Delegates to OpenSteamClientUseCase for better separation of concerns.
  * This reduces ViewModel dependencies and improves testability.
  */
 fun openSteamClient(gameId: Long) {
  viewModelScope.launch {
   try {
    _steamLaunchState.value = SteamLaunchState.Launching

    when (val result = openSteamClientUseCase(gameId)) {
     is DataResult.Success -> {
      _steamLaunchState.value = SteamLaunchState.Running(0)
      AppLogger.i(TAG, "Steam Big Picture opened successfully")
     }
     is DataResult.Error -> {
      val errorMessage = result.error.toUserMessage(context)
      _steamLaunchState.value = SteamLaunchState.Error(errorMessage)
      AppLogger.e(TAG, "Failed to open Steam Big Picture: $errorMessage")
     }
     is DataResult.Loading -> {
      // Handled by Launching state
     }
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
  * Trigger game download via Steam client in Wine container
  *
  * @Deprecated("Use launchOrDownloadGame instead - it handles all scenarios automatically")
  *
  * This method is kept for API compatibility but delegates to launchOrDownloadGame.
  * launchOrDownloadGame provides:
  * - Automatic Steam installation if needed
  * - Better error handling
  * - Auto-launch after installation complete
  * - Unified state management
  */
 @Deprecated(
  message = "Use launchOrDownloadGame instead",
  replaceWith = ReplaceWith("launchOrDownloadGame(gameId, xServer, xServerView)")
 )
 fun triggerGameDownload(gameId: Long) {
  AppLogger.w(TAG, "triggerGameDownload is deprecated. Delegating to launchOrDownloadGame.")
  // Delegate to the unified method without XServer (download only, no auto-launch)
  launchOrDownloadGame(gameId, null, null)
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
  * Unified launch/download method - handles all scenarios
  *
  * NEW 2025: One-button game launch UX
  * Automatically:
  * 1. Installs Steam if needed (fully hidden from user)
  * 2. Launches Big Picture if game not installed
  * 3. Monitors download/install progress
  * 4. Auto-launches game when installation completes
  * 5. Launches game directly if already installed
  *
  * Technical constraint: Big Picture UI will be displayed for downloads
  * (Steam -applaunch does not guarantee auto-download for uninstalled games)
  */
 fun launchOrDownloadGame(
  gameId: Long,
  xServer: XServer?,
  xServerView: com.steamdeck.mobile.presentation.widget.XServerView?
 ) {
  viewModelScope.launch {
   try {
    _launchState.value = LaunchState.Launching
    AppLogger.i(TAG, "Starting unified launch/download for gameId=$gameId")

    when (val result = launchOrDownloadGameUseCase(
     gameId = gameId,
     steamInstallProgressCallback = { progress, message, detail ->
      // Update Steam installation progress in real-time (2025 Fix)
      _steamLaunchState.value = SteamLaunchState.InstallingSteam(progress)
      AppLogger.d(TAG, "Steam install progress: ${(progress * 100).toInt()}% - $message")
     }
    )) {
     is DataResult.Success -> {
      when (val data = result.data) {
       is LaunchOrDownloadResult.Launched -> {
        // Game launched directly (already installed)
        _launchState.value = LaunchState.Running(data.processId)
        AppLogger.i(TAG, "Game launched directly: processId=${data.processId}")

        // Start controller routing
        startControllerRouting()

        // Monitor game process
        monitorGameProcess(gameId, data.processId)
       }

       is LaunchOrDownloadResult.DownloadStarted -> {
        // Big Picture launched for download
        if (data.autoTrackingEnabled) {
         _steamLaunchState.value = SteamLaunchState.Downloading(0)
         AppLogger.i(TAG, "Big Picture launched for download: downloadId=${data.downloadId}")
        } else {
         // FileObserver monitoring failed - show manual tracking message
         _steamLaunchState.value = SteamLaunchState.DownloadStartedManualTracking
         AppLogger.w(TAG, "Big Picture launched, but automatic tracking unavailable: downloadId=${data.downloadId}")
        }

        // FIXED (2025-12-26): Keep Launching state during monitoring
        // This prevents premature UI reset and ensures monitoring survives screen navigation
        // State will be updated by observeInstallationProgressWithAutoLaunch when progress changes

        // Start observing installation progress (will auto-launch when complete)
        observeInstallationProgressWithAutoLaunch(gameId, xServer, xServerView)
       }

       is LaunchOrDownloadResult.InProgress -> {
        // Already downloading/installing
        _steamLaunchState.value = when (data.status) {
         InstallationStatus.DOWNLOADING ->
          SteamLaunchState.Downloading(data.progress)
         InstallationStatus.INSTALLING ->
          SteamLaunchState.Installing(data.progress)
         else -> SteamLaunchState.Idle
        }

        // FIXED (2025-12-26): Keep Launching state to maintain UI consistency
        // Will be updated by monitoring flow when status changes
        AppLogger.i(TAG, "Game already in progress: status=${data.status}, progress=${data.progress}%")

        // Start observing installation progress (will auto-launch when complete)
        observeInstallationProgressWithAutoLaunch(gameId, xServer, xServerView)
       }

       is LaunchOrDownloadResult.SteamInstalling -> {
        // Steam installation in progress (first-time setup)
        _steamLaunchState.value = SteamLaunchState.InstallingSteam(data.progress)
        // FIXED (2025-12-26): Keep Launching state during Steam installation
        AppLogger.i(TAG, "Steam installation in progress: ${(data.progress * 100).toInt()}%")
       }
      }
     }

     is DataResult.Error -> {
      val errorMessage = result.error.toUserMessage(context)
      _launchState.value = LaunchState.Error(errorMessage)
      _steamLaunchState.value = SteamLaunchState.Error(errorMessage)
      AppLogger.e(TAG, "Launch/download failed: $errorMessage")
     }

     is DataResult.Loading -> {
      // Handled by LaunchState.Launching
     }
    }

   } catch (e: Exception) {
    _launchState.value = LaunchState.Error(
     e.message ?: "An unexpected error occurred"
    )
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: "An unexpected error occurred"
    )
    AppLogger.e(TAG, "Exception during launch/download", e)
   }
  }
 }

 /**
  * Observe installation progress with automatic game launch when complete
  *
  * NEW 2025: Auto-launch enhancement
  * When download/installation completes, automatically launches the game
  *
  * FIXED (2025-12-26): Comprehensive bug fixes
  * - Job reference stored to survive screen navigation
  * - 2-hour timeout prevents infinite monitoring
  * - Null safety after delay (game may be deleted)
  * - Error handling for loadGame failures
  * - Uses strings.xml for all user-facing messages
  */
 private var installProgressMonitoringJob: Job? = null

 private fun observeInstallationProgressWithAutoLaunch(
  gameId: Long,
  xServer: XServer?,
  xServerView: com.steamdeck.mobile.presentation.widget.XServerView?
 ) {
  // FIXED (2025-12-26): Synchronized cancellation to prevent race condition
  // Key issue: Multiple calls can happen simultaneously (e.g., loadGame + launchOrDownloadGame)
  // Without synchronization, both create new jobs → duplicate monitoring
  // FIXED (2025-12-26): Use dedicated lock object instead of ViewModel instance
  synchronized(monitoringJobLock) {
   // Cancel previous job (if exists)
   val oldJob = installProgressMonitoringJob
   oldJob?.cancel()

   // Start new monitoring in ViewModel scope (survives screen navigation)
   installProgressMonitoringJob = viewModelScope.launch {
    // Wait for previous job to fully complete cancellation
    // This prevents two jobs from observing the same gameId Flow concurrently
    oldJob?.join()
   try {
    AppLogger.i(TAG, "Starting installation progress monitoring for gameId=$gameId")

    // FIXED (2025-12-26): Use withTimeout for proper Flow timeout enforcement
    // This ensures the entire Flow collection completes within 2 hours
    val twoHoursInMillis = 2 * 60 * 60 * 1000L // 2 hours

    withTimeout(twoHoursInMillis) {
     gameRepository.observeGame(gameId)
      .takeWhile { game ->
       // Stop observing when installation completes or fails
       val shouldContinue = game?.installationStatus !in listOf(
        InstallationStatus.INSTALLED,
        InstallationStatus.VALIDATION_FAILED
       )

       if (!shouldContinue) {
        AppLogger.i(TAG, "Monitoring complete: status=${game?.installationStatus}")
       }

       shouldContinue
      }
     .collect { game ->
      if (game == null) {
       AppLogger.w(TAG, "Game not found during progress monitoring")
       return@collect
      }

      AppLogger.d(TAG, "Installation status: ${game.installationStatus}, progress: ${game.installProgress}%")

      when (game.installationStatus) {
       InstallationStatus.DOWNLOADING -> {
        _steamLaunchState.value = SteamLaunchState.Downloading(game.installProgress)
        // FIXED (2025-12-26): Reset launch state to Idle when actively downloading
        // User can navigate away and monitoring continues in ViewModel scope
        _launchState.value = LaunchState.Idle
       }
       InstallationStatus.INSTALLING -> {
        _steamLaunchState.value = SteamLaunchState.Installing(game.installProgress)
        // FIXED (2025-12-26): Reset launch state to Idle when actively installing
        _launchState.value = LaunchState.Idle
       }
       InstallationStatus.INSTALLED -> {
        _steamLaunchState.value = SteamLaunchState.InstallComplete(game.name)
        AppLogger.i(TAG, "Installation complete: ${game.name}, auto-launching in 1 second...")

        // Brief delay for user to see completion message
        kotlinx.coroutines.delay(1000)

        // FIXED (2025-12-26): Reload game and verify it still exists (may be deleted during delay)
        try {
         val updatedGame = gameRepository.getGameById(gameId)
         if (updatedGame == null) {
          AppLogger.w(TAG, "Game was deleted during auto-launch delay, aborting auto-launch")
          _steamLaunchState.value = SteamLaunchState.Error(
           context.getString(com.steamdeck.mobile.R.string.error_game_not_found)
          )
          _launchState.value = LaunchState.Idle
          return@collect
         }

         // Update UI state with fresh game data
         _uiState.value = GameDetailUiState.Success(updatedGame)

         // Auto-launch game (FIXED 2025: Handle null XServer)
         AppLogger.i(TAG, "Auto-launching game: ${updatedGame.name}")
         if (xServer != null && xServerView != null) {
          launchGame(gameId, xServer, xServerView)
         } else {
          // XServer not initialized - skip auto-launch (user must manually launch)
          AppLogger.w(TAG, "XServer not available for auto-launch")
          _steamLaunchState.value = SteamLaunchState.InstallComplete(updatedGame.name)
          _launchState.value = LaunchState.Idle
         }
        } catch (e: Exception) {
         // FIXED (2025-12-26): Handle loadGame errors gracefully
         AppLogger.e(TAG, "Failed to reload game after installation", e)
         _steamLaunchState.value = SteamLaunchState.Error(
          e.message ?: context.getString(com.steamdeck.mobile.R.string.error_unknown)
         )
         _launchState.value = LaunchState.Idle
        }
       }
       InstallationStatus.VALIDATION_FAILED -> {
        // FIXED (2025-12-26): Use strings.xml for error messages
        _steamLaunchState.value = SteamLaunchState.ValidationFailed(
         listOf(context.getString(com.steamdeck.mobile.R.string.game_status_validation_failed))
        )
        // FIXED (2025-12-26): Reset launch state to Idle on validation failure
        _launchState.value = LaunchState.Idle
       }
       else -> {
        // Other statuses - no action needed
       }
      }
     }
    }

    AppLogger.i(TAG, "Finished observing installation progress for gameId=$gameId")

   } catch (e: TimeoutCancellationException) {
    // FIXED (2025-12-26): Handle timeout separately with specific error message
    AppLogger.w(TAG, "Monitoring timeout after 2 hours for gameId=$gameId")
    _steamLaunchState.value = SteamLaunchState.Error("Installation monitoring timeout (2 hours)")
    _launchState.value = LaunchState.Idle
   } catch (e: Exception) {
    AppLogger.e(TAG, "Error observing installation progress", e)
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: context.getString(com.steamdeck.mobile.R.string.error_unknown)
    )
    _launchState.value = LaunchState.Idle
   }
   } // End of viewModelScope.launch
  } // End of synchronized block
 }

 /**
  * DEPRECATED (2025-12-26): Use observeInstallationProgressWithAutoLaunch instead
  *
  * This method is a duplicate of observeInstallationProgressWithAutoLaunch without auto-launch.
  * Kept for reference but should not be called.
  *
  * @see observeInstallationProgressWithAutoLaunch
  */
 @Deprecated(
  message = "Use observeInstallationProgressWithAutoLaunch instead",
  replaceWith = ReplaceWith("observeInstallationProgressWithAutoLaunch(gameId, xServer, xServerView)")
 )
 private fun observeInstallationProgress(gameId: Long) {
  AppLogger.w(TAG, "DEPRECATED: observeInstallationProgress called. Use observeInstallationProgressWithAutoLaunch instead.")
  // No-op: This method should not be used
 }

 /**
  * Start controller input routing (FIXED: Restored missing method with scope parameter)
  */
 private fun startControllerRouting() {
  try {
   controllerInputRouter.startRouting(viewModelScope)
   AppLogger.d(TAG, "Controller input routing started")
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to start controller routing", e)
  }
 }

 /**
  * Monitor game process and handle termination (FIXED: Simplified implementation)
  *
  * Note: Process monitoring is handled by LaunchGameUseCase's monitoringFlow.
  * This method is kept for backward compatibility but doesn't actively monitor.
  */
 private fun monitorGameProcess(gameId: Long, processId: Int) {
  // Process monitoring is now handled by LaunchGameUseCase
  // The monitoringFlow in LaunchInfo handles play time tracking
  AppLogger.d(TAG, "Game process monitoring delegated to LaunchGameUseCase (processId=$processId)")
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

 /** Installing Steam client (first-time setup) */
 @Immutable
 data class InstallingSteam(val progress: Float) : SteamLaunchState()

 /** Downloading game files */
 @Immutable
 data class Downloading(val progress: Int) : SteamLaunchState()

 /** Download started but automatic tracking unavailable (manual check required) */
 @Immutable
 data object DownloadStartedManualTracking : SteamLaunchState()

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
