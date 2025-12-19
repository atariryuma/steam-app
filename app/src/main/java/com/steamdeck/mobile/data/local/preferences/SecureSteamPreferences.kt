package com.steamdeck.mobile.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam関連 機密information暗号化してmanagementdoclass
 *
 * 2025年Best practice:
 * - EncryptedSharedPreferences API Key暗号化
 * - MasterKey (AES256_GCM) use
 * - Android Keystore 鍵save
 *
 * セキュリティReference:
 * - https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
 * - https://medium.com/@moraneus/secure-your-android-apps-data-with-encryptedsharedpreferences-7091da539f5e
 */
@Singleton
class SecureSteamPreferences @Inject constructor(
 @ApplicationContext private val context: Context
) {
 private val masterKey: MasterKey by lazy {
  MasterKey.Builder(context)
   .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
   .build()
 }

 private val encryptedPreferences: SharedPreferences by lazy {
  EncryptedSharedPreferences.create(
   context,
   PREFS_NAME,
   masterKey,
   EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
   EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
  )
 }

 // StateFlow リアクティブ 変更monitor
 private val _steamApiKeyFlow = MutableStateFlow<String?>(null)
 private val _steamIdFlow = MutableStateFlow<String?>(null)
 private val _steamUsernameFlow = MutableStateFlow<String?>(null)
 private val _lastSyncTimestampFlow = MutableStateFlow<Long?>(null)
 private val _steamAccessTokenFlow = MutableStateFlow<String?>(null)
 private val _steamRefreshTokenFlow = MutableStateFlow<String?>(null)

 /**
  * Mutex for thread-safe initialization (coroutine-safe replacement for synchronized)
  */
 private val initMutex = Mutex()

 /**
  * initializationフラグ - ANR防止 ため遅延initialization
  */
 @Volatile
 private var isInitialized = false

 /**
  * 初期value非同期 ロード
  * ANR防止 ため、最初 アクセス時 一度だけexecutionされる
  */
 private suspend fun ensureInitialized() {
  if (!isInitialized) {
   initMutex.withLock {
    if (!isInitialized) {
     withContext(Dispatchers.IO) {
      _steamApiKeyFlow.value = encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
      _steamIdFlow.value = encryptedPreferences.getString(KEY_STEAM_ID, null)
      _steamUsernameFlow.value = encryptedPreferences.getString(KEY_STEAM_USERNAME, null)
      _lastSyncTimestampFlow.value = encryptedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, -1L)
       .takeIf { it != -1L }
      _steamAccessTokenFlow.value = encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
      _steamRefreshTokenFlow.value = encryptedPreferences.getString(KEY_STEAM_REFRESH_TOKEN, null)
     }
     isInitialized = true
    }
   }
  }
 }

 companion object {
  private const val PREFS_NAME = "secure_steam_preferences"
  private const val KEY_STEAM_API_KEY = "steam_api_key"
  private const val KEY_STEAM_ID = "steam_id"
  private const val KEY_STEAM_USERNAME = "steam_username"
  private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
  private const val KEY_STEAM_ACCESS_TOKEN = "steam_access_token"
  private const val KEY_STEAM_REFRESH_TOKEN = "steam_refresh_token"
 }

 /**
  * Steam API Key暗号化してsave
  */
 suspend fun setSteamApiKey(apiKey: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_API_KEY, apiKey)
   .apply()
  _steamApiKeyFlow.value = apiKey
 }

 /**
  * 暗号化されたSteam API Keyretrieve
  */
 suspend fun getSteamApiKey(): Flow<String?> {
  ensureInitialized()
  return _steamApiKeyFlow
 }

 /**
  * Steam API Key同期的 retrieve（Repository層 みuse）
  */
 suspend fun getSteamApiKeyDirect(): String? = withContext(Dispatchers.IO) {
  encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
 }

 /**
  * Steam IDsave
  */
 suspend fun setSteamId(steamId: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_ID, steamId)
   .apply()
  _steamIdFlow.value = steamId
 }

 /**
  * Steam IDretrieve
  */
 suspend fun getSteamId(): Flow<String?> {
  ensureInitialized()
  return _steamIdFlow
 }

 /**
  * Steamユーザー名save
  */
 suspend fun setSteamUsername(username: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_USERNAME, username)
   .apply()
  _steamUsernameFlow.value = username
 }

 /**
  * Steamユーザー名retrieve
  */
 suspend fun getSteamUsername(): Flow<String?> {
  ensureInitialized()
  return _steamUsernameFlow
 }

 /**
  * 最終同期date and timesave
  */
 suspend fun setLastSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp)
   .apply()
  _lastSyncTimestampFlow.value = timestamp
 }

 /**
  * 最終同期date and timeretrieve
  */
 suspend fun getLastSyncTimestamp(): Flow<Long?> {
  ensureInitialized()
  return _lastSyncTimestampFlow
 }

 /**
  * Steamauthenticationinformation configurationされているかcheck
  *
  * 2つ authentication方法support:
  * 1. API Keyauthentication: API Key + Steam ID
  * 2. QRauthentication: Access Token (JWT with Steam ID in "sub" claim)
  *
  * @return true if either authentication method is configured
  */
 suspend fun isSteamConfigured(): Boolean {
  ensureInitialized()
  return withContext(Dispatchers.IO) {
   // 方法1: API Keyauthentication completedしているか
   val apiKey = encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
   val steamIdFromApiKey = encryptedPreferences.getString(KEY_STEAM_ID, null)
   val hasApiKeyAuth = !apiKey.isNullOrBlank() && !steamIdFromApiKey.isNullOrBlank()

   // 方法2: QRauthentication completedしているか
   val accessToken = encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
   val hasQrAuth = !accessToken.isNullOrBlank()

   // どちらか authentication方法 configurationされていればtrue
   hasApiKeyAuth || hasQrAuth
  }
 }

 /**
  * all Steamconfigurationクリア
  */
 suspend fun clearSteamSettings() = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .clear()
   .apply()
  _steamApiKeyFlow.value = null
  _steamIdFlow.value = null
  _steamUsernameFlow.value = null
  _lastSyncTimestampFlow.value = null
  _steamAccessTokenFlow.value = null
  _steamRefreshTokenFlow.value = null
 }

 // ===== Steam QR Authentication Token Management =====

 /**
  * Steam Access Token暗号化してsave
  * QRauthenticationsuccess時 use
  */
 suspend fun setSteamAccessToken(accessToken: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_ACCESS_TOKEN, accessToken)
   .apply()
  _steamAccessTokenFlow.value = accessToken
 }

 /**
  * 暗号化されたSteam Access Tokenretrieve
  */
 suspend fun getSteamAccessToken(): Flow<String?> {
  ensureInitialized()
  return _steamAccessTokenFlow
 }

 /**
  * Steam Access Token同期的 retrieve（Repository層 みuse）
  */
 suspend fun getSteamAccessTokenDirect(): String? = withContext(Dispatchers.IO) {
  encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
 }

 /**
  * Steam Refresh Token暗号化してsave
  * トークンupdate use
  */
 suspend fun setSteamRefreshToken(refreshToken: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_REFRESH_TOKEN, refreshToken)
   .apply()
  _steamRefreshTokenFlow.value = refreshToken
 }

 /**
  * 暗号化されたSteam Refresh Tokenretrieve
  */
 suspend fun getSteamRefreshToken(): Flow<String?> {
  ensureInitialized()
  return _steamRefreshTokenFlow
 }

 /**
  * Steam Refresh Token同期的 retrieve（Repository層 みuse）
  */
 suspend fun getSteamRefreshTokenDirect(): String? = withContext(Dispatchers.IO) {
  encryptedPreferences.getString(KEY_STEAM_REFRESH_TOKEN, null)
 }

 /**
  * Steamauthenticationトークン configurationされているかcheck
  * QRauthentication successconfirmationdoため use
  */
 suspend fun isSteamAuthTokenAvailable(): Boolean {
  ensureInitialized()
  return withContext(Dispatchers.IO) {
   val accessToken = encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
   !accessToken.isNullOrBlank()
  }
 }

 /**
  * Steamauthenticationトークン みクリア（API Key/Steam ID 保持）
  */
 suspend fun clearSteamAuthTokens() = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .remove(KEY_STEAM_ACCESS_TOKEN)
   .remove(KEY_STEAM_REFRESH_TOKEN)
   .apply()
  _steamAccessTokenFlow.value = null
  _steamRefreshTokenFlow.value = null
 }
}
