package com.steamdeck.mobile.data.mapper

/**
 * Generic enum mapper utility for converting enums with identical names.
 * Reduces boilerplate when mapping entity enums to domain enums.
 *
 * Usage:
 * ```
 * private fun EntityStatus.toDomain(): DomainStatus = mapByName()
 * private fun DomainStatus.toEntity(): EntityStatus = mapByName()
 * ```
 */
inline fun <reified D : Enum<D>> Enum<*>.mapByName(): D {
    return enumValueOf(this.name)
}

/**
 * Safe enum mapper with fallback value.
 * Returns fallback if enum name doesn't exist in target type.
 *
 * Usage:
 * ```
 * private fun EntityStatus.toDomain(): DomainStatus =
 *     mapByNameOrDefault(DomainStatus.UNKNOWN)
 * ```
 */
inline fun <reified D : Enum<D>> Enum<*>.mapByNameOrDefault(default: D): D {
    return try {
        enumValueOf(this.name)
    } catch (e: IllegalArgumentException) {
        default
    }
}
