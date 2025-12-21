package com.steamdeck.mobile.domain.model

/**
 * Game installation status
 *
 * Tracks the current state of a game's installation lifecycle from initial download
 * through validation to playable state.
 */
enum class InstallationStatus {
    /**
     * Game has not been downloaded yet
     */
    NOT_INSTALLED,

    /**
     * Steam is currently downloading game files
     * Corresponds to ACF StateFlags = 2
     */
    DOWNLOADING,

    /**
     * Downloaded files are being unpacked/processed by Steam
     * Transition state between download and installation complete
     */
    INSTALLING,

    /**
     * Game is fully installed and ready to play
     * Corresponds to ACF StateFlags = 4
     */
    INSTALLED,

    /**
     * Installation validation failed (missing DLLs, corrupt files, etc.)
     * User action required before game can launch
     */
    VALIDATION_FAILED,

    /**
     * Steam detected an update is available
     * Corresponds to ACF StateFlags = 1
     */
    UPDATE_REQUIRED,

    /**
     * Update download is paused
     * Corresponds to ACF StateFlags = 6
     */
    UPDATE_PAUSED
}
