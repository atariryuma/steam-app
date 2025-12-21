package com.steamdeck.mobile.core.steam

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * NSIS (Nullsoft Install System) file format parser
 *
 * Based on NSIS v3.x file format specification:
 * https://github.com/tonytheodore/nsis/blob/master/Source/exehead/fileform.h
 *
 * NSIS file structure:
 * 1. PE/EXE stub (~34KB) - Windows executable header
 * 2. firstheader - NSIS header with signature 0xDEADBEEF
 * 3. Compressed data blocks (LZMA/BZIP2/Deflate)
 * 4. File data in NB_DATA block
 */
class NsisParser(private val nsisFile: File) {

    companion object {
        private const val TAG = "NsisParser"

        // NSIS signatures
        private const val FH_SIG = 0xDEADBEEF.toInt() // firstheader signature
        private const val FH_INT_SIZE = 4 // Integer size in bytes

        // NSIS compression types
        private const val FH_FLAGS_MASK = 0x0F
        private const val FH_FLAGS_NO_CRC = 0x80

        // Compression methods
        private const val COMPRESS_NONE = 0
        private const val COMPRESS_DEFLATE = 1
        private const val COMPRESS_BZIP2 = 2
        private const val COMPRESS_LZMA = 3

        // Maximum header search size (NSIS stub is typically 34-36KB)
        private const val MAX_HEADER_SEARCH = 512 * 1024 // 512KB max search
    }

    /**
     * NSIS firstheader structure
     */
    data class NsisHeader(
        val signature: Int,
        val compressionType: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val headerOffset: Long,
        val dataOffset: Long,
        val hasCrc: Boolean
    ) {
        val isValid: Boolean
            get() = signature == FH_SIG

        val compressionName: String
            get() = when (compressionType) {
                COMPRESS_NONE -> "None"
                COMPRESS_DEFLATE -> "Deflate"
                COMPRESS_BZIP2 -> "BZIP2"
                COMPRESS_LZMA -> "LZMA"
                else -> "Unknown ($compressionType)"
            }
    }

    /**
     * Parse NSIS firstheader from the installer file
     *
     * @return NsisHeader containing compression info and data offsets
     * @throws Exception if NSIS header not found or invalid
     */
    fun parseHeader(): NsisHeader {
        if (!nsisFile.exists()) {
            throw IllegalArgumentException("NSIS file not found: ${nsisFile.absolutePath}")
        }

        Log.i(TAG, "Parsing NSIS header from: ${nsisFile.absolutePath}")
        Log.i(TAG, "File size: ${nsisFile.length()} bytes")

        RandomAccessFile(nsisFile, "r").use { raf ->
            // Search for firstheader signature
            val headerOffset = findFirstHeaderOffset(raf)

            if (headerOffset < 0) {
                throw IllegalStateException("NSIS firstheader signature not found (expected 0xDEADBEEF)")
            }

            Log.i(TAG, "Found NSIS firstheader at offset: $headerOffset")

            // Parse firstheader structure
            return parseFirstHeaderStruct(raf, headerOffset)
        }
    }

    /**
     * Search for NSIS firstheader signature (0xDEADBEEF)
     *
     * @return Offset of firstheader, or -1 if not found
     */
    private fun findFirstHeaderOffset(raf: RandomAccessFile): Long {
        val searchSize = minOf(MAX_HEADER_SEARCH.toLong(), raf.length())
        val buffer = ByteArray(4096)
        var offset = 0L

        while (offset < searchSize) {
            val bytesRead = raf.read(buffer)
            if (bytesRead < 4) break

            // Search for FH_SIG in buffer
            for (i in 0 until bytesRead - 3) {
                val sig = ByteBuffer.wrap(buffer, i, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int

                if (sig == FH_SIG) {
                    return offset + i
                }
            }

            offset += bytesRead
        }

        return -1 // Not found
    }

    /**
     * Parse NSIS firstheader structure
     *
     * Based on firstheader struct from fileform.h:
     * - INT32 flags (compression type + options)
     * - INT32 siginfo (signature info)
     * - INT32 nsinst[] (variable-length data)
     */
    private fun parseFirstHeaderStruct(raf: RandomAccessFile, offset: Long): NsisHeader {
        raf.seek(offset)

        // Read signature (should be 0xDEADBEEF)
        val signature = readInt32LE(raf)

        if (signature != FH_SIG) {
            throw IllegalStateException("Invalid NSIS signature: 0x${signature.toString(16)}")
        }

        // Read flags (contains compression type)
        val flags = readInt32LE(raf)
        val compressionType = flags and FH_FLAGS_MASK
        val hasCrc = (flags and FH_FLAGS_NO_CRC) == 0

        Log.i(TAG, "NSIS signature: 0x${signature.toString(16)}")
        Log.i(TAG, "Flags: 0x${flags.toString(16)}")
        Log.i(TAG, "Compression type: $compressionType")
        Log.i(TAG, "Has CRC: $hasCrc")

        // Skip siginfo (4 bytes)
        raf.skipBytes(4)

        // Read nsinst array (contains offsets and sizes)
        // nsinst[0] = length of all the data (compressed)
        // nsinst[1] = length of all the data (uncompressed)
        val compressedSize = readInt32LE(raf).toLong() and 0xFFFFFFFFL
        val uncompressedSize = readInt32LE(raf).toLong() and 0xFFFFFFFFL

        Log.i(TAG, "Compressed size: $compressedSize bytes")
        Log.i(TAG, "Uncompressed size: $uncompressedSize bytes")

        // Data offset is after firstheader structure
        // firstheader is approximately 28 bytes + variable data
        val headerEndOffset = raf.filePointer
        val dataOffset = headerEndOffset

        Log.i(TAG, "Data offset: $dataOffset")

        return NsisHeader(
            signature = signature,
            compressionType = compressionType,
            compressedSize = compressedSize,
            uncompressedSize = uncompressedSize,
            headerOffset = offset,
            dataOffset = dataOffset,
            hasCrc = hasCrc
        )
    }

    /**
     * Read 32-bit little-endian integer from RandomAccessFile
     */
    private fun readInt32LE(raf: RandomAccessFile): Int {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
    }

    /**
     * Read compressed LZMA data block from NSIS file
     *
     * @param header Parsed NSIS header
     * @return ByteArray containing compressed LZMA data
     */
    fun readCompressedData(header: NsisHeader): ByteArray {
        if (header.compressionType != COMPRESS_LZMA) {
            throw UnsupportedOperationException(
                "Only LZMA compression is supported (found: ${header.compressionName})"
            )
        }

        RandomAccessFile(nsisFile, "r").use { raf ->
            raf.seek(header.dataOffset)

            val compressedData = ByteArray(header.compressedSize.toInt())
            raf.readFully(compressedData)

            Log.i(TAG, "Read ${compressedData.size} bytes of compressed LZMA data")
            return compressedData
        }
    }
}
