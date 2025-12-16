package com.steamdeck.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.data.local.preferences.SteamPreferences
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
    private val steamPreferences: SteamPreferences,
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
     */
    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                steamPreferences.getSteamApiKey(),
                steamPreferences.getSteamId(),
                steamPreferences.getSteamUsername(),
                steamPreferences.getLastSyncTimestamp(),
                steamPreferences.isSteamConfigured()
            ) { apiKey, steamId, username, lastSync, isConfigured ->
                SettingsData(
                    steamApiKey = apiKey.orEmpty(),
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
     * Steam認証情報を保存
     *
     * @param apiKey Steam Web API Key
     * @param steamId Steam ID
     */
    fun saveSteamCredentials(apiKey: String, steamId: String) {
        if (apiKey.isBlank() || steamId.isBlank()) {
            _uiState.value = SettingsUiState.Error("API KeyとSteam IDは必須です")
            return
        }

        viewModelScope.launch {
            try {
                // Steam API Keyの妥当性チェック（プレイヤー情報取得で確認）
                val playerResult = steamRepository.getPlayerSummary(apiKey, steamId)

                if (playerResult.isFailure) {
                    _uiState.value = SettingsUiState.Error(
                        "認証に失敗しました: ${playerResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                val player = playerResult.getOrNull()
                if (player == null) {
                    _uiState.value = SettingsUiState.Error("プレイヤー情報が取得できませんでした")
                    return@launch
                }

                // 認証成功 - 保存
                steamPreferences.setSteamApiKey(apiKey)
                steamPreferences.setSteamId(steamId)
                steamPreferences.setSteamUsername(player.personaName)

                // UIを更新
                loadSettings()

                _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
                    successMessage = "Steam認証が完了しました"
                ) ?: SettingsUiState.Success(
                    data = SettingsData(
                        steamApiKey = apiKey,
                        steamId = steamId,
                        steamUsername = player.personaName,
                        lastSyncTimestamp = null,
                        isSteamConfigured = true
                    ),
                    successMessage = "Steam認証が完了しました"
                )
            } catch (e: Exception) {
                _uiState.value = SettingsUiState.Error("エラーが発生しました: ${e.message}")
            }
        }
    }

    /**
     * Steamライブラリを同期
     */
    fun syncSteamLibrary() {
        viewModelScope.launch {
            val currentData = (_uiState.value as? SettingsUiState.Success)?.data
            if (currentData == null || !currentData.isSteamConfigured) {
                _syncState.value = SyncState.Error("Steam認証情報が設定されていません")
                return@launch
            }

            _syncState.value = SyncState.Syncing(progress = 0f, message = "同期を開始しています...")

            try {
                val result = syncSteamLibraryUseCase(
                    apiKey = currentData.steamApiKey,
                    steamId = currentData.steamId
                )

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
 */
data class SettingsData(
    val steamApiKey: String,
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
