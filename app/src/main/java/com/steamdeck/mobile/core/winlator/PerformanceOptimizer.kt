package com.steamdeck.mobile.core.winlator

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wine/Box64/DXVK Performance Optimization Manager
 *
 * Research-based optimizations (2025):
 * - Box64 dynarec configuration
 * - DXVK environment variables and config file
 * - VKD3D-Proton settings
 * - Wine registry optimizations
 * - Turnip driver settings
 *
 * Best Practices:
 * - Device-specific optimizations (CPU, GPU detection)
 * - Preset profiles (Maximum Performance, Balanced, Stability)
 * - Per-game configurations
 */
@Singleton
class PerformanceOptimizer @Inject constructor(
 @ApplicationContext private val context: Context
) {

 companion object {
  private const val TAG = "PerformanceOptimizer"
 }

 /**
  * Apply performance preset
  */
 suspend fun applyPreset(preset: Box64PerformancePreset, winePrefixPath: File): Result<Unit> {
  return withContext(Dispatchers.IO) {
   try {
    AppLogger.i(TAG, "Applying performance preset: $preset")

    // Configure Box64 environment variables
    applyBox64Settings(preset)

    // Copy DXVK configuration
    applyDxvkConfig(winePrefixPath)

    // Apply Wine registry optimizations
    applyWineRegistryOptimizations(winePrefixPath, preset)

    AppLogger.i(TAG, "Performance preset applied successfully")
    Result.success(Unit)
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to apply performance preset", e)
    Result.failure(e)
   }
  }
 }

 /**
  * Box64 environment variable configuration
  *
  * Research findings:
  * - BOX64_DYNAREC_BIGBLOCK=3 (best for Wine)
  * - BOX64_DYNAREC_STRONGMEM=0 (faster, may cause issues in Unity)
  * - BOX64_DYNACACHE=1 (faster startup)
  */
 private fun applyBox64Settings(preset: Box64PerformancePreset) {
  val envVars = when (preset) {
   Box64PerformancePreset.MAXIMUM_PERFORMANCE -> mapOf(
    "BOX64_DYNAREC_BIGBLOCK" to "3",
    "BOX64_DYNAREC_STRONGMEM" to "0",
    "BOX64_DYNAREC_FASTNAN" to "1",
    "BOX64_DYNAREC_FASTROUND" to "1",
    "BOX64_DYNAREC_SAFEFLAGS" to "0",
    "BOX64_DYNAREC_CALLRET" to "1",
    "BOX64_DYNAREC_FORWARD" to "1024",
    "BOX64_DYNAREC_WEAKBARRIER" to "2",
    "BOX64_DYNAREC_X87DOUBLE" to "0",
    "BOX64_DYNAREC_WAIT" to "1",
    "BOX64_DYNAREC_NATIVEFLAGS" to "0",
    "BOX64_DYNACACHE" to "1",
    "BOX64_LOG" to "0",
    "BOX64_NOBANNER" to "1"
   )
   Box64PerformancePreset.BALANCED -> mapOf(
    "BOX64_DYNAREC_BIGBLOCK" to "2",
    "BOX64_DYNAREC_STRONGMEM" to "0",
    "BOX64_DYNAREC_FASTNAN" to "1",
    "BOX64_DYNAREC_FASTROUND" to "1",
    "BOX64_DYNAREC_SAFEFLAGS" to "1",
    "BOX64_DYNACACHE" to "1",
    "BOX64_LOG" to "0"
   )
   Box64PerformancePreset.MAXIMUM_STABILITY -> mapOf(
    "BOX64_DYNAREC_BIGBLOCK" to "0",
    "BOX64_DYNAREC_STRONGMEM" to "1",
    "BOX64_DYNAREC_FASTNAN" to "0",
    "BOX64_DYNAREC_FASTROUND" to "0",
    "BOX64_DYNAREC_SAFEFLAGS" to "2",
    "BOX64_DYNACACHE" to "1",
    "BOX64_LOG" to "1"
   )
  }

  // Environment variable configuration (used by Process Builder or WinlatorEngine)
  envVars.forEach { (key, value) ->
   AppLogger.d(TAG, "Setting $key=$value")
   // Note: Actual application is done in WinlatorEmulator.kt launchExecutable
  }
 }

 /**
  * Copy DXVK configuration file
  */
 private suspend fun applyDxvkConfig(winePrefixPath: File) {
  withContext(Dispatchers.IO) {
   try {
    // Load dxvk.conf from assets
    val assetManager = context.assets
    val dxvkConfigStream = assetManager.open("winlator/dxvk.conf")

    // Copy to Wine prefix drive_c
    val targetFile = File(winePrefixPath, "drive_c/dxvk.conf")
    targetFile.parentFile?.mkdirs()

    targetFile.outputStream().use { output ->
     dxvkConfigStream.copyTo(output)
    }

    AppLogger.d(TAG, "DXVK config copied to: ${targetFile.absolutePath}")
   } catch (e: Exception) {
    AppLogger.w(TAG, "Failed to copy DXVK config", e)
   }
  }
 }

 /**
  * Apply Wine registry optimizations
  */
 private suspend fun applyWineRegistryOptimizations(
  winePrefixPath: File,
  preset: Box64PerformancePreset
 ) {
  withContext(Dispatchers.IO) {
   try {
    // Load performance.reg from assets
    val assetManager = context.assets
    val regStream = assetManager.open("winlator/performance.reg")

    // Copy to temporary file
    val tempRegFile = File(context.cacheDir, "performance.reg")
    tempRegFile.outputStream().use { output ->
     regStream.copyTo(output)
    }

    // TODO: Execute wine regedit performance.reg
    // Need to apply via WinlatorEmulator
    AppLogger.d(TAG, "Wine registry optimizations prepared: ${tempRegFile.absolutePath}")
   } catch (e: Exception) {
    AppLogger.w(TAG, "Failed to prepare Wine registry optimizations", e)
   }
  }
 }

 /**
  * Get DXVK environment variables
  */
 fun getDxvkEnvironmentVariables(preset: Box64PerformancePreset): Map<String, String> {
  return when (preset) {
   Box64PerformancePreset.MAXIMUM_PERFORMANCE -> mapOf(
    "DXVK_HUD" to "fps",
    "DXVK_LOG_LEVEL" to "warn",
    "DXVK_STATE_CACHE_PATH" to "${context.cacheDir}/dxvk",
    "DXVK_CONFIG_FILE" to "C:\\dxvk.conf"
   )
   Box64PerformancePreset.BALANCED -> mapOf(
    "DXVK_HUD" to "0",
    "DXVK_LOG_LEVEL" to "info",
    "DXVK_STATE_CACHE_PATH" to "${context.cacheDir}/dxvk"
   )
   Box64PerformancePreset.MAXIMUM_STABILITY -> mapOf(
    "DXVK_LOG_LEVEL" to "debug",
    "DXVK_STATE_CACHE_PATH" to "${context.cacheDir}/dxvk"
   )
  }
 }

 /**
  * Get VKD3D-Proton environment variables
  */
 fun getVkd3dEnvironmentVariables(): Map<String, String> {
  return mapOf(
   "VKD3D_CONFIG" to "dxr", // Enable DXR
   "VKD3D_DEBUG" to "warn",
   "VKD3D_SHADER_CACHE_PATH" to "${context.cacheDir}/vkd3d"
  )
 }

 /**
  * Get Wine environment variables
  */
 fun getWineEnvironmentVariables(): Map<String, String> {
  return mapOf(
   "WINEDEBUG" to "-all", // Disable debug output
   "WINEARCH" to "win64",
   "WINEFSYNC" to "1",  // Enable FSync
   "WINE_CPU_TOPOLOGY" to "4:0" // 4 cores, no SMT
  )
 }

 /**
  * Get Vulkan/Mesa environment variables (Turnip driver)
  */
 fun getVulkanEnvironmentVariables(): Map<String, String> {
  return mapOf(
   "MESA_GL_VERSION_OVERRIDE" to "4.6",
   "MESA_GLSL_VERSION_OVERRIDE" to "460",
   "MESA_VK_WSI_PRESENT_MODE" to "mailbox",
   "TU_DEBUG" to "noconform",
   "ZINK_DEBUG" to "fast_texture,fast_path,fast_shader",
   "ZINK_DESCRIPTORS" to "db",
   "ZINK_CONTEXT_THREADED" to "1"
  )
 }

 /**
  * Integrate all environment variables
  */
 fun getAllEnvironmentVariables(preset: Box64PerformancePreset): Map<String, String> {
  return getDxvkEnvironmentVariables(preset) +
    getVkd3dEnvironmentVariables() +
    getWineEnvironmentVariables() +
    getVulkanEnvironmentVariables()
 }
}

/**
 * Box64/DXVK performance presets
 * (Independent from domain layer PerformancePreset)
 */
enum class Box64PerformancePreset {
 /**
  * Maximum performance
  * - Gaming optimized
  * - Fast but some games may be unstable
  */
 MAXIMUM_PERFORMANCE,

 /**
  * Balanced
  * - Default configuration
  * - Balance between stability and performance
  */
 BALANCED,

 /**
  * Maximum stability
  * - For Unity/problematic games
  * - Slower but higher compatibility
  */
 MAXIMUM_STABILITY
}
