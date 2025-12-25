package com.steamdeck.mobile.core.steam

import androidx.compose.runtime.Immutable

/**
 * Steam Client Installation Progress States
 *
 * Type-safe progress tracking using sealed classes (2025 Android Best Practice).
 * This replaces callback-based progress reporting with Flow-based reactive streams.
 *
 * Benefits:
 * - Compile-time type checking prevents invalid state transitions
 * - Clear progress range allocation per step
 * - Easy to add new installation steps
 * - Testable with standard Flow testing tools
 *
 * Progress Range Allocation:
 * - Initializing: 0.00 ~ 0.40 (40%, first-time only: 2-3 minutes for Wine/Box64 extraction)
 * - Downloading: 0.40 ~ 0.50 (10%, ~5 seconds for SteamSetup.exe)
 * - CreatingContainer: 0.50 ~ 0.75 (25%, 30-60 seconds for Wine prefix creation)
 * - ExtractingNSIS: 0.75 ~ 0.90 (15%, ~10 seconds for 7-Zip extraction)
 * - InitializingSteam: 0.90 ~ 1.00 (10%, ~30 seconds for Steam client first-run)
 *
 * @see SteamSetupManager.installSteam
 */
@Immutable
sealed class SteamInstallProgress {
    /**
     * Overall progress (0.0 ~ 1.0)
     */
    abstract val progress: Float

    /**
     * Step 1: Initializing Winlator (Wine + Box64 extraction)
     * Progress: 0.00 ~ 0.40
     *
     * @param progress Current progress (0.0 ~ 1.0, mapped to 0.00 ~ 0.40 overall)
     * @param message Detailed status message (e.g., "Extracting Wine rootfs...")
     */
    @Immutable
    data class Initializing(
        override val progress: Float,
        val message: String
    ) : SteamInstallProgress()

    /**
     * Step 2: Downloading Steam installer
     * Progress: 0.40 ~ 0.50
     *
     * @param progress Current progress (0.0 ~ 1.0, mapped to 0.40 ~ 0.50 overall)
     * @param bytesDownloaded Bytes downloaded so far
     * @param totalBytes Total file size in bytes
     */
    @Immutable
    data class Downloading(
        override val progress: Float,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L
    ) : SteamInstallProgress() {
        /**
         * Download percentage (0-100)
         */
        val percentage: Int
            get() = if (totalBytes > 0) {
                ((bytesDownloaded.toDouble() / totalBytes.toDouble()) * 100).toInt()
            } else 0
    }

    /**
     * Step 3: Creating Wine container
     * Progress: 0.50 ~ 0.75
     *
     * @param progress Current progress (0.0 ~ 1.0, mapped to 0.50 ~ 0.75 overall)
     * @param containerId Container ID being created
     * @param message Detailed status (e.g., "Running wineboot...", "Creating system32...")
     */
    @Immutable
    data class CreatingContainer(
        override val progress: Float,
        val containerId: String,
        val message: String = "Creating Wine container..."
    ) : SteamInstallProgress()

    /**
     * Step 4: Extracting Steam files from NSIS installer
     * Progress: 0.75 ~ 0.90
     *
     * @param progress Current progress (0.0 ~ 1.0, mapped to 0.75 ~ 0.90 overall)
     * @param filesExtracted Number of files extracted so far
     * @param totalFiles Total number of files to extract
     */
    @Immutable
    data class ExtractingNSIS(
        override val progress: Float,
        val filesExtracted: Int,
        val totalFiles: Int
    ) : SteamInstallProgress() {
        /**
         * Extraction percentage (0-100)
         */
        val percentage: Int
            get() = if (totalFiles > 0) {
                ((filesExtracted.toDouble() / totalFiles.toDouble()) * 100).toInt()
            } else 0
    }

    /**
     * Step 5: Initializing Steam client (first-run setup)
     * Progress: 0.90 ~ 1.00
     *
     * @param progress Current progress (0.0 ~ 1.0, mapped to 0.90 ~ 1.00 overall)
     * @param message Detailed status (e.g., "Creating steamapps directory...")
     */
    @Immutable
    data class InitializingSteam(
        override val progress: Float,
        val message: String = "Initializing Steam client..."
    ) : SteamInstallProgress()

    /**
     * Installation completed successfully
     *
     * @param installPath Steam installation path (e.g., "C:\Program Files (x86)\Steam")
     * @param containerId Container ID where Steam was installed
     */
    @Immutable
    data class Success(
        val installPath: String,
        val containerId: String
    ) : SteamInstallProgress() {
        override val progress: Float = 1.0f
    }

    /**
     * Installation failed with error
     *
     * @param message User-friendly error message
     * @param currentStep Step where error occurred (for debugging)
     * @param technicalDetails Technical error details (for logging)
     */
    @Immutable
    data class Error(
        val message: String,
        val currentStep: String,
        val technicalDetails: String? = null
    ) : SteamInstallProgress() {
        override val progress: Float = 0.0f
    }
}
