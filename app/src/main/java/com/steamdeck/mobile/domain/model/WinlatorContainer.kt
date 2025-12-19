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
  * environmentvariableJSON文字列 conversion
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
   * JSON文字列fromenvironmentvariableMapgenerate
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
   * defaultcontainergenerate
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
 * Box64performanceプリセット
 */
enum class Box64Preset {
 /** performance重視 */
 PERFORMANCE,

 /** 安定性重視（Unity Engineetc 推奨） */
 STABILITY,

 /** customconfiguration */
 CUSTOM;

 /**
  * プリセット 説明文
  */
 val description: String
  get() = when (this) {
   PERFORMANCE -> "performance優先（ほ んど game 推奨）"
   STABILITY -> "安定性優先（Unity Enginegameetc 推奨）"
   CUSTOM -> "customconfiguration"
  }
}
