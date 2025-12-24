package com.steamdeck.mobile.core.winlator

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Container configuration metadata for version tracking and idempotent updates
 *
 * 2025 Best Practice: Idempotent Configuration Pattern
 * - Track which configurations have been applied
 * - Enable safe re-application without side effects
 * - Support incremental updates across app versions
 *
 * References:
 * - https://microservices.io/patterns/communication-style/idempotent-consumer.html
 * - https://www.rickyspears.com/technology/the-idempotence-principle-a-cornerstone-of-robust-software-architecture/
 */
@Serializable
data class ContainerConfigMetadata(
    /**
     * Configuration version number
     * Increment when new configurations are added to trigger re-application
     */
    val version: Int = CURRENT_VERSION,

    /**
     * X11 graphics driver configured
     * HKEY_CURRENT_USER\Software\Wine\Drivers "Graphics"="x11"
     */
    val x11Configured: Boolean = false,

    /**
     * DNS registry settings configured
     * Wine network name resolution settings
     */
    val dnsConfigured: Boolean = false,

    /**
     * DirectInput deadzone configured
     * Game controller joystick settings
     */
    val directInputConfigured: Boolean = false,

    /**
     * Wine Mono installed
     * .NET Framework compatibility layer
     */
    val monoInstalled: Boolean = false,

    /**
     * Wine Gecko installed
     * Internet Explorer compatibility layer
     */
    val geckoInstalled: Boolean = false,

    /**
     * Windows 10 registry version configured
     * Required for Steam and modern Windows apps
     */
    val windows10Configured: Boolean = false,

    /**
     * Timestamp of last configuration update
     */
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Current configuration version
         * Increment this when adding new configurations
         */
        const val CURRENT_VERSION = 1

        /**
         * Metadata filename
         */
        private const val METADATA_FILENAME = ".container_config.json"

        /**
         * JSON serializer (lenient for forward compatibility)
         */
        private val json = Json {
            ignoreUnknownKeys = true // Forward compatibility
            prettyPrint = true
        }

        /**
         * Load metadata from container directory
         * Returns default metadata if file doesn't exist (idempotent)
         */
        fun load(containerDir: File): ContainerConfigMetadata {
            val metadataFile = File(containerDir, METADATA_FILENAME)
            return if (metadataFile.exists()) {
                try {
                    json.decodeFromString(serializer(), metadataFile.readText())
                } catch (e: Exception) {
                    // Corrupted metadata - return default (will trigger full reconfiguration)
                    ContainerConfigMetadata()
                }
            } else {
                // No metadata - return default (new container or legacy upgrade)
                ContainerConfigMetadata()
            }
        }

        /**
         * Save metadata to container directory
         * Idempotent - can be called multiple times safely
         */
        fun save(containerDir: File, metadata: ContainerConfigMetadata) {
            val metadataFile = File(containerDir, METADATA_FILENAME)
            metadataFile.writeText(json.encodeToString(serializer(), metadata))
        }
    }

    /**
     * Check if any configuration is missing
     * Used to determine if re-configuration is needed
     */
    fun needsConfiguration(): Boolean {
        return !x11Configured ||
               !dnsConfigured ||
               !directInputConfigured ||
               !monoInstalled ||
               !geckoInstalled ||
               !windows10Configured ||
               version < CURRENT_VERSION
    }

    /**
     * Get list of missing configurations for logging
     */
    fun getMissingConfigurations(): List<String> {
        val missing = mutableListOf<String>()
        if (!x11Configured) missing.add("X11 graphics driver")
        if (!dnsConfigured) missing.add("DNS settings")
        if (!directInputConfigured) missing.add("DirectInput deadzone")
        if (!monoInstalled) missing.add("Wine Mono")
        if (!geckoInstalled) missing.add("Wine Gecko")
        if (!windows10Configured) missing.add("Windows 10 registry")
        if (version < CURRENT_VERSION) missing.add("Configuration version upgrade")
        return missing
    }
}
