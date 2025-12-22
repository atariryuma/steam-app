package com.steamdeck.mobile.data.repository

import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.data.mapper.SteamGameMapper
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.ISteamRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that implements domain ISteamRepository interface
 * using the existing data layer SteamRepository.
 * This maintains Clean Architecture boundaries by converting
 * data layer types to domain models.
 */
@Singleton
class SteamRepositoryAdapter @Inject constructor(
 private val steamRepository: SteamRepository
) : ISteamRepository {

 override suspend fun getUserLibrary(steamId: String, apiKey: String): Result<List<Game>> {
  return when (val result = steamRepository.getOwnedGames(apiKey, steamId)) {
   is DataResult.Success -> Result.success(result.data.map { SteamGameMapper.toDomain(it) })
   is DataResult.Error -> Result.failure(Exception(result.error.message))
   is DataResult.Loading -> Result.failure(Exception("Loading"))
  }
 }

 override suspend fun getGameDetails(appId: String): Result<Game?> {
  // Note: Current SteamRepository doesn't have getGameDetails endpoint
  // This would require extending SteamApiService with Steam Store API
  return Result.success(null)
 }

 override suspend fun validateApiKey(apiKey: String): Result<Boolean> {
  // Simple validation: Try to fetch player summary with dummy SteamID
  // A valid API key will return proper response format even with invalid ID
  return try {
   when (val result = steamRepository.getPlayerSummary(apiKey, "76561197960435530")) {
    is DataResult.Success -> Result.success(true)
    is DataResult.Error -> Result.success(false)
    is DataResult.Loading -> Result.success(false)
   }
  } catch (e: Exception) {
   Result.success(false)
  }
 }
}
