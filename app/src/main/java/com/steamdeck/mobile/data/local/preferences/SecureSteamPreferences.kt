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
 * Steam関連の機密情報を暗号化して管理するクラス
 *
 * 2025年ベストプラクティス:
 * - EncryptedSharedPreferencesでAPI Keyを暗号化
 * - MasterKey (AES256_GCM) を使用
 * - Android Keystoreに鍵を保存
 *
 * セキュリティ参考:
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

    // StateFlowでリアクティブに変更を監視
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
     * 初期化フラグ - ANR防止のため遅延初期化
     */
    @Volatile
    private var isInitialized = false

    /**
     * 初期値を非同期でロード
     * ANR防止のため、最初のアクセス時に一度だけ実行される
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
     * Steam API Keyを暗号化して保存
     */
    suspend fun setSteamApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_API_KEY, apiKey)
            .apply()
        _steamApiKeyFlow.value = apiKey
    }

    /**
     * 暗号化されたSteam API Keyを取得
     */
    suspend fun getSteamApiKey(): Flow<String?> {
        ensureInitialized()
        return _steamApiKeyFlow
    }

    /**
     * Steam API Keyを同期的に取得（Repository層でのみ使用）
     */
    suspend fun getSteamApiKeyDirect(): String? = withContext(Dispatchers.IO) {
        encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
    }

    /**
     * Steam IDを保存
     */
    suspend fun setSteamId(steamId: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_ID, steamId)
            .apply()
        _steamIdFlow.value = steamId
    }

    /**
     * Steam IDを取得
     */
    suspend fun getSteamId(): Flow<String?> {
        ensureInitialized()
        return _steamIdFlow
    }

    /**
     * Steamユーザー名を保存
     */
    suspend fun setSteamUsername(username: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_USERNAME, username)
            .apply()
        _steamUsernameFlow.value = username
    }

    /**
     * Steamユーザー名を取得
     */
    suspend fun getSteamUsername(): Flow<String?> {
        ensureInitialized()
        return _steamUsernameFlow
    }

    /**
     * 最終同期日時を保存
     */
    suspend fun setLastSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp)
            .apply()
        _lastSyncTimestampFlow.value = timestamp
    }

    /**
     * 最終同期日時を取得
     */
    suspend fun getLastSyncTimestamp(): Flow<Long?> {
        ensureInitialized()
        return _lastSyncTimestampFlow
    }

    /**
     * Steam認証情報が設定されているかチェック
     *
     * 2つの認証方法をサポート:
     * 1. API Key認証: API Key + Steam ID
     * 2. QR認証: Access Token (JWT with Steam ID in "sub" claim)
     *
     * @return true if either authentication method is configured
     */
    suspend fun isSteamConfigured(): Boolean {
        ensureInitialized()
        return withContext(Dispatchers.IO) {
            // 方法1: API Key認証が完了しているか
            val apiKey = encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
            val steamIdFromApiKey = encryptedPreferences.getString(KEY_STEAM_ID, null)
            val hasApiKeyAuth = !apiKey.isNullOrBlank() && !steamIdFromApiKey.isNullOrBlank()

            // 方法2: QR認証が完了しているか
            val accessToken = encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
            val hasQrAuth = !accessToken.isNullOrBlank()

            // どちらかの認証方法が設定されていればtrue
            hasApiKeyAuth || hasQrAuth
        }
    }

    /**
     * すべてのSteam設定をクリア
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
     * Steam Access Tokenを暗号化して保存
     * QR認証成功時に使用
     */
    suspend fun setSteamAccessToken(accessToken: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_ACCESS_TOKEN, accessToken)
            .apply()
        _steamAccessTokenFlow.value = accessToken
    }

    /**
     * 暗号化されたSteam Access Tokenを取得
     */
    suspend fun getSteamAccessToken(): Flow<String?> {
        ensureInitialized()
        return _steamAccessTokenFlow
    }

    /**
     * Steam Access Tokenを同期的に取得（Repository層でのみ使用）
     */
    suspend fun getSteamAccessTokenDirect(): String? = withContext(Dispatchers.IO) {
        encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
    }

    /**
     * Steam Refresh Tokenを暗号化して保存
     * トークン更新に使用
     */
    suspend fun setSteamRefreshToken(refreshToken: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_REFRESH_TOKEN, refreshToken)
            .apply()
        _steamRefreshTokenFlow.value = refreshToken
    }

    /**
     * 暗号化されたSteam Refresh Tokenを取得
     */
    suspend fun getSteamRefreshToken(): Flow<String?> {
        ensureInitialized()
        return _steamRefreshTokenFlow
    }

    /**
     * Steam Refresh Tokenを同期的に取得（Repository層でのみ使用）
     */
    suspend fun getSteamRefreshTokenDirect(): String? = withContext(Dispatchers.IO) {
        encryptedPreferences.getString(KEY_STEAM_REFRESH_TOKEN, null)
    }

    /**
     * Steam認証トークンが設定されているかチェック
     * QR認証の成功を確認するために使用
     */
    suspend fun isSteamAuthTokenAvailable(): Boolean {
        ensureInitialized()
        return withContext(Dispatchers.IO) {
            val accessToken = encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
            !accessToken.isNullOrBlank()
        }
    }

    /**
     * Steam認証トークンのみをクリア（API Key/Steam IDは保持）
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
