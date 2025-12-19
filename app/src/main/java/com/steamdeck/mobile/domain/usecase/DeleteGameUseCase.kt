package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * ゲームを削除するUseCase
 *
 * 2025 Best Practice: DataResult<T> for type-safe error handling
 */
class DeleteGameUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(game: Game): DataResult<Unit> {
        return try {
            gameRepository.deleteGame(game)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(AppError.from(e))
        }
    }
}
