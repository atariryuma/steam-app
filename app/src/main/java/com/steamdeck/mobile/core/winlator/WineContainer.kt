package com.steamdeck.mobile.core.winlator

import java.io.File

/**
 * Wine container configuration.
 *
 * Based on Winlator's Container class structure.
 * Represents an isolated Wine environment for running Windows applications.
 */
data class WineContainer(
 val id: String,
 val name: String,
 val screenSize: String = DEFAULT_SCREEN_SIZE,
 val wineVersion: String = DEFAULT_WINE_VERSION,
 val graphicsDriver: GraphicsDriver = GraphicsDriver.TURNIP,
 val dxwrapper: DxWrapper = DxWrapper.DXVK,
 val audioDriver: AudioDriver = AudioDriver.ALSA,
 val box86Preset: Box64Preset = Box64Preset.COMPATIBILITY,
 val box64Preset: Box64Preset = Box64Preset.COMPATIBILITY,
 val showFPS: Boolean = false,
 val wow64Mode: Boolean = true,
 val envVars: Map<String, String> = defaultEnvVars(),
 val rootPath: String
) {
 companion object {
  const val DEFAULT_SCREEN_SIZE = "1280x720"
  const val DEFAULT_WINE_VERSION = "9.0"

  fun defaultEnvVars() = mapOf(
   "ZINK_DESCRIPTORS" to "lazy",
   "ZINK_DEBUG" to "compact",
   "MESA_SHADER_CACHE_DISABLE" to "false",
   "MESA_SHADER_CACHE_MAX_SIZE" to "512MB",
   "mesa_glthread" to "true",
   "WINEESYNC" to "1",
   "MESA_VK_WSI_PRESENT_MODE" to "mailbox",
   "TU_DEBUG" to "noconform"
  )

  /**
   * Creates a default container for testing.
   */
  fun createDefault(dataDir: File): WineContainer {
   val containerId = System.currentTimeMillis().toString()
   return WineContainer(
    id = containerId,
    name = "default",
    rootPath = File(dataDir, "containers/$containerId").absolutePath
   )
  }
 }

 /**
  * Returns the Wine prefix path (drive_c directory).
  */
 fun getWinePrefix(): String = "$rootPath/drive_c"

 /**
  * Returns the path where executables should be placed.
  */
 fun getProgramFilesPath(): String = "$rootPath/drive_c/Program Files"

 /**
  * Checks if container is properly initialized.
  */
 fun isInitialized(): Boolean {
  return File(getWinePrefix()).exists()
 }
}

/**
 * Graphics driver options for Wine.
 */
enum class GraphicsDriver(val value: String) {
 TURNIP("turnip"),  // Qualcomm Adreno (recommended for Snapdragon)
 ZINK("zink"),   // OpenGL-on-Vulkan
 VIRGL("virgl"),  // VirGL (fallback)
 LLVMPIPE("llvmpipe") // Software rendering (slow)
}

/**
 * DirectX wrapper options.
 */
enum class DxWrapper(val value: String) {
 DXVK("dxvk"),   // DirectX 9/10/11 to Vulkan (recommended)
 WineD3D("wined3d"), // Wine's built-in DirectX to OpenGL
 VKD3D("vkd3d"),  // DirectX 12 to Vulkan
 NONE("none")   // No wrapper
}

/**
 * Audio driver options.
 */
enum class AudioDriver(val value: String) {
 ALSA("alsa"),   // ALSA (recommended)
 PULSEAUDIO("pulseaudio"), // PulseAudio
 NONE("none")   // No audio
}

/**
 * Box86/Box64 performance presets.
 */
enum class Box64Preset(val value: String) {
 PERFORMANCE("performance"), // Maximum performance, may be unstable
 COMPATIBILITY("compatibility"), // Balanced (recommended)
 STABILITY("stability")   // Maximum stability, slower
}
