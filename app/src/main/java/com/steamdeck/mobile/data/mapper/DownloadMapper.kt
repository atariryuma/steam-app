package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus as DomainDownloadStatus
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus as EntityDownloadStatus
import com.steamdeck.mobile.domain.model.InstallationStatus as DomainInstallationStatus
import com.steamdeck.mobile.data.local.database.entity.InstallationStatus as EntityInstallationStatus

/**
 * DownloadEntity <-> Download のマッパー
 */
object DownloadMapper {
    /**
     * EntityをDomain modelに変換
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
     * Domain modelをEntityに変換
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
     * Entity listをDomain model listに変換
     */
    fun toDomainList(entities: List<DownloadEntity>): List<Download> {
        return entities.map { toDomain(it) }
    }

    /**
     * Domain model listをEntity listに変換
     */
    fun toEntityList(domains: List<Download>): List<DownloadEntity> {
        return domains.map { toEntity(it) }
    }

    /**
     * EntityDownloadStatusをDomainDownloadStatusに変換
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
     * DomainDownloadStatusをEntityDownloadStatusに変換
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
     * EntityInstallationStatusをDomainInstallationStatusに変換
     */
    private fun EntityInstallationStatus.toDomain(): DomainInstallationStatus {
        return when (this) {
            EntityInstallationStatus.NOT_INSTALLED -> DomainInstallationStatus.NOT_INSTALLED
            EntityInstallationStatus.PENDING -> DomainInstallationStatus.PENDING
            EntityInstallationStatus.INSTALLING -> DomainInstallationStatus.INSTALLING
            EntityInstallationStatus.INSTALLED -> DomainInstallationStatus.INSTALLED
            EntityInstallationStatus.FAILED -> DomainInstallationStatus.FAILED
        }
    }

    /**
     * DomainInstallationStatusをEntityInstallationStatusに変換
     */
    private fun DomainInstallationStatus.toEntity(): EntityInstallationStatus {
        return when (this) {
            DomainInstallationStatus.NOT_INSTALLED -> EntityInstallationStatus.NOT_INSTALLED
            DomainInstallationStatus.PENDING -> EntityInstallationStatus.PENDING
            DomainInstallationStatus.INSTALLING -> EntityInstallationStatus.INSTALLING
            DomainInstallationStatus.INSTALLED -> EntityInstallationStatus.INSTALLED
            DomainInstallationStatus.FAILED -> EntityInstallationStatus.FAILED
        }
    }
}
