package com.steamdeck.mobile.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Secure preferences repository interface.
 * Abstracts encrypted storage operations from the domain layer.
 */
interface ISecurePreferences {
 /**
  * Retrieves the stored Steam authentication token.
  *
  * @return The Steam token if available, null otherwise
  */
 suspend fun getSteamToken(): String?

 /**
  * Saves the Steam authentication token securely.
  *
  * @param token The Steam token to store
  */
 suspend fun saveSteamToken(token: String)

 /**
  * Retrieves the stored Steam ID as a Flow.
  *
  * @return Flow emitting the Steam ID if available, null otherwise
  */
 suspend fun getSteamId(): Flow<String?>

 /**
  * Saves the Steam ID.
  *
  * @param steamId The Steam ID to store
  */
 suspend fun saveSteamId(steamId: String)

 /**
  * Retrieves the stored Steam username as a Flow.
  *
  * @return Flow emitting the Steam username if available, null otherwise
  */
 suspend fun getSteamUsername(): Flow<String?>

 /**
  * Saves the Steam username.
  *
  * @param username The Steam username to store
  */
 suspend fun saveSteamUsername(username: String)

 /**
  * Retrieves the stored Steam API key.
  *
  * @return The API key if available, null otherwise
  */
 suspend fun getSteamApiKey(): String?

 /**
  * Saves the Steam API key securely.
  *
  * @param apiKey The API key to store
  */
 suspend fun saveSteamApiKey(apiKey: String)

 /**
  * Retrieves the last sync timestamp as a Flow.
  *
  * @return Flow emitting the last sync timestamp if available, null otherwise
  */
 suspend fun getLastSyncTimestamp(): Flow<Long?>

 /**
  * Saves the last sync timestamp.
  *
  * @param timestamp The timestamp to store
  */
 suspend fun setLastSyncTimestamp(timestamp: Long)

 /**
  * Clears all stored Steam credentials and settings.
  */
 suspend fun clearSteamSettings()

 /**
  * Checks if Steam credentials are stored.
  *
  * @return True if credentials exist, false otherwise
  */
 suspend fun hasSteamCredentials(): Boolean

 /**
  * Retrieves the Steam Big Picture Mode preference.
  *
  * @return True if Big Picture Mode is enabled, false otherwise (default: true)
  */
 suspend fun getSteamBigPictureMode(): Boolean

 /**
  * Saves the Steam Big Picture Mode preference.
  *
  * @param enabled True to enable Big Picture Mode, false for normal mode
  */
 suspend fun saveSteamBigPictureMode(enabled: Boolean)
}
