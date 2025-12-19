package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * お気に入り状態を切り替えるUseCase
 *
 * 2025 Best Practice: DataResult<T> for type-safe error handling
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(gameId: Long, isFavorite: Boolean): DataResult<Unit> {
        return try {
            gameRepository.updateFavoriteStatus(gameId, isFavorite)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(AppError.from(e))
        }
    }
}
