package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.data.local.preferences.SecureSteamPreferences
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Named

/**
 * SteamライブラリをローカルDBに同期するUseCase
 *
 * Best Practice: Embedded API Key (GameHub approach)
 * ユーザーはSteam IDのみ必要（QR認証で自動取得）
 */
class SyncSteamLibraryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val steamRepository: SteamRepository,
    private val gameRepository: GameRepository,
    private val steamPreferences: SecureSteamPreferences,
    @Named("embedded_steam_api_key") private val embeddedApiKey: String
) {
    /**
     * Steamライブラリを同期
     *
     * @param steamId Steam ID (from QR authentication)
     * @return 同期結果（成功した場合は同期されたゲーム数）
     */
    suspend operator fun invoke(steamId: String): Result<Int> {
        return try {
            // API Keyを取得（ユーザー設定 > 埋め込みAPI Key）
            val userApiKey = steamPreferences.getSteamApiKey().first()
            val apiKey = if (!userApiKey.isNullOrBlank()) {
                android.util.Log.d(
                    "SyncSteamLibrary",
                    "Using user-provided API Key"
                )
                userApiKey
            } else {
                android.util.Log.d(
                    "SyncSteamLibrary",
                    "Using embedded API Key"
                )
                embeddedApiKey
            }

            // Steam APIからゲーム一覧を取得
            android.util.Log.d(
                "SyncSteamLibrary",
                "Starting sync - Steam ID: $steamId"
            )

            val steamGamesResult = steamRepository.getOwnedGames(apiKey, steamId)
            if (steamGamesResult.isFailure) {
                val error = steamGamesResult.exceptionOrNull()
                android.util.Log.e(
                    "SyncSteamLibrary",
                    "GetOwnedGames failed - Steam ID: $steamId",
                    error
                )

                // Check if it's an auth failure vs network issue
                val errorMessage = error?.message ?: ""
                when {
                    errorMessage.contains("403") -> {
                        return Result.failure(
                            Exception("Steamプロフィールが非公開です。Steamの設定でプロフィールを公開にしてください。")
                        )
                    }
                    errorMessage.contains("401") -> {
                        return Result.failure(
                            Exception("Steam API認証エラー。Steamプロフィールが非公開の可能性があります。Steamの設定で「ゲームの詳細」を公開にしてください。")
                        )
                    }
                    errorMessage.contains("timeout", ignoreCase = true) -> {
                        return Result.failure(Exception("ネットワークタイムアウト。接続を確認してください。"))
                    }
                    else -> {
                        return Result.failure(Exception("Steam APIエラー: $errorMessage"))
                    }
                }
            }

            android.util.Log.d("SyncSteamLibrary", "Successfully fetched games from Steam API")

            val steamGames = steamGamesResult.getOrNull() ?: emptyList()
            if (steamGames.isEmpty()) {
                return Result.success(0)
            }

            // 既存のSteamゲームを取得（重複チェック用）
            // CRITICAL FIX: collect()は無限ループになるため、first()を使用
            val existingGames = gameRepository.getGamesBySource(GameSource.STEAM)
                .first()
                .mapNotNull { it.steamAppId }
                .toSet()

            // 新規追加が必要なゲームをフィルタリング
            val newGames = steamGames.filter { !existingGames.contains(it.appId) }

            android.util.Log.d(
                "SyncSteamLibrary",
                "Found ${newGames.size} new games to sync (total: ${steamGames.size}, existing: ${existingGames.size})"
            )

            if (newGames.isEmpty()) {
                android.util.Log.d("SyncSteamLibrary", "No new games to sync")
                return Result.success(0)
            }

            // 画像保存用ディレクトリ
            val imageDir = File(context.filesDir, "game_images")
            imageDir.mkdirs()

            // 並列処理でゲームを同期（高速化）
            coroutineScope {
                val syncJobs = newGames.map { steamGame ->
                    async {
                        try {
                            // アイコン・バナー画像をダウンロード
                            val iconPath = File(imageDir, "${steamGame.appId}_icon.jpg").absolutePath
                            val bannerPath = File(imageDir, "${steamGame.appId}_banner.jpg").absolutePath

                            // 画像ダウンロードを並列実行
                            val iconJob = async {
                                steamRepository.downloadGameImage(steamGame.getIconUrl(), iconPath)
                            }
                            val bannerJob = async {
                                steamRepository.downloadGameImage(steamGame.getHeaderUrl(), bannerPath)
                            }
                            iconJob.await()
                            bannerJob.await()

                            // ゲームをDBに追加
                            val game = Game(
                                name = steamGame.name,
                                steamAppId = steamGame.appId,
                                executablePath = "", // Steamゲームは実行パス不要（Steam経由で起動）
                                installPath = "", // インストールパスは後で設定可能
                                source = GameSource.STEAM,
                                winlatorContainerId = null,
                                playTimeMinutes = steamGame.playtimeMinutes,
                                lastPlayedTimestamp = null,
                                iconPath = iconPath,
                                bannerPath = bannerPath,
                                addedTimestamp = System.currentTimeMillis(),
                                isFavorite = false
                            )

                            gameRepository.insertGame(game)

                            android.util.Log.d(
                                "SyncSteamLibrary",
                                "Synced: ${steamGame.name} (${steamGame.appId})"
                            )

                            true // 成功
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "SyncSteamLibrary",
                                "Failed to sync game: ${steamGame.name}",
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
                    "Sync completed - $syncedCount/${newGames.size} games synced successfully"
                )

                Result.success(syncedCount)
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncSteamLibrary", "Sync failed with exception", e)
            Result.failure(e)
        }
    }
}
