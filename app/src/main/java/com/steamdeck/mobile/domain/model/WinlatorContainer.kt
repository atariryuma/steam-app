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
     * 環境変数をJSON文字列に変換
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
         * JSON文字列から環境変数Mapを生成
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
         * デフォルトコンテナを生成
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
 * Box64パフォーマンスプリセット
 */
enum class Box64Preset {
    /** パフォーマンス重視 */
    PERFORMANCE,

    /** 安定性重視（Unity Engine等に推奨） */
    STABILITY,

    /** カスタム設定 */
    CUSTOM;

    /**
     * プリセットの説明文
     */
    val description: String
        get() = when (this) {
            PERFORMANCE -> "パフォーマンス優先（ほとんどのゲームに推奨）"
            STABILITY -> "安定性優先（Unity Engineゲーム等に推奨）"
            CUSTOM -> "カスタム設定"
        }
}
