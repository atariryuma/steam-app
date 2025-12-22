package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.WinlatorContainer
import kotlinx.coroutines.flow.Flow

/**
 * Winlator container management repository interface
 */
interface WinlatorContainerRepository {
 /**
  * Retrieve all containers
  */
 fun getAllContainers(): Flow<List<WinlatorContainer>>

 /**
  * Retrieve container by ID
  */
 suspend fun getContainerById(containerId: Long): WinlatorContainer?

 /**
  * Search containers by name
  */
 fun searchContainers(query: String): Flow<List<WinlatorContainer>>

 /**
  * Add container
  */
 suspend fun insertContainer(container: WinlatorContainer): Long

 /**
  * Update container
  */
 suspend fun updateContainer(container: WinlatorContainer)

 /**
  * Delete container
  */
 suspend fun deleteContainer(container: WinlatorContainer)

 /**
  * Delete all containers
  */
 suspend fun deleteAllContainers()
}
