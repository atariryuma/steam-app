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
import com.steamdeck.mobile.domain.usecase.DeleteGameUseCase
import com.steamdeck.mobile.domain.usecase.GetGameByIdUseCase
import com.steamdeck.mobile.domain.usecase.LaunchGameUseCase
import com.steamdeck.mobile.domain.usecase.ToggleFavoriteUseCase
import com.steamdeck.mobile.presentation.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * game詳細画面 ViewModel
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
 private val scanInstalledGamesUseCase: com.steamdeck.mobile.domain.usecase.ScanInstalledGamesUseCase
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
  * game詳細読み込み
  */
 fun loadGame(gameId: Long) {
  viewModelScope.launch {
   try {
    val game = getGameByIdUseCase(gameId)
    _uiState.value = if (game != null) {
     GameDetailUiState.Success(game)
    } else {
     GameDetailUiState.Error("game not found")
    }
   } catch (e: Exception) {
    _uiState.value = GameDetailUiState.Error(e.message ?: "不明なError")
   }
  }
 }

 /**
  * gamelaunch
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
  * favorite状態切り替え
  */
 fun toggleFavorite(gameId: Long, isFavorite: Boolean) {
  viewModelScope.launch {
   when (val result = toggleFavoriteUseCase(gameId, isFavorite)) {
    is DataResult.Success -> {
     // UI状態Update
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
  * gamedelete
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
  * Steaminstallation状態check
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
  * Steam Clientvia gamelaunch
  */
 fun launchGameViaSteam(gameId: Long) {
  viewModelScope.launch {
   try {
    _steamLaunchState.value = SteamLaunchState.Launching

    // 現在 game情報retrieve
    val currentState = _uiState.value
    if (currentState !is GameDetailUiState.Success) {
     _steamLaunchState.value = SteamLaunchState.Error("game情報 retrieve failed")
     return@launch
    }

    val game = currentState.game

    // ContainerID Steam App IDcheck
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

    // Steam Clientvia launch
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
       error.message ?: "Steamvia launch failed"
      )
      AppLogger.e(TAG, "Failed to launch game via Steam", error)
     }

   } catch (e: Exception) {
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: "Steamlaunchin 予期しないError 発生しました"
    )
    AppLogger.e(TAG, "Exception during Steam launch", e)
   }
  }
 }

 /**
  * Steam Clientopen
  */
 fun openSteamClient(gameId: Long) {
  viewModelScope.launch {
   try {
    _steamLaunchState.value = SteamLaunchState.Launching

    // 現在 game情報retrieve
    val currentState = _uiState.value
    if (currentState !is GameDetailUiState.Success) {
     _steamLaunchState.value = SteamLaunchState.Error("game情報 retrieve failed")
     return@launch
    }

    val game = currentState.game

    // ContainerIDcheck
    if (game.winlatorContainerId == null) {
     _steamLaunchState.value = SteamLaunchState.Error(
      "Winlator container is not configured. Please create a container in Settings."
     )
     return@launch
    }

    // Steam Clientlaunch
    val result = steamLauncher.launchSteamClient(game.winlatorContainerId.toString())

    result
     .onSuccess {
      _steamLaunchState.value = SteamLaunchState.Running(0)
      AppLogger.i(TAG, "Steam Client opened successfully")
     }
     .onFailure { error ->
      _steamLaunchState.value = SteamLaunchState.Error(
       error.message ?: "Steam Client launch failed"
      )
      AppLogger.e(TAG, "Failed to open Steam Client", error)
     }

   } catch (e: Exception) {
    _steamLaunchState.value = SteamLaunchState.Error(
     e.message ?: "Steam Clientlaunchin 予期しないError 発生しました"
    )
    AppLogger.e(TAG, "Exception while opening Steam Client", e)
   }
  }
 }

 /**
  * Steamlaunch状態リセット
  */
 fun resetSteamLaunchState() {
  _steamLaunchState.value = SteamLaunchState.Idle
 }

 /**
  * Steam Deep Link でゲームのインストール画面を開く
  *
  * Android版Steamアプリの steam://install/<appId> プロトコルを使用して、
  * 公式Steamアプリでゲームをダウンロード/インストールできるようにする
  */
 fun openSteamInstallPage(gameId: Long) {
  viewModelScope.launch {
   try {
    // 現在のゲーム情報を取得
    val currentState = _uiState.value
    if (currentState !is GameDetailUiState.Success) {
     AppLogger.e(TAG, "Cannot open Steam install page: game info not loaded")
     return@launch
    }

    val game = currentState.game

    // Steam App IDチェック
    if (game.steamAppId == null) {
     _steamLaunchState.value = SteamLaunchState.Error(
      "This game has no Steam App ID"
     )
     return@launch
    }

    // Steam Deep Link で開く
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
  * インストール済みゲームをスキャンして実行ファイルパスを更新
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
       // ゲーム情報を再読み込み
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
 */
@Immutable
sealed class SteamLaunchState {
 /** Idle state */
 @Immutable
 data object Idle : SteamLaunchState()

 /** Checking installation */
 @Immutable
 data object CheckingInstallation : SteamLaunchState()

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
