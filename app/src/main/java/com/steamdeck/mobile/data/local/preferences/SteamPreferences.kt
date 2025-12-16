package com.steamdeck.mobile.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.steamDataStore: DataStore<Preferences> by preferencesDataStore(name = "steam_preferences")

/**
 * Steam関連の設定を管理するクラス
 */
@Singleton
class SteamPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferenceKeys {
        val STEAM_API_KEY = stringPreferencesKey("steam_api_key")
        val STEAM_ID = stringPreferencesKey("steam_id")
        val STEAM_USERNAME = stringPreferencesKey("steam_username")
        val LAST_SYNC_TIMESTAMP = stringPreferencesKey("last_sync_timestamp")
    }

    /**
     * Steam API Keyを保存
     */
    suspend fun setSteamApiKey(apiKey: String) {
        context.steamDataStore.edit { preferences ->
            preferences[PreferenceKeys.STEAM_API_KEY] = apiKey
        }
    }

    /**
     * Steam API Keyを取得
     */
    fun getSteamApiKey(): Flow<String?> {
        return context.steamDataStore.data.map { preferences ->
            preferences[PreferenceKeys.STEAM_API_KEY]
        }
    }

    /**
     * Steam IDを保存
     */
    suspend fun setSteamId(steamId: String) {
        context.steamDataStore.edit { preferences ->
            preferences[PreferenceKeys.STEAM_ID] = steamId
        }
    }

    /**
     * Steam IDを取得
     */
    fun getSteamId(): Flow<String?> {
        return context.steamDataStore.data.map { preferences ->
            preferences[PreferenceKeys.STEAM_ID]
        }
    }

    /**
     * Steamユーザー名を保存
     */
    suspend fun setSteamUsername(username: String) {
        context.steamDataStore.edit { preferences ->
            preferences[PreferenceKeys.STEAM_USERNAME] = username
        }
    }

    /**
     * Steamユーザー名を取得
     */
    fun getSteamUsername(): Flow<String?> {
        return context.steamDataStore.data.map { preferences ->
            preferences[PreferenceKeys.STEAM_USERNAME]
        }
    }

    /**
     * 最終同期日時を保存
     */
    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.steamDataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_SYNC_TIMESTAMP] = timestamp.toString()
        }
    }

    /**
     * 最終同期日時を取得
     */
    fun getLastSyncTimestamp(): Flow<Long?> {
        return context.steamDataStore.data.map { preferences ->
            preferences[PreferenceKeys.LAST_SYNC_TIMESTAMP]?.toLongOrNull()
        }
    }

    /**
     * Steam認証情報が設定されているかチェック
     */
    fun isSteamConfigured(): Flow<Boolean> {
        return context.steamDataStore.data.map { preferences ->
            !preferences[PreferenceKeys.STEAM_API_KEY].isNullOrBlank() &&
                    !preferences[PreferenceKeys.STEAM_ID].isNullOrBlank()
        }
    }

    /**
     * すべてのSteam設定をクリア
     */
    suspend fun clearSteamSettings() {
        context.steamDataStore.edit { preferences ->
            preferences.remove(PreferenceKeys.STEAM_API_KEY)
            preferences.remove(PreferenceKeys.STEAM_ID)
            preferences.remove(PreferenceKeys.STEAM_USERNAME)
            preferences.remove(PreferenceKeys.LAST_SYNC_TIMESTAMP)
        }
    }
}
