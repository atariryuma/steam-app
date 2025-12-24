package com.steamdeck.mobile.data.mapper

/**
 * Base interface for all entity-domain mappers.
 * Provides default implementations for list conversions to reduce boilerplate.
 *
 * @param E Entity type (data layer)
 * @param D Domain type (domain layer)
 */
interface BaseMapper<E, D> {
    /**
     * Converts entity to domain model
     */
    fun toDomain(entity: E): D

    /**
     * Converts domain model to entity
     */
    fun toEntity(domain: D): E

    /**
     * Converts list of entities to list of domain models
     * Default implementation uses map + toDomain
     */
    fun toDomainList(entities: List<E>): List<D> = entities.map { toDomain(it) }

    /**
     * Converts list of domain models to list of entities
     * Default implementation uses map + toEntity
     */
    fun toEntityList(domains: List<D>): List<E> = domains.map { toEntity(it) }
}
