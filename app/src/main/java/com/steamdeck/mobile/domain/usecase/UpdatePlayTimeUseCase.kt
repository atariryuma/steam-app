package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Update play time UseCase
 *
 * 2025 Best Practice: DataResult<T> for type-safe error handling
 */
class UpdatePlayTimeUseCase @Inject constructor(
 private val gameRepository: GameRepository
) {
 /**
  * Update play time
  * @param gameId Game ID
  * @param additionalMinutes Additional play time (minutes)
  */
 suspend operator fun invoke(gameId: Long, additionalMinutes: Long): DataResult<Unit> {
  return try {
   if (additionalMinutes < 0) {
    return DataResult.Error(
     AppError.DatabaseError("Play time must be a positive value", null)
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
