package com.steamdeck.mobile.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.security.PathValidator
import com.steamdeck.mobile.core.util.UriUtils
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
 * Home Screen ViewModel
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
  * Load game list
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
    _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
   }
  }
 }

 /**
  * Search games
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
    _uiState.value = HomeUiState.Error(e.message ?: "Search error")
   }
  }
 }

 /**
  * Toggle favorite status
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
  * Refresh game list
  */
 fun refresh() {
  _uiState.value = HomeUiState.Loading
  loadGames()
 }

 /**
  * Add game manually
  *
  * Best practices:
  * - Validate input values
  * - Check for duplicates
  * - Verify insertGame return value
  * - Proper error handling
  */
 fun addGame(name: String, executablePath: String, installPath: String) {
  viewModelScope.launch {
   try {
    // 1. Validate input values
    if (name.isBlank()) {
     _uiState.value = HomeUiState.Error("Please enter game name")
     return@launch
    }
    if (executablePath.isBlank()) {
     _uiState.value = HomeUiState.Error("Please select executable")
     return@launch
    }
    // Install path is optional - will be auto-derived from executable path

    // 2. Path validation (prevent path traversal attacks)
    // Validate both file:// paths and content:// URIs
    if (!isPathSafe(executablePath)) {
     _uiState.value = HomeUiState.Error("Invalid executable path - potential security risk detected")
     return@launch
    }
    // Only validate install path if it's provided
    if (installPath.isNotBlank() && !isPathSafe(installPath)) {
     _uiState.value = HomeUiState.Error("Invalid install path - potential security risk detected")
     return@launch
    }

    // 3. URI scheme validation (content:// or absolute path)
    // Storage Access Framework URIs are recommended
    val isValidExecutablePath = executablePath.startsWith("content://") ||
           executablePath.startsWith("/")

    if (!isValidExecutablePath) {
     _uiState.value = HomeUiState.Error("Executable path is invalid")
     return@launch
    }

    // Install path is optional - will be auto-derived from executable if not provided
    val isValidInstallPath = installPath.isBlank() ||
          installPath.startsWith("content://") ||
          installPath.startsWith("/")

    if (!isValidInstallPath) {
     _uiState.value = HomeUiState.Error("Installation folder path is invalid")
     return@launch
    }

    // For Android 10 (API 29) and above, content:// URI is recommended (Scoped Storage)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
     if (!executablePath.startsWith("content://")) {
      AppLogger.w(TAG,
       "Executable path should use content:// URI on Android 10+: $executablePath")
     }
     if (installPath.isNotBlank() && !installPath.startsWith("content://")) {
      AppLogger.w(TAG,
       "Install path should use content:// URI on Android 10+: $installPath")
     }
    }

    // 3. Convert content:// URIs to file paths
    val resolvedExecutablePath = if (executablePath.startsWith("content://")) {
     AppLogger.d(TAG, "Converting executable URI to file path: $executablePath")
     UriUtils.getFilePathFromUri(context, executablePath) ?: run {
      _uiState.value = HomeUiState.Error("Failed to access executable file. Please try selecting the file again.")
      return@launch
     }
    } else {
     executablePath
    }

    // For install path: Use the directory containing the executable
    val resolvedInstallPath = if (installPath.startsWith("content://")) {
     AppLogger.d(TAG, "Install path is content URI, deriving from executable path")
     // Get parent directory of the executable
     val execFile = java.io.File(resolvedExecutablePath)
     execFile.parent ?: resolvedExecutablePath
    } else if (installPath.isNotBlank()) {
     installPath
    } else {
     // Default: Use executable's parent directory
     val execFile = java.io.File(resolvedExecutablePath)
     execFile.parent ?: resolvedExecutablePath
    }

    AppLogger.i(TAG, "Resolved paths - Executable: $resolvedExecutablePath, Install: $resolvedInstallPath")

    // 4. Check for duplicates (same name or same executable path)
    val currentGames = when (val state = _uiState.value) {
     is HomeUiState.Success -> state.games
     else -> emptyList()
    }

    val isDuplicateName = currentGames.any { it.name.equals(name, ignoreCase = true) }
    val isDuplicatePath = currentGames.any { it.executablePath == resolvedExecutablePath }

    if (isDuplicateName) {
     _uiState.value = HomeUiState.Error("A game with the same name already exists: $name")
     return@launch
    }
    if (isDuplicatePath) {
     _uiState.value = HomeUiState.Error("The same executable is already registered")
     return@launch
    }

    // 5. Create game with resolved file paths
    val game = Game(
     name = name,
     executablePath = resolvedExecutablePath,
     installPath = resolvedInstallPath,
     source = com.steamdeck.mobile.domain.model.GameSource.IMPORTED
    )

    // 5. Insert into database and get ID
    val insertedId = gameRepository.insertGame(game)

    // 6. Verify insertion result
    if (insertedId <= 0) {
     _uiState.value = HomeUiState.Error("Failed to add game (invalid ID: $insertedId)")
     AppLogger.e(TAG, "Invalid game ID returned: $insertedId")
     return@launch
    }

    AppLogger.i(TAG, "Game added successfully: $name (ID: $insertedId)")

    // 7. Update list after adding
    // Note: Flow updates automatically, but explicitly calling refresh() ensures immediate reflection
    refresh()

   } catch (e: android.database.sqlite.SQLiteConstraintException) {
    // Database constraint violation (UNIQUE constraint etc.)
    _uiState.value = HomeUiState.Error("Game already exists")
    AppLogger.e(TAG, "SQLite constraint error while adding game", e)
   } catch (e: Exception) {
    _uiState.value = HomeUiState.Error("Failed to add game: ${e.message}")
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
 private fun isPathSafe(path: String): Boolean = PathValidator.isPathSafe(path, context)
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
