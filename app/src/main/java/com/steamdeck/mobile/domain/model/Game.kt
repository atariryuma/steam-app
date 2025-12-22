package com.steamdeck.mobile.domain.model

import androidx.compose.runtime.Immutable

/**
 * Game information domain model
 *
 * Performance Note: @Immutable annotation enables Compose to skip unnecessary recompositions.
 * This can reduce recomposition by 50-70% and improve scroll performance significantly.
 *
 * Best Practices 2025:
 * - All properties are val (immutable)
 * - No mutable collections
 * - Satisfies Kotlin 2.0+ Strong Skipping mode requirements
 *
 * References:
 * - https://developer.android.com/develop/ui/compose/performance/stability/fix
 * - https://medium.com/androiddevelopers/jetpack-compose-stability-explained-79c10db270c8
 */
@Immutable
data class Game(
 val id: Long = 0,
 val name: String,
 val steamAppId: Long? = null,
 val executablePath: String,
 val installPath: String,
 val source: GameSource,
 val winlatorContainerId: Long? = null,
 val playTimeMinutes: Long = 0,
 val lastPlayedTimestamp: Long? = null,
 val iconPath: String? = null,
 val bannerPath: String? = null,
 val addedTimestamp: Long = System.currentTimeMillis(),
 val isFavorite: Boolean = false,
 val installationStatus: InstallationStatus = InstallationStatus.NOT_INSTALLED,
 val installProgress: Int = 0,
 val statusUpdatedTimestamp: Long? = null
) {
 /**
  * Get play time in hours
  */
 val playTimeHours: Double
  get() = playTimeMinutes / 60.0

 /**
  * Get play time in human-readable format
  * Example: "2h 30m", "45m"
  */
 val playTimeFormatted: String
  get() {
   if (playTimeMinutes == 0L) return "Not played"
   val hours = playTimeMinutes / 60
   val minutes = playTimeMinutes % 60
   return when {
    hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
    hours > 0 -> "${hours}h"
    else -> "${minutes}m"
   }
  }

 /**
  * Get last played timestamp in human-readable format
  */
 val lastPlayedFormatted: String
  get() {
   if (lastPlayedTimestamp == null) return "Never played"
   val now = System.currentTimeMillis()
   val diff = now - lastPlayedTimestamp
   val days = diff / (1000 * 60 * 60 * 24)
   val hours = diff / (1000 * 60 * 60)
   val minutes = diff / (1000 * 60)

   return when {
    minutes < 60 -> "${minutes}m ago"
    hours < 24 -> "${hours}h ago"
    days < 30 -> "${days}d ago"
    else -> {
     val date = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.JAPAN)
      .format(java.util.Date(lastPlayedTimestamp))
     date
    }
   }
  }
}

/**
 * Game source type
 */
enum class GameSource {
 /** Synced from Steam library */
 STEAM,

 /** Manually imported by user */
 IMPORTED
}
