package com.steamdeck.mobile.data.remote.steam.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Steam IAuthenticationService API models
 *
 * Reference: https://steamapi.xpaw.me/
 * API Endpoints:
 * - IAuthenticationService/BeginAuthSessionViaQR
 * - IAuthenticationService/PollAuthSessionStatus
 * - IAuthenticationService/UpdateAuthSessionWithMobileConfirmation
 *
 * Best Practice: Kotlinx Serializationを使用してJSONパース
 * Reference: https://kotlinlang.org/docs/serialization.html
 */

/**
 * QR認証セッション開始リクエスト
 */
@Serializable
data class BeginAuthSessionViaQRRequest(
    @SerialName("device_friendly_name")
    val deviceFriendlyName: String,

    @SerialName("platform_type")
    val platformType: Int = 3, // 3 = MobileApp (1=SteamClient, 2=WebBrowser, 3=MobileApp)

    @SerialName("device_details")
    val deviceDetails: String,

    @SerialName("website_id")
    val websiteId: String = "Mobile"
)

/**
 * パスワード認証セッション開始リクエスト
 */
@Serializable
data class BeginAuthSessionViaCredentialsRequest(
    @SerialName("account_name")
    val accountName: String,

    @SerialName("encrypted_password")
    val encryptedPassword: String,

    @SerialName("encryption_timestamp")
    val encryptionTimestamp: String,

    @SerialName("remember_login")
    val rememberLogin: Boolean = true,

    @SerialName("platform_type")
    val platformType: Int = 3, // 3 = MobileApp

    @SerialName("device_friendly_name")
    val deviceFriendlyName: String,

    @SerialName("device_details")
    val deviceDetails: String,

    @SerialName("website_id")
    val websiteId: String = "Mobile"
)

/**
 * QR認証セッション開始レスポンス
 */
@Serializable
data class BeginAuthSessionViaQRResponse(
    @SerialName("response")
    val response: BeginAuthSessionData
)

@Serializable
data class BeginAuthSessionData(
    @SerialName("client_id")
    val clientId: String,

    @SerialName("request_id")
    val requestId: String,

    @SerialName("interval")
    val interval: Float = 1.0f, // ポーリング間隔（秒）

    @SerialName("allowed_confirmations")
    val allowedConfirmations: List<AllowedConfirmation>,

    @SerialName("version")
    val version: Int,

    @SerialName("challenge_url")
    val challengeUrl: String
)

@Serializable
data class AllowedConfirmation(
    @SerialName("confirmation_type")
    val confirmationType: Int,

    @SerialName("associated_message")
    val associatedMessage: String? = null
)

/**
 * 認証セッションステータスポーリングリクエスト
 *
 * NOTE: client_id is sent as query parameter, not in body
 */
@Serializable
data class PollAuthSessionStatusRequest(
    @SerialName("request_id")
    val requestId: String
)

/**
 * 認証セッションステータスポーリングレスポンス
 */
@Serializable
data class PollAuthSessionStatusResponse(
    @SerialName("response")
    val response: PollAuthSessionData
)

@Serializable
data class PollAuthSessionData(
    @SerialName("new_client_id")
    val newClientId: String? = null,

    @SerialName("new_challenge_url")
    val newChallengeUrl: String? = null,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    @SerialName("access_token")
    val accessToken: String? = null,

    @SerialName("had_remote_interaction")
    val hadRemoteInteraction: Boolean = false, // QRスキャンされたか

    @SerialName("account_name")
    val accountName: String? = null,

    @SerialName("interval")
    val interval: Float? = null // Steam推奨のポーリング間隔（秒）
)

/**
 * モバイル承認更新リクエスト
 */
@Serializable
data class UpdateAuthSessionWithMobileConfirmationRequest(
    @SerialName("client_id")
    val clientId: String,

    @SerialName("steamid")
    val steamId: String,

    @SerialName("signature")
    val signature: String,

    @SerialName("confirm")
    val confirm: Boolean, // true = 承認, false = 拒否

    @SerialName("version")
    val version: Int = 1,

    @SerialName("persistence")
    val persistence: Int = 1 // 1 = セッション保持
)

/**
 * モバイル承認更新レスポンス
 */
@Serializable
data class UpdateAuthSessionWithMobileConfirmationResponse(
    @SerialName("response")
    val response: UpdateAuthSessionData
)

@Serializable
data class UpdateAuthSessionData(
    @SerialName("success")
    val success: Boolean = false
)

/**
 * 認証セッション情報取得リクエスト
 */
@Serializable
data class GetAuthSessionInfoRequest(
    @SerialName("client_id")
    val clientId: String
)

/**
 * 認証セッション情報取得レスポンス
 */
@Serializable
data class GetAuthSessionInfoResponse(
    @SerialName("response")
    val response: AuthSessionInfo
)

@Serializable
data class AuthSessionInfo(
    @SerialName("ip")
    val ip: String? = null,

    @SerialName("geoloc")
    val geoloc: String? = null, // 地理位置情報

    @SerialName("city")
    val city: String? = null,

    @SerialName("state")
    val state: String? = null,

    @SerialName("country")
    val country: String? = null,

    @SerialName("device_friendly_name")
    val deviceFriendlyName: String? = null,

    @SerialName("platform_type")
    val platformType: Int = 0,

    @SerialName("version")
    val version: Int = 0
)

/**
 * Steam認証セッションの状態
 */
enum class SteamAuthSessionStatus {
    /** 初期状態：QRコード表示待機中 */
    WAITING_FOR_QR_SCAN,

    /** QRスキャン済み：ユーザー承認待機中 */
    WAITING_FOR_APPROVAL,

    /** 承認済み：認証完了 */
    APPROVED,

    /** 拒否：認証失敗 */
    DENIED,

    /** タイムアウト：30秒経過 */
    TIMEOUT,

    /** エラー：API通信失敗 */
    ERROR
}
