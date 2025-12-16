package com.steamdeck.mobile.domain.model

/**
 * ゲーム情報のドメインモデル
 */
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
     * プレイ時間を時間単位で取得
     */
    val playTimeHours: Double
        get() = playTimeMinutes / 60.0

    /**
     * プレイ時間を人間が読みやすい形式で取得
     * 例: "2h 30m", "45m"
     */
    val playTimeFormatted: String
        get() {
            if (playTimeMinutes == 0L) return "未プレイ"
            val hours = playTimeMinutes / 60
            val minutes = playTimeMinutes % 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}時間${minutes}分"
                hours > 0 -> "${hours}時間"
                else -> "${minutes}分"
            }
        }

    /**
     * 最終プレイ日時を人間が読みやすい形式で取得
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
                minutes < 60 -> "${minutes}分前"
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
 * ゲームのソース種別
 */
enum class GameSource {
    /** Steamライブラリから同期 */
    STEAM,

    /** ユーザーが手動でインポート */
    IMPORTED
}
