package com.steamdeck.mobile.core.steam

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * NSIS extractor using XZ-Java for LZMA decompression
 *
 * This class extracts files from NSIS installers by:
 * 1. Parsing NSIS header with NsisParser
 * 2. Reading compressed LZMA data block
 * 3. Decompressing using XZ-Java's LZMAInputStream
 * 4. Extracting files to target directory
 *
 * Pure Java implementation - no native libraries required (ARM64 compatible)
 */
class NsisExtractor(private val nsisFile: File) {

    companion object {
        private const val TAG = "NsisExtractor"
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Extract Steam client files from NSIS installer
     *
     * @param targetDir Target directory for extracted files
     * @return Result indicating success or failure with extracted file count
     */
    suspend fun extractSteamFiles(targetDir: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // 1. Parse NSIS header
            Log.i(TAG, "Parsing NSIS header...")
            val parser = NsisParser(nsisFile)
            val header = parser.parseHeader()

            if (!header.isValid) {
                return@withContext Result.failure(
                    IllegalStateException("Invalid NSIS header")
                )
            }

            Log.i(TAG, "NSIS header parsed successfully")
            Log.i(TAG, "Compression: ${header.compressionName}")
            Log.i(TAG, "Compressed size: ${header.compressedSize} bytes")
            Log.i(TAG, "Uncompressed size: ${header.uncompressedSize} bytes")

            // 2. Read compressed LZMA data
            Log.i(TAG, "Reading compressed LZMA data...")
            val compressedData = parser.readCompressedData(header)

            // 3. Decompress LZMA stream
            Log.i(TAG, "Decompressing LZMA stream to: ${targetDir.absolutePath}")
            targetDir.mkdirs()

            val extractedFiles = decompressLzmaStream(
                compressedData = compressedData,
                outputDir = targetDir,
                expectedSize = header.uncompressedSize
            )

            Log.i(TAG, "Extraction complete: $extractedFiles files extracted")
            Result.success(extractedFiles)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract NSIS installer", e)
            Result.failure(e)
        }
    }

    /**
     * Decompress LZMA stream and extract files
     *
     * NSIS stores files as a continuous LZMA stream. The decompressed data
     * contains file entries with headers followed by file data.
     *
     * For SteamSetup.exe, we extract all files to the output directory.
     */
    private fun decompressLzmaStream(
        compressedData: ByteArray,
        outputDir: File,
        expectedSize: Long
    ): Int {
        var extractedFiles = 0

        ByteArrayInputStream(compressedData).use { inputStream ->
            LZMAInputStream(inputStream).use { lzmaStream ->
                // For SteamSetup.exe, the LZMA stream contains the installer data
                // We need to extract the embedded files

                // Strategy: Extract the entire decompressed stream to a single file
                // and then parse it to find individual files
                //
                // For now, we'll extract the main Steam files we know exist:
                // - steam.exe
                // - steamclient.dll
                // - etc.

                val decompressedData = lzmaStream.readBytes()
                Log.i(TAG, "Decompressed ${decompressedData.size} bytes (expected: $expectedSize)")

                // Parse the decompressed data to extract individual files
                extractedFiles = parseAndExtractFiles(decompressedData, outputDir)
            }
        }

        return extractedFiles
    }

    /**
     * Parse decompressed NSIS data and extract individual files
     *
     * NSIS stores files in the decompressed stream with a specific structure.
     * For SteamSetup.exe, we need to locate and extract Steam.exe and related files.
     *
     * Note: This is a simplified implementation that extracts the main executable.
     * A full NSIS parser would handle the complete instruction set.
     */
    private fun parseAndExtractFiles(data: ByteArray, outputDir: File): Int {
        var extractedCount = 0

        // For SteamSetup.exe, we know the structure contains Steam.exe
        // Search for PE executable signature (MZ header)
        val peSignature = byteArrayOf(0x4D, 0x5A) // "MZ"

        var offset = 0
        while (offset < data.size - 2) {
            if (data[offset] == peSignature[0] && data[offset + 1] == peSignature[1]) {
                // Found potential PE executable
                Log.i(TAG, "Found PE signature at offset: $offset")

                // Try to extract this as an executable file
                val extracted = tryExtractExecutable(data, offset, outputDir)
                if (extracted) {
                    extractedCount++
                }
            }
            offset++
        }

        // If no files were found using PE signature search,
        // save the entire decompressed stream as a fallback
        if (extractedCount == 0) {
            Log.w(TAG, "No PE executables found, saving entire stream as steam_data.bin")
            val fallbackFile = File(outputDir, "steam_data.bin")
            FileOutputStream(fallbackFile).use { it.write(data) }
            extractedCount = 1
        }

        return extractedCount
    }

    /**
     * Try to extract a PE executable from the data stream
     *
     * @param data Full decompressed data
     * @param offset Offset where PE signature was found
     * @param outputDir Output directory
     * @return true if successfully extracted
     */
    private fun tryExtractExecutable(data: ByteArray, offset: Int, outputDir: File): Boolean {
        try {
            // Read PE header to determine file size
            // This is a simplified approach - we'll extract a reasonable chunk

            // For Steam.exe, typical size is around 3-5 MB
            // We'll extract up to 10MB or until we find another PE signature
            val maxSize = 10 * 1024 * 1024 // 10 MB
            var endOffset = minOf(offset + maxSize, data.size)

            // Search for next PE signature to determine actual file end
            for (i in (offset + 1024) until endOffset step 512) {
                if (i + 1 < data.size &&
                    data[i] == 0x4D.toByte() &&
                    data[i + 1] == 0x5A.toByte()) {
                    endOffset = i
                    break
                }
            }

            val fileSize = endOffset - offset
            if (fileSize < 1024) {
                // Too small to be a valid executable
                return false
            }

            Log.i(TAG, "Extracting executable: size=$fileSize bytes")

            // Determine filename based on offset
            val filename = when {
                offset < 100000 -> "steam.exe" // First executable is usually steam.exe
                else -> "file_$offset.exe"
            }

            val outputFile = File(outputDir, filename)
            FileOutputStream(outputFile).use { out ->
                out.write(data, offset, fileSize)
            }

            Log.i(TAG, "Extracted: ${outputFile.name} (${outputFile.length()} bytes)")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract executable at offset $offset", e)
            return false
        }
    }
}
