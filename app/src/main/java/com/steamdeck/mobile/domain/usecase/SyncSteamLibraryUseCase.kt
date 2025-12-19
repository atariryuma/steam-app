package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.domain.error.SteamSyncError
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import com.steamdeck.mobile.domain.repository.ISteamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import javax.inject.Inject

/**
 * SteamライブラリをローカルDBに同期するUseCase
 *
 * Best Practice: User-provided API Key
 * ユーザーは自身のSteam Web API Keyを登録する必要があります
 * 取得先: https://steamcommunity.com/dev/apikey
 *
 * Clean Architecture: Only depends on domain layer interfaces
 */
class SyncSteamLibraryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
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
    suspend operator fun invoke(steamId: String): Result<Int> {
        return try {
            // API Keyを取得（必須: ユーザー自身のAPI Key）
            val apiKey = securePreferences.getSteamApiKey()

            if (apiKey.isNullOrBlank()) {
                android.util.Log.e(
                    "SyncSteamLibrary",
                    "Steam API Key not configured"
                )
                return Result.failure(SteamSyncError.ApiKeyNotConfigured)
            }

            android.util.Log.d(
                "SyncSteamLibrary",
                "Using user-provided API Key"
            )

            // Steam APIからゲーム一覧を取得
            android.util.Log.d(
                "SyncSteamLibrary",
                "Starting sync - Steam ID: $steamId"
            )

            val steamGamesResult = steamRepository.getUserLibrary(steamId, apiKey)
            if (steamGamesResult.isFailure) {
                val error = steamGamesResult.exceptionOrNull()
                android.util.Log.e(
                    "SyncSteamLibrary",
                    "GetOwnedGames failed - Steam ID: $steamId",
                    error
                )

                // Check if it's an auth failure vs network issue
                val errorMessage = error?.message ?: ""
                return Result.failure(
                    when {
                        errorMessage.contains("403") -> SteamSyncError.PrivateProfile
                        errorMessage.contains("401") -> SteamSyncError.AuthFailed
                        errorMessage.contains("timeout", ignoreCase = true) -> SteamSyncError.NetworkTimeout
                        else -> SteamSyncError.ApiError(errorMessage)
                    }
                )
            }

            android.util.Log.d("SyncSteamLibrary", "Successfully fetched games from Steam API")

            val games = steamGamesResult.getOrNull() ?: emptyList()
            if (games.isEmpty()) {
                return Result.success(0)
            }

            // 並列処理でゲームを同期（高速化）
            coroutineScope {
                val syncJobs = games.map { game ->
                    async {
                        try {
                            // ゲームをDBに追加
                            gameRepository.insertGame(game)

                            android.util.Log.d(
                                "SyncSteamLibrary",
                                "Synced: ${game.name} (${game.steamAppId})"
                            )

                            true // 成功
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "SyncSteamLibrary",
                                "Failed to sync game: ${game.name}",
                                e
                            )
                            false // 失敗
                        }
                    }
                }

                // すべての同期ジョブを待機
                val results = syncJobs.awaitAll()
                val syncedCount = results.count { it }

                android.util.Log.d(
                    "SyncSteamLibrary",
                    "Sync completed - $syncedCount/${games.size} games synced successfully"
                )

                Result.success(syncedCount)
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncSteamLibrary", "Sync failed with exception", e)
            Result.failure(e)
        }
    }
}
