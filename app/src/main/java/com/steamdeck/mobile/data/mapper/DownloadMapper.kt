package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus as DomainDownloadStatus
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus as EntityDownloadStatus
import com.steamdeck.mobile.domain.model.InstallationStatus as DomainInstallationStatus
import com.steamdeck.mobile.data.local.database.entity.InstallationStatus as EntityInstallationStatus

/**
 * DownloadEntity <-> Download mapper
 */
object DownloadMapper {
 /**
  * EntityDomain model conversion
  */
 fun toDomain(entity: DownloadEntity): Download {
  return Download(
   id = entity.id,
   gameId = entity.gameId,
   fileName = entity.fileName,
   url = entity.url,
   status = entity.status.toDomain(),
   installationStatus = entity.installationStatus.toDomain(),
   progress = entity.progress,
   downloadedBytes = entity.downloadedBytes,
   totalBytes = entity.totalBytes,
   destinationPath = entity.destinationPath,
   startedTimestamp = entity.startedTimestamp,
   completedTimestamp = entity.completedTimestamp,
   errorMessage = entity.errorMessage
  )
 }

 /**
  * Domain modelEntity conversion
  */
 fun toEntity(domain: Download): DownloadEntity {
  return DownloadEntity(
   id = domain.id,
   gameId = domain.gameId,
   fileName = domain.fileName,
   url = domain.url,
   status = domain.status.toEntity(),
   installationStatus = domain.installationStatus.toEntity(),
   progress = domain.progress,
   downloadedBytes = domain.downloadedBytes,
   totalBytes = domain.totalBytes,
   destinationPath = domain.destinationPath,
   startedTimestamp = domain.startedTimestamp,
   completedTimestamp = domain.completedTimestamp,
   errorMessage = domain.errorMessage
  )
 }

 /**
  * Entity listDomain model list conversion
  */
 fun toDomainList(entities: List<DownloadEntity>): List<Download> {
  return entities.map { toDomain(it) }
 }

 /**
  * Domain model listEntity list conversion
  */
 fun toEntityList(domains: List<Download>): List<DownloadEntity> {
  return domains.map { toEntity(it) }
 }

 /**
  * EntityDownloadStatusDomainDownloadStatus conversion
  */
 private fun EntityDownloadStatus.toDomain(): DomainDownloadStatus {
  return when (this) {
   EntityDownloadStatus.PENDING -> DomainDownloadStatus.PENDING
   EntityDownloadStatus.DOWNLOADING -> DomainDownloadStatus.DOWNLOADING
   EntityDownloadStatus.PAUSED -> DomainDownloadStatus.PAUSED
   EntityDownloadStatus.COMPLETED -> DomainDownloadStatus.COMPLETED
   EntityDownloadStatus.FAILED -> DomainDownloadStatus.FAILED
   EntityDownloadStatus.CANCELLED -> DomainDownloadStatus.CANCELLED
  }
 }

 /**
  * DomainDownloadStatusEntityDownloadStatus conversion
  */
 private fun DomainDownloadStatus.toEntity(): EntityDownloadStatus {
  return when (this) {
   DomainDownloadStatus.PENDING -> EntityDownloadStatus.PENDING
   DomainDownloadStatus.DOWNLOADING -> EntityDownloadStatus.DOWNLOADING
   DomainDownloadStatus.PAUSED -> EntityDownloadStatus.PAUSED
   DomainDownloadStatus.COMPLETED -> EntityDownloadStatus.COMPLETED
   DomainDownloadStatus.FAILED -> EntityDownloadStatus.FAILED
   DomainDownloadStatus.CANCELLED -> EntityDownloadStatus.CANCELLED
  }
 }

 /**
  * EntityInstallationStatusDomainInstallationStatus conversion
  */
 private fun EntityInstallationStatus.toDomain(): DomainInstallationStatus {
  return when (this) {
   EntityInstallationStatus.NOT_INSTALLED -> DomainInstallationStatus.NOT_INSTALLED
   EntityInstallationStatus.PENDING -> DomainInstallationStatus.INSTALLING // Map PENDING to INSTALLING
   EntityInstallationStatus.INSTALLING -> DomainInstallationStatus.INSTALLING
   EntityInstallationStatus.INSTALLED -> DomainInstallationStatus.INSTALLED
   EntityInstallationStatus.FAILED -> DomainInstallationStatus.VALIDATION_FAILED // Map FAILED to VALIDATION_FAILED
  }
 }

 /**
  * DomainInstallationStatusEntityInstallationStatus conversion
  */
 private fun DomainInstallationStatus.toEntity(): EntityInstallationStatus {
  return when (this) {
   DomainInstallationStatus.NOT_INSTALLED -> EntityInstallationStatus.NOT_INSTALLED
   DomainInstallationStatus.DOWNLOADING -> EntityInstallationStatus.PENDING // Map DOWNLOADING to PENDING
   DomainInstallationStatus.INSTALLING -> EntityInstallationStatus.INSTALLING
   DomainInstallationStatus.INSTALLED -> EntityInstallationStatus.INSTALLED
   DomainInstallationStatus.VALIDATION_FAILED -> EntityInstallationStatus.FAILED // Map VALIDATION_FAILED to FAILED
   DomainInstallationStatus.UPDATE_REQUIRED -> EntityInstallationStatus.NOT_INSTALLED // Map UPDATE_REQUIRED to NOT_INSTALLED
   DomainInstallationStatus.UPDATE_PAUSED -> EntityInstallationStatus.PENDING // Map UPDATE_PAUSED to PENDING
  }
 }
}
