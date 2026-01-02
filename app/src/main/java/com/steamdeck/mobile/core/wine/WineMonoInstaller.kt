package com.steamdeck.mobile.core.wine

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wine Mono Auto-Installer
 *
 * Installs .NET Framework compatibility layer to Wine containers,
 * enabling execution of 32-bit applications (e.g., SteamSetup.exe)
 */
@Singleton
class WineMonoInstaller @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WineMonoInstaller"

        // Wine Mono latest version (2024 release)
        private const val WINE_MONO_VERSION = "9.0.0"
        private const val WINE_MONO_URL = "https://dl.winehq.org/wine/wine-mono/$WINE_MONO_VERSION/wine-mono-$WINE_MONO_VERSION-x86.msi"
        private const val WINE_MONO_FILENAME = "wine-mono-$WINE_MONO_VERSION.msi"

        // Expected file size (approximately 50MB)
        private const val EXPECTED_SIZE_MB = 50
    }

    /**
     * Download Wine Mono MSI
     *
     * @return Downloaded MSI file
     */
    suspend fun downloadWineMono(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(context.cacheDir, "wine_mono")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val msiFile = File(downloadDir, WINE_MONO_FILENAME)

            // Reuse existing file if it exists and size is valid
            if (msiFile.exists() && msiFile.length() > EXPECTED_SIZE_MB * 1024 * 1024 * 0.9) {
                AppLogger.i(TAG, "Using cached Wine Mono: ${msiFile.absolutePath}")
                return@withContext Result.success(msiFile)
            }

            AppLogger.i(TAG, "Downloading Wine Mono $WINE_MONO_VERSION from $WINE_MONO_URL")

            val request = okhttp3.Request.Builder()
                .url(WINE_MONO_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to download Wine Mono: HTTP ${response.code}")
                )
            }

            val responseBody = response.body
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val contentLength = responseBody.contentLength()
            var bytesDownloaded = 0L

            // PERFORMANCE: Use 128KB buffer for 50MB file download
            responseBody.byteStream().use { input ->
                msiFile.outputStream().buffered(131072).use { output ->
                    val buffer = ByteArray(131072)  // 128KB buffer
                    var bytes: Int
                    var lastLoggedPercent = 0

                    while (input.read(buffer).also { bytes = it } >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesDownloaded += bytes

                        // Log progress every 10%
                        val currentPercent = ((bytesDownloaded * 100) / contentLength).toInt()
                        if (currentPercent >= lastLoggedPercent + 10) {
                            AppLogger.d(TAG, "Wine Mono download: $currentPercent% (${bytesDownloaded / 1024 / 1024}MB)")
                            lastLoggedPercent = currentPercent
                        }
                    }
                    output.flush()
                }
            }

            AppLogger.i(TAG, "Wine Mono downloaded: ${msiFile.absolutePath} (${msiFile.length() / 1024 / 1024}MB)")
            Result.success(msiFile)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to download Wine Mono", e)
            Result.failure(e)
        }
    }

    /**
     * Install Wine Mono MSI to Wine container
     *
     * @param msiFile Downloaded MSI file
     * @param containerDir Wine container directory
     * @param executeCommand Wine msiexec command execution function
     * @return Installation result
     */
    suspend fun installWineMono(
        msiFile: File,
        containerDir: File,
        executeCommand: suspend (executable: String, arguments: List<String>) -> Result<Unit>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Installing Wine Mono to ${containerDir.absolutePath}")

            // msiexec /i wine-mono.msi /qn (silent installation)
            val result = executeCommand(
                "msiexec",
                listOf(
                    "/i",
                    msiFile.absolutePath,
                    "/qn"  // Silent mode
                )
            )

            if (result.isSuccess) {
                AppLogger.i(TAG, "Wine Mono installed successfully")
                Result.success(Unit)
            } else {
                AppLogger.e(TAG, "Wine Mono installation failed: ${result.exceptionOrNull()?.message}")
                result
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to install Wine Mono", e)
            Result.failure(e)
        }
    }
}
