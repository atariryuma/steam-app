package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * SteamライブラリをローカルDBに同期するUseCase
 */
class SyncSteamLibraryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val steamRepository: SteamRepository,
    private val gameRepository: GameRepository
) {
    /**
     * Steamライブラリを同期
     *
     * @param apiKey Steam Web API Key
     * @param steamId Steam ID
     * @return 同期結果（成功した場合は同期されたゲーム数）
     */
    suspend operator fun invoke(apiKey: String, steamId: String): Result<Int> {
        return try {
            // Steam APIからゲーム一覧を取得
            val steamGamesResult = steamRepository.getOwnedGames(apiKey, steamId)
            if (steamGamesResult.isFailure) {
                return Result.failure(steamGamesResult.exceptionOrNull() ?: Exception("Steam APIエラー"))
            }

            val steamGames = steamGamesResult.getOrNull() ?: emptyList()
            if (steamGames.isEmpty()) {
                return Result.success(0)
            }

            // 既存のSteamゲームを取得（重複チェック用）
            val existingGames = mutableSetOf<Long>()
            gameRepository.getGamesBySource(GameSource.STEAM).collect { games ->
                existingGames.addAll(games.mapNotNull { it.steamAppId })
            }

            // 画像保存用ディレクトリ
            val imageDir = File(context.filesDir, "game_images")
            imageDir.mkdirs()

            var syncedCount = 0

            // 各ゲームをローカルDBに追加
            for (steamGame in steamGames) {
                // 既に存在する場合はスキップ
                if (existingGames.contains(steamGame.appId)) {
                    continue
                }

                // アイコン・バナー画像をダウンロード
                val iconPath = File(imageDir, "${steamGame.appId}_icon.jpg").absolutePath
                val bannerPath = File(imageDir, "${steamGame.appId}_banner.jpg").absolutePath

                steamRepository.downloadGameImage(steamGame.getIconUrl(), iconPath)
                steamRepository.downloadGameImage(steamGame.getHeaderUrl(), bannerPath)

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
                syncedCount++
            }

            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
