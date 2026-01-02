package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.SteamAuthManager
import com.steamdeck.mobile.core.steam.SteamConfigManager
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open Steam Big Picture mode use case
 *
 * This use case handles:
 * 1. Steam credentials (loginusers.vdf + config.vdf) configuration
 * 2. CDN server pre-configuration
 * 3. Auto-login setup if user authenticated via QR
 * 4. Steam Big Picture launch
 *
 * Architecture:
 * - Encapsulates Steam launch logic from GameDetailViewModel
 * - Reduces ViewModel dependencies (SteamAuthManager, SteamConfigManager, etc.)
 * - Single Responsibility: Only handles Steam client opening
 */
@Singleton
class OpenSteamClientUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val steamLauncher: SteamLauncher,
    private val steamAuthManager: SteamAuthManager,
    private val steamConfigManager: SteamConfigManager,
    private val securePreferences: ISecurePreferences
) {
    companion object {
        private const val TAG = "OpenSteamClientUseCase"
    }

    /**
     * Open Steam Big Picture mode for a specific game
     *
     * @param gameId Game database ID (used to determine container)
     * @return DataResult indicating success or error
     */
    suspend operator fun invoke(gameId: Long): DataResult<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Opening Steam Big Picture for gameId=$gameId")

            // Get game information
            val game = gameRepository.getGameById(gameId)
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError("Game not found: $gameId", null)
                )

            // Check Container ID
            val containerId = game.winlatorContainerId ?: "default_shared_container"
            AppLogger.d(TAG, "Using container: $containerId")

            // Configure Steam credentials before launch (2025-12-23 Enhancement)
            // Ensures auto-login if user completed QR authentication
            val containerDir = File(context.filesDir, "winlator/containers/$containerId")
            val steamId = try {
                securePreferences.getSteamId().first()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to get Steam ID: ${e.message}")
                null
            }

            if (steamId != null) {
                AppLogger.i(TAG, "Configuring Steam auto-login: SteamID=$steamId")

                // Get Steam username for auto-login (NOT SteamID64)
                val steamUsername = try {
                    securePreferences.getSteamUsername().first()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to get Steam username: ${e.message}")
                    null
                }

                // Create loginusers.vdf
                val authResult = steamAuthManager.createLoginUsersVdf(containerDir)
                if (authResult.isFailure) {
                    AppLogger.w(TAG, "Failed to create loginusers.vdf (non-fatal): ${authResult.exceptionOrNull()?.message}")
                }

                // Create config.vdf with CDN servers + auto-login
                // CRITICAL: Use Steam account name, NOT SteamID64
                val configResult = steamConfigManager.createConfigVdf(containerDir, steamUsername)
                if (configResult.isFailure) {
                    AppLogger.w(TAG, "Failed to create config.vdf (non-fatal): ${configResult.exceptionOrNull()?.message}")
                }
            } else {
                AppLogger.i(TAG, "No Steam ID found - Steam will require manual login")
            }

            // Launch Steam in background mode (no UI, for download monitoring)
            // NOTE: XServer is automatically started by SteamLauncher if not running
            // Uses -silent -no-browser flags for headless UI (X11 still required)
            val result = steamLauncher.launchSteam(
                containerId = containerId,
                mode = com.steamdeck.mobile.core.steam.SteamLauncher.SteamLaunchMode.BACKGROUND
            )

            return@withContext if (result.isSuccess) {
                AppLogger.i(TAG, "Steam client opened successfully in background mode")
                DataResult.Success(Unit)
            } else {
                val error = result.exceptionOrNull()
                AppLogger.e(TAG, "Failed to open Steam client", error)
                DataResult.Error(
                    AppError.Unknown(error ?: Exception("Steam client launch failed"))
                )
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception while opening Steam Big Picture", e)
            return@withContext DataResult.Error(AppError.from(e))
        }
    }
}
