package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.data.local.database.entity.GameEntity
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.model.GameSource as DomainGameSource
import com.steamdeck.mobile.data.local.database.entity.GameSource as EntityGameSource

/**
 * GameEntity <-> Game mapper
 * Implements BaseMapper to reduce boilerplate for list conversions
 */
object GameMapper : BaseMapper<GameEntity, Game> {
 /**
  * EntityDomain model conversion
  */
 override fun toDomain(entity: GameEntity): Game {
  return Game(
   id = entity.id,
   name = entity.name,
   steamAppId = entity.steamAppId,
   executablePath = entity.executablePath,
   installPath = entity.installPath,
   source = entity.source.toDomain(),
   winlatorContainerId = entity.winlatorContainerId,
   playTimeMinutes = entity.playTimeMinutes,
   lastPlayedTimestamp = entity.lastPlayedTimestamp,
   iconPath = entity.iconPath,
   bannerPath = entity.bannerPath,
   addedTimestamp = entity.addedTimestamp,
   isFavorite = entity.isFavorite,
   installationStatus = try {
    InstallationStatus.valueOf(entity.installationStatus)
   } catch (e: IllegalArgumentException) {
    AppLogger.w("GameMapper", "Invalid installation status '${entity.installationStatus}', defaulting to NOT_INSTALLED")
    InstallationStatus.NOT_INSTALLED
   },
   installProgress = entity.installProgress,
   statusUpdatedTimestamp = entity.statusUpdatedTimestamp
  )
 }

 /**
  * Domain modelEntity conversion
  */
 override fun toEntity(domain: Game): GameEntity {
  return GameEntity(
   id = domain.id,
   name = domain.name,
   steamAppId = domain.steamAppId,
   executablePath = domain.executablePath,
   installPath = domain.installPath,
   source = domain.source.toEntity(),
   winlatorContainerId = domain.winlatorContainerId,
   playTimeMinutes = domain.playTimeMinutes,
   lastPlayedTimestamp = domain.lastPlayedTimestamp,
   iconPath = domain.iconPath,
   bannerPath = domain.bannerPath,
   addedTimestamp = domain.addedTimestamp,
   isFavorite = domain.isFavorite,
   installationStatus = domain.installationStatus.name,
   installProgress = domain.installProgress,
   statusUpdatedTimestamp = domain.statusUpdatedTimestamp
  )
 }

 // toDomainList and toEntityList are inherited from BaseMapper

 /**
  * EntityGameSourceDomainGameSource conversion
  * Uses generic enum mapper to reduce boilerplate
  */
 private fun EntityGameSource.toDomain(): DomainGameSource = mapByName()

 /**
  * DomainGameSourceEntityGameSource conversion
  * Uses generic enum mapper to reduce boilerplate
  */
 private fun DomainGameSource.toEntity(): EntityGameSource = mapByName()
}
