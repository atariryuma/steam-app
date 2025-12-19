package com.steamdeck.mobile.data.repository

import com.steamdeck.mobile.data.local.database.dao.WinlatorContainerDao
import com.steamdeck.mobile.data.mapper.WinlatorContainerMapper
import com.steamdeck.mobile.domain.model.WinlatorContainer
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * WinlatorContainerRepository implementation
 */
class WinlatorContainerRepositoryImpl @Inject constructor(
 private val containerDao: WinlatorContainerDao
) : WinlatorContainerRepository {

 override fun getAllContainers(): Flow<List<WinlatorContainer>> {
  return containerDao.getAllContainers().map { entities ->
   WinlatorContainerMapper.toDomainList(entities)
  }
 }

 override suspend fun getContainerById(containerId: Long): WinlatorContainer? {
  return containerDao.getContainerById(containerId)?.let { entity ->
   WinlatorContainerMapper.toDomain(entity)
  }
 }

 override fun searchContainers(query: String): Flow<List<WinlatorContainer>> {
  return containerDao.searchContainers(query).map { entities ->
   WinlatorContainerMapper.toDomainList(entities)
  }
 }

 override suspend fun insertContainer(container: WinlatorContainer): Long {
  val entity = WinlatorContainerMapper.toEntity(container)
  return containerDao.insertContainer(entity)
 }

 override suspend fun updateContainer(container: WinlatorContainer) {
  val entity = WinlatorContainerMapper.toEntity(container)
  containerDao.updateContainer(entity)
 }

 override suspend fun deleteContainer(container: WinlatorContainer) {
  val entity = WinlatorContainerMapper.toEntity(container)
  containerDao.deleteContainer(entity)
 }

 override suspend fun deleteAllContainers() {
  containerDao.deleteAllContainers()
 }
}
