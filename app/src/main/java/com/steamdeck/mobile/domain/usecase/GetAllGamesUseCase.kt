package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * all gameretrievedoUseCase
 */
class GetAllGamesUseCase @Inject constructor(
 private val gameRepository: GameRepository
) {
 operator fun invoke(): Flow<List<Game>> {
  return gameRepository.getAllGames()
 }
}
