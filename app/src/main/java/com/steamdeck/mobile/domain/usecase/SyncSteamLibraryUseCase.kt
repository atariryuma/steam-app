package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.data.mapper.SteamGameMapper
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.domain.error.SteamSyncError
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import com.steamdeck.mobile.domain.repository.ISteamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Sync Steam library to local database use case
 *
 * Best Practice: User-provided API Key
 * Users must register their own Steam Web API Key
 * Obtain at: https://steamcommunity.com/dev/apikey
 *
 * Clean Architecture: Only depends on domain layer interfaces
 *
 * 2025 Best Practice:
 * - DataResult<T> for type-safe results with Loading/Success/Error
 * - AppLogger for centralized logging
 * - Structured concurrency with proper error handling
 * - No Android dependencies in domain layer (no Context)
 */
class SyncSteamLibraryUseCase @Inject constructor(
 @ApplicationContext private val context: Context,
 private val steamRepository: ISteamRepository,
 private val steamRepositoryImpl: SteamRepository,
 private val gameRepository: GameRepository,
 private val securePreferences: ISecurePreferences
) {
 /**
  * Sync Steam library
  *
  * @param steamId Steam ID (from QR authentication)
  * @param steamContainerId Steam container ID (where Steam client is installed)
  * @return Sync result (number of games synced on success)
  */
 suspend operator fun invoke(steamId: String, steamContainerId: Long): DataResult<Int> {
  return try {
   // Get API Key (required: user must provide their own API Key)
   val apiKey = securePreferences.getSteamApiKey()

   if (apiKey.isNullOrBlank()) {
    AppLogger.e(TAG, "Steam API Key not configured")
    return DataResult.Error(AppError.AuthError.ApiKeyNotConfigured)
   }

   AppLogger.d(TAG, "Using user-provided API Key for Steam ID: $steamId")

   // Fetch game list from Steam API (use direct repository to get SteamGame objects)
   val steamGamesResult = steamRepositoryImpl.getOwnedGames(apiKey, steamId)
   when (steamGamesResult) {
    is DataResult.Error -> {
     AppLogger.e(TAG, "GetOwnedGames failed for Steam ID: $steamId - ${steamGamesResult.error.message}")
     return DataResult.Error(steamGamesResult.error)
    }
    is DataResult.Loading -> {
     AppLogger.w(TAG, "GetOwnedGames still loading")
     return DataResult.Error(AppError.Unknown(Exception("Still loading")))
    }
    is DataResult.Success -> {
     AppLogger.d(TAG, "Successfully fetched games from Steam API")

     val steamGames = steamGamesResult.data
     if (steamGames.isEmpty()) {
      AppLogger.i(TAG, "No games found for Steam ID: $steamId")
      return DataResult.Success(0)
     }

   // Process game sync in parallel (performance optimization)
   val syncedCount = coroutineScope {
    val syncJobs = steamGames.mapIndexed { index, steamGame ->
     async {
      try {
       // Download game images
       val imagesDir = context.getExternalFilesDir("game_images")
       val iconPath = downloadGameIcon(steamGame, imagesDir?.absolutePath ?: "")
       val bannerPath = downloadGameBanner(steamGame, imagesDir?.absolutePath ?: "")

       // Convert to domain model with image paths
       val game = SteamGameMapper.toDomain(steamGame).copy(
        iconPath = iconPath,
        bannerPath = bannerPath,
        // Automatically assign Steam container ID to enable download functionality
        winlatorContainerId = steamContainerId
       )

       // Add game to database
       gameRepository.insertGame(game)
       AppLogger.d(TAG, "Synced [${index + 1}/${steamGames.size}]: ${game.name}")
       true
      } catch (e: Exception) {
       AppLogger.e(TAG, "Failed to sync game: ${steamGame.name}", e)
       false
      }
     }
    }

    // Wait for all sync jobs to complete
    val results = syncJobs.awaitAll()
    results.count { it }
   }

   AppLogger.i(TAG, "Sync completed - $syncedCount/${steamGames.size} games synced successfully")
   DataResult.Success(syncedCount)
    }
   }

  } catch (e: Exception) {
   AppLogger.e(TAG, "Sync failed with exception", e)
   DataResult.Error(AppError.from(e))
  }
 }

 /**
  * Download game icon image
  */
 private suspend fun downloadGameIcon(steamGame: com.steamdeck.mobile.data.remote.steam.model.SteamGame, imagesDir: String): String? {
  return try {
   val iconUrl = steamGame.getHeaderUrl() // Use header image (460x215) for better quality
   val iconFileName = "icon_${steamGame.appId}.jpg"
   val iconPath = "$imagesDir/$iconFileName"

   when (val result = steamRepositoryImpl.downloadGameImage(iconUrl, iconPath)) {
    is DataResult.Success -> iconPath
    is DataResult.Error -> {
     AppLogger.w(TAG, "Failed to download icon for ${steamGame.name}: ${result.error.message}")
     null
    }
    is DataResult.Loading -> {
     AppLogger.w(TAG, "Icon download still loading for ${steamGame.name}")
     null
    }
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Exception downloading icon for ${steamGame.name}", e)
   null
  }
 }

 /**
  * Download game banner image
  */
 private suspend fun downloadGameBanner(steamGame: com.steamdeck.mobile.data.remote.steam.model.SteamGame, imagesDir: String): String? {
  return try {
   val bannerUrl = steamGame.getLibraryAssetUrl() // Use library asset (600x900) for banner
   val bannerFileName = "banner_${steamGame.appId}.jpg"
   val bannerPath = "$imagesDir/$bannerFileName"

   when (val result = steamRepositoryImpl.downloadGameImage(bannerUrl, bannerPath)) {
    is DataResult.Success -> bannerPath
    is DataResult.Error -> {
     AppLogger.w(TAG, "Failed to download banner for ${steamGame.name}: ${result.error.message}")
     null
    }
    is DataResult.Loading -> {
     AppLogger.w(TAG, "Banner download still loading for ${steamGame.name}")
     null
    }
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Exception downloading banner for ${steamGame.name}", e)
   null
  }
 }

 /**
  * Convert legacy Result errors to domain-specific errors
  */
 private fun convertToSteamSyncError(error: Throwable?): AppError {
  val errorMessage = error?.message ?: ""

  // Check for SteamSyncError first
  if (error is SteamSyncError) {
   return when (error) {
    is SteamSyncError.PrivateProfile -> AppError.AuthError("Private profile")
    is SteamSyncError.AuthFailed -> AppError.AuthError("Authentication failed")
    is SteamSyncError.NetworkTimeout -> AppError.TimeoutError("Steam API request")
    is SteamSyncError.ApiError -> AppError.NetworkError(0, error, retryable = true)
   }
  }

  // HTTP error code mapping
  return when {
   errorMessage.contains("403") -> AppError.AuthError("Private profile (403)")
   errorMessage.contains("401") -> AppError.AuthError("Authentication failed (401)")
   errorMessage.contains("timeout", ignoreCase = true) -> AppError.TimeoutError("Steam API request")
   error != null -> AppError.NetworkError(0, error, retryable = true)
   else -> AppError.Unknown()
  }
 }

 companion object {
  private const val TAG = "SyncSteamLibrary"
 }
}
