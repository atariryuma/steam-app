package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * play timeupdatedoUseCase
 *
 * 2025 Best Practice: DataResult<T> for type-safe error handling
 */
class UpdatePlayTimeUseCase @Inject constructor(
 private val gameRepository: GameRepository
) {
 /**
  * play timeupdate
  * @param gameId gameID
  * @param additionalMinutes addplay time（minutes）
  */
 suspend operator fun invoke(gameId: Long, additionalMinutes: Long): DataResult<Unit> {
  return try {
   if (additionalMinutes < 0) {
    return DataResult.Error(
     AppError.DatabaseError("play time 正 value ある必要 あります", null)
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
