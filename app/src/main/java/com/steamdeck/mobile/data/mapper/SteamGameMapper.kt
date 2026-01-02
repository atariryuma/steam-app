package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.remote.steam.model.SteamGame
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.model.InstallationStatus

/**
 * Mapper for converting between SteamGame (data layer DTO)
 * and Game (domain model).
 */
object SteamGameMapper {

 /**
  * Converts a SteamGame DTO to a Game domain model.
  *
  * @param steamGame The Steam API game data
  * @return Game domain model with default values for non-Steam fields
  *
  * FIXED (2025-12-26): Explicitly set installation status to NOT_INSTALLED
  * for proper UI state management and download flow.
  */
 fun toDomain(steamGame: SteamGame): Game {
  return Game(
   id = 0, // Will be assigned by database
   name = steamGame.name,
   steamAppId = steamGame.appId,
   executablePath = "", // To be set during installation/import
   installPath = "", // To be set during installation/import
   source = GameSource.STEAM,
   winlatorContainerId = null, // To be assigned when container is created
   playTimeMinutes = steamGame.playtimeMinutes,
   lastPlayedTimestamp = null, // Steam API doesn't provide last played timestamp
   iconPath = null, // Will be downloaded separately
   bannerPath = null, // Will be downloaded separately
   addedTimestamp = System.currentTimeMillis(),
   isFavorite = false,
   installationStatus = InstallationStatus.NOT_INSTALLED, // Synced games start as not installed
   installProgress = 0, // No progress initially
   statusUpdatedTimestamp = System.currentTimeMillis() // Track when status was set
  )
 }

 /**
  * Converts a list of SteamGame DTOs to Game domain models.
  *
  * @param steamGames List of Steam API game data
  * @return List of Game domain models
  */
 fun toDomainList(steamGames: List<SteamGame>): List<Game> = steamGames.map(::toDomain)
}
