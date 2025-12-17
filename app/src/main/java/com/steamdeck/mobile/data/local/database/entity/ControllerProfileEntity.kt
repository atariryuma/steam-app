package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.steamdeck.mobile.domain.model.GameAction

/**
 * Room entity for controller profiles.
 *
 * Stores saved controller configurations including button mappings.
 */
@Entity(tableName = "controller_profiles")
data class ControllerProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Controller unique ID (vendorId:productId format).
     */
    val controllerId: String,

    /**
     * Profile name (user-defined).
     */
    val name: String,

    /**
     * Button mappings (stored as GameAction enum names).
     */
    val buttonA: String = GameAction.CONFIRM.name,
    val buttonB: String = GameAction.CANCEL.name,
    val buttonX: String = GameAction.ACTION1.name,
    val buttonY: String = GameAction.ACTION2.name,
    val buttonL1: String = GameAction.SHOULDER_LEFT.name,
    val buttonR1: String = GameAction.SHOULDER_RIGHT.name,
    val buttonL2: String = GameAction.TRIGGER_LEFT.name,
    val buttonR2: String = GameAction.TRIGGER_RIGHT.name,
    val buttonStart: String = GameAction.MENU.name,
    val buttonSelect: String = GameAction.VIEW.name,
    val dpadUp: String = GameAction.DPAD_UP.name,
    val dpadDown: String = GameAction.DPAD_DOWN.name,
    val dpadLeft: String = GameAction.DPAD_LEFT.name,
    val dpadRight: String = GameAction.DPAD_RIGHT.name,
    val leftStickButton: String = GameAction.STICK_LEFT.name,
    val rightStickButton: String = GameAction.STICK_RIGHT.name,

    /**
     * Vibration enabled flag.
     */
    val vibrationEnabled: Boolean = true,

    /**
     * Joystick deadzone (0.0 - 1.0).
     */
    val deadzone: Float = 0.1f,

    /**
     * Creation timestamp.
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Last used timestamp.
     */
    val lastUsedAt: Long = System.currentTimeMillis()
)
