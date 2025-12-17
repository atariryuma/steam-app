package com.steamdeck.mobile.core.winlator

import android.util.Log
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for decompressing .tzst (tar + zstd) files.
 *
 * Uses zstd-jni library for Zstandard decompression.
 * Based on best practices from:
 * - https://github.com/luben/zstd-jni
 * - https://github.com/square/zstd-kmp
 *
 * Reference: https://github.com/luben/zstd-jni
 */
@Singleton
class ZstdDecompressor @Inject constructor() {

    companion object {
        private const val TAG = "ZstdDecompressor"
        private const val BUFFER_SIZE = 8192 // 8KB buffer
    }

    /**
     * Decompresses a .tzst file to a .tar file.
     *
     * @param inputFile .tzst compressed file
     * @param outputFile Output .tar file
     * @param progressCallback Optional progress callback (0.0 to 1.0)
     * @return Result indicating success or failure
     */
    suspend fun decompress(
        inputFile: File,
        outputFile: File,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!inputFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Input file not found: ${inputFile.absolutePath}")
                )
            }

            val fileSize = inputFile.length()
            Log.i(TAG, "Decompressing ${inputFile.name} (${fileSize / 1024}KB)...")

            outputFile.parentFile?.mkdirs()

            var bytesRead = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            FileInputStream(inputFile).use { fileInput ->
                ZstdInputStream(fileInput).use { zstdInput ->
                    FileOutputStream(outputFile).use { output ->
                        var len: Int
                        while (zstdInput.read(buffer).also { len = it } > 0) {
                            output.write(buffer, 0, len)
                            bytesRead += len

                            // Report progress based on input file size
                            // Note: This is approximate since compressed size != decompressed size
                            val progress = (fileInput.channel.position().toFloat() / fileSize)
                            progressCallback?.invoke(progress.coerceIn(0f, 1f))
                        }
                    }
                }
            }

            Log.i(TAG, "Decompression complete: ${outputFile.name} (${outputFile.length() / 1024}KB)")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Decompression failed", e)
            if (outputFile.exists()) {
                outputFile.delete() // Cleanup partial file
            }
            Result.failure(e)
        }
    }

    /**
     * Decompresses and extracts a .tzst archive in one step.
     *
     * @param tzstFile .tzst compressed archive
     * @param targetDir Directory to extract to
     * @param progressCallback Optional progress callback (0.0 to 1.0)
     * @return Result with extracted directory
     */
    suspend fun decompressAndExtract(
        tzstFile: File,
        targetDir: File,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Decompress .tzst to .tar (50% of progress)
            progressCallback?.invoke(0.0f, "Decompressing ${tzstFile.name}...")

            val tarFile = File(tzstFile.parentFile, tzstFile.nameWithoutExtension)

            decompress(tzstFile, tarFile) { decompressProgress ->
                progressCallback?.invoke(decompressProgress * 0.5f, "Decompressing...")
            }.getOrElse { return@withContext Result.failure(it) }

            // Step 2: Extract .tar archive (50% of progress)
            progressCallback?.invoke(0.5f, "Extracting tar archive...")

            val extractResult = extractTar(tarFile, targetDir) { extractProgress ->
                progressCallback?.invoke(0.5f + extractProgress * 0.5f, "Extracting...")
            }

            // Cleanup temporary .tar file
            if (tarFile.exists()) {
                tarFile.delete()
                Log.d(TAG, "Cleaned up temporary tar file: ${tarFile.name}")
            }

            progressCallback?.invoke(1.0f, "Extraction complete")
            extractResult
        } catch (e: Exception) {
            Log.e(TAG, "Decompress and extract failed", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts a .tar archive using Apache Commons Compress.
     *
     * Preserves Unix file permissions including executable bits.
     * Based on best practices from Apache Commons Compress documentation.
     *
     * @param tarFile .tar archive
     * @param targetDir Target directory
     * @param progressCallback Optional progress callback
     * @return Result with target directory
     */
    private suspend fun extractTar(
        tarFile: File,
        targetDir: File,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetDir.mkdirs()

            val tarFileSize = tarFile.length()
            var bytesProcessed = 0L

            Log.i(TAG, "Extracting tar archive: ${tarFile.name} (${tarFileSize / 1024}KB)")

            BufferedInputStream(FileInputStream(tarFile)).use { bufferedInput ->
                TarArchiveInputStream(bufferedInput).use { tarInput ->
                    var entry: TarArchiveEntry? = tarInput.nextEntry as TarArchiveEntry?

                    while (entry != null) {
                        val outputFile = File(targetDir, entry.name)

                        // Security: Prevent path traversal attacks
                        if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                            Log.w(TAG, "Skipping suspicious entry: ${entry.name}")
                            entry = tarInput.nextEntry as TarArchiveEntry?
                            continue
                        }

                        if (entry.isDirectory) {
                            // Create directory
                            outputFile.mkdirs()
                            Log.d(TAG, "Created directory: ${entry.name}")
                        } else {
                            // Extract file
                            outputFile.parentFile?.mkdirs()

                            FileOutputStream(outputFile).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var len: Int
                                while (tarInput.read(buffer).also { len = it } > 0) {
                                    output.write(buffer, 0, len)
                                    bytesProcessed += len
                                }
                            }

                            // Set executable permissions if present
                            // Unix mode: 0755 (rwxr-xr-x) or 0777 (rwxrwxrwx) indicates executable
                            val mode = entry.mode
                            val isExecutable = (mode and 0x49) != 0 // Check owner/group/other execute bits

                            if (isExecutable) {
                                outputFile.setExecutable(true, false)
                                Log.d(TAG, "Set executable: ${entry.name} (mode: ${mode.toString(8)})")
                            }

                            Log.d(TAG, "Extracted: ${entry.name} (${entry.size} bytes)")
                        }

                        // Report progress
                        val progress = (bytesProcessed.toFloat() / tarFileSize).coerceIn(0f, 1f)
                        progressCallback?.invoke(progress)

                        entry = tarInput.nextEntry as TarArchiveEntry?
                    }
                }
            }

            Log.i(TAG, "Tar extraction complete: ${targetDir.absolutePath}")
            progressCallback?.invoke(1.0f)
            Result.success(targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Tar extraction failed", e)
            Result.failure(e)
        }
    }

    /**
     * Gets the decompressed size estimate.
     *
     * @param tzstFile .tzst file
     * @return Estimated decompressed size in bytes, or null if cannot determine
     */
    fun getDecompressedSize(tzstFile: File): Long? {
        return try {
            FileInputStream(tzstFile).use { fileInput ->
                ZstdInputStream(fileInput).use { zstdInput ->
                    // Note: This reads the entire stream to calculate size
                    // Not efficient for large files, but works for size estimation
                    var size = 0L
                    val buffer = ByteArray(BUFFER_SIZE)
                    var len: Int
                    while (zstdInput.read(buffer).also { len = it } > 0) {
                        size += len
                    }
                    size
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get decompressed size", e)
            null
        }
    }

    /**
     * Extracts a .txz (tar + xz) archive.
     *
     * Winlator's rootfs uses XZ compression instead of Zstandard.
     *
     * @param txzFile .txz compressed archive
     * @param targetDir Directory to extract to
     * @param progressCallback Optional progress callback (0.0 to 1.0, status message)
     * @return Result with extracted directory
     */
    suspend fun extractTxz(
        txzFile: File,
        targetDir: File,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!txzFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Input file not found: ${txzFile.absolutePath}")
                )
            }

            targetDir.mkdirs()

            val txzFileSize = txzFile.length()
            var bytesProcessed = 0L

            Log.i(TAG, "Extracting txz archive: ${txzFile.name} (${txzFileSize / 1024 / 1024}MB)")
            progressCallback?.invoke(0.0f, "Extracting ${txzFile.name}...")

            BufferedInputStream(FileInputStream(txzFile)).use { bufferedInput ->
                XZCompressorInputStream(bufferedInput).use { xzInput ->
                    TarArchiveInputStream(xzInput).use { tarInput ->
                        var entry: TarArchiveEntry? = tarInput.nextEntry as TarArchiveEntry?

                        while (entry != null) {
                            val outputFile = File(targetDir, entry.name)

                            // Security: Prevent path traversal attacks
                            if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                                Log.w(TAG, "Skipping suspicious entry: ${entry.name}")
                                entry = tarInput.nextEntry as TarArchiveEntry?
                                continue
                            }

                            if (entry.isDirectory) {
                                // Create directory
                                outputFile.mkdirs()
                                Log.d(TAG, "Created directory: ${entry.name}")
                            } else if (entry.isSymbolicLink) {
                                // Skip symlinks on Windows (not supported)
                                Log.d(TAG, "Skipping symlink: ${entry.name} -> ${entry.linkName}")
                            } else {
                                // Extract file
                                outputFile.parentFile?.mkdirs()

                                FileOutputStream(outputFile).use { output ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var len: Int
                                    while (tarInput.read(buffer).also { len = it } > 0) {
                                        output.write(buffer, 0, len)
                                        bytesProcessed += len
                                    }
                                }

                                // Set executable permissions if present
                                val mode = entry.mode
                                val isExecutable = (mode and 0x49) != 0 // Check execute bits

                                if (isExecutable) {
                                    outputFile.setExecutable(true, false)
                                    Log.d(TAG, "Set executable: ${entry.name} (mode: ${mode.toString(8)})")
                                }

                                Log.d(TAG, "Extracted: ${entry.name} (${entry.size} bytes)")
                            }

                            // Report progress
                            val progress = (bytesProcessed.toFloat() / (txzFileSize * 5)).coerceIn(0f, 1f)
                            progressCallback?.invoke(progress, "Extracting...")

                            entry = tarInput.nextEntry as TarArchiveEntry?
                        }
                    }
                }
            }

            Log.i(TAG, "Txz extraction complete: ${targetDir.absolutePath}")
            progressCallback?.invoke(1.0f, "Extraction complete")
            Result.success(targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Txz extraction failed", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a file is a valid zstd compressed file.
     *
     * @param file File to check
     * @return true if valid zstd file
     */
    fun isValidZstd(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                ZstdInputStream(input).use {
                    // If we can create stream without exception, it's valid
                    true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not a valid zstd file: ${file.name}")
            false
        }
    }
}
