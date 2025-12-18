package com.steamdeck.mobile.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.download.SteamDownloadManager
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.steam.SteamSetupManager
import com.steamdeck.mobile.data.remote.steam.model.SteamDownloadProgress
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.usecase.DeleteGameUseCase
import com.steamdeck.mobile.domain.usecase.GetGameByIdUseCase
import com.steamdeck.mobile.domain.usecase.LaunchGameUseCase
import com.steamdeck.mobile.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ゲーム詳細画面のViewModel
 *
 * ベストプラクティス適用:
 * - 構造化された並行性（structured concurrency）
 * - 適切なキャンセル処理
 * - onCleared()でリソースクリーンアップ
 */
@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val getGameByIdUseCase: GetGameByIdUseCase,
    private val launchGameUseCase: LaunchGameUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val deleteGameUseCase: DeleteGameUseCase,
    private val steamDownloadManager: SteamDownloadManager,
    private val steamLauncher: SteamLauncher,
    private val steamSetupManager: SteamSetupManager
) : ViewModel() {

    companion object {
        private const val TAG = "GameDetailViewModel"
    }

    private val _uiState = MutableStateFlow<GameDetailUiState>(GameDetailUiState.Loading)
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private val _launchState = MutableStateFlow<LaunchState>(LaunchState.Idle)
    val launchState: StateFlow<LaunchState> = _launchState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<SteamDownloadProgress?>(null)
    val downloadProgress: StateFlow<SteamDownloadProgress?> = _downloadProgress.asStateFlow()

    private val _steamLaunchState = MutableStateFlow<SteamLaunchState>(SteamLaunchState.Idle)
    val steamLaunchState: StateFlow<SteamLaunchState> = _steamLaunchState.asStateFlow()

    private val _isSteamInstalled = MutableStateFlow(false)
    val isSteamInstalled: StateFlow<Boolean> = _isSteamInstalled.asStateFlow()

    // ダウンロードジョブを追跡（キャンセル用）
    private var downloadJob: Job? = null

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
            launchGameUseCase(gameId)
                .onSuccess { processId ->
                    _launchState.value = LaunchState.Running(processId)
                }
                .onFailure { error ->
                    _launchState.value = LaunchState.Error(error.message ?: "起動エラー")
                }
        }
    }

    /**
     * お気に入り状態を切り替え
     */
    fun toggleFavorite(gameId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            toggleFavoriteUseCase(gameId, isFavorite)
                .onSuccess {
                    // UI状態を更新
                    val currentState = _uiState.value
                    if (currentState is GameDetailUiState.Success) {
                        _uiState.value = GameDetailUiState.Success(
                            currentState.game.copy(isFavorite = isFavorite)
                        )
                    }
                }
                .onFailure { error ->
                    // エラーハンドリング（必要に応じてSnackbar等で表示）
                }
        }
    }

    /**
     * ゲームを削除
     */
    fun deleteGame(game: Game) {
        viewModelScope.launch {
            deleteGameUseCase(game)
                .onSuccess {
                    _uiState.value = GameDetailUiState.Deleted
                }
                .onFailure { error ->
                    _uiState.value = GameDetailUiState.Error(error.message ?: "削除エラー")
                }
        }
    }

    /**
     * Steamからゲームをダウンロード
     *
     * ベストプラクティス:
     * - coroutineScope を使用して構造化された並行性を実現
     * - 進捗監視とダウンロードを適切に協調させる
     * - CancellationExceptionは再スローしてキャンセル処理を適切に行う
     */
    fun startDownload(appId: Long) {
        // 既存のダウンロードをキャンセル
        downloadJob?.cancel()

        downloadJob = viewModelScope.launch {
            try {
                // 現在の状態からゲーム情報を取得
                val currentState = _uiState.value
                val installPath = if (currentState is GameDetailUiState.Success) {
                    currentState.game.installPath
                } else {
                    // デフォルトパス
                    "/sdcard/SteamDeck/games/$appId"
                }

                // coroutineScope を使用して構造化
                // これにより、内部のコルーチンが全て完了するまで待機
                coroutineScope {
                    // 進捗監視（バックグラウンド）
                    launch {
                        steamDownloadManager.observeDownloadProgress(appId).collect { progress ->
                            _downloadProgress.value = progress
                        }
                    }

                    // ダウンロード実行（メイン処理）
                    launch {
                        val result = steamDownloadManager.downloadSteamGame(appId, installPath)

                        result.onFailure { error ->
                            _downloadProgress.value = SteamDownloadProgress.Error(
                                "ダウンロードに失敗しました: ${error.message}",
                                error
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                // キャンセルは正常な終了なので再スロー
                throw e
            } catch (e: Exception) {
                _downloadProgress.value = SteamDownloadProgress.Error(
                    "ダウンロードの開始に失敗しました: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * ダウンロードをキャンセル
     */
    fun cancelDownload(appId: Long) {
        // ダウンロードジョブをキャンセル
        downloadJob?.cancel()
        downloadJob = null

        viewModelScope.launch {
            steamDownloadManager.cancelDownload(appId)
            _downloadProgress.value = null
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
                Log.d(TAG, "Steam installation status: $isInstalled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check Steam installation", e)
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
                    containerId = game.winlatorContainerId,
                    appId = game.steamAppId
                )

                result
                    .onSuccess {
                        _steamLaunchState.value = SteamLaunchState.Running(game.steamAppId.toInt())
                        Log.i(TAG, "Game launched via Steam: appId=${game.steamAppId}")
                    }
                    .onFailure { error ->
                        _steamLaunchState.value = SteamLaunchState.Error(
                            error.message ?: "Steam経由での起動に失敗しました"
                        )
                        Log.e(TAG, "Failed to launch game via Steam", error)
                    }

            } catch (e: Exception) {
                _steamLaunchState.value = SteamLaunchState.Error(
                    e.message ?: "Steam起動中に予期しないエラーが発生しました"
                )
                Log.e(TAG, "Exception during Steam launch", e)
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
                val result = steamLauncher.launchSteamClient(game.winlatorContainerId)

                result
                    .onSuccess {
                        _steamLaunchState.value = SteamLaunchState.Running(0)
                        Log.i(TAG, "Steam Client opened successfully")
                    }
                    .onFailure { error ->
                        _steamLaunchState.value = SteamLaunchState.Error(
                            error.message ?: "Steam Clientの起動に失敗しました"
                        )
                        Log.e(TAG, "Failed to open Steam Client", error)
                    }

            } catch (e: Exception) {
                _steamLaunchState.value = SteamLaunchState.Error(
                    e.message ?: "Steam Client起動中に予期しないエラーが発生しました"
                )
                Log.e(TAG, "Exception while opening Steam Client", e)
            }
        }
    }

    /**
     * Steam起動状態をリセット
     */
    fun resetSteamLaunchState() {
        _steamLaunchState.value = SteamLaunchState.Idle
    }

    /**
     * ViewModelがクリアされる時の処理
     *
     * ベストプラクティス: リソースをクリーンアップ
     */
    override fun onCleared() {
        super.onCleared()
        // 実行中のダウンロードをキャンセル
        downloadJob?.cancel()
    }
}

/**
 * ゲーム詳細画面のUI状態
 */
sealed class GameDetailUiState {
    /** 読み込み中 */
    object Loading : GameDetailUiState()

    /** 成功 */
    data class Success(val game: Game) : GameDetailUiState()

    /** 削除完了 */
    object Deleted : GameDetailUiState()

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
