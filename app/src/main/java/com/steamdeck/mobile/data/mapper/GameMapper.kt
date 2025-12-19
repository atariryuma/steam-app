package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.local.database.entity.GameEntity
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource as DomainGameSource
import com.steamdeck.mobile.data.local.database.entity.GameSource as EntityGameSource

/**
 * GameEntity <-> Game マッパー
 */
object GameMapper {
 /**
  * EntityDomain model conversion
  */
 fun toDomain(entity: GameEntity): Game {
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
   isFavorite = entity.isFavorite
  )
 }

 /**
  * Domain modelEntity conversion
  */
 fun toEntity(domain: Game): GameEntity {
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
   isFavorite = domain.isFavorite
  )
 }

 /**
  * Entity listDomain model list conversion
  */
 fun toDomainList(entities: List<GameEntity>): List<Game> {
  return entities.map { toDomain(it) }
 }

 /**
  * Domain model listEntity list conversion
  */
 fun toEntityList(domains: List<Game>): List<GameEntity> {
  return domains.map { toEntity(it) }
 }

 /**
  * EntityGameSourceDomainGameSource conversion
  */
 private fun EntityGameSource.toDomain(): DomainGameSource {
  return when (this) {
   EntityGameSource.STEAM -> DomainGameSource.STEAM
   EntityGameSource.IMPORTED -> DomainGameSource.IMPORTED
  }
 }

 /**
  * DomainGameSourceEntityGameSource conversion
  */
 private fun DomainGameSource.toEntity(): EntityGameSource {
  return when (this) {
   DomainGameSource.STEAM -> EntityGameSource.STEAM
   DomainGameSource.IMPORTED -> EntityGameSource.IMPORTED
  }
 }
}
