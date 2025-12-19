package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * gameadddoUseCase
 *
 * 2025 Best Practice: DataResult<T> for type-safe error handling
 */
class AddGameUseCase @Inject constructor(
 private val gameRepository: GameRepository
) {
 /**
  * gameadd
  * @return addされたgame ID
  */
 suspend operator fun invoke(game: Game): DataResult<Long> {
  return try {
   // バリデーション
   if (game.name.isBlank()) {
    return DataResult.Error(
     AppError.DatabaseError("game名入力please", null)
    )
   }
   if (game.executablePath.isBlank()) {
    return DataResult.Error(
     AppError.FileError("executionfilepath入力please", null)
    )
   }
   if (game.installPath.isBlank()) {
    return DataResult.Error(
     AppError.FileError("installationpath入力please", null)
    )
   }

   val gameId = gameRepository.insertGame(game)
   DataResult.Success(gameId)
  } catch (e: Exception) {
   DataResult.Error(AppError.from(e))
  }
 }
}
