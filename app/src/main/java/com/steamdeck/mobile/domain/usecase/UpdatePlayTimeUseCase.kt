package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * プレイ時間を更新するUseCase
 *
 * 2025 Best Practice: DataResult<T> for type-safe error handling
 */
class UpdatePlayTimeUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    /**
     * プレイ時間を更新
     * @param gameId ゲームID
     * @param additionalMinutes 追加プレイ時間（分）
     */
    suspend operator fun invoke(gameId: Long, additionalMinutes: Long): DataResult<Unit> {
        return try {
            if (additionalMinutes < 0) {
                return DataResult.Error(
                    AppError.DatabaseError("プレイ時間は正の値である必要があります", null)
                )
            }
            val timestamp = System.currentTimeMillis()
            gameRepository.updatePlayTime(gameId, additionalMinutes, timestamp)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(AppError.from(e))
        }
    }
}
