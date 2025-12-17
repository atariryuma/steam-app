package com.steamdeck.mobile.domain.model

import com.steamdeck.mobile.data.remote.steam.model.SteamAuthSessionStatus

/**
 * Steam認証結果
 *
 * Domain層のモデル - Clean Architecture Best Practice
 *
 * @property status 認証セッションステータス
 * @property accessToken JWT形式のアクセストークン
 * @property refreshToken リフレッシュトークン
 * @property accountName Steamアカウント名
 * @property steamId Steam ID (steamid64) - 17桁の数値ID
 * @property newClientId 新しいクライアントID（セッション更新時）
 * @property newChallengeUrl 新しいチャレンジURL（セッション更新時）
 */
data class SteamAuthResult(
    val status: SteamAuthSessionStatus,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accountName: String? = null,
    val steamId: String? = null,
    val newClientId: String? = null,
    val newChallengeUrl: String? = null
)
