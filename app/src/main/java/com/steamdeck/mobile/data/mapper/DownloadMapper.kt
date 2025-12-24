package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus as DomainDownloadStatus
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus as EntityDownloadStatus
import com.steamdeck.mobile.domain.model.InstallationStatus as DomainInstallationStatus
import com.steamdeck.mobile.data.local.database.entity.InstallationStatus as EntityInstallationStatus

/**
 * DownloadEntity <-> Download mapper
 * Implements BaseMapper to reduce boilerplate for list conversions
 */
object DownloadMapper : BaseMapper<DownloadEntity, Download> {
 /**
  * EntityDomain model conversion
  */
 override fun toDomain(entity: DownloadEntity): Download {
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
 override fun toEntity(domain: Download): DownloadEntity {
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

 // toDomainList and toEntityList are inherited from BaseMapper

 /**
  * EntityDownloadStatusDomainDownloadStatus conversion
  * Uses generic enum mapper to reduce boilerplate
  */
 private fun EntityDownloadStatus.toDomain(): DomainDownloadStatus = mapByName()

 /**
  * DomainDownloadStatusEntityDownloadStatus conversion
  * Uses generic enum mapper to reduce boilerplate
  */
 private fun DomainDownloadStatus.toEntity(): EntityDownloadStatus = mapByName()

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
