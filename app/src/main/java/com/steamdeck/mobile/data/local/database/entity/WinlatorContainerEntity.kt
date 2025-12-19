package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Winlatorcontainerconfiguration格納doエンティティ
 */
@Entity(tableName = "winlator_containers")
data class WinlatorContainerEntity(
 @PrimaryKey(autoGenerate = true)
 val id: Long = 0,

 /** container名 */
 val name: String,

 /** Box64プリセット (PERFORMANCE / STABILITY) */
 val box64Preset: Box64Preset,

 /** Wineバージョン */
 val wineVersion: String,

 /** environmentvariable（JSON形式） */
 val environmentVars: String = "{}",

 /** 画面解像度（Example: "1920x1080"） */
 val screenResolution: String = "1280x720",

 /** DXVK有効化 */
 val enableDXVK: Boolean = true,

 /** D3D Extras有効化 */
 val enableD3DExtras: Boolean = false,

 /** customcommandラインargument */
 val customArgs: String = "",

 /** createdate and time（Unix timestamp） */
 val createdTimestamp: Long = System.currentTimeMillis()
)

/**
 * Box64performanceプリセット
 */
enum class Box64Preset {
 /** performance重視 */
 PERFORMANCE,

 /** 安定性重視（Unity Engineetc 推奨） */
 STABILITY,

 /** customconfiguration */
 CUSTOM
}
