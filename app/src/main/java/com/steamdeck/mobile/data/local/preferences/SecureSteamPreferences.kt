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
 * Class for encrypted management of Steam-related confidential information
 *
 * 2025 Best practice:
 * - Encrypt API Key with EncryptedSharedPreferences
 * - Use MasterKey (AES256_GCM)
 * - Save keys in Android Keystore
 *
 * Security Reference:
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

 // StateFlow for reactive change monitoring
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
  * Initialization flag - lazy initialization to prevent ANR
  */
 @Volatile
 private var isInitialized = false

 /**
  * Load initial values asynchronously
  * To prevent ANR, executed only once on first access
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
  * Save Steam API Key encrypted
  */
 suspend fun setSteamApiKey(apiKey: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_API_KEY, apiKey)
   .apply()
  _steamApiKeyFlow.value = apiKey
 }

 /**
  * Retrieve encrypted Steam API Key
  */
 suspend fun getSteamApiKey(): Flow<String?> {
  ensureInitialized()
  return _steamApiKeyFlow
 }

 /**
  * Retrieve Steam API Key synchronously (used only by Repository layer)
  */
 suspend fun getSteamApiKeyDirect(): String? = withContext(Dispatchers.IO) {
  encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
 }

 /**
  * Save Steam ID
  */
 suspend fun setSteamId(steamId: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_ID, steamId)
   .apply()
  _steamIdFlow.value = steamId
 }

 /**
  * Retrieve Steam ID
  */
 suspend fun getSteamId(): Flow<String?> {
  ensureInitialized()
  return _steamIdFlow
 }

 /**
  * Save Steam username
  */
 suspend fun setSteamUsername(username: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_USERNAME, username)
   .apply()
  _steamUsernameFlow.value = username
 }

 /**
  * Retrieve Steam username
  */
 suspend fun getSteamUsername(): Flow<String?> {
  ensureInitialized()
  return _steamUsernameFlow
 }

 /**
  * Save last sync timestamp
  */
 suspend fun setLastSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp)
   .apply()
  _lastSyncTimestampFlow.value = timestamp
 }

 /**
  * Retrieve last sync timestamp
  */
 suspend fun getLastSyncTimestamp(): Flow<Long?> {
  ensureInitialized()
  return _lastSyncTimestampFlow
 }

 /**
  * Check if Steam authentication information is configured
  *
  * Supports 2 authentication methods:
  * 1. API Key authentication: API Key + Steam ID
  * 2. QR authentication: Access Token (JWT with Steam ID in "sub" claim)
  *
  * @return true if either authentication method is configured
  */
 suspend fun isSteamConfigured(): Boolean {
  ensureInitialized()
  return withContext(Dispatchers.IO) {
   // Method 1: API Key authentication completed
   val apiKey = encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
   val steamIdFromApiKey = encryptedPreferences.getString(KEY_STEAM_ID, null)
   val hasApiKeyAuth = !apiKey.isNullOrBlank() && !steamIdFromApiKey.isNullOrBlank()

   // Method 2: QR authentication completed
   val accessToken = encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
   val hasQrAuth = !accessToken.isNullOrBlank()

   // true if either authentication method is configured
   hasApiKeyAuth || hasQrAuth
  }
 }

 /**
  * Clear all Steam configuration
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
  * Save Steam Access Token encrypted
  * Used when QR authentication succeeds
  */
 suspend fun setSteamAccessToken(accessToken: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_ACCESS_TOKEN, accessToken)
   .apply()
  _steamAccessTokenFlow.value = accessToken
 }

 /**
  * Retrieve encrypted Steam Access Token
  */
 suspend fun getSteamAccessToken(): Flow<String?> {
  ensureInitialized()
  return _steamAccessTokenFlow
 }

 /**
  * Retrieve Steam Access Token synchronously (used only by Repository layer)
  */
 suspend fun getSteamAccessTokenDirect(): String? = withContext(Dispatchers.IO) {
  encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
 }

 /**
  * Save Steam Refresh Token encrypted
  * Used for token updates
  */
 suspend fun setSteamRefreshToken(refreshToken: String) = withContext(Dispatchers.IO) {
  encryptedPreferences.edit()
   .putString(KEY_STEAM_REFRESH_TOKEN, refreshToken)
   .apply()
  _steamRefreshTokenFlow.value = refreshToken
 }

 /**
  * Retrieve encrypted Steam Refresh Token
  */
 suspend fun getSteamRefreshToken(): Flow<String?> {
  ensureInitialized()
  return _steamRefreshTokenFlow
 }

 /**
  * Retrieve Steam Refresh Token synchronously (used only by Repository layer)
  */
 suspend fun getSteamRefreshTokenDirect(): String? = withContext(Dispatchers.IO) {
  encryptedPreferences.getString(KEY_STEAM_REFRESH_TOKEN, null)
 }

 /**
  * Check if Steam authentication token is configured
  * Used to confirm QR authentication success
  */
 suspend fun isSteamAuthTokenAvailable(): Boolean {
  ensureInitialized()
  return withContext(Dispatchers.IO) {
   val accessToken = encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
   !accessToken.isNullOrBlank()
  }
 }

 /**
  * Clear Steam authentication tokens only (keep API Key/Steam ID)
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
