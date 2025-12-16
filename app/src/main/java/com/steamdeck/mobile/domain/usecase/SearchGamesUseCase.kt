package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * ゲームを検索するUseCase
 */
class SearchGamesUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    operator fun invoke(query: String): Flow<List<Game>> {
        return if (query.isBlank()) {
            gameRepository.getAllGames()
        } else {
            gameRepository.searchGames(query)
        }
    }
}
