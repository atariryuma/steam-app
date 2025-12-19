package com.steamdeck.mobile.presentation.viewmodel

import androidx.compose.runtime.Immutable
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
 * Steam OpenIDLoginViewModel（規約準拠版）
 *
 * ⚠️ 変更内容:
 * - QRコードLogindelete（Steam規約違反リスク ため）
 * - Steam OpenID 2.0authentication 移行（Valve公式推奨）
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
  * Steam OpenIDauthenticationstart
  *
  * @return Pair(authenticationURL, ステート値)
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
  * コールバックURL処理してSteamID64extract
  *
  * @param callbackUrl Steamauthentication後 コールバックURL
  */
 fun handleCallback(callbackUrl: String) {
  viewModelScope.launch {
   _uiState.update { SteamLoginUiState.Loading }

   val expectedState = currentState
   if (expectedState == null) {
    _uiState.update {
     SteamLoginUiState.Error("authenticationセッション Disabled す。もう一度Loginplease。")
    }
    return@launch
   }

   val steamId = SteamOpenIdAuthenticator.extractSteamId(
    callbackUrl = callbackUrl,
    expectedState = expectedState
   )

   if (steamId != null && SteamOpenIdAuthenticator.isValidSteamId64(steamId)) {
    // SteamID64save
    securePreferences.saveSteamId(steamId)

    AppLogger.i(TAG, "OpenID authentication successful. SteamID64: $steamId")

    _uiState.update { SteamLoginUiState.Success(steamId = steamId) }
   } else {
    AppLogger.w(TAG, "Failed to extract SteamID from callback: $callbackUrl")

    _uiState.update {
     SteamLoginUiState.Error("authentication failed。SteamIDretrieve きません した。")
    }
   }
  }
 }

 /**
  * Error状態クリア
  */
 fun clearError() {
  _uiState.update { SteamLoginUiState.Initial }
 }

 /**
  * retry
  */
 fun retry() {
  startOpenIdLogin()
 }
}

/**
 * Steam Login UI State
 *
 * Best Practice: Sealed class for type-safe state management with @Immutable for performance
 */
@Immutable
sealed class SteamLoginUiState {
 /** Initial state */
 @Immutable
 data object Initial : SteamLoginUiState()

 /** Loading */
 @Immutable
 data object Loading : SteamLoginUiState()

 /** Show WebView (OpenID auth page) */
 @Immutable
 data class ShowWebView(
  val authUrl: String
 ) : SteamLoginUiState()

 /** Authentication success */
 @Immutable
 data class Success(val steamId: String) : SteamLoginUiState()

 /** Error */
 @Immutable
 data class Error(val message: String) : SteamLoginUiState()
}
