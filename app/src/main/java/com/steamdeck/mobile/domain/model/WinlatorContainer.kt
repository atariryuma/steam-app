package com.steamdeck.mobile.domain.model

import androidx.compose.runtime.Immutable

/**
 * Winlator container configuration domain model
 *
 * @Immutable enables Compose performance optimization.
 */
@Immutable
data class WinlatorContainer(
 val id: Long = 0,
 val name: String,
 val box64Preset: Box64Preset,
 val wineVersion: String,
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
   * Generate environment variable Map from JSON string
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
  fun createDefault(name: String = "Default Container"): WinlatorContainer {
   return WinlatorContainer(
    name = name,
    box64Preset = Box64Preset.PERFORMANCE,
    wineVersion = "8.0",
    environmentVars = emptyMap(),
    screenResolution = "1280x720",
    enableDXVK = true
   )
  }
 }
}

/**
 * Box64 performance preset
 */
enum class Box64Preset {
 /** Performance-focused */
 PERFORMANCE,

 /** Stability-focused (recommended for Unity Engine, etc.) */
 STABILITY,

 /** Custom configuration */
 CUSTOM;

 /**
  * Preset description
  */
 val description: String
  get() = when (this) {
   PERFORMANCE -> "Performance priority (recommended for most games)"
   STABILITY -> "Stability priority (recommended for Unity Engine games, etc.)"
   CUSTOM -> "Custom configuration"
  }
}
