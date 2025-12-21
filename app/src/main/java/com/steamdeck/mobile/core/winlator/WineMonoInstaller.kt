package com.steamdeck.mobile.core.winlator

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wine Mono installer for providing .NET Framework and Windows system DLLs
 *
 * Wine Mono includes:
 * - .NET Framework implementation
 * - Common Windows system DLLs (OLEAUT32, WINMM, WS2_32, etc.)
 * - Visual C++ runtime libraries
 *
 * Latest stable version: 9.0.0 (compatible with Wine 9.x)
 * Download: https://dl.winehq.org/wine/wine-mono/9.0.0/wine-mono-9.0.0-x86.msi
 */
@Singleton
class WineMonoInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WineMonoInstaller"

        // Wine Mono 9.0.0 - latest stable version (Dec 2023)
        // Using tarball instead of MSI to avoid msiexec crashes on Box64/Wine
        private const val WINE_MONO_VERSION = "9.0.0"
        private const val WINE_MONO_URL = "https://dl.winehq.org/wine/wine-mono/$WINE_MONO_VERSION/wine-mono-$WINE_MONO_VERSION-x86.tar.xz"
        private const val WINE_MONO_FILENAME = "wine-mono-$WINE_MONO_VERSION.tar.xz"

        // Approximate size: ~50MB compressed, ~150MB uncompressed
        private const val EXPECTED_SIZE_MB = 50
    }

    private val monoDir = File(context.filesDir, "winlator/mono")

    /**
     * Download Wine Mono tarball if not already cached
     * Returns the path to the downloaded tar.xz file
     */
    suspend fun downloadWineMono(
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            monoDir.mkdirs()
            val monoMsi = File(monoDir, WINE_MONO_FILENAME)

            // Check if already downloaded
            if (monoMsi.exists() && monoMsi.length() > 1024 * 1024) {
                AppLogger.i(TAG, "Wine Mono already downloaded: ${monoMsi.absolutePath}")
                progressCallback?.invoke(1.0f, "Wine Mono ready")
                return@withContext Result.success(monoMsi)
            }

            AppLogger.i(TAG, "Downloading Wine Mono $WINE_MONO_VERSION from $WINE_MONO_URL")
            progressCallback?.invoke(0.0f, "Downloading Wine Mono...")

            val url = URL(WINE_MONO_URL)
            val connection = url.openConnection()
            connection.connect()

            val fileLength = connection.contentLength
            AppLogger.d(TAG, "Wine Mono size: ${fileLength / 1024 / 1024}MB")

            connection.getInputStream().use { input ->
                FileOutputStream(monoMsi).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (fileLength > 0) {
                            val progress = totalBytesRead.toFloat() / fileLength
                            val mbDownloaded = totalBytesRead / 1024 / 1024
                            val mbTotal = fileLength / 1024 / 1024
                            progressCallback?.invoke(
                                progress,
                                "Downloading Wine Mono: ${mbDownloaded}MB / ${mbTotal}MB"
                            )
                        }
                    }
                }
            }

            AppLogger.i(TAG, "Wine Mono downloaded successfully: ${monoMsi.length() / 1024 / 1024}MB")
            progressCallback?.invoke(1.0f, "Wine Mono downloaded")
            Result.success(monoMsi)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to download Wine Mono", e)
            Result.failure(EmulatorException("Failed to download Wine Mono: ${e.message}", e))
        }
    }

    /**
     * Install Wine Mono into a Wine container by extracting tarball
     * Uses Apache Commons Compress for reliable tar.xz extraction
     * Security: Prevents path traversal attacks by normalizing paths
     */
    suspend fun installWineMonoToContainer(
        container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
        monoTarball: File,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Installing Wine Mono to container: ${container.name}")
            progressCallback?.invoke(0.0f, "Extracting Wine Mono...")

            // Wine Mono should be extracted to C:\windows\mono
            val monoInstallDir = File(container.rootPath, "drive_c/windows/mono")
            monoInstallDir.mkdirs()
            val canonicalDestPath = monoInstallDir.canonicalPath

            AppLogger.d(TAG, "Extracting Wine Mono tarball to: ${monoInstallDir.absolutePath}")

            // Extract tar.xz using Apache Commons Compress
            // XZCompressorInputStream -> TarArchiveInputStream (layered approach)
            FileInputStream(monoTarball).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    XZCompressorInputStream(bis).use { xzIn ->
                        TarArchiveInputStream(xzIn).use { tarIn ->
                            var entry = tarIn.nextTarEntry
                            var filesExtracted = 0

                            while (entry != null) {
                                // Security: Validate entry can be read
                                if (!tarIn.canReadEntryData(entry)) {
                                    AppLogger.w(TAG, "Skipping unreadable entry: ${entry.name}")
                                    entry = tarIn.nextTarEntry
                                    continue
                                }

                                val outputFile = File(monoInstallDir, entry.name)

                                // Security: Prevent path traversal attacks (Zip Slip)
                                val canonicalOutputPath = outputFile.canonicalPath
                                if (!canonicalOutputPath.startsWith(canonicalDestPath)) {
                                    AppLogger.e(TAG, "Path traversal detected: ${entry.name}")
                                    throw EmulatorException("Path traversal attack detected in Wine Mono archive")
                                }

                                if (entry.isDirectory) {
                                    outputFile.mkdirs()
                                } else {
                                    outputFile.parentFile?.mkdirs()
                                    FileOutputStream(outputFile).use { fos ->
                                        tarIn.copyTo(fos)
                                    }
                                    filesExtracted++
                                }

                                entry = tarIn.nextTarEntry
                            }

                            AppLogger.i(TAG, "Wine Mono extracted successfully ($filesExtracted files)")
                            progressCallback?.invoke(0.9f, "Verifying extraction...")
                        }
                    }
                }
            }

            // Verify extraction
            val extractedDir = File(monoInstallDir, "wine-mono-$WINE_MONO_VERSION")
            if (extractedDir.exists() && extractedDir.listFiles()?.isNotEmpty() == true) {
                AppLogger.i(TAG, "Wine Mono installation verified: ${extractedDir.absolutePath}")
                progressCallback?.invoke(1.0f, "Wine Mono installed")
                Result.success(Unit)
            } else {
                AppLogger.e(TAG, "Wine Mono extraction succeeded but files not found at: ${extractedDir.absolutePath}")
                Result.failure(EmulatorException("Wine Mono files not found after extraction"))
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to install Wine Mono", e)
            Result.failure(EmulatorException("Failed to install Wine Mono: ${e.message}", e))
        }
    }

    /**
     * Check if Wine Mono is installed in a container
     */
    suspend fun isWineMonoInstalled(container: com.steamdeck.mobile.domain.emulator.EmulatorContainer): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check for Wine Mono installation directory (C:\windows\mono\wine-mono-9.0.0)
            val monoInstallDir = File(container.rootPath, "drive_c/windows/mono/wine-mono-$WINE_MONO_VERSION")
            val installed = monoInstallDir.exists() && monoInstallDir.listFiles()?.isNotEmpty() == true
            AppLogger.d(TAG, "Wine Mono installed in ${container.name}: $installed (path: ${monoInstallDir.absolutePath})")
            installed
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to check Wine Mono installation", e)
            false
        }
    }
}
