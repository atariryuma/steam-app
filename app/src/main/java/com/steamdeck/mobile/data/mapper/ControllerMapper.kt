package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.local.database.entity.ControllerProfileEntity
import com.steamdeck.mobile.domain.model.ButtonMapping
import com.steamdeck.mobile.domain.model.ControllerProfile
import com.steamdeck.mobile.domain.model.GameAction

/**
 * Maps ControllerProfileEntity to ControllerProfile domain model.
 */
fun ControllerProfileEntity.toDomain(): ControllerProfile {
 return ControllerProfile(
  id = id,
  controllerId = controllerId,
  name = name,
  buttonMapping = ButtonMapping(
   buttonA = GameAction.valueOf(buttonA),
   buttonB = GameAction.valueOf(buttonB),
   buttonX = GameAction.valueOf(buttonX),
   buttonY = GameAction.valueOf(buttonY),
   buttonL1 = GameAction.valueOf(buttonL1),
   buttonR1 = GameAction.valueOf(buttonR1),
   buttonL2 = GameAction.valueOf(buttonL2),
   buttonR2 = GameAction.valueOf(buttonR2),
   buttonStart = GameAction.valueOf(buttonStart),
   buttonSelect = GameAction.valueOf(buttonSelect),
   dpadUp = GameAction.valueOf(dpadUp),
   dpadDown = GameAction.valueOf(dpadDown),
   dpadLeft = GameAction.valueOf(dpadLeft),
   dpadRight = GameAction.valueOf(dpadRight),
   leftStickButton = GameAction.valueOf(leftStickButton),
   rightStickButton = GameAction.valueOf(rightStickButton)
  ),
  vibrationEnabled = vibrationEnabled,
  deadzone = deadzone,
  createdAt = createdAt,
  lastUsedAt = lastUsedAt
 )
}

/**
 * Maps ControllerProfile domain model to ControllerProfileEntity.
 */
fun ControllerProfile.toEntity(): ControllerProfileEntity {
 return ControllerProfileEntity(
  id = id,
  controllerId = controllerId,
  name = name,
  buttonA = buttonMapping.buttonA.name,
  buttonB = buttonMapping.buttonB.name,
  buttonX = buttonMapping.buttonX.name,
  buttonY = buttonMapping.buttonY.name,
  buttonL1 = buttonMapping.buttonL1.name,
  buttonR1 = buttonMapping.buttonR1.name,
  buttonL2 = buttonMapping.buttonL2.name,
  buttonR2 = buttonMapping.buttonR2.name,
  buttonStart = buttonMapping.buttonStart.name,
  buttonSelect = buttonMapping.buttonSelect.name,
  dpadUp = buttonMapping.dpadUp.name,
  dpadDown = buttonMapping.dpadDown.name,
  dpadLeft = buttonMapping.dpadLeft.name,
  dpadRight = buttonMapping.dpadRight.name,
  leftStickButton = buttonMapping.leftStickButton.name,
  rightStickButton = buttonMapping.rightStickButton.name,
  vibrationEnabled = vibrationEnabled,
  deadzone = deadzone,
  createdAt = createdAt,
  lastUsedAt = lastUsedAt
 )
}
