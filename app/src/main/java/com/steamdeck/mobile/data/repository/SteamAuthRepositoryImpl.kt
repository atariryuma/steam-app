package com.steamdeck.mobile.data.repository

import android.os.Build
import com.steamdeck.mobile.core.auth.JwtDecoder
import com.steamdeck.mobile.data.remote.steam.SteamAuthenticationService
import com.steamdeck.mobile.data.remote.steam.model.AuthSessionInfo
import com.steamdeck.mobile.data.remote.steam.model.BeginAuthSessionData
import com.steamdeck.mobile.data.remote.steam.model.GetAuthSessionInfoRequest
import com.steamdeck.mobile.data.remote.steam.model.SteamAuthSessionStatus
import com.steamdeck.mobile.domain.model.SteamAuthResult
import com.steamdeck.mobile.domain.repository.SteamAuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Steam認証リポジトリ実装
 *
 * Best Practices:
 * - Exponential backoff for polling (https://medium.com/@dharmakshetri/exponential-back-off-in-android-753730d4faeb)
 * - Flow-based polling (https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
 * - 30-second timeout (Steam QR code expiration)
 *
 * Reference: https://github.com/DoctorMcKay/node-steam-session
 */
class SteamAuthRepositoryImpl @Inject constructor(
    private val steamAuthService: SteamAuthenticationService
) : SteamAuthRepository {

    companion object {
        private const val INITIAL_POLL_INTERVAL_MS = 1000L // 1秒（Steam APIのinterval推奨値）
        private const val MAX_POLL_INTERVAL_MS = 5000L     // 最大5秒
        private const val BACKOFF_MULTIPLIER = 1.5          // 指数バックオフ係数
    }

    override suspend fun beginAuthSessionViaQR(deviceName: String): Result<BeginAuthSessionData> {
        return try {
            android.util.Log.d(
                "SteamAuthRepository",
                "🚀 BeginAuthSessionViaQR request: deviceName=$deviceName, platformType=3, websiteId=Mobile"
            )

            val response = steamAuthService.beginAuthSessionViaQR(
                deviceFriendlyName = deviceName,
                platformType = 3, // 3 = MobileApp (1=SteamClient, 2=WebBrowser, 3=MobileApp)
                websiteId = "Mobile"
            )

            if (response.isSuccessful && response.body() != null) {
                val sessionData = response.body()!!.response
                android.util.Log.i(
                    "SteamAuthRepository",
                    "✅ QR session created: clientId=${sessionData.clientId}, " +
                    "requestId=${sessionData.requestId}, " +
                    "challengeUrl=${sessionData.challengeUrl.take(50)}..."
                )
                Result.success(sessionData)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e(
                    "SteamAuthRepository",
                    "❌ BeginAuthSessionViaQR failed: code=${response.code()}, body=$errorBody"
                )
                Result.failure(Exception("Failed to begin auth session: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun pollAuthSessionStatus(
        clientId: String,
        requestId: String,
        timeoutSeconds: Int
    ): Flow<SteamAuthResult> = flow {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeoutSeconds.seconds.inWholeMilliseconds
        var pollInterval = INITIAL_POLL_INTERVAL_MS
        var hadRemoteInteraction = false
        var currentClientId = clientId // ミュータブル変数：セッション更新対応

        android.util.Log.d(
            "SteamAuthRepository",
            "🔄 Starting polling: clientId=$clientId, timeout=${timeoutSeconds}s (${timeoutMillis}ms)"
        )

        emit(SteamAuthResult(status = SteamAuthSessionStatus.WAITING_FOR_QR_SCAN))

        while (true) {
            try {
                val response = steamAuthService.pollAuthSessionStatus(
                    clientId = currentClientId, // Form field for routing
                    requestId = requestId
                )

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.response

                    android.util.Log.d(
                        "SteamAuthRepository",
                        "📡 Poll response: hasAccessToken=${!data.accessToken.isNullOrBlank()}, " +
                        "hadRemoteInteraction=${data.hadRemoteInteraction}, " +
                        "interval=${data.interval}, " +
                        "newClientId=${data.newClientId?.take(10)}..."
                    )

                    // 🔥 優先度1: 認証完了検知（access_tokenが返ってくる）
                    // 他の処理より先にチェックして即座にbreakする
                    if (!data.accessToken.isNullOrBlank()) {
                        // JWTトークンからSteam IDを抽出
                        val steamId = JwtDecoder.extractSteamId(data.accessToken)

                        if (steamId == null) {
                            android.util.Log.w(
                                "SteamAuthRepository",
                                "Failed to extract Steam ID from access token"
                            )
                        }

                        android.util.Log.i(
                            "SteamAuthRepository",
                            "✅ Authentication completed successfully! Steam ID: $steamId, Account: ${data.accountName}"
                        )

                        emit(
                            SteamAuthResult(
                                status = SteamAuthSessionStatus.APPROVED,
                                accessToken = data.accessToken,
                                refreshToken = data.refreshToken,
                                accountName = data.accountName,
                                steamId = steamId
                            )
                        )
                        break  // 即座にループを抜けてFlowを完了
                    }

                    // セッション更新チェック（Steam APIがnew_client_idを返した場合）
                    if (!data.newClientId.isNullOrBlank() && data.newClientId != currentClientId) {
                        currentClientId = data.newClientId
                        android.util.Log.i(
                            "SteamAuthRepository",
                            "Session updated with new client_id"
                        )
                        // 新しいセッション情報をEmit
                        emit(
                            SteamAuthResult(
                                status = SteamAuthSessionStatus.WAITING_FOR_QR_SCAN,
                                newClientId = currentClientId,
                                newChallengeUrl = data.newChallengeUrl
                            )
                        )
                    }

                    // QRスキャン検知
                    if (data.hadRemoteInteraction && !hadRemoteInteraction) {
                        hadRemoteInteraction = true
                        android.util.Log.i(
                            "SteamAuthRepository",
                            "📱 QR code scanned, waiting for approval..."
                        )
                        emit(SteamAuthResult(status = SteamAuthSessionStatus.WAITING_FOR_APPROVAL))
                    }

                    // ポーリング間隔の決定
                    // 優先度1: Steam APIが指定するintervalを使用（推奨）
                    // 優先度2: 指数バックオフ（フォールバック）
                    pollInterval = if (data.interval != null && data.interval > 0) {
                        // Steam推奨の間隔を使用
                        (data.interval * 1000).toLong()
                    } else {
                        // フォールバック: 指数バックオフ
                        (pollInterval * BACKOFF_MULTIPLIER).toLong()
                            .coerceAtMost(MAX_POLL_INTERVAL_MS)
                    }

                    android.util.Log.d(
                        "SteamAuthRepository",
                        "⏱️ Next poll in ${pollInterval}ms (Steam interval: ${data.interval}s)"
                    )

                    delay(pollInterval.milliseconds)

                    // タイムアウトチェック（delay後にチェック）
                    if (System.currentTimeMillis() - startTime > timeoutMillis) {
                        android.util.Log.w(
                            "SteamAuthRepository",
                            "⏰ Authentication timeout after ${(System.currentTimeMillis() - startTime) / 1000}s"
                        )
                        emit(SteamAuthResult(status = SteamAuthSessionStatus.TIMEOUT))
                        break
                    }
                } else {
                    // APIエラー: リトライ可能なエラーコードかチェック
                    val errorCode = response.code()
                    val errorBody = response.errorBody()?.string()

                    android.util.Log.e(
                        "SteamAuthRepository",
                        "API Error - Code: $errorCode, Body: $errorBody"
                    )

                    when (errorCode) {
                        408, 502, 503, 504 -> {
                            // タイムアウト/サーバーエラー: 指数バックオフでリトライ
                            delay(pollInterval.milliseconds)
                            pollInterval = (pollInterval * BACKOFF_MULTIPLIER).toLong()
                                .coerceAtMost(MAX_POLL_INTERVAL_MS)

                            // タイムアウトチェック
                            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                                emit(SteamAuthResult(status = SteamAuthSessionStatus.TIMEOUT))
                                break
                            }
                        }
                        else -> {
                            // その他のエラー: 詳細ログを出力して中断
                            android.util.Log.e(
                                "SteamAuthRepository",
                                "Authentication failed - HTTP $errorCode"
                            )
                            emit(SteamAuthResult(status = SteamAuthSessionStatus.ERROR))
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // ネットワークエラー: 指数バックオフでリトライ
                android.util.Log.w("SteamAuthRepository", "Network error during polling", e)
                delay(pollInterval.milliseconds)
                pollInterval = (pollInterval * BACKOFF_MULTIPLIER).toLong()
                    .coerceAtMost(MAX_POLL_INTERVAL_MS)

                // タイムアウトチェック
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    emit(SteamAuthResult(status = SteamAuthSessionStatus.TIMEOUT))
                    break
                }
            }
        }
    }

    override suspend fun getAuthSessionInfo(clientId: String): Result<AuthSessionInfo> {
        return try {
            val request = GetAuthSessionInfoRequest(clientId = clientId)
            val response = steamAuthService.getAuthSessionInfo(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.response)
            } else {
                Result.failure(Exception("Failed to get auth session info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
