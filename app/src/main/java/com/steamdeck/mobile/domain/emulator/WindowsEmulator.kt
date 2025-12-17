package com.steamdeck.mobile.domain.emulator

import java.io.File

/**
 * Abstract interface for Windows emulation layer.
 *
 * This abstraction allows switching between different emulation backends:
 * - Winlator (current: Wine + Box64)
 * - Proton + FEX (future: when Android support is available)
 * - Mobox (alternative: QEMU-based)
 *
 * Design Pattern: Strategy Pattern + Dependency Injection
 * Based on OOP best practices for third-party library integration.
 *
 * @see <a href="https://blog.evanemran.info/integrating-third-party-libraries-using-oop-in-android">OOP Integration Guide</a>
 */
interface WindowsEmulator {

    /**
     * Returns the name of the emulator backend.
     */
    val name: String

    /**
     * Returns the version of the emulator backend.
     */
    val version: String

    /**
     * Checks if the emulator is available and properly configured.
     *
     * @return Result containing true if available, or error details
     */
    suspend fun isAvailable(): Result<Boolean>

    /**
     * Initializes the emulator environment.
     *
     * This may involve:
     * - Extracting binaries from assets
     * - Setting up root filesystem
     * - Downloading Wine/Proton components
     * - Configuring environment variables
     *
     * @param progressCallback Optional callback for progress updates (0.0 to 1.0)
     * @return Result indicating success or failure with error message
     */
    suspend fun initialize(
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<Unit>

    /**
     * Creates a new container for running Windows applications.
     *
     * A container is an isolated environment with its own Wine prefix,
     * similar to a virtual machine but lighter weight.
     *
     * @param config Container configuration (graphics, audio, etc.)
     * @return Result containing the created Container or error
     */
    suspend fun createContainer(
        config: EmulatorContainerConfig
    ): Result<EmulatorContainer>

    /**
     * Lists all existing containers.
     *
     * @return Result containing list of containers or error
     */
    suspend fun listContainers(): Result<List<EmulatorContainer>>

    /**
     * Deletes a container and all its data.
     *
     * @param containerId Container identifier
     * @return Result indicating success or failure
     */
    suspend fun deleteContainer(containerId: String): Result<Unit>

    /**
     * Launches a Windows executable in a container.
     *
     * @param container Target container
     * @param executable Path to .exe file
     * @param arguments Command-line arguments for the executable
     * @return Result containing running process handle or error
     */
    suspend fun launchExecutable(
        container: EmulatorContainer,
        executable: File,
        arguments: List<String> = emptyList()
    ): Result<EmulatorProcess>

    /**
     * Installs a Windows application (.exe, .msi) in a container.
     *
     * @param container Target container
     * @param installer Path to installer file
     * @param silent Whether to use silent installation
     * @return Result indicating success or failure
     */
    suspend fun installApplication(
        container: EmulatorContainer,
        installer: File,
        silent: Boolean = false
    ): Result<Unit>

    /**
     * Gets the status of a running process.
     *
     * @param processId Process identifier
     * @return Result containing process status or error
     */
    suspend fun getProcessStatus(processId: String): Result<EmulatorProcessStatus>

    /**
     * Terminates a running process.
     *
     * @param processId Process identifier
     * @param force Whether to force kill (SIGKILL vs SIGTERM)
     * @return Result indicating success or failure
     */
    suspend fun killProcess(processId: String, force: Boolean = false): Result<Unit>

    /**
     * Gets emulator-specific information (paths, versions, capabilities).
     *
     * @return Emulator information
     */
    fun getEmulatorInfo(): EmulatorInfo

    /**
     * Cleans up temporary files and caches.
     *
     * @return Result with bytes freed or error
     */
    suspend fun cleanup(): Result<Long>
}

/**
 * Container configuration for emulator.
 */
data class EmulatorContainerConfig(
    val name: String,
    val screenWidth: Int = 1280,
    val screenHeight: Int = 720,
    val graphicsDriver: GraphicsDriverType = GraphicsDriverType.TURNIP,
    val directXWrapper: DirectXWrapperType = DirectXWrapperType.DXVK,
    val audioDriver: AudioDriverType = AudioDriverType.ALSA,
    val performancePreset: PerformancePreset = PerformancePreset.BALANCED,
    val enableFPS: Boolean = false,
    val customEnvVars: Map<String, String> = emptyMap()
)

/**
 * Represents a container instance.
 */
data class EmulatorContainer(
    val id: String,
    val name: String,
    val config: EmulatorContainerConfig,
    val rootPath: File,
    val createdAt: Long,
    val lastUsedAt: Long,
    val sizeBytes: Long
) {
    fun getWinePrefix(): File = File(rootPath, "drive_c")
    fun getProgramFiles(): File = File(rootPath, "drive_c/Program Files")
    fun isInitialized(): Boolean = getWinePrefix().exists()
}

/**
 * Running process handle.
 */
data class EmulatorProcess(
    val id: String,
    val containerId: String,
    val executable: String,
    val startedAt: Long,
    val pid: Int? = null
)

/**
 * Process status information.
 */
data class EmulatorProcessStatus(
    val processId: String,
    val isRunning: Boolean,
    val exitCode: Int? = null,
    val cpuUsage: Float = 0f,
    val memoryUsageMB: Long = 0,
    val uptime: Long = 0
)

/**
 * Emulator backend information.
 */
data class EmulatorInfo(
    val name: String,
    val version: String,
    val backend: EmulatorBackend,
    val wineVersion: String?,
    val translationLayer: String?, // Box64, FEX, QEMU
    val graphicsBackend: String?, // DXVK, VKD3D
    val installPath: File,
    val capabilities: Set<EmulatorCapability>
)

/**
 * Emulator backend types.
 */
enum class EmulatorBackend {
    WINLATOR,      // Wine + Box64
    PROTON_FEX,    // Proton + FEX (future)
    MOBOX,         // QEMU-based
    CUSTOM         // Custom implementation
}

/**
 * Graphics driver options.
 */
enum class GraphicsDriverType {
    TURNIP,    // Qualcomm Adreno (recommended)
    ZINK,      // OpenGL-on-Vulkan
    VIRGL,     // VirGL
    LLVMPIPE,  // Software rendering
    AUTO       // Auto-detect
}

/**
 * DirectX wrapper options.
 */
enum class DirectXWrapperType {
    DXVK,      // DirectX 9/10/11 → Vulkan (recommended)
    VKD3D,     // DirectX 12 → Vulkan
    WINED3D,   // Wine's DirectX → OpenGL
    NONE       // No wrapper
}

/**
 * Audio driver options.
 */
enum class AudioDriverType {
    ALSA,         // ALSA (recommended)
    PULSEAUDIO,   // PulseAudio
    NONE          // No audio
}

/**
 * Performance presets.
 */
enum class PerformancePreset {
    MAXIMUM_PERFORMANCE,  // Fastest, may be unstable
    BALANCED,             // Recommended
    MAXIMUM_STABILITY     // Most stable, slower
}

/**
 * Emulator capabilities.
 */
enum class EmulatorCapability {
    DIRECT3D_9,
    DIRECT3D_10,
    DIRECT3D_11,
    DIRECT3D_12,
    OPENGL,
    VULKAN,
    X86_TRANSLATION,
    X86_64_TRANSLATION,
    AUDIO_PLAYBACK,
    GAMEPAD_SUPPORT,
    KEYBOARD_MOUSE,
    WIDEVINE_DRM,
    STEAM_CLIENT
}

/**
 * Exception thrown by emulator operations.
 */
class EmulatorException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
