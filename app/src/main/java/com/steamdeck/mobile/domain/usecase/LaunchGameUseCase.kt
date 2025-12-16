package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.winlator.LaunchResult
import com.steamdeck.mobile.core.winlator.WinlatorEngine
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import javax.inject.Inject

/**
 * ゲームを起動するUseCase
 */
class LaunchGameUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val containerRepository: WinlatorContainerRepository,
    private val winlatorEngine: WinlatorEngine
) {
    /**
     * ゲームを起動
     * @param gameId ゲームID
     * @return 起動結果
     */
    suspend operator fun invoke(gameId: Long): Result<Int> {
        return try {
            // ゲーム情報を取得
            val game = gameRepository.getGameById(gameId)
                ?: return Result.failure(IllegalArgumentException("ゲームが見つかりません"))

            // コンテナ情報を取得（設定されている場合）
            val container = game.winlatorContainerId?.let { containerId ->
                containerRepository.getContainerById(containerId)
            }

            // ゲームを起動
            when (val result = winlatorEngine.launchGame(game, container)) {
                is LaunchResult.Success -> {
                    // プレイ開始時刻を記録（今後プレイ時間計測に使用）
                    val startTimestamp = System.currentTimeMillis()
                    gameRepository.updatePlayTime(gameId, 0, startTimestamp)

                    Result.success(result.processId)
                }
                is LaunchResult.Error -> {
                    Result.failure(Exception(result.message, result.cause))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
