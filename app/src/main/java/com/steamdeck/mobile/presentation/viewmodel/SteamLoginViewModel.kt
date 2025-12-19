package com.steamdeck.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.auth.SteamOpenIdAuthenticator
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steam OpenIDログインViewModel（規約準拠版）
 *
 * ⚠️ 変更内容:
 * - QRコードログイン削除（Steam規約違反リスクのため）
 * - Steam OpenID 2.0認証に移行（Valve公式推奨）
 *
 * Best Practices:
 * - StateFlow for UI state
 * - viewModelScope for coroutine lifecycle
 * - Immutable UI state exposure
 * - Error handling with sealed class
 *
 * Clean Architecture: Only depends on domain layer interfaces
 *
 * 公式ドキュメント:
 * - https://partner.steamgames.com/doc/features/auth
 * - https://steamcommunity.com/dev
 */
@HiltViewModel
class SteamLoginViewModel @Inject constructor(
    private val securePreferences: ISecurePreferences
) : ViewModel() {

    companion object {
        private const val TAG = "SteamLoginVM"
        private const val CALLBACK_URL = "steamdeckmobile://auth/callback"
        private const val REALM = "steamdeckmobile://"
    }

    private val _uiState = MutableStateFlow<SteamLoginUiState>(SteamLoginUiState.Initial)
    val uiState: StateFlow<SteamLoginUiState> = _uiState.asStateFlow()

    private var currentState: String? = null

    /**
     * Steam OpenID認証を開始
     *
     * @return Pair(認証URL, ステート値)
     */
    fun startOpenIdLogin(): Pair<String, String> {
        _uiState.update { SteamLoginUiState.Loading }

        val (authUrl, state) = SteamOpenIdAuthenticator.generateAuthUrl(
            returnUrl = CALLBACK_URL,
            realm = REALM
        )

        currentState = state

        _uiState.update {
            SteamLoginUiState.ShowWebView(
                authUrl = authUrl
            )
        }

        AppLogger.d(TAG, "OpenID auth URL generated: $authUrl")

        return Pair(authUrl, state)
    }

    /**
     * コールバックURLを処理してSteamID64を抽出
     *
     * @param callbackUrl Steam認証後のコールバックURL
     */
    fun handleCallback(callbackUrl: String) {
        viewModelScope.launch {
            _uiState.update { SteamLoginUiState.Loading }

            val expectedState = currentState
            if (expectedState == null) {
                _uiState.update {
                    SteamLoginUiState.Error("認証セッションが無効です。もう一度ログインしてください。")
                }
                return@launch
            }

            val steamId = SteamOpenIdAuthenticator.extractSteamId(
                callbackUrl = callbackUrl,
                expectedState = expectedState
            )

            if (steamId != null && SteamOpenIdAuthenticator.isValidSteamId64(steamId)) {
                // SteamID64を保存
                securePreferences.saveSteamId(steamId)

                AppLogger.i(TAG, "OpenID authentication successful. SteamID64: $steamId")

                _uiState.update { SteamLoginUiState.Success(steamId = steamId) }
            } else {
                AppLogger.w(TAG, "Failed to extract SteamID from callback: $callbackUrl")

                _uiState.update {
                    SteamLoginUiState.Error("認証に失敗しました。SteamIDを取得できませんでした。")
                }
            }
        }
    }

    /**
     * エラー状態をクリア
     */
    fun clearError() {
        _uiState.update { SteamLoginUiState.Initial }
    }

    /**
     * 再試行
     */
    fun retry() {
        startOpenIdLogin()
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

    /** WebView表示（OpenID認証ページ） */
    data class ShowWebView(
        val authUrl: String
    ) : SteamLoginUiState()

    /** 認証成功 */
    data class Success(val steamId: String) : SteamLoginUiState()

    /** エラー */
    data class Error(val message: String) : SteamLoginUiState()
}
