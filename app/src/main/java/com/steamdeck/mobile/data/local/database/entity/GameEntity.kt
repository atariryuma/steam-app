package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ゲーム情報を格納するエンティティ
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ゲーム名 */
    val name: String,

    /** Steam App ID (Steam統合時に使用) */
    val steamAppId: Long? = null,

    /** 実行可能ファイルのパス (.exe) */
    val executablePath: String,

    /** ゲームのインストールパス */
    val installPath: String,

    /** ゲームのソース (STEAM / IMPORTED) */
    val source: GameSource,

    /** 関連するWinlatorコンテナID */
    val winlatorContainerId: Long? = null,

    /** プレイ時間（分） */
    val playTimeMinutes: Long = 0,

    /** 最後にプレイした日時（Unix timestamp） */
    val lastPlayedTimestamp: Long? = null,

    /** ゲームアイコンのローカルパス */
    val iconPath: String? = null,

    /** ゲームバナーのローカルパス */
    val bannerPath: String? = null,

    /** 追加日時（Unix timestamp） */
    val addedTimestamp: Long = System.currentTimeMillis(),

    /** お気に入りフラグ */
    val isFavorite: Boolean = false
)

/**
 * ゲームのソース種別
 */
enum class GameSource {
    /** Steamライブラリから同期 */
    STEAM,

    /** ユーザーが手動でインポート */
    IMPORTED
}
