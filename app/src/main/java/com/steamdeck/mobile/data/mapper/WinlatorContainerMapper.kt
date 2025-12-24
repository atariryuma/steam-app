package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity
import com.steamdeck.mobile.domain.model.WinlatorContainer
import com.steamdeck.mobile.domain.model.Box64Preset as DomainBox64Preset
import com.steamdeck.mobile.data.local.database.entity.Box64Preset as EntityBox64Preset

/**
 * WinlatorContainerEntity <-> WinlatorContainer mapper
 * Implements BaseMapper to reduce boilerplate for list conversions
 */
object WinlatorContainerMapper : BaseMapper<WinlatorContainerEntity, WinlatorContainer> {
 /**
  * EntityDomain model conversion
  */
 override fun toDomain(entity: WinlatorContainerEntity): WinlatorContainer {
  return WinlatorContainer(
   id = entity.id,
   name = entity.name,
   box64Preset = entity.box64Preset.toDomain(),
   wineVersion = entity.wineVersion,
   environmentVars = WinlatorContainer.parseEnvironmentVars(entity.environmentVars),
   screenResolution = entity.screenResolution,
   enableDXVK = entity.enableDXVK,
   enableD3DExtras = entity.enableD3DExtras,
   customArgs = entity.customArgs,
   createdTimestamp = entity.createdTimestamp
  )
 }

 /**
  * Domain modelEntity conversion
  */
 override fun toEntity(domain: WinlatorContainer): WinlatorContainerEntity {
  return WinlatorContainerEntity(
   id = domain.id,
   name = domain.name,
   box64Preset = domain.box64Preset.toEntity(),
   wineVersion = domain.wineVersion,
   environmentVars = domain.environmentVarsToJson(),
   screenResolution = domain.screenResolution,
   enableDXVK = domain.enableDXVK,
   enableD3DExtras = domain.enableD3DExtras,
   customArgs = domain.customArgs,
   createdTimestamp = domain.createdTimestamp
  )
 }

 // toDomainList and toEntityList are inherited from BaseMapper

 /**
  * EntityBox64PresetDomainBox64Preset conversion
  * Uses generic enum mapper to reduce boilerplate
  */
 private fun EntityBox64Preset.toDomain(): DomainBox64Preset = mapByName()

 /**
  * DomainBox64PresetEntityBox64Preset conversion
  * Uses generic enum mapper to reduce boilerplate
  */
 private fun DomainBox64Preset.toEntity(): EntityBox64Preset = mapByName()
}
