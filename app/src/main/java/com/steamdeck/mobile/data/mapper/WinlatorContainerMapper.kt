package com.steamdeck.mobile.data.mapper

import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity
import com.steamdeck.mobile.domain.model.WinlatorContainer
import com.steamdeck.mobile.domain.model.Box64Preset as DomainBox64Preset
import com.steamdeck.mobile.data.local.database.entity.Box64Preset as EntityBox64Preset

/**
 * WinlatorContainerEntity <-> WinlatorContainer のマッパー
 */
object WinlatorContainerMapper {
    /**
     * EntityをDomain modelに変換
     */
    fun toDomain(entity: WinlatorContainerEntity): WinlatorContainer {
        return WinlatorContainer(
            id = entity.id,
            name = entity.name,
            box64Preset = entity.box64Preset.toDomain(),
            wineVersion = entity.wineVersion,
            environmentVars = WinlatorContainer.parseEnvironmentVars(entity.environmentVars),
            screenResolution = entity.screenResolution,
            enableDXVK = entity.enableDXVK,
            enableD3DExtras = entity.enableD3DExtras,
            customArgs = entity.customArgs,
            createdTimestamp = entity.createdTimestamp
        )
    }

    /**
     * Domain modelをEntityに変換
     */
    fun toEntity(domain: WinlatorContainer): WinlatorContainerEntity {
        return WinlatorContainerEntity(
            id = domain.id,
            name = domain.name,
            box64Preset = domain.box64Preset.toEntity(),
            wineVersion = domain.wineVersion,
            environmentVars = domain.environmentVarsToJson(),
            screenResolution = domain.screenResolution,
            enableDXVK = domain.enableDXVK,
            enableD3DExtras = domain.enableD3DExtras,
            customArgs = domain.customArgs,
            createdTimestamp = domain.createdTimestamp
        )
    }

    /**
     * Entity listをDomain model listに変換
     */
    fun toDomainList(entities: List<WinlatorContainerEntity>): List<WinlatorContainer> {
        return entities.map { toDomain(it) }
    }

    /**
     * Domain model listをEntity listに変換
     */
    fun toEntityList(domains: List<WinlatorContainer>): List<WinlatorContainerEntity> {
        return domains.map { toEntity(it) }
    }

    /**
     * EntityBox64PresetをDomainBox64Presetに変換
     */
    private fun EntityBox64Preset.toDomain(): DomainBox64Preset {
        return when (this) {
            EntityBox64Preset.PERFORMANCE -> DomainBox64Preset.PERFORMANCE
            EntityBox64Preset.STABILITY -> DomainBox64Preset.STABILITY
            EntityBox64Preset.CUSTOM -> DomainBox64Preset.CUSTOM
        }
    }

    /**
     * DomainBox64PresetをEntityBox64Presetに変換
     */
    private fun DomainBox64Preset.toEntity(): EntityBox64Preset {
        return when (this) {
            DomainBox64Preset.PERFORMANCE -> EntityBox64Preset.PERFORMANCE
            DomainBox64Preset.STABILITY -> EntityBox64Preset.STABILITY
            DomainBox64Preset.CUSTOM -> EntityBox64Preset.CUSTOM
        }
    }
}
