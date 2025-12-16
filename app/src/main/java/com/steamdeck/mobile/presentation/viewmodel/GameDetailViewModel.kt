package com.steamdeck.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ゲーム詳細画面のViewModel
 */
@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GameDetailUiState>(GameDetailUiState.Loading)
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    /**
     * ゲーム詳細を読み込み
     */
    fun loadGame(gameId: Long) {
        viewModelScope.launch {
            try {
                val game = gameRepository.getGameById(gameId)
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
            try {
                // TODO: Winlator統合でゲームを起動
                // 起動開始時刻を記録
                val startTime = System.currentTimeMillis()

                // ここでWinlatorEngineを呼び出してゲームを起動
                // 一旦プレースホルダーとして、5分プレイしたことにする
                gameRepository.updatePlayTime(gameId, 5, startTime)
            } catch (e: Exception) {
                // エラーハンドリング
            }
        }
    }

    /**
     * お気に入り状態を切り替え
     */
    fun toggleFavorite(gameId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                gameRepository.updateFavoriteStatus(gameId, isFavorite)
                // UI状態を更新
                val currentState = _uiState.value
                if (currentState is GameDetailUiState.Success) {
                    _uiState.value = GameDetailUiState.Success(
                        currentState.game.copy(isFavorite = isFavorite)
                    )
                }
            } catch (e: Exception) {
                // エラーハンドリング
            }
        }
    }

    /**
     * ゲームを削除
     */
    fun deleteGame(game: Game) {
        viewModelScope.launch {
            try {
                gameRepository.deleteGame(game)
                _uiState.value = GameDetailUiState.Deleted
            } catch (e: Exception) {
                _uiState.value = GameDetailUiState.Error(e.message ?: "削除エラー")
            }
        }
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
