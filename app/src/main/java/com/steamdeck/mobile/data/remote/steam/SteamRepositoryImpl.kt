package com.steamdeck.mobile.data.remote.steam

import android.content.Context
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.data.remote.steam.model.SteamGame
import com.steamdeck.mobile.data.remote.steam.model.SteamPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SteamRepository implementation
 *
 * MIGRATION (2025-12-22): Result<T> → DataResult<T>
 * - Use AppError.fromHttpCode() for HTTP errors (proper error classification)
 * - Use AppError.from() for generic exceptions (type-safe error handling)
 * - Benefits: Retryability determination, consistent error messages, UI-friendly errors
 */
@Singleton
class SteamRepositoryImpl @Inject constructor(
 @ApplicationContext private val context: Context,
 private val steamApiService: SteamApiService,
 private val okHttpClient: OkHttpClient
) : SteamRepository {

 companion object {
  private const val TAG = "SteamRepository"
 }

 override suspend fun getOwnedGames(apiKey: String, steamId: String): DataResult<List<SteamGame>> {
  return withContext(Dispatchers.IO) {
   try {
    val response = steamApiService.getOwnedGames(
     key = apiKey,
     steamId = steamId,
     includeAppInfo = 1,
     includePlayedFreeGames = 1
    )

    if (response.isSuccessful) {
     val games = response.body()?.response?.games ?: emptyList()
     AppLogger.d(TAG, "Successfully fetched ${games.size} games from Steam")
     DataResult.Success(games)
    } else {
     // Use AppError.fromHttpCode() for proper error classification
     val error = AppError.fromHttpCode(response.code(), response.message())
     AppLogger.e(TAG, "Steam API error: ${error.message}")
     DataResult.Error(error)
    }
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to fetch owned games", e)
    DataResult.Error(AppError.from(e))
   }
  }
 }

 override suspend fun getPlayerSummary(apiKey: String, steamId: String): DataResult<SteamPlayer> {
  return withContext(Dispatchers.IO) {
   try {
    val response = steamApiService.getPlayerSummaries(
     key = apiKey,
     steamIds = steamId
    )

    if (response.isSuccessful) {
     val player = response.body()?.response?.players?.firstOrNull()
     if (player != null) {
      AppLogger.d(TAG, "Successfully fetched player: ${player.personaName}")
      DataResult.Success(player)
     } else {
      val error = AppError.NetworkError(404, Exception("Player not found"), retryable = false)
      AppLogger.w(TAG, "Player information not found")
      DataResult.Error(error)
     }
    } else {
     // Use AppError.fromHttpCode() for proper error classification
     val error = AppError.fromHttpCode(response.code(), response.message())
     AppLogger.e(TAG, "Steam API error: ${error.message}")
     DataResult.Error(error)
    }
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to fetch player summary", e)
    DataResult.Error(AppError.from(e))
   }
  }
 }

 override suspend fun downloadGameImage(url: String, destinationPath: String): DataResult<Unit> {
  return withContext(Dispatchers.IO) {
   try {
    val request = Request.Builder()
     .url(url)
     .build()

    okHttpClient.newCall(request).execute().use { response ->
     if (!response.isSuccessful) {
      // Use AppError.fromHttpCode() for HTTP errors
      val error = AppError.fromHttpCode(response.code, "Image download failed")
      AppLogger.e(TAG, "Image download failed: ${error.message}")
      return@withContext DataResult.Error(error)
     }

     val file = File(destinationPath)
     file.parentFile?.mkdirs()

     response.body?.byteStream()?.use { inputStream ->
      FileOutputStream(file).use { outputStream ->
       inputStream.copyTo(outputStream)
      }
     }

     AppLogger.d(TAG, "Successfully downloaded image to: $destinationPath")
     DataResult.Success(Unit)
    }
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to download image from $url", e)
    DataResult.Error(AppError.from(e))
   }
  }
 }
}
