package com.steamdeck.mobile.core.util

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * ELF binary patcher for Android compatibility
 *
 * 背景:
 * Android 5.0+ (Lollipop以降) では、Position Independent Executable (PIE) のみがサポートされています。
 * ELFヘッダーの e_type が ET_EXEC (2) のバイナリは「unexpected e_type」エラーで実行できません。
 * e_type を ET_DYN (3) に変更することで、Android linkerが受け入れるようになります。
 *
 * ELF Header構造 (64-bit):
 * - Offset 0x00: e_ident[16] (Magic number: 0x7F 'E' 'L' 'F')
 * - Offset 0x10: e_type (2 bytes) - 実行可能ファイルの型
 *   - ET_EXEC = 2 (非PIE実行ファイル) - Android 5.0+でサポート終了
 *   - ET_DYN = 3 (共有ライブラリ/PIE実行ファイル) - Android必須
 *
 * 参考:
 * - https://blog.katastros.com/a?ID=00500-0c1d52d4-1789-49a0-a8e8-1a78c1519328
 * - https://yurushao.info/tech/2016/03/14/Android-PIE.html
 * - https://xdaforums.com/t/fix-android-l-bypassing-the-new-pie-security-check.2797731/
 */
object ElfPatcher {

    private const val TAG = "ElfPatcher"

    // ELF Header constants
    private const val ELF_MAGIC = 0x7F454C46 // 0x7F 'E' 'L' 'F'
    private const val E_TYPE_OFFSET = 0x10 // Offset of e_type field (16 bytes from start)
    private const val ET_EXEC: Short = 2 // Non-PIE executable (not supported on Android 5.0+)
    private const val ET_DYN: Short = 3 // Shared object / PIE executable (required)

    /**
     * Little-endian 読み取りヘルパー関数
     */
    private fun readShortLE(raf: RandomAccessFile): Short {
        val b1 = raf.readByte().toInt() and 0xFF
        val b2 = raf.readByte().toInt() and 0xFF
        return (b1 or (b2 shl 8)).toShort()
    }

    private fun readIntLE(raf: RandomAccessFile): Int {
        val b1 = raf.readByte().toInt() and 0xFF
        val b2 = raf.readByte().toInt() and 0xFF
        val b3 = raf.readByte().toInt() and 0xFF
        val b4 = raf.readByte().toInt() and 0xFF
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    private fun readLongLE(raf: RandomAccessFile): Long {
        val b1 = raf.readByte().toLong() and 0xFF
        val b2 = raf.readByte().toLong() and 0xFF
        val b3 = raf.readByte().toLong() and 0xFF
        val b4 = raf.readByte().toLong() and 0xFF
        val b5 = raf.readByte().toLong() and 0xFF
        val b6 = raf.readByte().toLong() and 0xFF
        val b7 = raf.readByte().toLong() and 0xFF
        val b8 = raf.readByte().toLong() and 0xFF
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24) or
                (b5 shl 32) or (b6 shl 40) or (b7 shl 48) or (b8 shl 56)
    }

