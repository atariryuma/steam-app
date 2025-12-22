package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Add game UseCase
 *
 * 2025 Best Practice: DataResult<T> for type-safe error handling
 */
class AddGameUseCase @Inject constructor(
 private val gameRepository: GameRepository
) {
 /**
  * Add game
  * @return Added game ID
  */
 suspend operator fun invoke(game: Game): DataResult<Long> {
  return try {
   // Validation
   if (game.name.isBlank()) {
    return DataResult.Error(
     AppError.DatabaseError("Please enter game name", null)
    )
   }
   if (game.executablePath.isBlank()) {
    return DataResult.Error(
     AppError.FileError("Please enter executable file path", null)
    )
   }
   if (game.installPath.isBlank()) {
    return DataResult.Error(
     AppError.FileError("Please enter installation path", null)
    )
   }

   val gameId = gameRepository.insertGame(game)
   DataResult.Success(gameId)
  } catch (e: Exception) {
   DataResult.Error(AppError.from(e))
  }
 }
}
