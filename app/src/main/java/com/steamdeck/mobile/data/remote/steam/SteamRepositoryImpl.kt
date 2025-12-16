package com.steamdeck.mobile.data.remote.steam

import android.content.Context
import android.util.Log
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
 * SteamRepositoryの実装
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

    override suspend fun getOwnedGames(apiKey: String, steamId: String): Result<List<SteamGame>> {
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
                    Log.d(TAG, "Successfully fetched ${games.size} games from Steam")
                    Result.success(games)
                } else {
                    val errorMsg = "Steam API error: ${response.code()} ${response.message()}"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch owned games", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getPlayerSummary(apiKey: String, steamId: String): Result<SteamPlayer> {
        return withContext(Dispatchers.IO) {
            try {
                val response = steamApiService.getPlayerSummaries(
                    key = apiKey,
                    steamIds = steamId
                )

                if (response.isSuccessful) {
                    val player = response.body()?.response?.players?.firstOrNull()
                    if (player != null) {
                        Log.d(TAG, "Successfully fetched player: ${player.personaName}")
                        Result.success(player)
                    } else {
                        Result.failure(Exception("プレイヤー情報が見つかりません"))
                    }
                } else {
                    val errorMsg = "Steam API error: ${response.code()} ${response.message()}"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch player summary", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun downloadGameImage(url: String, destinationPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("画像ダウンロード失敗: ${response.code}"))
                    }

                    val file = File(destinationPath)
                    file.parentFile?.mkdirs()

                    response.body?.byteStream()?.use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    Log.d(TAG, "Successfully downloaded image to: $destinationPath")
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download image from $url", e)
                Result.failure(e)
            }
        }
    }
}
