package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.data.remote.steam.model.AuthSessionInfo
import com.steamdeck.mobile.data.remote.steam.model.BeginAuthSessionData
import com.steamdeck.mobile.domain.model.SteamAuthResult
import kotlinx.coroutines.flow.Flow

/**
 * Steam認証リポジトリインターフェース
 *
 * Domain層のRepository interface
 * Clean Architecture Best Practice
 */
interface SteamAuthRepository {

    /**
     * QR認証セッションを開始
     *
     * @param deviceName デバイス名
     * @return BeginAuthSessionData (client_id, challenge_url)
     */
    suspend fun beginAuthSessionViaQR(deviceName: String): Result<BeginAuthSessionData>

    /**
     * 認証セッションのステータスをポーリング
     *
     * Best Practice: Flow-based polling with exponential backoff
     * Reference: https://www.valueof.io/blog/kotlin-flow-retry-policy-with-exponential-backoff
     *
     * @param clientId クライアントID
     * @param requestId リクエストID
     * @param timeoutSeconds タイムアウト（秒）デフォルト30秒
     * @return Flow<SteamAuthResult> - 認証結果（ステータス + トークン）
     */
    fun pollAuthSessionStatus(
        clientId: String,
        requestId: String,
        timeoutSeconds: Int = 30
    ): Flow<SteamAuthResult>

    /**
     * 認証セッション情報を取得（位置情報）
     *
     * @param clientId クライアントID
     * @return AuthSessionInfo (IP, 位置情報)
     */
    suspend fun getAuthSessionInfo(clientId: String): Result<AuthSessionInfo>
}
