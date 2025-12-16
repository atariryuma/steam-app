package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * ゲームIDでゲームを取得するUseCase
 */
class GetGameByIdUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(gameId: Long): Game? {
        return gameRepository.getGameById(gameId)
    }
}
