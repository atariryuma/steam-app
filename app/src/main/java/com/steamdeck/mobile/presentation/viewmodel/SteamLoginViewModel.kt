package com.steamdeck.mobile.presentation.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.qr.QrCodeGenerator
import com.steamdeck.mobile.data.local.preferences.SecureSteamPreferences
import com.steamdeck.mobile.data.remote.steam.model.SteamAuthSessionStatus
import com.steamdeck.mobile.domain.repository.SteamAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steam QRログインViewModel
 *
 * Best Practices:
 * - StateFlow for UI state (https://funkymuse.dev/posts/properly-load-data/)
 * - viewModelScope for coroutine lifecycle (https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
 * - Immutable UI state exposure
 * - Error handling with sealed class
 *
 * Reference: https://developer.android.com/topic/libraries/architecture/coroutines
 */
@HiltViewModel
class SteamLoginViewModel @Inject constructor(
    private val steamAuthRepository: SteamAuthRepository,
    private val secureSteamPreferences: SecureSteamPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<SteamLoginUiState>(SteamLoginUiState.Initial)
    val uiState: StateFlow<SteamLoginUiState> = _uiState.asStateFlow()

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()

    private var currentClientId: String? = null
    private var currentRequestId: String? = null
    private var pollingJob: kotlinx.coroutines.Job? = null

    /**
     * QRコードログインセッションを開始
     *
     * @param deviceName デバイス名（例: "Pixel 8 Pro"）
     */
    fun startQrCodeLogin(deviceName: String = android.os.Build.MODEL) {
        // 既存のポーリングジョブをキャンセル
        pollingJob?.cancel()
        pollingJob = null

        viewModelScope.launch {
            _uiState.update { SteamLoginUiState.Loading }

            steamAuthRepository.beginAuthSessionViaQR(deviceName)
                .onSuccess { sessionData ->
                    currentClientId = sessionData.clientId
                    currentRequestId = sessionData.requestId

                    // QRコード生成
                    val qrBitmap = QrCodeGenerator.generateSteamQrCode(
                        challengeUrl = sessionData.challengeUrl,
                        size = 512
                    )

                    // QRコードを永続的なStateFlowに保存
                    _qrCodeBitmap.value = qrBitmap

                    _uiState.update {
                        SteamLoginUiState.QrCodeReady(
                            status = SteamAuthSessionStatus.WAITING_FOR_QR_SCAN
                        )
                    }

                    // ポーリング開始
                    startPolling(sessionData.clientId, sessionData.requestId)
                }
                .onFailure { error ->
                    _uiState.update {
                        SteamLoginUiState.Error(error.message ?: "Unknown error")
                    }
                }
        }
    }

    /**
     * 認証ステータスのポーリング開始
     *
     * Best Practice: Flow-based polling with exponential backoff
     * Securely persists tokens using EncryptedSharedPreferences
     */
    private fun startPolling(clientId: String, requestId: String) {
        pollingJob = viewModelScope.launch {
            steamAuthRepository.pollAuthSessionStatus(
                clientId = clientId,
                requestId = requestId,
                timeoutSeconds = 120 // Steam QRコード有効期限（2分）
            )
                .catch { error ->
                    _uiState.update {
                        SteamLoginUiState.Error(error.message ?: "Polling failed")
                    }
                }
                .collect { authResult ->
                    android.util.Log.d(
                        "SteamLoginViewModel",
                        "📡 Polling result: status=${authResult.status}, hasToken=${!authResult.accessToken.isNullOrBlank()}"
                    )

                    when (authResult.status) {
                        SteamAuthSessionStatus.WAITING_FOR_QR_SCAN -> {
                            // セッション更新チェック（new_client_idが返された場合）
                            if (authResult.newClientId != null && authResult.newChallengeUrl != null) {
                                // 新しいセッション情報を保存
                                currentClientId = authResult.newClientId
                                // requestIdは通常変わらないため、更新不要

                                // 新しいQRコードを生成
                                val qrBitmap = QrCodeGenerator.generateSteamQrCode(
                                    challengeUrl = authResult.newChallengeUrl,
                                    size = 512
                                )
                                _qrCodeBitmap.value = qrBitmap

                                android.util.Log.i(
                                    "SteamLoginViewModel",
                                    "QR code updated with new session"
                                )
                            }
                            // QRコード表示済みのため、特にUI更新なし
                        }
                        SteamAuthSessionStatus.WAITING_FOR_APPROVAL -> {
                            _uiState.update {
                                SteamLoginUiState.QrCodeReady(
                                    status = authResult.status
                                )
                            }
                        }
                        SteamAuthSessionStatus.APPROVED -> {
                            android.util.Log.d(
                                "SteamLoginViewModel",
                                "QR authentication approved - Steam ID: ${authResult.steamId}, Account: ${authResult.accountName}"
                            )

                            // トークンを暗号化して保存
                            authResult.accessToken?.let { accessToken ->
                                secureSteamPreferences.setSteamAccessToken(accessToken)
                                android.util.Log.d("SteamLoginViewModel", "Access token saved (length: ${accessToken.length})")
                            }
                            authResult.refreshToken?.let { refreshToken ->
                                secureSteamPreferences.setSteamRefreshToken(refreshToken)
                                android.util.Log.d("SteamLoginViewModel", "Refresh token saved")
                            }
                            authResult.accountName?.let { accountName ->
                                secureSteamPreferences.setSteamUsername(accountName)
                            }
                            authResult.steamId?.let { steamId ->
                                secureSteamPreferences.setSteamId(steamId)
                            }

                            android.util.Log.i(
                                "SteamLoginViewModel",
                                "Authentication complete. Note: Steam API Key still required for library sync."
                            )

                            _uiState.update { SteamLoginUiState.Success }
                        }
                        SteamAuthSessionStatus.DENIED -> {
                            _uiState.update { SteamLoginUiState.Error("Login denied by user") }
                        }
                        SteamAuthSessionStatus.TIMEOUT -> {
                            _uiState.update {
                                SteamLoginUiState.Error("QRコードの有効期限が切れました。\n「QRコードを再生成」ボタンをタップしてください。")
                            }
                            // 自動リトライしない（ユーザーが手動で再生成ボタンを押す）
                        }
                        SteamAuthSessionStatus.ERROR -> {
                            _uiState.update {
                                SteamLoginUiState.Error("認証エラーが発生しました。\n「QRコードを再生成」ボタンをタップしてください。")
                            }
                            // 自動リトライしない（ユーザーが手動で再生成ボタンを押す）
                        }
                    }
                }
        }
    }

    /**
     * 通常のユーザー名/パスワードログイン
     *
     * 注意: Steamはパスワードログインに2FA/Steam Guardを要求するため、
     * QRログインの使用を強く推奨します。
     *
     * ⚠️ 簡易実装: パスワード認証にはRSA暗号化が必要ですが、
     * 現在は代わりにQRコードログインへ誘導します。
     *
     * Reference: https://github.com/DoctorMcKay/node-steam-session
     */
    fun loginWithCredentials(username: String, password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            _uiState.update { SteamLoginUiState.Loading }

            android.util.Log.i(
                "SteamLoginViewModel",
                "Password login attempted for user: $username (redirecting to QR login)"
            )

            kotlinx.coroutines.delay(500) // UX: ローディング表示

            // パスワードログインの代わりに、QRコードログインを開始
            // Steam Web APIのパスワード認証はRSA暗号化とSteam Guard必須のため
            // モバイルアプリではQRコード方式を推奨
            _uiState.update {
                SteamLoginUiState.Error(
                    "パスワードログインには2段階認証が必要です。\n\n" +
                    "右側のQRコードをSteamモバイルアプリで\n" +
                    "スキャンして簡単にログインできます。"
                )
            }
        }
    }

    /**
     * エラー状態をクリア
     *
     * エラー表示後、QRコード表示状態に戻す
     */
    fun clearError() {
        _uiState.update { currentState ->
            if (currentState is SteamLoginUiState.Error && _qrCodeBitmap.value != null) {
                // エラー状態の場合、QRコード表示に戻る
                SteamLoginUiState.QrCodeReady(
                    status = SteamAuthSessionStatus.WAITING_FOR_QR_SCAN
                )
            } else {
                currentState
            }
        }
    }

    /**
     * QRコードを再生成
     *
     * Best Practice: 期限切れセッションの再利用は不可
     * 常に新しいBeginAuthSessionViaQRを呼び出す必要がある
     *
     * Reference: node-steam-session実装
     * - タイムアウト/エラー後は新しいLoginSessionを作成
     * - セッションの再利用はSteam APIで許可されていない
     */
    fun regenerateQrCode() {
        // ❌ 古い実装: 期限切れのclientId/requestIdを再利用
        // ✅ 新しい実装: 常に新しいセッションを作成
        startQrCodeLogin()
    }
}

/**
 * Steam Login UI State
 *
 * Best Practice: Sealed class for type-safe state management
 */
sealed class SteamLoginUiState {
    /** 初期状態 */
    data object Initial : SteamLoginUiState()

    /** ローディング中 */
    data object Loading : SteamLoginUiState()

    /** QRコード表示準備完了 */
    data class QrCodeReady(
        val status: SteamAuthSessionStatus
    ) : SteamLoginUiState()

    /** 認証成功 */
    data object Success : SteamLoginUiState()

    /** エラー */
    data class Error(val message: String) : SteamLoginUiState()
}
