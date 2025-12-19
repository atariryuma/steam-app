package com.steamdeck.mobile.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ISecurePreferences using EncryptedSharedPreferences.
 *
 * Security best practices (2025):
 * - EncryptedSharedPreferences for secure token storage
 * - MasterKey with AES256_GCM
 * - Android Keystore for key management
 *
 * References:
 * - https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
 * - https://medium.com/@moraneus/secure-your-android-apps-data-with-encryptedsharedpreferences-7091da539f5e
 */
@Singleton
class SecurePreferencesImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ISecurePreferences {

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

    // StateFlows for reactive state
    private val _steamIdFlow = MutableStateFlow<String?>(null)
    private val _steamUsernameFlow = MutableStateFlow<String?>(null)
    private val _lastSyncTimestampFlow = MutableStateFlow<Long?>(null)

    private val initMutex = Mutex()

    @Volatile
    private var isInitialized = false

    companion object {
        private const val PREFS_NAME = "secure_steam_preferences"
        private const val KEY_STEAM_ACCESS_TOKEN = "steam_access_token"
        private const val KEY_STEAM_REFRESH_TOKEN = "steam_refresh_token"
        private const val KEY_STEAM_ID = "steam_id"
        private const val KEY_STEAM_USERNAME = "steam_username"
        private const val KEY_STEAM_API_KEY = "steam_api_key"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    }

    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            initMutex.withLock {
                if (!isInitialized) {
                    withContext(Dispatchers.IO) {
                        _steamIdFlow.value = encryptedPreferences.getString(KEY_STEAM_ID, null)
                        _steamUsernameFlow.value = encryptedPreferences.getString(KEY_STEAM_USERNAME, null)
                        _lastSyncTimestampFlow.value = encryptedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, -1L)
                            .takeIf { it != -1L }
                    }
                    isInitialized = true
                }
            }
        }
    }

    override suspend fun getSteamToken(): String? = withContext(Dispatchers.IO) {
        encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)
    }

    override suspend fun saveSteamToken(token: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_ACCESS_TOKEN, token)
            .apply()
    }

    override suspend fun getSteamId(): Flow<String?> {
        ensureInitialized()
        return _steamIdFlow.asStateFlow()
    }

    override suspend fun saveSteamId(steamId: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_ID, steamId)
            .apply()
        _steamIdFlow.value = steamId
    }

    override suspend fun getSteamUsername(): Flow<String?> {
        ensureInitialized()
        return _steamUsernameFlow.asStateFlow()
    }

    override suspend fun saveSteamUsername(username: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_USERNAME, username)
            .apply()
        _steamUsernameFlow.value = username
    }

    override suspend fun getSteamApiKey(): String? = withContext(Dispatchers.IO) {
        encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
    }

    override suspend fun saveSteamApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_STEAM_API_KEY, apiKey)
            .apply()
    }

    override suspend fun getLastSyncTimestamp(): Flow<Long?> {
        ensureInitialized()
        return _lastSyncTimestampFlow.asStateFlow()
    }

    override suspend fun setLastSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp)
            .apply()
        _lastSyncTimestampFlow.value = timestamp
    }

    override suspend fun clearSteamSettings() = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .clear()
            .apply()
        _steamIdFlow.value = null
        _steamUsernameFlow.value = null
        _lastSyncTimestampFlow.value = null
    }

    override suspend fun hasSteamCredentials(): Boolean = withContext(Dispatchers.IO) {
        val apiKey = encryptedPreferences.getString(KEY_STEAM_API_KEY, null)
        val steamId = encryptedPreferences.getString(KEY_STEAM_ID, null)
        val accessToken = encryptedPreferences.getString(KEY_STEAM_ACCESS_TOKEN, null)

        // Either API key auth or token auth is configured
        (!apiKey.isNullOrBlank() && !steamId.isNullOrBlank()) || !accessToken.isNullOrBlank()
    }
}
