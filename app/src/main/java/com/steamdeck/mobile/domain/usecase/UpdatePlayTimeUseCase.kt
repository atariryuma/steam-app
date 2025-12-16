package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * プレイ時間を更新するUseCase
 */
class UpdatePlayTimeUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    /**
     * プレイ時間を更新
     * @param gameId ゲームID
     * @param additionalMinutes 追加プレイ時間（分）
     */
    suspend operator fun invoke(gameId: Long, additionalMinutes: Long): Result<Unit> {
        return try {
            if (additionalMinutes < 0) {
                return Result.failure(IllegalArgumentException("プレイ時間は正の値である必要があります"))
            }
            val timestamp = System.currentTimeMillis()
            gameRepository.updatePlayTime(gameId, additionalMinutes, timestamp)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
