package com.steamdeck.mobile.core.winlator

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.domain.emulator.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Winlator implementation of WindowsEmulator interface.
 *
 * Winlator architecture:
 * - Wine 9.0+ for Windows API translation
 * - Box64 0.3.6 for x86_64 → ARM64 binary translation
 * - DXVK 2.4.1 for DirectX → Vulkan
 * - Linux rootfs (chroot environment)
 *
 * @see WindowsEmulator
 */
@Singleton
class WinlatorEmulator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zstdDecompressor: ZstdDecompressor,
    private val processMonitor: ProcessMonitor
) : WindowsEmulator {

    override val name: String = "Winlator"
    override val version: String = "10.1.0"

    private val dataDir = File(context.filesDir, "winlator")
    private val box64Dir = File(dataDir, "box64")
    private val rootfsDir = File(dataDir, "rootfs")
    private val wineDir = File(rootfsDir, "opt/wine")
    private val containersDir = File(dataDir, "containers")

    // Active processes map (processId -> ProcessInfo)
    private val activeProcesses = mutableMapOf<String, ProcessInfo>()

    private data class ProcessInfo(
        val process: Process,
        val startTime: Long
    )

    companion object {
        private const val TAG = "WinlatorEmulator"

        // Box64 assets
        private const val BOX64_ASSET = "winlator/box64-0.3.6.tzst"
        private const val BOX64_RC_ASSET = "winlator/default.box64rc"
        private const val ENV_VARS_ASSET = "winlator/env_vars.json"

        // Rootfs assets (contains Wine 9.0+)
        private const val ROOTFS_ASSET = "winlator/rootfs.txz"
    }

    override suspend fun isAvailable(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Check if Box64 binary exists
            val box64Binary = File(box64Dir, "box64")
            val wineBinary = File(wineDir, "bin/wine")
            val isExtracted = box64Binary.exists() && wineBinary.exists()

            // Check if assets are available
            val hasAssets = try {
                context.assets.open(BOX64_ASSET).use { true }
            } catch (e: Exception) {
                false
            }

            val hasRootfs = try {
                context.assets.open(ROOTFS_ASSET).use { true }
            } catch (e: Exception) {
                false
            }

            Result.success(isExtracted || (hasAssets && hasRootfs))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking availability", e)
            Result.failure(EmulatorException("Failed to check Winlator availability", e))
        }
    }

    override suspend fun initialize(
        progressCallback: ((Float, String) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing Winlator emulator...")
            progressCallback?.invoke(0.0f, "Initializing Winlator...")

            // Create directories
            dataDir.mkdirs()
            box64Dir.mkdirs()
            rootfsDir.mkdirs()
            containersDir.mkdirs()

            // Step 1: Extract Box64 (0.0 - 0.3)
            progressCallback?.invoke(0.0f, "Extracting Box64 binary...")

            val box64Binary = File(box64Dir, "box64")
            if (!box64Binary.exists()) {
                // Extract Box64 assets
                extractAsset(BOX64_ASSET, File(box64Dir, "box64-0.3.6.tzst"))
                extractAsset(BOX64_RC_ASSET, File(box64Dir, "default.box64rc"))
                extractAsset(ENV_VARS_ASSET, File(box64Dir, "env_vars.json"))

                progressCallback?.invoke(0.1f, "Decompressing Box64 binary...")

                // Decompress and extract Box64 .tzst archive
                val box64TzstFile = File(box64Dir, "box64-0.3.6.tzst")

                if (box64TzstFile.exists()) {
                    zstdDecompressor.decompressAndExtract(
                        tzstFile = box64TzstFile,
                        targetDir = box64Dir
                    ) { extractProgress, status ->
                        progressCallback?.invoke(0.1f + extractProgress * 0.2f, status)
                    }.onSuccess {
                        Log.i(TAG, "Box64 extraction successful")

                        // Verify box64 binary exists and is executable
                        if (box64Binary.exists()) {
                            box64Binary.setExecutable(true, false)
                            Log.i(TAG, "Box64 binary ready: ${box64Binary.absolutePath}")
                        } else {
                            Log.w(TAG, "Box64 binary not found after extraction")
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Box64 extraction failed: ${error.message}")
                    }
                }
            }

            progressCallback?.invoke(0.3f, "Box64 ready")

            // Step 2: Extract Rootfs/Wine (0.3 - 1.0)
            val wineBinary = File(wineDir, "bin/wine")
            if (!wineBinary.exists()) {
                progressCallback?.invoke(0.3f, "Extracting Wine rootfs (53MB)...")

                // Extract rootfs.txz from assets
                val rootfsTxzFile = File(dataDir, "rootfs.txz")
                if (!rootfsTxzFile.exists()) {
                    extractAsset(ROOTFS_ASSET, rootfsTxzFile)
                }

                progressCallback?.invoke(0.4f, "Decompressing Wine rootfs (this may take 2-3 minutes)...")

                // Extract .txz archive
                if (rootfsTxzFile.exists()) {
                    zstdDecompressor.extractTxz(
                        txzFile = rootfsTxzFile,
                        targetDir = rootfsDir
                    ) { extractProgress, status ->
                        // 0.4 to 1.0 = 60% of total progress
                        progressCallback?.invoke(0.4f + extractProgress * 0.6f, status)
                    }.onSuccess {
                        Log.i(TAG, "Rootfs extraction successful")

                        // Verify Wine binary exists
                        if (wineBinary.exists()) {
                            wineBinary.setExecutable(true, false)
                            Log.i(TAG, "Wine binary ready: ${wineBinary.absolutePath}")

                            // Set wineserver executable
                            val wineserver = File(wineDir, "bin/wineserver")
                            if (wineserver.exists()) {
                                wineserver.setExecutable(true, false)
                            }
                        } else {
                            Log.w(TAG, "Wine binary not found after extraction at: ${wineBinary.absolutePath}")
                        }

                        // Delete temporary .txz file to save space
                        if (rootfsTxzFile.exists()) {
                            rootfsTxzFile.delete()
                            Log.d(TAG, "Cleaned up temporary rootfs.txz file")
                        }
                    }.onFailure { error ->
                        Log.e(TAG, "Rootfs extraction failed: ${error.message}", error)
                        return@withContext Result.failure(
                            EmulatorException("Failed to extract Wine rootfs: ${error.message}", error)
                        )
                    }
                }
            } else {
                Log.i(TAG, "Wine already extracted, skipping")
                progressCallback?.invoke(1.0f, "Wine already ready")
            }

            progressCallback?.invoke(1.0f, "Initialization complete")

            Log.i(TAG, "Winlator initialization complete")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            Result.failure(EmulatorException("Failed to initialize Winlator: ${e.message}", e))
        }
    }

    override suspend fun createContainer(
        config: EmulatorContainerConfig
    ): Result<EmulatorContainer> = withContext(Dispatchers.IO) {
        try {
            val containerId = "${System.currentTimeMillis()}"
            val containerDir = File(containersDir, containerId)

            // Create container directory structure
            val driveC = File(containerDir, "drive_c")
            File(driveC, "windows").mkdirs()
            File(driveC, "Program Files").mkdirs()
            File(driveC, "Program Files (x86)").mkdirs()
            File(driveC, "users/Public").mkdirs()

            // Initialize Wine prefix with wineboot
            val initResult = initializeWinePrefix(containerDir)
            if (initResult.isFailure) {
                Log.w(TAG, "Wine prefix initialization failed: ${initResult.exceptionOrNull()?.message}")
                // Continue anyway - some games might work without full prefix initialization
            }

            val container = EmulatorContainer(
                id = containerId,
                name = config.name,
                config = config,
                rootPath = containerDir,
                createdAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis(),
                sizeBytes = calculateDirectorySize(containerDir)
            )

            Log.i(TAG, "Created container: ${container.name} (${container.id})")
            Result.success(container)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create container", e)
            Result.failure(EmulatorException("Failed to create container: ${e.message}", e))
        }
    }

    override suspend fun listContainers(): Result<List<EmulatorContainer>> = withContext(Dispatchers.IO) {
        try {
            if (!containersDir.exists()) {
                return@withContext Result.success(emptyList())
            }

            val containers = containersDir.listFiles()?.mapNotNull { dir ->
                if (!dir.isDirectory) return@mapNotNull null

                try {
                    EmulatorContainer(
                        id = dir.name,
                        name = dir.name, // TODO: Load from metadata file
                        config = EmulatorContainerConfig(name = dir.name), // TODO: Load actual config
                        rootPath = dir,
                        createdAt = dir.lastModified(),
                        lastUsedAt = dir.lastModified(),
                        sizeBytes = calculateDirectorySize(dir)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load container ${dir.name}", e)
                    null
                }
            } ?: emptyList()

            Result.success(containers)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list containers", e)
            Result.failure(EmulatorException("Failed to list containers", e))
        }
    }

    override suspend fun deleteContainer(containerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val containerDir = File(containersDir, containerId)
            if (!containerDir.exists()) {
                return@withContext Result.failure(
                    ContainerNotFoundException(containerId)
                )
            }

            containerDir.deleteRecursively()
            Log.i(TAG, "Deleted container: $containerId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete container", e)
            Result.failure(EmulatorException("Failed to delete container", e))
        }
    }

    override suspend fun launchExecutable(
        container: EmulatorContainer,
        executable: File,
        arguments: List<String>
    ): Result<EmulatorProcess> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Launching executable: ${executable.absolutePath}")
            Log.d(TAG, "Container: ${container.name}, Arguments: $arguments")

            // 1. Verify executable exists
            if (!executable.exists()) {
                return@withContext Result.failure(
                    EmulatorException("Executable not found: ${executable.absolutePath}")
                )
            }

            // 2. Verify Wine and Box64 are initialized
            val box64Binary = File(box64Dir, "box64")
            val wine64Binary = File(wineDir, "bin/wine64")

            if (!box64Binary.exists()) {
                return@withContext Result.failure(
                    Box64BinaryNotFoundException()
                )
            }

            if (!wine64Binary.exists()) {
                return@withContext Result.failure(
                    WineBinaryNotFoundException()
                )
            }

            // 3. Build environment variables
            val environmentVars = buildEnvironmentVariables(container)

            // 4. Build command
            val command = buildList {
                add(box64Binary.absolutePath)
                add(wine64Binary.absolutePath)
                add(executable.absolutePath)
                addAll(arguments)
            }

            Log.d(TAG, "Command: ${command.joinToString(" ")}")
            Log.d(TAG, "Environment: $environmentVars")

            // 5. Start process
            val processBuilder = ProcessBuilder(command)
            processBuilder.environment().putAll(environmentVars)
            processBuilder.redirectErrorStream(true)

            // Set working directory to executable's parent
            executable.parentFile?.let { workingDir ->
                if (workingDir.exists()) {
                    processBuilder.directory(workingDir)
                    Log.d(TAG, "Working directory: ${workingDir.absolutePath}")
                }
            }

            val process = processBuilder.start()
            val pid = getPid(process)
            val processId = "${System.currentTimeMillis()}_$pid"

            Log.i(TAG, "Process launched: PID=$pid, ProcessId=$processId")

            // 6. Monitor output in background (optional, for debugging)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            Log.d(TAG, "[Wine:$pid] $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Output stream monitoring failed", e)
                }
            }

            val emulatorProcess = EmulatorProcess(
                id = processId,
                containerId = container.id,
                executable = executable.absolutePath,
                startedAt = System.currentTimeMillis(),
                pid = pid
            )

            // Store process reference for status checking
            activeProcesses[processId] = ProcessInfo(
                process = process,
                startTime = System.currentTimeMillis()
            )

            Result.success(emulatorProcess)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch executable", e)
            Result.failure(ProcessLaunchException("Failed to launch executable: ${e.message}", e))
        }
    }

    override suspend fun installApplication(
        container: EmulatorContainer,
        installer: File,
        silent: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // TODO: Implement installer execution
        Result.failure(EmulatorException("installApplication not implemented"))
    }

    override suspend fun getProcessStatus(processId: String): Result<EmulatorProcessStatus> = withContext(Dispatchers.IO) {
        try {
            val processInfo = activeProcesses[processId]
                ?: return@withContext Result.failure(
                    ProcessNotFoundException(processId)
                )

            val isRunning = processInfo.process.isAlive
            val exitCode = if (!isRunning) processInfo.process.exitValue() else null

            // Get real-time process metrics
            val pid = getPid(processInfo.process)
            val metrics = if (isRunning && pid > 0) {
                try {
                    readProcessMetricsOnce(pid, processInfo.startTime)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read process metrics", e)
                    null
                }
            } else {
                null
            }

            val status = EmulatorProcessStatus(
                processId = processId,
                isRunning = isRunning,
                exitCode = exitCode,
                cpuUsage = metrics?.cpuPercent ?: 0f,
                memoryUsageMB = metrics?.memoryMB?.toLong() ?: 0L,
                uptime = if (isRunning) System.currentTimeMillis() - processInfo.startTime else 0L
            )

            Result.success(status)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get process status", e)
            Result.failure(EmulatorException("Failed to get process status: ${e.message}", e))
        }
    }

    override suspend fun killProcess(processId: String, force: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val processInfo = activeProcesses[processId]
                ?: return@withContext Result.failure(
                    ProcessNotFoundException(processId)
                )

            Log.i(TAG, "Killing process: $processId (force=$force)")

            if (force) {
                // SIGKILL
                processInfo.process.destroyForcibly()
                Log.d(TAG, "Process forcibly destroyed: $processId")
            } else {
                // SIGTERM - graceful shutdown
                processInfo.process.destroy()

                // Wait up to 10 seconds for graceful shutdown
                val exited = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    while (processInfo.process.isAlive) {
                        kotlinx.coroutines.delay(100)
                    }
                    true
                }

                if (exited == null) {
                    Log.w(TAG, "Process did not exit gracefully, force killing")
                    processInfo.process.destroyForcibly()
                }
            }

            // Wait for exit
            processInfo.process.waitFor()

            // Remove from active processes
            activeProcesses.remove(processId)

            Log.i(TAG, "Process killed successfully: $processId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kill process", e)
            Result.failure(EmulatorException("Failed to kill process: ${e.message}", e))
        }
    }

    override fun getEmulatorInfo(): EmulatorInfo {
        return EmulatorInfo(
            name = name,
            version = version,
            backend = EmulatorBackend.WINLATOR,
            wineVersion = "9.0+",
            translationLayer = "Box64 0.3.6",
            graphicsBackend = "DXVK 2.4.1 / VKD3D 2.13",
            installPath = dataDir,
            capabilities = setOf(
                EmulatorCapability.DIRECT3D_9,
                EmulatorCapability.DIRECT3D_10,
                EmulatorCapability.DIRECT3D_11,
                EmulatorCapability.DIRECT3D_12,
                EmulatorCapability.VULKAN,
                EmulatorCapability.X86_64_TRANSLATION,
                EmulatorCapability.AUDIO_PLAYBACK,
                EmulatorCapability.GAMEPAD_SUPPORT,
                EmulatorCapability.KEYBOARD_MOUSE
            )
        )
    }

    override suspend fun cleanup(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            var bytesFreed = 0L

            // Clean temporary files
            val tempDir = File(dataDir, "temp")
            if (tempDir.exists()) {
                bytesFreed += calculateDirectorySize(tempDir)
                tempDir.deleteRecursively()
            }

            // Clean cache
            val cacheDir = File(dataDir, "cache")
            if (cacheDir.exists()) {
                bytesFreed += calculateDirectorySize(cacheDir)
                cacheDir.deleteRecursively()
            }

            Log.i(TAG, "Cleanup freed ${bytesFreed / 1024 / 1024}MB")
            Result.success(bytesFreed)
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            Result.failure(EmulatorException("Cleanup failed", e))
        }
    }

    // Helper functions

    /**
     * Initializes Wine prefix by running `wineboot --init`.
     *
     * This creates the necessary registry files and directory structure
     * for Wine to function properly.
     *
     * @param containerDir Container root directory
     * @return Result indicating success or failure
     */
    private suspend fun initializeWinePrefix(containerDir: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val box64Binary = File(box64Dir, "box64")
            val winebootBinary = File(wineDir, "bin/wineboot")

            if (!box64Binary.exists()) {
                return@withContext Result.failure(Box64BinaryNotFoundException())
            }

            if (!winebootBinary.exists()) {
                return@withContext Result.failure(WineBinaryNotFoundException())
            }

            Log.i(TAG, "Initializing Wine prefix: ${containerDir.absolutePath}")

            val command = listOf(
                box64Binary.absolutePath,
                winebootBinary.absolutePath,
                "--init"
            )

            val environmentVars = mapOf(
                "WINEPREFIX" to containerDir.absolutePath,
                "WINEDEBUG" to "-all",
                "WINEARCH" to "win64",
                "PATH" to "${wineDir.absolutePath}/bin:${box64Dir.absolutePath}",
                "LD_LIBRARY_PATH" to "${rootfsDir.absolutePath}/usr/lib:${rootfsDir.absolutePath}/usr/lib/aarch64-linux-gnu"
            )

            val processBuilder = ProcessBuilder(command)
            processBuilder.environment().putAll(environmentVars)
            processBuilder.redirectErrorStream(true)

            Log.d(TAG, "Running: ${command.joinToString(" ")}")

            val process = processBuilder.start()

            // Monitor output for debugging
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    Log.d(TAG, "[wineboot] $line")
                    output.appendLine(line)
                }
            }

            // Wait for completion with timeout (60 seconds)
            val completed = kotlinx.coroutines.withTimeoutOrNull(60_000) {
                process.waitFor()
                true
            }

            if (completed == null) {
                Log.w(TAG, "wineboot initialization timed out after 60 seconds")
                process.destroyForcibly()
                return@withContext Result.failure(
                    WinePrefixException("Wine prefix initialization timed out")
                )
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                Log.w(TAG, "wineboot exited with code $exitCode")
                Log.d(TAG, "wineboot output: $output")
                // Don't fail - some games might work without full init
                return@withContext Result.failure(
                    WinePrefixException("wineboot exited with code $exitCode")
                )
            }

            Log.i(TAG, "Wine prefix initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Wine prefix initialization failed", e)
            Result.failure(WinePrefixException("Failed to initialize Wine prefix: ${e.message}", e))
        }
    }

    /**
     * Reads process metrics once (synchronously) for status checking.
     *
     * @param pid Process ID
     * @param startTime Process start time
     * @return ProcessMetrics or null if unavailable
     */
    private fun readProcessMetricsOnce(pid: Int, startTime: Long): ProcessMetrics? {
        val statFile = File("/proc/$pid/stat")
        val statusFile = File("/proc/$pid/status")

        if (!statFile.exists() || !statusFile.exists()) {
            return null
        }

        try {
            // Read memory from /proc/[pid]/status
            val statusContent = statusFile.readText()
            val vmRssLine = statusContent.lines().find { it.startsWith("VmRSS:") }
            val memoryMB = if (vmRssLine != null) {
                val parts = vmRssLine.split(Regex("\\s+"))
                val memoryKB = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                (memoryKB / 1024).toInt()
            } else {
                0
            }

            val uptimeMs = System.currentTimeMillis() - startTime

            // CPU calculation would require previous sample, so we return 0 for single read
            return ProcessMetrics(
                pid = pid,
                cpuPercent = 0f, // Cannot calculate from single sample
                memoryMB = memoryMB,
                uptimeMs = uptimeMs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read process metrics for PID $pid", e)
            return null
        }
    }

    /**
     * Builds environment variables for Wine process execution.
     */
    private fun buildEnvironmentVariables(container: EmulatorContainer): Map<String, String> {
        val config = container.config
        val winePrefix = container.getWinePrefix()

        return buildMap {
            // Wine basic configuration
            put("WINEPREFIX", winePrefix.absolutePath)
            put("WINEDEBUG", "-all") // Reduce log noise
            put("WINEARCH", "win64")

            // Graphics configuration
            when (config.directXWrapper) {
                DirectXWrapperType.DXVK -> {
                    put("DXVK_HUD", if (config.enableFPS) "fps" else "0")
                    put("DXVK_LOG_LEVEL", "warn")
                    put("DXVK_STATE_CACHE_PATH", File(dataDir, "cache/dxvk").absolutePath)
                }
                DirectXWrapperType.VKD3D -> {
                    put("VKD3D_CONFIG", "dxr")
                    put("VKD3D_DEBUG", "warn")
                }
                DirectXWrapperType.WINED3D -> {
                    put("WINED3D", "1")
                }
                DirectXWrapperType.NONE -> {
                    // No DirectX wrapper
                }
            }

            // Box64 performance configuration
            when (config.performancePreset) {
                PerformancePreset.MAXIMUM_PERFORMANCE -> {
                    put("BOX64_DYNAREC_BIGBLOCK", "3")
                    put("BOX64_DYNAREC_STRONGMEM", "0")
                    put("BOX64_DYNAREC_FASTNAN", "1")
                    put("BOX64_DYNAREC_FASTROUND", "1")
                }
                PerformancePreset.BALANCED -> {
                    put("BOX64_DYNAREC_BIGBLOCK", "2")
                    put("BOX64_DYNAREC_STRONGMEM", "1")
                    put("BOX64_DYNAREC_FASTNAN", "1")
                }
                PerformancePreset.MAXIMUM_STABILITY -> {
                    put("BOX64_DYNAREC_BIGBLOCK", "1")
                    put("BOX64_DYNAREC_STRONGMEM", "2")
                    put("BOX64_DYNAREC_FASTNAN", "0")
                    put("BOX64_DYNAREC_FASTROUND", "0")
                }
            }

            // Box64 log configuration
            put("BOX64_LOG", "0") // Disable verbose logging
            put("BOX64_NOBANNER", "1") // Disable startup banner

            // Display configuration
            put("DISPLAY", ":0")
            put("MESA_GL_VERSION_OVERRIDE", "4.6")
            put("MESA_GLSL_VERSION_OVERRIDE", "460")

            // Audio configuration
            when (config.audioDriver) {
                AudioDriverType.ALSA -> {
                    put("AUDIODRIVER", "alsa")
                }
                AudioDriverType.PULSEAUDIO -> {
                    put("AUDIODRIVER", "pulseaudio")
                }
                AudioDriverType.NONE -> {
                    put("AUDIODRIVER", "null")
                }
            }

            // Custom environment variables from config
            putAll(config.customEnvVars)

            // Add system paths
            val existingPath = System.getenv("PATH") ?: ""
            put("PATH", "${wineDir.absolutePath}/bin:${box64Dir.absolutePath}:$existingPath")
            put("LD_LIBRARY_PATH", "${rootfsDir.absolutePath}/usr/lib:${rootfsDir.absolutePath}/usr/lib/aarch64-linux-gnu")
        }
    }

    /**
     * Gets the PID of a Process using reflection.
     * Note: Process.pid() is available in Android API 24+, but not as a public method.
     */
    private fun getPid(process: Process): Int {
        return try {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getInt(process)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get PID via reflection, using fallback", e)
            -1 // Fallback PID
        }
    }

    private fun extractAsset(assetPath: String, destination: File) {
        destination.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Extracted asset: $assetPath -> ${destination.absolutePath}")
    }

    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0
        return directory.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}
