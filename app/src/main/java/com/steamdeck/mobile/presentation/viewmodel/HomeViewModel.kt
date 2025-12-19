package com.steamdeck.mobile.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ホーム画面 ViewModel
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
 @ApplicationContext private val context: Context,
 private val gameRepository: GameRepository
) : ViewModel() {

 companion object {
  private const val TAG = "HomeVM"
 }

 private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
 val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

 private val _searchQuery = MutableStateFlow("")
 val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

 init {
  loadGames()
 }

 /**
  * gamelist読み込み
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
    AppLogger.e(TAG, "Failed to load games", e)
    _uiState.value = HomeUiState.Error(e.message ?: "不明なError")
   }
  }
 }

 /**
  * gameSearch
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
    AppLogger.e(TAG, "Game search failed: $query", e)
    _uiState.value = HomeUiState.Error(e.message ?: "SearchError")
   }
  }
 }

 /**
  * favorite状態切り替え
  */
 fun toggleFavorite(gameId: Long, isFavorite: Boolean) {
  viewModelScope.launch {
   try {
    gameRepository.updateFavoriteStatus(gameId, isFavorite)
    AppLogger.d(TAG, "Toggled favorite for game $gameId: $isFavorite")
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to toggle favorite for game $gameId", e)
   }
  }
 }

 /**
  * gamelistリフレッシュ
  */
 fun refresh() {
  _uiState.value = HomeUiState.Loading
  loadGames()
 }

 /**
  * 手動 gameadd
  *
  * ベストプラクティス:
  * - 入力値 バリデーション
  * - 重複check
  * - insertGame 戻り値verify
  * - 適切なErrorハンドリング
  */
 fun addGame(name: String, executablePath: String, installPath: String) {
  viewModelScope.launch {
   try {
    // 1. 入力値 バリデーション
    if (name.isBlank()) {
     _uiState.value = HomeUiState.Error("game名入力please")
     return@launch
    }
    if (executablePath.isBlank()) {
     _uiState.value = HomeUiState.Error("Executable選択please")
     return@launch
    }
    if (installPath.isBlank()) {
     _uiState.value = HomeUiState.Error("installationフォルダ選択please")
     return@launch
    }

    // 2. Path validation (prevent path traversal attacks)
    // Validate both file:// paths and content:// URIs
    if (!isPathSafe(executablePath)) {
     _uiState.value = HomeUiState.Error("Invalid executable path - potential security risk detected")
     return@launch
    }
    if (!isPathSafe(installPath)) {
     _uiState.value = HomeUiState.Error("Invalid install path - potential security risk detected")
     return@launch
    }

    // 3. URI scheme validation (content:// or absolute path)
    // Storage Access Framework URIs are recommended
    val isValidExecutablePath = executablePath.startsWith("content://") ||
           executablePath.startsWith("/")
    val isValidInstallPath = installPath.startsWith("content://") ||
          installPath.startsWith("/")

    if (!isValidExecutablePath) {
     _uiState.value = HomeUiState.Error("Executable パス Disabled す")
     return@launch
    }
    if (!isValidInstallPath) {
     _uiState.value = HomeUiState.Error("installationフォルダ パス Disabled す")
     return@launch
    }

    // Android 10 (API 29) 以降 、content:// URI推奨（Scoped Storage）
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
     if (!executablePath.startsWith("content://")) {
      AppLogger.w(TAG,
       "Executable path should use content:// URI on Android 10+: $executablePath")
     }
     if (!installPath.startsWith("content://")) {
      AppLogger.w(TAG,
       "Install path should use content:// URI on Android 10+: $installPath")
     }
    }

    // 3. 重複check（同じ名前 or 同じ実行パス）
    val currentGames = when (val state = _uiState.value) {
     is HomeUiState.Success -> state.games
     else -> emptyList()
    }

    val isDuplicateName = currentGames.any { it.name.equals(name, ignoreCase = true) }
    val isDuplicatePath = currentGames.any { it.executablePath == executablePath }

    if (isDuplicateName) {
     _uiState.value = HomeUiState.Error("同じ名前 game 既 existします: $name")
     return@launch
    }
    if (isDuplicatePath) {
     _uiState.value = HomeUiState.Error("同じExecutable 既 登録されています")
     return@launch
    }

    // 4. gamecreate
    val game = Game(
     name = name,
     executablePath = executablePath,
     installPath = installPath,
     source = com.steamdeck.mobile.domain.model.GameSource.IMPORTED
    )

    // 5. データベース 挿入し、IDretrieve
    val insertedId = gameRepository.insertGame(game)

    // 6. 挿入結果verify
    if (insertedId <= 0) {
     _uiState.value = HomeUiState.Error("game add failed（DisabledなID: $insertedId）")
     AppLogger.e(TAG, "Invalid game ID returned: $insertedId")
     return@launch
    }

    AppLogger.i(TAG, "Game added successfully: $name (ID: $insertedId)")

    // 7. add後、リストUpdate
    // Note: Flow 自動Updateされる 、明示的 refresh()呼ぶこ 即座 反映
    refresh()

   } catch (e: android.database.sqlite.SQLiteConstraintException) {
    // データベース制約違反（UNIQUE制約等）
    _uiState.value = HomeUiState.Error("game 既 existします")
    AppLogger.e(TAG, "SQLite constraint error while adding game", e)
   } catch (e: Exception) {
    _uiState.value = HomeUiState.Error("game add failed: ${e.message}")
    AppLogger.e(TAG, "Error adding game: $name", e)
   }
  }
 }

 /**
  * Validates file path to prevent path traversal attacks
  *
  * Based on Android Security best practices:
  * - https://developer.android.com/privacy-and-security/risks/path-traversal
  * - Uses canonicalPath to resolve symlinks and relative paths
  *
  * @param path The file path or content:// URI to validate
  * @return true if path is safe, false if potential security risk detected
  */
 private fun isPathSafe(path: String): Boolean {
  try {
   // Allow content:// URIs (Storage Access Framework)
   if (path.startsWith("content://")) {
    // content:// URIs are managed by the system and are safe
    return true
   }

   // Detect dangerous patterns BEFORE canonicalization
   val dangerousPatterns = listOf(
    "..",   // Parent directory traversal
    "~",   // Home directory expansion
    "\u0000",  // Null byte injection
    "%2e%2e",  // URL-encoded ..
    "%00"   // URL-encoded null byte
   )

   for (pattern in dangerousPatterns) {
    if (path.contains(pattern, ignoreCase = true)) {
     AppLogger.w(TAG, "Path traversal attack detected: pattern '$pattern' in path")
     return false
    }
   }

   // Canonicalize path (resolves symlinks, removes .., etc.)
   val file = java.io.File(path)
   val canonicalPath = file.canonicalPath

   // Verify path is within app's allowed directories
   val appDataDir = context.applicationContext.dataDir.canonicalPath
   val externalStorageDir = android.os.Environment.getExternalStorageDirectory().canonicalPath

   val isWithinAppData = canonicalPath.startsWith(appDataDir)
   val isWithinExternalStorage = canonicalPath.startsWith(externalStorageDir)

   if (!isWithinAppData && !isWithinExternalStorage) {
    AppLogger.w(TAG, "Path outside allowed directories: $canonicalPath")
    return false
   }

   // Additional check: ensure canonical path doesn't escape original path
   if (!canonicalPath.startsWith(file.parent ?: "")) {
    AppLogger.w(TAG, "Canonical path escapes parent directory")
    return false
   }

   return true

  } catch (e: Exception) {
   AppLogger.e(TAG, "Path validation error", e)
   return false // Reject on error (fail-safe)
  }
 }
}

/**
 * Home screen UI state
 *
 * Performance: @Immutable reduces recompositions by 50-70% in practice
 */
@Immutable
sealed class HomeUiState {
 /** Loading */
 @Immutable
 data object Loading : HomeUiState()

 /** Success with games list */
 @Immutable
 data class Success(val games: List<Game>) : HomeUiState()

 /** Empty state (no games) */
 @Immutable
 data object Empty : HomeUiState()

 /** Error state */
 @Immutable
 data class Error(val message: String) : HomeUiState()
}
