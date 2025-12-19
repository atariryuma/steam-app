package com.steamdeck.mobile.data.repository

import com.steamdeck.mobile.data.local.database.dao.DownloadDao
import com.steamdeck.mobile.data.mapper.DownloadMapper
import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus
import com.steamdeck.mobile.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus as EntityDownloadStatus

/**
 * DownloadRepository implementation
 */
class DownloadRepositoryImpl @Inject constructor(
 private val downloadDao: DownloadDao
) : DownloadRepository {

 override fun getAllDownloads(): Flow<List<Download>> {
  return downloadDao.getAllDownloads().map { entities ->
   DownloadMapper.toDomainList(entities)
  }
 }

 override fun getActiveDownloads(): Flow<List<Download>> {
  return downloadDao.getActiveDownloads().map { entities ->
   DownloadMapper.toDomainList(entities)
  }
 }

 override fun getDownloadsByGameId(gameId: Long): Flow<List<Download>> {
  return downloadDao.getDownloadsByGameId(gameId).map { entities ->
   DownloadMapper.toDomainList(entities)
  }
 }

 override suspend fun getDownloadById(downloadId: Long): Download? {
  return downloadDao.getDownloadByIdDirect(downloadId)?.let { entity ->
   DownloadMapper.toDomain(entity)
  }
 }

 override suspend fun insertDownload(download: Download): Long {
  val entity = DownloadMapper.toEntity(download)
  return downloadDao.insertDownload(entity)
 }

 override suspend fun updateDownload(download: Download) {
  val entity = DownloadMapper.toEntity(download)
  downloadDao.updateDownload(entity)
 }

 override suspend fun deleteDownload(download: Download) {
  downloadDao.deleteDownload(download.id)
 }

 override suspend fun updateDownloadProgress(
  downloadId: Long,
  progress: Int,
  downloadedBytes: Long,
  status: DownloadStatus
 ) {
  val entityStatus = when (status) {
   DownloadStatus.PENDING -> EntityDownloadStatus.PENDING
   DownloadStatus.DOWNLOADING -> EntityDownloadStatus.DOWNLOADING
   DownloadStatus.PAUSED -> EntityDownloadStatus.PAUSED
   DownloadStatus.COMPLETED -> EntityDownloadStatus.COMPLETED
   DownloadStatus.FAILED -> EntityDownloadStatus.FAILED
   DownloadStatus.CANCELLED -> EntityDownloadStatus.CANCELLED
  }
  // Update progress (bytes and percentage) and status
  downloadDao.updateDownloadProgress(
   downloadId = downloadId,
   downloadedBytes = downloadedBytes,
   progress = progress
  )
  downloadDao.updateDownloadStatus(downloadId, entityStatus)
 }

 override suspend fun markDownloadCompleted(downloadId: Long, status: DownloadStatus, completedTimestamp: Long) {
  val entityStatus = when (status) {
   DownloadStatus.PENDING -> EntityDownloadStatus.PENDING
   DownloadStatus.DOWNLOADING -> EntityDownloadStatus.DOWNLOADING
   DownloadStatus.PAUSED -> EntityDownloadStatus.PAUSED
   DownloadStatus.COMPLETED -> EntityDownloadStatus.COMPLETED
   DownloadStatus.FAILED -> EntityDownloadStatus.FAILED
   DownloadStatus.CANCELLED -> EntityDownloadStatus.CANCELLED
  }
  downloadDao.markDownloadCompleted(downloadId, entityStatus, completedTimestamp)
 }

 override suspend fun markDownloadError(downloadId: Long, status: DownloadStatus, errorMessage: String) {
  val entityStatus = when (status) {
   DownloadStatus.PENDING -> EntityDownloadStatus.PENDING
   DownloadStatus.DOWNLOADING -> EntityDownloadStatus.DOWNLOADING
   DownloadStatus.PAUSED -> EntityDownloadStatus.PAUSED
   DownloadStatus.COMPLETED -> EntityDownloadStatus.COMPLETED
   DownloadStatus.FAILED -> EntityDownloadStatus.FAILED
   DownloadStatus.CANCELLED -> EntityDownloadStatus.CANCELLED
  }
  downloadDao.markDownloadError(downloadId, entityStatus, errorMessage)
 }

 override suspend fun deleteCompletedDownloads() {
  downloadDao.deleteCompletedDownloads(EntityDownloadStatus.COMPLETED)
 }

 override suspend fun deleteAllDownloads() {
  downloadDao.deleteAllDownloads()
 }
}
