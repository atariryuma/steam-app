package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.remote.steam.model.SteamGame
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource

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
            isFavorite = false
        )
    }

    /**
     * Converts a list of SteamGame DTOs to Game domain models.
     *
     * @param steamGames List of Steam API game data
     * @return List of Game domain models
     */
    fun toDomainList(steamGames: List<SteamGame>): List<Game> {
        return steamGames.map { toDomain(it) }
    }
}
