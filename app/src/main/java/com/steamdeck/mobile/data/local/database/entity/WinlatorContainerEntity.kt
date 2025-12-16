package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Winlatorコンテナ設定を格納するエンティティ
 */
@Entity(tableName = "winlator_containers")
data class WinlatorContainerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** コンテナ名 */
    val name: String,

    /** Box64プリセット (PERFORMANCE / STABILITY) */
    val box64Preset: Box64Preset,

    /** Wineバージョン */
    val wineVersion: String,

    /** 環境変数（JSON形式） */
    val environmentVars: String = "{}",

    /** 画面解像度（例: "1920x1080"） */
    val screenResolution: String = "1280x720",

    /** DXVKを有効化 */
    val enableDXVK: Boolean = true,

    /** D3D Extrasを有効化 */
    val enableD3DExtras: Boolean = false,

    /** カスタムコマンドライン引数 */
    val customArgs: String = "",

    /** 作成日時（Unix timestamp） */
    val createdTimestamp: Long = System.currentTimeMillis()
)

/**
 * Box64パフォーマンスプリセット
 */
enum class Box64Preset {
    /** パフォーマンス重視 */
    PERFORMANCE,

    /** 安定性重視（Unity Engine等に推奨） */
    STABILITY,

    /** カスタム設定 */
    CUSTOM
}
