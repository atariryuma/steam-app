package com.steamdeck.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.data.local.preferences.SecureSteamPreferences
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.domain.usecase.SyncSteamLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings画面のViewModel
 *
 * Steam認証、ライブラリ同期、アプリ設定を管理
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val steamPreferences: SecureSteamPreferences,
    private val steamRepository: SteamRepository,
    private val syncSteamLibraryUseCase: SyncSteamLibraryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * 設定データをロード
     *
     * Note: API KeyはEmbedded（ユーザー不要）
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val steamIdFlow = steamPreferences.getSteamId()
            val usernameFlow = steamPreferences.getSteamUsername()
            val lastSyncFlow = steamPreferences.getLastSyncTimestamp()

            combine(
                steamIdFlow,
                usernameFlow,
                lastSyncFlow
            ) { steamId, username, lastSync ->
                val isConfigured = !steamId.isNullOrBlank()
                SettingsData(
                    steamId = steamId.orEmpty(),
                    steamUsername = username.orEmpty(),
                    lastSyncTimestamp = lastSync,
                    isSteamConfigured = isConfigured
                )
            }.collect { data ->
                _uiState.value = SettingsUiState.Success(data)
            }
        }
    }

    /**
     * QR認証後の自動ライブラリ同期
     *
     * QR認証成功後、自動的にライブラリを同期
     */
    fun syncAfterQrLogin() {
        viewModelScope.launch {
            // 少し待機（QR認証完了を確実にする）
            kotlinx.coroutines.delay(500)
            syncSteamLibrary()
        }
    }

    /**
     * Steamライブラリを同期
     *
     * Best Practice: Embedded API Key使用（ユーザーはSteam IDのみ必要）
     */
    fun syncSteamLibrary() {
        viewModelScope.launch {
            // Steam IDを取得
            val currentData = (_uiState.value as? SettingsUiState.Success)?.data
            val steamId = currentData?.steamId

            if (steamId.isNullOrBlank()) {
                _syncState.value = SyncState.Error("Steam IDが見つかりません。QRコードでログインしてください。")
                return@launch
            }

            _syncState.value = SyncState.Syncing(progress = 0f, message = "同期を開始しています...")

            try {
                // Embedded API Key使用（引数は steamId のみ）
                val result = syncSteamLibraryUseCase(steamId)

                if (result.isSuccess) {
                    val syncedCount = result.getOrNull() ?: 0
                    steamPreferences.setLastSyncTimestamp(System.currentTimeMillis())
                    _syncState.value = SyncState.Success(syncedCount)
                    loadSettings() // 最終同期日時を更新
                } else {
                    _syncState.value = SyncState.Error(
                        result.exceptionOrNull()?.message ?: "同期に失敗しました"
                    )
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("エラーが発生しました: ${e.message}")
            }
        }
    }

    /**
     * Steam API Keyを保存
     *
     * @param apiKey Steam Web API Key（32文字の16進数文字列）
     */
    fun saveSteamApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                // API Keyのバリデーション
                if (apiKey.isBlank()) {
                    _uiState.value = SettingsUiState.Error("API Keyを入力してください")
                    return@launch
                }

                if (apiKey.length != 32 || !apiKey.matches(Regex("^[0-9A-Fa-f]{32}$"))) {
                    _uiState.value = SettingsUiState.Error("無効なAPI Keyです（32文字の16進数である必要があります）")
                    return@launch
                }

                // API Keyを保存
                steamPreferences.setSteamApiKey(apiKey)
                android.util.Log.i("SettingsViewModel", "Steam API Key saved successfully")

                _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
                    successMessage = "API Keyを保存しました"
                ) ?: SettingsUiState.Loading
            } catch (e: Exception) {
                _uiState.value = SettingsUiState.Error("API Keyの保存に失敗しました: ${e.message}")
            }
        }
    }

    /**
     * Steam設定をクリア
     */
    fun clearSteamSettings() {
        viewModelScope.launch {
            try {
                steamPreferences.clearSteamSettings()
                loadSettings()
                _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
                    successMessage = "Steam設定をクリアしました"
                ) ?: SettingsUiState.Loading
            } catch (e: Exception) {
                _uiState.value = SettingsUiState.Error("クリアに失敗しました: ${e.message}")
            }
        }
    }

    /**
     * エラーメッセージをクリア
     */
    fun clearError() {
        if (_uiState.value is SettingsUiState.Error) {
            loadSettings()
        }
    }

    /**
     * 成功メッセージをクリア
     */
    fun clearSuccessMessage() {
        val currentState = _uiState.value as? SettingsUiState.Success
        if (currentState != null && currentState.successMessage != null) {
            _uiState.value = currentState.copy(successMessage = null)
        }
    }

    /**
     * 同期状態をリセット
     */
    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }
}

/**
 * Settings画面のUI状態
 */
sealed class SettingsUiState {
    object Loading : SettingsUiState()

    data class Success(
        val data: SettingsData,
        val successMessage: String? = null
    ) : SettingsUiState()

    data class Error(val message: String) : SettingsUiState()
}

/**
 * 設定データ
 *
 * Note: API Keyはアプリに埋め込み済み（ユーザー入力不要）
 */
data class SettingsData(
    val steamId: String,
    val steamUsername: String,
    val lastSyncTimestamp: Long?,
    val isSteamConfigured: Boolean
) {
    /**
     * 最終同期日時をフォーマット
     */
    val lastSyncFormatted: String
        get() {
            if (lastSyncTimestamp == null) return "未同期"
            val now = System.currentTimeMillis()
            val diff = now - lastSyncTimestamp

            return when {
                diff < 60_000 -> "1分以内"
                diff < 3600_000 -> "${diff / 60_000}分前"
                diff < 86400_000 -> "${diff / 3600_000}時間前"
                else -> "${diff / 86400_000}日前"
            }
        }
}

/**
 * 同期状態
 */
sealed class SyncState {
    object Idle : SyncState()

    data class Syncing(
        val progress: Float,
        val message: String
    ) : SyncState()

    data class Success(val syncedGamesCount: Int) : SyncState()

    data class Error(val message: String) : SyncState()
}
