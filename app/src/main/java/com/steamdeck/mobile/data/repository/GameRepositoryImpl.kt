package com.steamdeck.mobile.data.repository

import com.steamdeck.mobile.data.local.database.dao.GameDao
import com.steamdeck.mobile.data.mapper.GameMapper
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.steamdeck.mobile.data.local.database.entity.GameSource as EntityGameSource

/**
 * GameRepository implementation
 */
class GameRepositoryImpl @Inject constructor(
 private val gameDao: GameDao
) : GameRepository {

 override fun getAllGames(): Flow<List<Game>> {
  return gameDao.getAllGames().map { entities ->
   GameMapper.toDomainList(entities)
  }
 }

 override fun getFavoriteGames(): Flow<List<Game>> {
  return gameDao.getFavoriteGames().map { entities ->
   GameMapper.toDomainList(entities)
  }
 }

 override suspend fun getGameById(gameId: Long): Game? {
  return gameDao.getGameById(gameId)?.let { entity ->
   GameMapper.toDomain(entity)
  }
 }

 override suspend fun getGameBySteamAppId(steamAppId: Long): Game? {
  return gameDao.getGameBySteamAppId(steamAppId)?.let { entity ->
   GameMapper.toDomain(entity)
  }
 }

 override fun searchGames(query: String): Flow<List<Game>> {
  return gameDao.searchGames(query).map { entities ->
   GameMapper.toDomainList(entities)
  }
 }

 override fun getGamesBySource(source: GameSource): Flow<List<Game>> {
  val entitySource = when (source) {
   GameSource.STEAM -> EntityGameSource.STEAM
   GameSource.IMPORTED -> EntityGameSource.IMPORTED
  }
  return gameDao.getGamesBySource(entitySource).map { entities ->
   GameMapper.toDomainList(entities)
  }
 }

 override suspend fun insertGame(game: Game): Long {
  val entity = GameMapper.toEntity(game)
  return gameDao.insertGame(entity)
 }

 override suspend fun insertGames(games: List<Game>) {
  val entities = GameMapper.toEntityList(games)
  gameDao.insertGames(entities)
 }

 override suspend fun updateGame(game: Game) {
  val entity = GameMapper.toEntity(game)
  gameDao.updateGame(entity)
 }

 override suspend fun deleteGame(game: Game) {
  val entity = GameMapper.toEntity(game)
  gameDao.deleteGame(entity)
 }

 override suspend fun updatePlayTime(gameId: Long, additionalMinutes: Long, timestamp: Long) {
  gameDao.updatePlayTime(gameId, additionalMinutes, timestamp)
 }

 override suspend fun updateFavoriteStatus(gameId: Long, isFavorite: Boolean) {
  gameDao.updateFavoriteStatus(gameId, isFavorite)
 }

 override suspend fun deleteAllGames() {
  gameDao.deleteAllGames()
 }

 override suspend fun updateInstallationStatus(
  gameId: Long,
  status: InstallationStatus,
  progress: Int
 ) {
  gameDao.updateInstallationStatus(
   gameId = gameId,
   status = status.name,
   progress = progress,
   timestamp = System.currentTimeMillis()
  )
 }

 override fun observeGame(gameId: Long): Flow<Game?> {
  return gameDao.observeGame(gameId).map { entity ->
   entity?.let { GameMapper.toDomain(it) }
  }
 }

 override fun getGamesByInstallationStatus(status: InstallationStatus): Flow<List<Game>> {
  return gameDao.getGamesByInstallationStatus(status.name).map { entities ->
   GameMapper.toDomainList(entities)
  }
 }
}
