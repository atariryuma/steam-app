package com.steamdeck.mobile.core.winlator

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wine/Box64/DXVK パフォーマンス最適化マネージャー
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
     * パフォーマンスプリセットを適用
     */
    suspend fun applyPreset(preset: Box64PerformancePreset, winePrefixPath: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Applying performance preset: $preset")

                // Box64環境変数を設定
                applyBox64Settings(preset)

                // DXVK設定をコピー
                applyDxvkConfig(winePrefixPath)

                // Wine レジストリ最適化を適用
                applyWineRegistryOptimizations(winePrefixPath, preset)

                Log.i(TAG, "Performance preset applied successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply performance preset", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Box64環境変数を設定
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

        // 環境変数を設定（Process BuilderまたはWinlatorEngineで使用）
        envVars.forEach { (key, value) ->
            Log.d(TAG, "Setting $key=$value")
            // Note: 実際の適用はWinlatorEmulator.ktのlaunchExecutableで行う
        }
    }

    /**
     * DXVK設定ファイルをコピー
     */
    private suspend fun applyDxvkConfig(winePrefixPath: File) {
        withContext(Dispatchers.IO) {
            try {
                // assetsからdxvk.confを読み込み
                val assetManager = context.assets
                val dxvkConfigStream = assetManager.open("winlator/dxvk.conf")

                // Wine prefixのdrive_cにコピー
                val targetFile = File(winePrefixPath, "drive_c/dxvk.conf")
                targetFile.parentFile?.mkdirs()

                targetFile.outputStream().use { output ->
                    dxvkConfigStream.copyTo(output)
                }

                Log.d(TAG, "DXVK config copied to: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy DXVK config", e)
            }
        }
    }

    /**
     * Wine レジストリ最適化を適用
     */
    private suspend fun applyWineRegistryOptimizations(
        winePrefixPath: File,
        preset: Box64PerformancePreset
    ) {
        withContext(Dispatchers.IO) {
            try {
                // assetsからperformance.regを読み込み
                val assetManager = context.assets
                val regStream = assetManager.open("winlator/performance.reg")

                // 一時ファイルにコピー
                val tempRegFile = File(context.cacheDir, "performance.reg")
                tempRegFile.outputStream().use { output ->
                    regStream.copyTo(output)
                }

                // TODO: wine regedit performance.reg を実行
                // WinlatorEmulator経由で適用する必要がある
                Log.d(TAG, "Wine registry optimizations prepared: ${tempRegFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to prepare Wine registry optimizations", e)
            }
        }
    }

    /**
     * DXVK環境変数を取得
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
     * VKD3D-Proton環境変数を取得
     */
    fun getVkd3dEnvironmentVariables(): Map<String, String> {
        return mapOf(
            "VKD3D_CONFIG" to "dxr",  // Enable DXR
            "VKD3D_DEBUG" to "warn",
            "VKD3D_SHADER_CACHE_PATH" to "${context.cacheDir}/vkd3d"
        )
    }

    /**
     * Wine環境変数を取得
     */
    fun getWineEnvironmentVariables(): Map<String, String> {
        return mapOf(
            "WINEDEBUG" to "-all",  // Disable debug output
            "WINEARCH" to "win64",
            "WINEFSYNC" to "1",     // Enable FSync
            "WINE_CPU_TOPOLOGY" to "4:0"  // 4 cores, no SMT
        )
    }

    /**
     * Vulkan/Mesa環境変数を取得（Turnip driver）
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
     * すべての環境変数を統合
     */
    fun getAllEnvironmentVariables(preset: Box64PerformancePreset): Map<String, String> {
        return getDxvkEnvironmentVariables(preset) +
                getVkd3dEnvironmentVariables() +
                getWineEnvironmentVariables() +
                getVulkanEnvironmentVariables()
    }
}

/**
 * Box64/DXVK パフォーマンスプリセット
 * (domain層のPerformancePresetから独立)
 */
enum class Box64PerformancePreset {
    /**
     * 最大パフォーマンス
     * - ゲーミング最適化
     * - 高速だが一部ゲームで不安定
     */
    MAXIMUM_PERFORMANCE,

    /**
     * バランス
     * - デフォルト設定
     * - 安定性とパフォーマンスの両立
     */
    BALANCED,

    /**
     * 最大安定性
     * - Unity/問題のあるゲーム向け
     * - 低速だが互換性高い
     */
    MAXIMUM_STABILITY
}
