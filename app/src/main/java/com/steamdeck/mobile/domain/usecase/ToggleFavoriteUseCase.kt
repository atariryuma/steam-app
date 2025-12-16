package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * お気に入り状態を切り替えるUseCase
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(gameId: Long, isFavorite: Boolean): Result<Unit> {
        return try {
            gameRepository.updateFavoriteStatus(gameId, isFavorite)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
