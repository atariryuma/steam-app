package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Winlator container configuration storage entity
 *
 * NOTE: This entity is used internally by SteamSetupManager for container persistence.
 * Repository layer (WinlatorContainerRepository) was removed per YAGNI, but entity remains
 * as it's actively used by Steam installation logic.
 */
@Entity(tableName = "winlator_containers")
data class WinlatorContainerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Container name */
    val name: String,

    /** Box64 preset (PERFORMANCE / STABILITY / CUSTOM) */
    val box64Preset: Box64Preset,

    /** Wine version */
    val wineVersion: String,

    /** Environment variables (JSON format) */
    val environmentVars: String = "{}",

    /** Screen resolution (Example: "1920x1080") */
    val screenResolution: String = "1280x720",

    /** Enable DXVK */
    val enableDXVK: Boolean = true,

    /** Enable D3D Extras */
    val enableD3DExtras: Boolean = false,

    /** Custom command line arguments */
    val customArgs: String = "",

    /** Created date/time (Unix timestamp) */
    val createdTimestamp: Long = System.currentTimeMillis()
)

/**
 * Box64 performance preset
 *
 * NOTE: Also defined in core.winlator.Box64Preset - kept separate for database layer isolation
 */
enum class Box64Preset {
    /** Performance-focused */
    PERFORMANCE,

    /** Stability-focused (recommended for Unity Engine etc.) */
    STABILITY,

    /** Custom configuration */
    CUSTOM
}
