package com.steamdeck.mobile.core.winlator

import androidx.compose.runtime.Immutable

/**
 * Winlator container configuration
 *
 * Note: This is an internal configuration model used by WinlatorEngine.
 * Not exposed through repository layer (removed WinlatorContainerRepository as per YAGNI).
 *
 * @Immutable enables Compose performance optimization.
 */
@Immutable
data class WinlatorContainer(
    val id: Long = 0,
    val name: String,
    val box64Preset: Box64Preset = Box64Preset.COMPATIBILITY,
    val wineVersion: String = "9.0",
    val environmentVars: Map<String, String> = emptyMap(),
    val screenResolution: String = "1280x720",
    val enableDXVK: Boolean = true,
    val enableD3DExtras: Boolean = false,
    val customArgs: String = "",
    val createdTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Convert environment variables to JSON string
     */
    fun environmentVarsToJson(): String {
        if (environmentVars.isEmpty()) return "{}"
        return environmentVars.entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}"
        ) { (key, value) -> "\"$key\":\"$value\"" }
    }

    companion object {
        /**
         * Parse environment variable Map from JSON string
         */
        fun parseEnvironmentVars(json: String): Map<String, String> {
            if (json.isBlank() || json == "{}") return emptyMap()

            return try {
                json.trim('{', '}')
                    .split(",")
                    .mapNotNull { pair ->
                        val parts = pair.trim().split(":")
                        if (parts.size == 2) {
                            val key = parts[0].trim('"')
                            val value = parts[1].trim('"')
                            key to value
                        } else null
                    }
                    .toMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }

        /**
         * Generate default container
         */
        fun default(name: String = "Default Container"): WinlatorContainer {
            return WinlatorContainer(
                name = name,
                box64Preset = Box64Preset.COMPATIBILITY,
                wineVersion = "9.0",
                screenResolution = "1280x720",
                enableDXVK = true
            )
        }
    }
}
