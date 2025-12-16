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
 * ホーム画面のViewModel
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadGames()
    }

    /**
     * ゲーム一覧を読み込み
     */
    private fun loadGames() {
        viewModelScope.launch {
            try {
                gameRepository.getAllGames().collect { games ->
                    _uiState.value = if (games.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(games)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "不明なエラー")
            }
        }
    }

    /**
     * ゲームを検索
     */
    fun searchGames(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            loadGames()
            return
        }

        viewModelScope.launch {
            try {
                gameRepository.searchGames(query).collect { games ->
                    _uiState.value = if (games.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(games)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "検索エラー")
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
            } catch (e: Exception) {
                // エラーハンドリング
            }
        }
    }

    /**
     * ゲーム一覧をリフレッシュ
     */
    fun refresh() {
        _uiState.value = HomeUiState.Loading
        loadGames()
    }
}

/**
 * ホーム画面のUI状態
 */
sealed class HomeUiState {
    /** 読み込み中 */
    object Loading : HomeUiState()

    /** 成功 */
    data class Success(val games: List<Game>) : HomeUiState()

    /** 空 */
    object Empty : HomeUiState()

    /** エラー */
    data class Error(val message: String) : HomeUiState()
}
