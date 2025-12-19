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
 val isFavorite: Boolean = false
) {
 /**
  * play time時間単位 retrieve
  */
 val playTimeHours: Double
  get() = playTimeMinutes / 60.0

 /**
  * play time人間 読み すい形式 retrieve
  * Example: "2h 30m", "45m"
  */
 val playTimeFormatted: String
  get() {
   if (playTimeMinutes == 0L) return "未プレイ"
   val hours = playTimeMinutes / 60
   val minutes = playTimeMinutes % 60
   return when {
    hours > 0 && minutes > 0 -> "${hours}時間${minutes}minutes"
    hours > 0 -> "${hours}時間"
    else -> "${minutes}minutes"
   }
  }

 /**
  * 最終プレイdate and time人間 読み すい形式 retrieve
  */
 val lastPlayedFormatted: String
  get() {
   if (lastPlayedTimestamp == null) return "プレイ履歴なし"
   val now = System.currentTimeMillis()
   val diff = now - lastPlayedTimestamp
   val days = diff / (1000 * 60 * 60 * 24)
   val hours = diff / (1000 * 60 * 60)
   val minutes = diff / (1000 * 60)

   return when {
    minutes < 60 -> "${minutes}minutes前"
    hours < 24 -> "${hours}時間前"
    days < 30 -> "${days}日前"
    else -> {
     val date = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.JAPAN)
      .format(java.util.Date(lastPlayedTimestamp))
     date
    }
   }
  }
}

/**
 * game ソース種別
 */
enum class GameSource {
 /** Steamlibraryfrom同期 */
 STEAM,

 /** ユーザー 手動 インポート */
 IMPORTED
}
