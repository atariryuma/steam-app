package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.WinlatorContainer
import kotlinx.coroutines.flow.Flow

/**
 * Winlatorcontainermanagementリポジトリ interface
 */
interface WinlatorContainerRepository {
 /**
  * all containerretrieve
  */
 fun getAllContainers(): Flow<List<WinlatorContainer>>

 /**
  * containerID containerretrieve
  */
 suspend fun getContainerById(containerId: Long): WinlatorContainer?

 /**
  * container名 container検索
  */
 fun searchContainers(query: String): Flow<List<WinlatorContainer>>

 /**
  * containeradd
  */
 suspend fun insertContainer(container: WinlatorContainer): Long

 /**
  * containerupdate
  */
 suspend fun updateContainer(container: WinlatorContainer)

 /**
  * containerdelete
  */
 suspend fun deleteContainer(container: WinlatorContainer)

 /**
  * all containerdelete
  */
 suspend fun deleteAllContainers()
}
