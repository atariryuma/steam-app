package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.local.database.entity.ControllerProfileEntity
import com.steamdeck.mobile.domain.model.ButtonMapping
import com.steamdeck.mobile.domain.model.ControllerProfile
import com.steamdeck.mobile.domain.model.GameAction

/**
 * ControllerProfileEntity <-> ControllerProfile mapper
 * Implements BaseMapper to reduce boilerplate for list conversions
 */
object ControllerMapper : BaseMapper<ControllerProfileEntity, ControllerProfile> {
 /**
  * EntityDomain model conversion
  */
 override fun toDomain(entity: ControllerProfileEntity): ControllerProfile {
  return ControllerProfile(
   id = entity.id,
   controllerId = entity.controllerId,
   name = entity.name,
   buttonMapping = ButtonMapping(
    buttonA = GameAction.valueOf(entity.buttonA),
    buttonB = GameAction.valueOf(entity.buttonB),
    buttonX = GameAction.valueOf(entity.buttonX),
    buttonY = GameAction.valueOf(entity.buttonY),
    buttonL1 = GameAction.valueOf(entity.buttonL1),
    buttonR1 = GameAction.valueOf(entity.buttonR1),
    buttonL2 = GameAction.valueOf(entity.buttonL2),
    buttonR2 = GameAction.valueOf(entity.buttonR2),
    buttonStart = GameAction.valueOf(entity.buttonStart),
    buttonSelect = GameAction.valueOf(entity.buttonSelect),
    dpadUp = GameAction.valueOf(entity.dpadUp),
    dpadDown = GameAction.valueOf(entity.dpadDown),
    dpadLeft = GameAction.valueOf(entity.dpadLeft),
    dpadRight = GameAction.valueOf(entity.dpadRight),
    leftStickButton = GameAction.valueOf(entity.leftStickButton),
    rightStickButton = GameAction.valueOf(entity.rightStickButton)
   ),
   vibrationEnabled = entity.vibrationEnabled,
   deadzone = entity.deadzone,
   createdAt = entity.createdAt,
   lastUsedAt = entity.lastUsedAt
  )
 }

 /**
  * Domain modelEntity conversion
  */
 override fun toEntity(domain: ControllerProfile): ControllerProfileEntity {
  return ControllerProfileEntity(
   id = domain.id,
   controllerId = domain.controllerId,
   name = domain.name,
   buttonA = domain.buttonMapping.buttonA.name,
   buttonB = domain.buttonMapping.buttonB.name,
   buttonX = domain.buttonMapping.buttonX.name,
   buttonY = domain.buttonMapping.buttonY.name,
   buttonL1 = domain.buttonMapping.buttonL1.name,
   buttonR1 = domain.buttonMapping.buttonR1.name,
   buttonL2 = domain.buttonMapping.buttonL2.name,
   buttonR2 = domain.buttonMapping.buttonR2.name,
   buttonStart = domain.buttonMapping.buttonStart.name,
   buttonSelect = domain.buttonMapping.buttonSelect.name,
   dpadUp = domain.buttonMapping.dpadUp.name,
   dpadDown = domain.buttonMapping.dpadDown.name,
   dpadLeft = domain.buttonMapping.dpadLeft.name,
   dpadRight = domain.buttonMapping.dpadRight.name,
   leftStickButton = domain.buttonMapping.leftStickButton.name,
   rightStickButton = domain.buttonMapping.rightStickButton.name,
   vibrationEnabled = domain.vibrationEnabled,
   deadzone = domain.deadzone,
   createdAt = domain.createdAt,
   lastUsedAt = domain.lastUsedAt
  )
 }

 // toDomainList and toEntityList are inherited from BaseMapper
}

// Extension functions for backward compatibility (delegates to ControllerMapper)
fun ControllerProfileEntity.toDomain(): ControllerProfile = ControllerMapper.toDomain(this)
fun ControllerProfile.toEntity(): ControllerProfileEntity = ControllerMapper.toEntity(this)
