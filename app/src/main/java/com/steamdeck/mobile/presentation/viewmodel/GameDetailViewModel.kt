package com.steamdeck.mobile.presentation.viewmodel

import android.content.Context
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
 * ゲーム詳細画面のViewModel
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
    private val steamSetupManager: SteamSetupManager
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

    init {
        checkSteamInstallation()
    }

    /**
     * ゲーム詳細を読み込み
     */
    fun loadGame(gameId: Long) {
        viewModelScope.launch {
            try {
                val game = getGameByIdUseCase(gameId)
                _uiState.value = if (game != null) {
                    GameDetailUiState.Success(game)
                } else {
                    GameDetailUiState.Error("ゲームが見つかりません")
                }
            } catch (e: Exception) {
                _uiState.value = GameDetailUiState.Error(e.message ?: "不明なエラー")
            }
        }
    }

    /**
     * ゲームを起動
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
     * お気に入り状態を切り替え
     */
    fun toggleFavorite(gameId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            when (val result = toggleFavoriteUseCase(gameId, isFavorite)) {
                is DataResult.Success -> {
                    // UI状態を更新
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
     * ゲームを削除
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
     * Steamインストール状態をチェック
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
     * Steam Client経由でゲームを起動
     */
    fun launchGameViaSteam(gameId: Long) {
        viewModelScope.launch {
            try {
                _steamLaunchState.value = SteamLaunchState.Launching

                // 現在のゲーム情報を取得
                val currentState = _uiState.value
                if (currentState !is GameDetailUiState.Success) {
                    _steamLaunchState.value = SteamLaunchState.Error("ゲーム情報の取得に失敗しました")
                    return@launch
                }

                val game = currentState.game

                // コンテナIDとSteam App IDをチェック
                if (game.winlatorContainerId == null) {
                    _steamLaunchState.value = SteamLaunchState.Error(
                        "コンテナが設定されていません。設定画面でコンテナを作成してください。"
                    )
                    return@launch
                }

                if (game.steamAppId == null) {
                    _steamLaunchState.value = SteamLaunchState.Error(
                        "Steam App IDが設定されていません"
                    )
                    return@launch
                }

                // Steam Client経由で起動
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
                            error.message ?: "Steam経由での起動に失敗しました"
                        )
                        AppLogger.e(TAG, "Failed to launch game via Steam", error)
                    }

            } catch (e: Exception) {
                _steamLaunchState.value = SteamLaunchState.Error(
                    e.message ?: "Steam起動中に予期しないエラーが発生しました"
                )
                AppLogger.e(TAG, "Exception during Steam launch", e)
            }
        }
    }

    /**
     * Steam Clientを開く
     */
    fun openSteamClient(gameId: Long) {
        viewModelScope.launch {
            try {
                _steamLaunchState.value = SteamLaunchState.Launching

                // 現在のゲーム情報を取得
                val currentState = _uiState.value
                if (currentState !is GameDetailUiState.Success) {
                    _steamLaunchState.value = SteamLaunchState.Error("ゲーム情報の取得に失敗しました")
                    return@launch
                }

                val game = currentState.game

                // コンテナIDをチェック
                if (game.winlatorContainerId == null) {
                    _steamLaunchState.value = SteamLaunchState.Error(
                        "コンテナが設定されていません。設定画面でコンテナを作成してください。"
                    )
                    return@launch
                }

                // Steam Clientを起動
                val result = steamLauncher.launchSteamClient(game.winlatorContainerId.toString())

                result
                    .onSuccess {
                        _steamLaunchState.value = SteamLaunchState.Running(0)
                        AppLogger.i(TAG, "Steam Client opened successfully")
                    }
                    .onFailure { error ->
                        _steamLaunchState.value = SteamLaunchState.Error(
                            error.message ?: "Steam Clientの起動に失敗しました"
                        )
                        AppLogger.e(TAG, "Failed to open Steam Client", error)
                    }

            } catch (e: Exception) {
                _steamLaunchState.value = SteamLaunchState.Error(
                    e.message ?: "Steam Client起動中に予期しないエラーが発生しました"
                )
                AppLogger.e(TAG, "Exception while opening Steam Client", e)
            }
        }
    }

    /**
     * Steam起動状態をリセット
     */
    fun resetSteamLaunchState() {
        _steamLaunchState.value = SteamLaunchState.Idle
    }

}

/**
 * ゲーム詳細画面のUI状態
 */
sealed class GameDetailUiState {
    /** 読み込み中 */
    data object Loading : GameDetailUiState()

    /** 成功 */
    data class Success(val game: Game) : GameDetailUiState()

    /** 削除完了 */
    data object Deleted : GameDetailUiState()

    /** エラー */
    data class Error(val message: String) : GameDetailUiState()
}

/**
 * ゲーム起動状態
 */
sealed class LaunchState {
    /** アイドル状態 */
    object Idle : LaunchState()

    /** 起動中 */
    object Launching : LaunchState()

    /** 実行中 */
    data class Running(val processId: Int) : LaunchState()

    /** エラー */
    data class Error(val message: String) : LaunchState()
}

/**
 * Steam Client起動状態
 */
sealed class SteamLaunchState {
    /** アイドル状態 */
    object Idle : SteamLaunchState()

    /** インストール状態確認中 */
    object CheckingInstallation : SteamLaunchState()

    /** 起動中 */
    object Launching : SteamLaunchState()

    /** 実行中 */
    data class Running(val processId: Int) : SteamLaunchState()

    /** エラー */
    data class Error(val message: String) : SteamLaunchState()

    /** 未インストール */
    data class NotInstalled(val message: String) : SteamLaunchState()
}