    /**
     * ELFバイナリをAndroid互換（PIE）に変換
     *
     * バイナリがET_EXEC形式の場合、ET_DYN形式にパッチします。
     * これにより、Android 5.0+のPIE要件を満たします。
     *
     * @param binaryFile パッチするELFバイナリファイル
     * @return Result<Unit> 成功または失敗
     */
    fun patchToPie(binaryFile: File): Result<Unit> {
        if (!binaryFile.exists()) {
            return Result.failure(IllegalArgumentException("Binary file not found: ${binaryFile.absolutePath}"))
        }

        if (!binaryFile.canRead() || !binaryFile.canWrite()) {
            return Result.failure(IllegalStateException("Binary file is not readable/writable: ${binaryFile.absolutePath}"))
        }

        return try {
            RandomAccessFile(binaryFile, "rw").use { raf ->
                // 1. Verify ELF magic number
                if (raf.length() < 20) {
                    return Result.failure(IllegalArgumentException("File too small to be an ELF binary"))
                }

                raf.seek(0)
                val magic = raf.readInt()
                if (magic != ELF_MAGIC) {
                    return Result.failure(IllegalArgumentException("Not a valid ELF file (bad magic): ${binaryFile.name}"))
                }

                // 2. Read e_type field (offset 0x10, 2 bytes, little-endian)
                raf.seek(E_TYPE_OFFSET.toLong())
                // Java's readShort() reads in big-endian, but ELF uses little-endian
                // Read bytes manually and convert
                val byte1 = raf.readByte().toInt() and 0xFF
                val byte2 = raf.readByte().toInt() and 0xFF
                val eType = (byte1 or (byte2 shl 8)).toShort()

                Log.d(TAG, "ELF binary ${binaryFile.name}: e_type = $eType (0x${eType.toString(16)})")

                // 3. Check if patching is needed
                when (eType) {
                    ET_EXEC -> {
                        // Patch: ET_EXEC (2) → ET_DYN (3)
                        Log.i(TAG, "Patching ${binaryFile.name}: ET_EXEC → ET_DYN")

                        raf.seek(E_TYPE_OFFSET.toLong())
                        // Write in little-endian format
                        raf.writeByte(ET_DYN.toInt() and 0xFF)  // Low byte
                        raf.writeByte((ET_DYN.toInt() shr 8) and 0xFF)  // High byte

                        // Verify the patch
                        raf.seek(E_TYPE_OFFSET.toLong())
                        val verifyByte1 = raf.readByte().toInt() and 0xFF
                        val verifyByte2 = raf.readByte().toInt() and 0xFF
                        val newEType = (verifyByte1 or (verifyByte2 shl 8)).toShort()

                        if (newEType == ET_DYN) {
                            Log.i(TAG, "Successfully patched ${binaryFile.name} to PIE (ET_DYN)")
                            Result.success(Unit)
                        } else {
                            Result.failure(IllegalStateException("Patch verification failed: e_type = $newEType"))
                        }
                    }
                    ET_DYN -> {
                        // Already PIE, no patching needed
                        Log.d(TAG, "${binaryFile.name} is already PIE (ET_DYN)")
                        Result.success(Unit)
                    }
                    else -> {
                        Log.w(TAG, "${binaryFile.name} has unknown e_type: $eType")
                        Result.success(Unit) // Don't fail, just warn
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch ${binaryFile.name}", e)
            Result.failure(e)
        }
    }

    /**
     * バイナリがET_EXEC形式かどうかをチェック
     *
     * @param binaryFile チェックするELFバイナリ
     * @return true: ET_EXEC (パッチ必要), false: ET_DYN or その他
     */
    fun needsPiePatch(binaryFile: File): Boolean {
        if (!binaryFile.exists() || !binaryFile.canRead()) {
            return false
        }

        return try {
            RandomAccessFile(binaryFile, "r").use { raf ->
                if (raf.length() < 20) return false

                // Check ELF magic
                raf.seek(0)
                val magic = raf.readInt()
                if (magic != ELF_MAGIC) return false

                // Read e_type
                raf.seek(E_TYPE_OFFSET.toLong())
                val eType = raf.readShort()

                eType == ET_EXEC
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check ${binaryFile.name}", e)
            false
        }
    }

    /**
     * ELFバイナリのインタープリタパスを書き換え
     *
     * Box64などのバイナリに埋め込まれたインタープリタパスを
     * アプリのパッケージディレクトリに対応したパスに書き換えます。
     *
     * @param binaryFile パッチするELFバイナリファイル
     * @param newInterpreterPath 新しいインタープリタパス
     * @return Result<Unit> 成功または失敗
     */
    fun patchInterpreterPath(binaryFile: File, newInterpreterPath: String): Result<Unit> {
        if (!binaryFile.exists()) {
            return Result.failure(IllegalArgumentException("Binary file not found: ${binaryFile.absolutePath}"))
        }

        if (!binaryFile.canRead() || !binaryFile.canWrite()) {
            return Result.failure(IllegalStateException("Binary file is not readable/writable: ${binaryFile.absolutePath}"))
        }

        return try {
            RandomAccessFile(binaryFile, "rw").use { raf ->
                // 1. Verify ELF magic number
                if (raf.length() < 20) {
                    return Result.failure(IllegalArgumentException("File too small to be an ELF binary"))
                }

                raf.seek(0)
                val magic = raf.readInt()
                if (magic != ELF_MAGIC) {
                    return Result.failure(IllegalArgumentException("Not a valid ELF file (bad magic): ${binaryFile.name}"))
                }

                // 2. Read ELF header to find program headers
                // For 64-bit ELF:
                // Offset 0x20: e_phoff (8 bytes) - program header offset
                // Offset 0x36: e_phnum (2 bytes) - number of program headers
                raf.seek(0x20)
                val phoff = readLongLE(raf)
                raf.seek(0x36)
                val phnum = readShortLE(raf)

                Log.d(TAG, "ELF ${binaryFile.name}: phoff=$phoff, phnum=$phnum")

                // 3. Search for PT_INTERP (type=3) program header
                for (i in 0 until phnum) {
                    val phOffset = phoff + (i * 56) // Each program header is 56 bytes in 64-bit ELF
                    raf.seek(phOffset)

                    val pType = readIntLE(raf)
                    if (pType == 3) { // PT_INTERP
                        // Found interpreter header
                        raf.seek(phOffset + 8) // Skip to p_offset
                        val interpOffset = readLongLE(raf)
                        raf.seek(phOffset + 32) // Skip to p_filesz
                        val interpSize = readLongLE(raf)

                        Log.d(TAG, "Found PT_INTERP at offset $interpOffset, size $interpSize")

                        // Read current interpreter path
                        raf.seek(interpOffset)
                        val currentPath = ByteArray(interpSize.toInt())
                        raf.readFully(currentPath)
                        val currentPathStr = String(currentPath).trim('\u0000')

                        Log.i(TAG, "Current interpreter: $currentPathStr")
                        Log.i(TAG, "New interpreter: $newInterpreterPath")

                        // Check if new path fits
                        if (newInterpreterPath.length >= interpSize) {
                            return Result.failure(IllegalArgumentException(
                                "New interpreter path too long: ${newInterpreterPath.length} >= $interpSize"
                            ))
                        }

                        // Write new interpreter path (null-terminated)
                        raf.seek(interpOffset)
                        val newPathBytes = (newInterpreterPath + "\u0000").toByteArray()
                        raf.write(newPathBytes)

                        // Fill remaining space with zeros
                        val remaining = interpSize - newPathBytes.size
                        if (remaining > 0) {
                            raf.write(ByteArray(remaining.toInt()))
                        }

                        // Verify
                        raf.seek(interpOffset)
                        val verifyPath = ByteArray(newPathBytes.size)
                        raf.readFully(verifyPath)
                        val verifyPathStr = String(verifyPath).trim('\u0000')

                        if (verifyPathStr == newInterpreterPath) {
                            Log.i(TAG, "Successfully patched interpreter path in ${binaryFile.name}")
                            return Result.success(Unit)
                        } else {
                            return Result.failure(IllegalStateException("Verification failed: $verifyPathStr != $newInterpreterPath"))
                        }
                    }
                }

                Result.failure(IllegalArgumentException("No PT_INTERP header found in ${binaryFile.name}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch interpreter path in ${binaryFile.name}", e)
            Result.failure(e)
        }
    }

    /**
     * ELFバイナリのTLSセグメントアライメントをAndroid 12+に対応させる
     *
     * Android 12 (API 31+) のBionicリンカーはTLSセグメントのアライメントが
     * 最低64バイト必要です。多くのバイナリは16バイトで、実行時にクラッシュします。
     *
     * @param binaryFile パッチするELFバイナリファイル
     * @param requiredAlignment 必要なアライメント (デフォルト: 64)
     * @return Result<Unit> 成功または失敗
     */
    fun patchTlsAlignment(binaryFile: File, requiredAlignment: Long = 64): Result<Unit> {
        if (!binaryFile.exists()) {
            return Result.failure(IllegalArgumentException("Binary file not found: ${binaryFile.absolutePath}"))
        }

        if (!binaryFile.canRead() || !binaryFile.canWrite()) {
            return Result.failure(IllegalStateException("Binary file is not readable/writable: ${binaryFile.absolutePath}"))
        }

        return try {
            RandomAccessFile(binaryFile, "rw").use { raf ->
                // 1. Verify ELF magic number
                if (raf.length() < 20) {
                    return Result.failure(IllegalArgumentException("File too small to be an ELF binary"))
                }

                raf.seek(0)
                val magic = raf.readInt()
                if (magic != ELF_MAGIC) {
                    return Result.failure(IllegalArgumentException("Not a valid ELF file (bad magic): ${binaryFile.name}"))
                }

                // 2. Read ELF header to find program headers
                // For 64-bit ELF:
                // Offset 0x20: e_phoff (8 bytes) - program header offset
                // Offset 0x36: e_phnum (2 bytes) - number of program headers
                raf.seek(0x20)
                val phoff = readLongLE(raf)
                raf.seek(0x36)
                val phnum = readShortLE(raf)

                Log.d(TAG, "ELF ${binaryFile.name}: phoff=$phoff, phnum=$phnum")

                var patchedCount = 0

                // 3. Search for PT_TLS (type=7) program headers
                for (i in 0 until phnum) {
                    val phOffset = phoff + (i * 56) // Each program header is 56 bytes in 64-bit ELF
                    raf.seek(phOffset)

                    val pType = readIntLE(raf)
                    if (pType == 7) { // PT_TLS
                        // Found TLS header
                        // p_align is at offset 48 from the start of the program header
                        raf.seek(phOffset + 48)
                        val currentAlign = readLongLE(raf)

                        Log.d(TAG, "Found PT_TLS with alignment: $currentAlign (required: $requiredAlignment)")

                        if (currentAlign < requiredAlignment) {
                            // Patch alignment
                            raf.seek(phOffset + 48)
                            // Write in little-endian format
                            raf.writeByte((requiredAlignment and 0xFF).toInt())
                            raf.writeByte(((requiredAlignment shr 8) and 0xFF).toInt())
                            raf.writeByte(((requiredAlignment shr 16) and 0xFF).toInt())
                            raf.writeByte(((requiredAlignment shr 24) and 0xFF).toInt())
                            raf.writeByte(((requiredAlignment shr 32) and 0xFF).toInt())
                            raf.writeByte(((requiredAlignment shr 40) and 0xFF).toInt())
                            raf.writeByte(((requiredAlignment shr 48) and 0xFF).toInt())
                            raf.writeByte(((requiredAlignment shr 56) and 0xFF).toInt())

                            // Verify
                            raf.seek(phOffset + 48)
                            val newAlign = readLongLE(raf)

                            if (newAlign == requiredAlignment) {
                                Log.i(TAG, "Successfully patched PT_TLS alignment: $currentAlign -> $newAlign")
                                patchedCount++
                            } else {
                                return Result.failure(IllegalStateException("TLS alignment patch verification failed: got $newAlign, expected $requiredAlignment"))
                            }
                        } else {
                            Log.d(TAG, "PT_TLS alignment already sufficient: $currentAlign >= $requiredAlignment")
                        }
                    }
                }

                if (patchedCount > 0) {
                    Log.i(TAG, "Patched $patchedCount PT_TLS segment(s) in ${binaryFile.name}")
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch TLS alignment in ${binaryFile.name}", e)
            Result.failure(e)
        }
    }
}
