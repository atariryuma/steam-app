package com.steamdeck.mobile.core.winlator

/**
 * Container ID Type Mapping Utilities
 *
 * Handles conversion between database Long IDs and filesystem String IDs.
 *
 * Architecture:
 * - Database Layer: Uses Long type (-1 for default container)
 * - Filesystem Layer: Uses String type ("default_shared_container")
 * - This mapper provides bidirectional conversion at layer boundaries
 *
 * Mapping Rules:
 * - Default container: -1L ↔ "default_shared_container"
 * - Custom containers: timestamp Long ↔ timestamp String
 */
object ContainerIdMapper {
    /**
     * Default container ID (database representation)
     */
    const val DEFAULT_CONTAINER_ID = -1L

    /**
     * Default container ID (filesystem representation)
     */
    const val DEFAULT_CONTAINER_NAME = "default_shared_container"

    /**
     * Convert database Long ID to filesystem String ID
     *
     * @param longId Database container ID (Long)
     * @return Filesystem container ID (String)
     */
    fun toFilesystemId(longId: Long): String = when (longId) {
        DEFAULT_CONTAINER_ID -> DEFAULT_CONTAINER_NAME
        else -> longId.toString()
    }

    /**
     * Convert filesystem String ID to database Long ID
     *
     * @param stringId Filesystem container ID (String)
     * @return Database container ID (Long), or null if invalid
     */
    fun toDatabaseId(stringId: String): Long? = when (stringId) {
        DEFAULT_CONTAINER_NAME -> DEFAULT_CONTAINER_ID
        else -> stringId.toLongOrNull()
    }

    /**
     * Convert filesystem String ID to database Long ID (throws on invalid)
     *
     * @param stringId Filesystem container ID (String)
     * @return Database container ID (Long)
     * @throws IllegalStateException if stringId is not a valid container ID
     */
    fun toDatabaseIdOrThrow(stringId: String): Long =
        toDatabaseId(stringId) ?: throw IllegalStateException("Invalid container ID: $stringId")
}
