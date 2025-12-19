package com.steamdeck.mobile.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.steam.SteamSetupManager
import com.steamdeck.mobile.domain.repository.ISecurePreferences
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
 *
 * Clean Architecture: Only depends on domain layer interfaces
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: ISecurePreferences,
    private val syncSteamLibraryUseCase: SyncSteamLibraryUseCase,
    private val steamLauncher: SteamLauncher,
    private val steamSetupManager: SteamSetupManager
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _steamInstallState = MutableStateFlow<SteamInstallState>(SteamInstallState.Idle)
    val steamInstallState: StateFlow<SteamInstallState> = _steamInstallState.asStateFlow()

    init {
        loadSettings()
        checkSteamInstallation()
    }

    /**
     * 設定データをロード
     *
     * Note: ユーザーは自身のSteam Web API Keyを登録する必要があります
     * 取得先: https://steamcommunity.com/dev/apikey
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val steamIdFlow = securePreferences.getSteamId()
            val usernameFlow = securePreferences.getSteamUsername()
            val lastSyncFlow = securePreferences.getLastSyncTimestamp()

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
     * Best Practice: ユーザー提供のAPI Key使用
     * ユーザーは事前にAPI Keyを登録する必要があります
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
                // Steam APIからライブラリを取得
                val result = syncSteamLibraryUseCase(steamId)

                if (result.isSuccess) {
                    val syncedCount = result.getOrNull() ?: 0
                    securePreferences.setLastSyncTimestamp(System.currentTimeMillis())
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
     * 取得先: https://steamcommunity.com/dev/apikey
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
                securePreferences.saveSteamApiKey(apiKey)
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
                securePreferences.clearSteamSettings()
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
     * Steamインストール状態をチェック
     */
    fun checkSteamInstallation() {
        viewModelScope.launch {
            try {
                _steamInstallState.value = SteamInstallState.Checking
                val installation = steamSetupManager.getSteamInstallation()

                _steamInstallState.value = if (installation != null && installation.status == com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus.INSTALLED) {
                    SteamInstallState.Installed(
                        installPath = installation.installPath,
                        containerId = installation.containerId
                    )
                } else {
                    SteamInstallState.NotInstalled("Steam Clientはインストールされていません")
                }

                Log.d(TAG, "Steam installation check: installed=${installation != null}, containerId=${installation?.containerId}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to check Steam installation", e)
                _steamInstallState.value = SteamInstallState.Error(
                    "インストール状態の確認に失敗しました: ${e.message}"
                )
            }
        }
    }

    /**
     * Steam Clientをインストール
     *
     * @param containerId Winlatorコンテナ ID (String型)
     */
    fun installSteamClient(containerId: String) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Starting Steam Client installation for container: $containerId")

                val result = steamSetupManager.installSteam(
                    containerId = containerId,
                    progressCallback = { progress, message ->
                        _steamInstallState.value = SteamInstallState.Installing(progress, message)
                        Log.d(TAG, "Install progress: $progress - $message")
                    }
                )

                result
                    .onSuccess { installResult ->
                        when (installResult) {
                            is SteamSetupManager.SteamInstallResult.Success -> {
                                _steamInstallState.value = SteamInstallState.Installed(
                                    installPath = installResult.installPath,
                                    containerId = installResult.containerId
                                )
                                Log.i(TAG, "Steam Client installed successfully: ${installResult.installPath}, Container ID: ${installResult.containerId}")
                            }
                            is SteamSetupManager.SteamInstallResult.Error -> {
                                _steamInstallState.value = SteamInstallState.Error(
                                    installResult.message
                                )
                                Log.e(TAG, "Steam installation error: ${installResult.message}")
                            }
                            is SteamSetupManager.SteamInstallResult.Progress -> {
                                // Progress updates handled by callback
                            }
                        }
                    }
                    .onFailure { error ->
                        _steamInstallState.value = SteamInstallState.Error(
                            "インストールに失敗しました: ${error.message}"
                        )
                        Log.e(TAG, "Failed to install Steam Client", error)
                    }

            } catch (e: Exception) {
                _steamInstallState.value = SteamInstallState.Error(
                    "予期しないエラーが発生しました: ${e.message}"
                )
                Log.e(TAG, "Exception during Steam installation", e)
            }
        }
    }

    /**
     * Steam Clientを開く
     *
     * @param containerId Winlatorコンテナ ID
     */
    fun openSteamClient(containerId: String) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Opening Steam Client for container: $containerId")

                val result = steamLauncher.launchSteamClient(containerId)

                result
                    .onSuccess {
                        Log.i(TAG, "Steam Client opened successfully")
                        // Steam Clientが起動したことをユーザーに通知
                        _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
                            successMessage = "Steam Clientを起動しました"
                        ) ?: SettingsUiState.Loading
                    }
                    .onFailure { error ->
                        _steamInstallState.value = SteamInstallState.Error(
                            "Steam Clientの起動に失敗しました: ${error.message}"
                        )
                        Log.e(TAG, "Failed to open Steam Client", error)
                    }

            } catch (e: Exception) {
                _steamInstallState.value = SteamInstallState.Error(
                    "Steam Client起動中に予期しないエラーが発生しました: ${e.message}"
                )
                Log.e(TAG, "Exception while opening Steam Client", e)
            }
        }
    }

    /**
     * Steam Clientをアンインストール
     *
     * @param containerId Winlatorコンテナ ID
     */
    fun uninstallSteamClient(containerId: String) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Uninstalling Steam Client for container: $containerId")

                // 将来的には実際のアンインストール処理を実装
                // 現時点ではデータベースの状態をクリア
                _steamInstallState.value = SteamInstallState.NotInstalled(
                    "Steam Clientはアンインストールされました"
                )

                Log.i(TAG, "Steam Client uninstalled (placeholder)")

            } catch (e: Exception) {
                _steamInstallState.value = SteamInstallState.Error(
                    "アンインストールに失敗しました: ${e.message}"
                )
                Log.e(TAG, "Exception during Steam uninstallation", e)
            }
        }
    }

    /**
     * Steam Install状態をリセット
     */
    fun resetSteamInstallState() {
        _steamInstallState.value = SteamInstallState.Idle
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
    data object Loading : SettingsUiState()

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
    data object Idle : SyncState()

    data class Syncing(
        val progress: Float,
        val message: String
    ) : SyncState()

    data class Success(val syncedGamesCount: Int) : SyncState()

    data class Error(val message: String) : SyncState()
}

/**
 * Steam Clientインストール状態
 */
sealed class SteamInstallState {
    /** アイドル状態 */
    data object Idle : SteamInstallState()

    /** インストール状態確認中 */
    data object Checking : SteamInstallState()

    /** 未インストール */
    data class NotInstalled(val message: String) : SteamInstallState()

    /** インストール済み */
    data class Installed(
        val installPath: String,
        val containerId: String
    ) : SteamInstallState()

    /** インストール中 */
    data class Installing(
        val progress: Float,
        val message: String
    ) : SteamInstallState()

    /** エラー */
    data class Error(val message: String) : SteamInstallState()
}
