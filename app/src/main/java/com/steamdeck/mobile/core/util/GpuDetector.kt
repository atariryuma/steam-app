package com.steamdeck.mobile.core.util

import android.content.Context
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal GPU Detection for Driver Selection
 *
 * Detects GPU vendor to choose optimal driver:
 * - Adreno → Turnip (fast)
 * - Others → VirGL (safe)
 *
 * IMPORTANT: Uses system properties instead of glGetString to avoid OpenGL context requirement
 */
@Singleton
class GpuDetector @Inject constructor(
    private val context: Context
) {
    /**
     * Check if Turnip driver should be used
     * Returns true for Qualcomm Adreno GPUs only
     *
     * Detection strategy:
     * 1. Check Build.HARDWARE for Qualcomm SoC names (e.g., "qcom", "msm", "sdm")
     * 2. Check Build.BOARD for Qualcomm board names
     * 3. Default to VirGL (safe fallback) if detection fails
     */
    fun shouldUseTurnip(): Boolean {
        return try {
            val hardware = Build.HARDWARE.lowercase()
            val board = Build.BOARD.lowercase()
            val manufacturer = Build.MANUFACTURER.lowercase()

            // Qualcomm SoC indicators
            val isQualcomm = hardware.contains("qcom") ||
                           hardware.contains("msm") ||
                           hardware.contains("sdm") ||
                           hardware.contains("sm") ||
                           board.contains("qcom") ||
                           board.contains("msm") ||
                           (manufacturer == "qualcomm")

            isQualcomm
        } catch (e: Exception) {
            false  // Default to VirGL on error
        }
    }
}
