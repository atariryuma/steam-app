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

    /**
     * 手動でゲームを追加
     *
     * ベストプラクティス:
     * - 入力値のバリデーション
     * - 重複チェック
     * - insertGameの戻り値を検証
     * - 適切なエラーハンドリング
     */
    fun addGame(name: String, executablePath: String, installPath: String) {
        viewModelScope.launch {
            try {
                // 1. 入力値のバリデーション
                if (name.isBlank()) {
                    _uiState.value = HomeUiState.Error("ゲーム名を入力してください")
                    return@launch
                }
                if (executablePath.isBlank()) {
                    _uiState.value = HomeUiState.Error("実行ファイルを選択してください")
                    return@launch
                }
                if (installPath.isBlank()) {
                    _uiState.value = HomeUiState.Error("インストールフォルダを選択してください")
                    return@launch
                }

                // 2. URIの検証（content://スキームまたは絶対パスを確認）
                // Storage Access Framework経由で取得したURIを推奨
                val isValidExecutablePath = executablePath.startsWith("content://") ||
                                           executablePath.startsWith("/")
                val isValidInstallPath = installPath.startsWith("content://") ||
                                        installPath.startsWith("/")

                if (!isValidExecutablePath) {
                    _uiState.value = HomeUiState.Error("実行ファイルのパスが無効です")
                    return@launch
                }
                if (!isValidInstallPath) {
                    _uiState.value = HomeUiState.Error("インストールフォルダのパスが無効です")
                    return@launch
                }

                // Android 10 (API 29) 以降では、content:// URIを推奨（Scoped Storage）
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (!executablePath.startsWith("content://")) {
                        android.util.Log.w("HomeViewModel",
                            "Executable path should use content:// URI on Android 10+: $executablePath")
                    }
                    if (!installPath.startsWith("content://")) {
                        android.util.Log.w("HomeViewModel",
                            "Install path should use content:// URI on Android 10+: $installPath")
                    }
                }

                // 3. 重複チェック（同じ名前 or 同じ実行パス）
                val currentGames = when (val state = _uiState.value) {
                    is HomeUiState.Success -> state.games
                    else -> emptyList()
                }

                val isDuplicateName = currentGames.any { it.name.equals(name, ignoreCase = true) }
                val isDuplicatePath = currentGames.any { it.executablePath == executablePath }

                if (isDuplicateName) {
                    _uiState.value = HomeUiState.Error("同じ名前のゲームが既に存在します: $name")
                    return@launch
                }
                if (isDuplicatePath) {
                    _uiState.value = HomeUiState.Error("同じ実行ファイルが既に登録されています")
                    return@launch
                }

                // 4. ゲームを作成
                val game = Game(
                    name = name,
                    executablePath = executablePath,
                    installPath = installPath,
                    source = com.steamdeck.mobile.domain.model.GameSource.IMPORTED
                )

                // 5. データベースに挿入し、IDを取得
                val insertedId = gameRepository.insertGame(game)

                // 6. 挿入結果を検証
                if (insertedId <= 0) {
                    _uiState.value = HomeUiState.Error("ゲームの追加に失敗しました（無効なID: $insertedId）")
                    return@launch
                }

                android.util.Log.d("HomeViewModel", "Game added successfully with ID: $insertedId")

                // 7. 追加後、リストを更新
                // Note: Flowで自動更新されるが、明示的にrefresh()を呼ぶことで即座に反映
                refresh()

            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // データベース制約違反（UNIQUE制約等）
                _uiState.value = HomeUiState.Error("ゲームが既に存在します")
                android.util.Log.e("HomeViewModel", "SQLite constraint error", e)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("ゲームの追加に失敗しました: ${e.message}")
                android.util.Log.e("HomeViewModel", "Error adding game", e)
            }
        }
    }
}

/**
 * ホーム画面のUI状態
 */
sealed class HomeUiState {
    /** 読み込み中 */
    data object Loading : HomeUiState()

    /** 成功 */
    data class Success(val games: List<Game>) : HomeUiState()

    /** 空 */
    data object Empty : HomeUiState()

    /** エラー */
    data class Error(val message: String) : HomeUiState()
}
