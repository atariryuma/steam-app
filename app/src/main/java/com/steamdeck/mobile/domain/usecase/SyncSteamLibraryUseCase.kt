package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.error.SteamSyncError
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import com.steamdeck.mobile.domain.repository.ISteamRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * SteamライブラリをローカルDBに同期するUseCase
 *
 * Best Practice: User-provided API Key
 * ユーザーは自身のSteam Web API Keyを登録する必要があります
 * 取得先: https://steamcommunity.com/dev/apikey
 *
 * Clean Architecture: Only depends on domain layer interfaces
 *
 * 2025 Best Practice:
 * - DataResult<T> for type-safe results with Loading/Success/Error
 * - AppLogger for centralized logging
 * - Structured concurrency with proper error handling
 * - No Android dependencies in domain layer (no Context)
 */
class SyncSteamLibraryUseCase @Inject constructor(
    private val steamRepository: ISteamRepository,
    private val gameRepository: GameRepository,
    private val securePreferences: ISecurePreferences
) {
    /**
     * Steamライブラリを同期
     *
     * @param steamId Steam ID (from QR authentication)
     * @return 同期結果（成功した場合は同期されたゲーム数）
     */
    suspend operator fun invoke(steamId: String): DataResult<Int> {
        return try {
            // API Keyを取得（必須: ユーザー自身のAPI Key）
            val apiKey = securePreferences.getSteamApiKey()

            if (apiKey.isNullOrBlank()) {
                AppLogger.e(TAG, "Steam API Key not configured")
                return DataResult.Error(AppError.AuthError.ApiKeyNotConfigured)
            }

            AppLogger.d(TAG, "Using user-provided API Key for Steam ID: $steamId")

            // Steam APIからゲーム一覧を取得
            val steamGamesResult = steamRepository.getUserLibrary(steamId, apiKey)
            if (steamGamesResult.isFailure) {
                val error = steamGamesResult.exceptionOrNull()
                AppLogger.e(TAG, "GetOwnedGames failed for Steam ID: $steamId", error)

                // Convert to domain-specific error
                val appError = convertToSteamSyncError(error)
                return DataResult.Error(appError)
            }

            AppLogger.d(TAG, "Successfully fetched games from Steam API")

            val games = steamGamesResult.getOrNull() ?: emptyList()
            if (games.isEmpty()) {
                AppLogger.i(TAG, "No games found for Steam ID: $steamId")
                return DataResult.Success(0)
            }

            // 並列処理でゲームを同期（高速化）
            val syncedCount = coroutineScope {
                val syncJobs = games.mapIndexed { index, game ->
                    async {
                        try {
                            // ゲームをDBに追加
                            gameRepository.insertGame(game)
                            AppLogger.d(TAG, "Synced [${index + 1}/${games.size}]: ${game.name}")
                            true
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed to sync game: ${game.name}", e)
                            false
                        }
                    }
                }

                // すべての同期ジョブを待機
                val results = syncJobs.awaitAll()
                results.count { it }
            }

            AppLogger.i(TAG, "Sync completed - $syncedCount/${games.size} games synced successfully")
            DataResult.Success(syncedCount)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Sync failed with exception", e)
            DataResult.Error(AppError.from(e))
        }
    }

    /**
     * Convert legacy Result errors to domain-specific errors
     */
    private fun convertToSteamSyncError(error: Throwable?): AppError {
        val errorMessage = error?.message ?: ""

        // Check for SteamSyncError first
        if (error is SteamSyncError) {
            return when (error) {
                is SteamSyncError.PrivateProfile -> AppError.AuthError("Private profile")
                is SteamSyncError.AuthFailed -> AppError.AuthError("Authentication failed")
                is SteamSyncError.NetworkTimeout -> AppError.TimeoutError("Steam API request")
                is SteamSyncError.ApiError -> AppError.NetworkError(0, error, retryable = true)
            }
        }

        // HTTP error code mapping
        return when {
            errorMessage.contains("403") -> AppError.AuthError("Private profile (403)")
            errorMessage.contains("401") -> AppError.AuthError("Authentication failed (401)")
            errorMessage.contains("timeout", ignoreCase = true) -> AppError.TimeoutError("Steam API request")
            error != null -> AppError.NetworkError(0, error, retryable = true)
            else -> AppError.Unknown()
        }
    }

    companion object {
        private const val TAG = "SyncSteamLibrary"
    }
}
