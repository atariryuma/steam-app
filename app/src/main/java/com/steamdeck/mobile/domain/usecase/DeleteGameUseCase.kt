package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * ゲームを削除するUseCase
 */
class DeleteGameUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(game: Game): Result<Unit> {
        return try {
            gameRepository.deleteGame(game)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
