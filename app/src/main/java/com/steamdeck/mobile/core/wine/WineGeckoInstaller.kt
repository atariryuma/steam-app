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
 * Wine Gecko Auto-Installer
 *
 * Installs HTML/Web rendering compatibility layer (MSHTML replacement) to Wine containers,
 * enabling web-based UI components in Windows applications (e.g., Steam login dialogs)
 */
@Singleton
class WineGeckoInstaller @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WineGeckoInstaller"

        // Wine Gecko latest version (2024 release for Wine 9.x)
        // See: https://wiki.winehq.org/Gecko
        private const val WINE_GECKO_VERSION = "2.47.4"
        private const val WINE_GECKO_URL = "https://dl.winehq.org/wine/wine-gecko/$WINE_GECKO_VERSION/wine-gecko-$WINE_GECKO_VERSION-x86_64.msi"
        private const val WINE_GECKO_FILENAME = "wine-gecko-$WINE_GECKO_VERSION-x86_64.msi"

        // Expected file size (approximately 60MB)
        private const val EXPECTED_SIZE_MB = 60
    }

    /**
     * Download Wine Gecko MSI
     *
     * @return Downloaded MSI file
     */
    suspend fun downloadWineGecko(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(context.cacheDir, "wine_gecko")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val msiFile = File(downloadDir, WINE_GECKO_FILENAME)

            // Reuse existing file if it exists and size is valid
            if (msiFile.exists() && msiFile.length() > EXPECTED_SIZE_MB * 1024 * 1024 * 0.9) {
                AppLogger.i(TAG, "Using cached Wine Gecko: ${msiFile.absolutePath}")
                return@withContext Result.success(msiFile)
            }

            AppLogger.i(TAG, "Downloading Wine Gecko $WINE_GECKO_VERSION from $WINE_GECKO_URL")

            val request = okhttp3.Request.Builder()
                .url(WINE_GECKO_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to download Wine Gecko: HTTP ${response.code}")
                )
            }

            val responseBody = response.body
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val contentLength = responseBody.contentLength()
            var bytesDownloaded = 0L

            // PERFORMANCE: Use 128KB buffer for 60MB file download
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
                            AppLogger.d(TAG, "Wine Gecko download: $currentPercent% (${bytesDownloaded / 1024 / 1024}MB)")
                            lastLoggedPercent = currentPercent
                        }
                    }
                    output.flush()
                }
            }

            AppLogger.i(TAG, "Wine Gecko downloaded: ${msiFile.absolutePath} (${msiFile.length() / 1024 / 1024}MB)")
            Result.success(msiFile)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to download Wine Gecko", e)
            Result.failure(e)
        }
    }

    /**
     * Install Wine Gecko MSI to Wine container
     *
     * @param msiFile Downloaded MSI file
     * @param containerDir Wine container directory
     * @param executeCommand Wine msiexec command execution function
     * @return Installation result
     */
    suspend fun installWineGecko(
        msiFile: File,
        containerDir: File,
        executeCommand: suspend (executable: String, arguments: List<String>) -> Result<Unit>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Installing Wine Gecko to ${containerDir.absolutePath}")

            // msiexec /i wine-gecko.msi /qn (silent installation)
            val result = executeCommand(
                "msiexec",
                listOf(
                    "/i",
                    msiFile.absolutePath,
                    "/qn"  // Silent mode
                )
            )

            if (result.isSuccess) {
                AppLogger.i(TAG, "Wine Gecko installed successfully")
                Result.success(Unit)
            } else {
                AppLogger.e(TAG, "Wine Gecko installation failed: ${result.exceptionOrNull()?.message}")
                result
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to install Wine Gecko", e)
            Result.failure(e)
        }
    }
}
