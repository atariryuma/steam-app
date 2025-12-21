package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.AppManifestParser
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Validate game installation use case
 *
 * Performs 3-level validation before launching a game:
 * 1. Executable file exists (.exe)
 * 2. Steam manifest StateFlags = 4 (fully installed)
 * 3. Required dependency DLLs exist
 *
 * This prevents game launch failures and provides clear error messages to users.
 */
class ValidateGameInstallationUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository
) {
    companion object {
        private const val TAG = "ValidateGameInstall"

        /**
         * Common Windows runtime DLLs required by most games
         */
        private val REQUIRED_DLLS = listOf(
            "vcruntime140.dll",    // Visual C++ 2015-2022 Runtime
            "msvcp140.dll",        // Visual C++ 2015-2022 Runtime
            "d3d11.dll"            // DirectX 11 (usually present in Wine)
        )
    }

    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError>
    ) {
        /**
         * User-friendly error message
         */
        fun getUserMessage(): String? {
            if (isValid) return null

            return errors.joinToString("\n") { error ->
                when (error) {
                    is ValidationError.ExecutableNotFound ->
                        "Game executable not found at: ${error.path}"
                    is ValidationError.ManifestNotFound ->
                        "Steam manifest file not found. Game may not be installed via Steam."
                    is ValidationError.StateFlagsNotReady ->
                        "Game is ${AppManifestParser.getStateDescription(error.currentFlags)}. Please wait for installation to complete."
                    is ValidationError.MissingDLL ->
                        "Missing required DLL: ${error.dllName}. Please install Visual C++ Redistributable."
                }
            }
        }
    }

    /**
     * Validation errors
     */
    sealed class ValidationError {
        data class ExecutableNotFound(val path: String) : ValidationError()
        data object ManifestNotFound : ValidationError()
        data class StateFlagsNotReady(val currentFlags: Int) : ValidationError()
        data class MissingDLL(val dllName: String) : ValidationError()
    }

    /**
     * Validate game installation
     *
     * @param gameId Game database ID
     * @return ValidationResult with errors if any
     */
    suspend operator fun invoke(gameId: Long): DataResult<ValidationResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Validating game installation: gameId=$gameId")

            val errors = mutableListOf<ValidationError>()

            // Get game from database
            val game = gameRepository.getGameById(gameId)
                ?: return@withContext DataResult.Success(
                    ValidationResult(
                        isValid = false,
                        errors = listOf(ValidationError.ExecutableNotFound("Game not found in database"))
                    )
                )

            // Check 1: Executable file exists
            val executableFile = File(game.executablePath)
            if (!executableFile.exists()) {
                AppLogger.w(TAG, "Executable not found: ${game.executablePath}")
                errors.add(ValidationError.ExecutableNotFound(game.executablePath))
            }

            // Check 2: Steam manifest StateFlags = 4 (only for Steam games)
            if (game.steamAppId != null && game.winlatorContainerId != null) {
                val containerId = game.winlatorContainerId.toString()
                val manifestFile = File(
                    context.filesDir,
                    "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/steamapps/appmanifest_${game.steamAppId}.acf"
                )

                if (!manifestFile.exists()) {
                    AppLogger.w(TAG, "Manifest not found: ${manifestFile.absolutePath}")
                    errors.add(ValidationError.ManifestNotFound)
                } else {
                    val manifestResult = AppManifestParser.parse(manifestFile)
                    if (manifestResult.isSuccess) {
                        val manifest = manifestResult.getOrThrow()
                        if (manifest.stateFlags != 4) {
                            AppLogger.w(TAG, "Game not fully installed. StateFlags=${manifest.stateFlags}")
                            errors.add(ValidationError.StateFlagsNotReady(manifest.stateFlags))
                        }
                    } else {
                        AppLogger.e(TAG, "Failed to parse manifest: ${manifestResult.exceptionOrNull()?.message}")
                        errors.add(ValidationError.ManifestNotFound)
                    }
                }
            }

            // Check 3: Required DLLs exist (only for Steam games with containers)
            if (game.winlatorContainerId != null) {
                val containerId = game.winlatorContainerId.toString()
                val system32Dir = File(
                    context.filesDir,
                    "winlator/containers/$containerId/drive_c/windows/system32"
                )

                for (dllName in REQUIRED_DLLS) {
                    val dllFile = File(system32Dir, dllName)
                    if (!dllFile.exists()) {
                        AppLogger.w(TAG, "Missing DLL: $dllName at ${dllFile.absolutePath}")
                        errors.add(ValidationError.MissingDLL(dllName))
                    }
                }
            }

            val result = ValidationResult(
                isValid = errors.isEmpty(),
                errors = errors
            )

            if (result.isValid) {
                AppLogger.i(TAG, "Game validation successful: ${game.name}")
            } else {
                AppLogger.w(TAG, "Game validation failed with ${errors.size} errors")
            }

            DataResult.Success(result)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to validate game installation", e)
            DataResult.Success(
                ValidationResult(
                    isValid = false,
                    errors = listOf(ValidationError.ExecutableNotFound(e.message ?: "Unknown error"))
                )
            )
        }
    }
}
