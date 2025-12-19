package com.steamdeck.mobile.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.steam.SteamSetupManager
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import com.steamdeck.mobile.domain.usecase.SyncSteamLibraryUseCase
import com.steamdeck.mobile.presentation.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings画面 ViewModel
 *
 * Steamauthentication、library sync、アプリsettings管理
 *
 * Clean Architecture: Only depends on domain layer interfaces
 *
 * 2025 Best Practice:
 * - DataResult<T> for type-safe results
 * - AppLogger for centralized logging
 * - ErrorExtensions for UI error mapping
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
 @ApplicationContext private val context: Context,
 private val securePreferences: ISecurePreferences,
 private val syncSteamLibraryUseCase: SyncSteamLibraryUseCase,
 private val steamLauncher: SteamLauncher,
 private val steamSetupManager: SteamSetupManager
) : ViewModel() {

 companion object {
  private const val TAG = "SettingsVM"
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
  autoConfigureDevApiKey() // Auto-configure API key from BuildConfig for development
 }

 /**
  * Development API Key Auto-Configuration
  *
  * Automatically configures Steam API Key from BuildConfig for development builds
  * if the user hasn't set one yet.
  *
  * Security Note:
  * - DEV_STEAM_API_KEY is loaded from local.properties (not committed to git)
  * - Only available in debug builds
  * - For production, users must obtain their own API key from:
  *   https://steamcommunity.com/dev/apikey
  */
 private fun autoConfigureDevApiKey() {
  viewModelScope.launch {
   try {
    val existingKey = securePreferences.getSteamApiKey()
    if (existingKey.isNullOrBlank()) {
     val devKey = com.steamdeck.mobile.BuildConfig.DEV_STEAM_API_KEY
     if (devKey.isNotBlank()) {
      securePreferences.saveSteamApiKey(devKey)
      AppLogger.d(TAG, "Auto-configured development API key from BuildConfig")
     } else {
      AppLogger.w(TAG, "DEV_STEAM_API_KEY not found in BuildConfig. Add to local.properties: STEAM_API_KEY=your_key")
     }
    }
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to auto-configure API key", e)
   }
  }
 }

 /**
  * settingsデータロード
  *
  * Note: ユーザー 自身 Steam Web API Key登録do必要 あります
  * retrieve先: https://steamcommunity.com/dev/apikey
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
  * QRauthentication後 自動library sync
  *
  * QRauthenticationSuccess後、自動的 library sync
  */
 fun syncAfterQrLogin() {
  viewModelScope.launch {
   // 少し待機（QRauthenticationComplete確実 do）
   kotlinx.coroutines.delay(500)
   syncSteamLibrary()
  }
 }

 /**
  * Steamlibrary sync
  *
  * Best Practice: ユーザー提供 API Keyuse
  * ユーザー 事前 API Key登録do必要 あります
  *
  * 2025 Best Practice: DataResult<T> Errorハンドリング
  */
 fun syncSteamLibrary() {
  viewModelScope.launch {
   // Steam IDretrieve
   val currentData = (_uiState.value as? SettingsUiState.Success)?.data
   val steamId = currentData?.steamId

   if (steamId.isNullOrBlank()) {
    AppLogger.w(TAG, "Sync attempted without Steam ID")
    _syncState.value = SyncState.Error("Steam ID not found. Please login.")
    return@launch
   }

   _syncState.value = SyncState.Syncing(progress = 0f, message = "Starting sync...")
   AppLogger.i(TAG, "Starting library sync for Steam ID: $steamId")

   // DataResult<Int>useした型安全なErrorハンドリング
   when (val result = syncSteamLibraryUseCase(steamId)) {
    is DataResult.Success -> {
     val syncedCount = result.data
     securePreferences.setLastSyncTimestamp(System.currentTimeMillis())
     _syncState.value = SyncState.Success(syncedCount)
     loadSettings() // 最終Syncdate and timeUpdate
     AppLogger.i(TAG, "Library sync completed: $syncedCount games")
    }
    is DataResult.Error -> {
     val errorMessage = result.error.toUserMessage(context)
     _syncState.value = SyncState.Error(errorMessage)
     AppLogger.e(TAG, "Library sync failed: $errorMessage")
    }
    is DataResult.Loading -> {
     // 進捗Update（将来的 ProgressBar 対応）
     _syncState.value = SyncState.Syncing(
      progress = result.progress ?: 0f,
      message = "Syncing..."
     )
    }
   }
  }
 }

 /**
  * Steam API Keysave
  *
  * @param apiKey Steam Web API Key（32文字 16進数文字列）
  * retrieve先: https://steamcommunity.com/dev/apikey
  */
 fun saveSteamApiKey(apiKey: String) {
  viewModelScope.launch {
   try {
    // API Key バリデーション
    if (apiKey.isBlank()) {
     _uiState.value = SettingsUiState.Error("API Key入力please")
     return@launch
    }

    if (apiKey.length != 32 || !apiKey.matches(Regex("^[0-9A-Fa-f]{32}$"))) {
     _uiState.value = SettingsUiState.Error("DisabledなAPI Key す（32文字 16進数 ある必要 あります）")
     return@launch
    }

    // API Keysave
    securePreferences.saveSteamApiKey(apiKey)
    AppLogger.i(TAG, "Steam API Key saved successfully")

    _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
     successMessage = "API Keysaveしました"
    ) ?: SettingsUiState.Loading
   } catch (e: Exception) {
    _uiState.value = SettingsUiState.Error("API Key save failed: ${e.message}")
   }
  }
 }

 /**
  * Steamsettingsクリア
  */
 fun clearSteamSettings() {
  viewModelScope.launch {
   try {
    securePreferences.clearSteamSettings()
    loadSettings()
    _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
     successMessage = "Steamsettingsクリアしました"
    ) ?: SettingsUiState.Loading
   } catch (e: Exception) {
    _uiState.value = SettingsUiState.Error("クリア failed: ${e.message}")
   }
  }
 }

 /**
  * Steaminstallation状態check
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
     SteamInstallState.NotInstalled("Steam Client is not installed")
    }

    AppLogger.d(TAG, "Steam installation check: installed=${installation != null}, containerId=${installation?.containerId}")

   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to check Steam installation", e)
    _steamInstallState.value = SteamInstallState.Error(
     "installation状態 confirm failed: ${e.message}"
    )
   }
  }
 }

 /**
  * Steam Clientinstallation
  *
  * @param containerId WinlatorContainer ID (String型)
  */
 fun installSteamClient(containerId: String) {
  viewModelScope.launch {
   try {
    AppLogger.i(TAG, "Starting Steam Client installation for container: $containerId")

    val result = steamSetupManager.installSteam(
     containerId = containerId,
     progressCallback = { progress, message ->
      _steamInstallState.value = SteamInstallState.Installing(progress, message)
      AppLogger.d(TAG, "Install progress: $progress - $message")
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
        AppLogger.i(TAG, "Steam Client installed successfully: ${installResult.installPath}, Container ID: ${installResult.containerId}")
       }
       is SteamSetupManager.SteamInstallResult.Error -> {
        _steamInstallState.value = SteamInstallState.Error(
         installResult.message
        )
        AppLogger.e(TAG, "Steam installation error: ${installResult.message}")
       }
       is SteamSetupManager.SteamInstallResult.Progress -> {
        // Progress updates handled by callback
       }
      }
     }
     .onFailure { error ->
      _steamInstallState.value = SteamInstallState.Error(
       "installation failed: ${error.message}"
      )
      AppLogger.e(TAG, "Failed to install Steam Client", error)
     }

   } catch (e: Exception) {
    _steamInstallState.value = SteamInstallState.Error(
     "予期しないError 発生しました: ${e.message}"
    )
    AppLogger.e(TAG, "Exception during Steam installation", e)
   }
  }
 }

 /**
  * Steam Clientopen
  *
  * @param containerId WinlatorContainer ID
  */
 fun openSteamClient(containerId: String) {
  viewModelScope.launch {
   try {
    AppLogger.i(TAG, "Opening Steam Client for container: $containerId")

    val result = steamLauncher.launchSteamClient(containerId)

    result
     .onSuccess {
      AppLogger.i(TAG, "Steam Client opened successfully")
      // Steam Client launchしたこ ユーザー 通知
      _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
       successMessage = "Steam Clientlaunchしました"
      ) ?: SettingsUiState.Loading
     }
     .onFailure { error ->
      _steamInstallState.value = SteamInstallState.Error(
       "Steam Client launch failed: ${error.message}"
      )
      AppLogger.e(TAG, "Failed to open Steam Client", error)
     }

   } catch (e: Exception) {
    _steamInstallState.value = SteamInstallState.Error(
     "Steam Clientlaunchin 予期しないError 発生しました: ${e.message}"
    )
    AppLogger.e(TAG, "Exception while opening Steam Client", e)
   }
  }
 }

 /**
  * Steam Clientアンinstallation
  *
  * @param containerId WinlatorContainer ID
  */
 fun uninstallSteamClient(containerId: String) {
  viewModelScope.launch {
   try {
    AppLogger.i(TAG, "Uninstalling Steam Client for container: $containerId")

    // 将来的 実際 アンinstallation処理実装
    // 現時点 データベース 状態クリア
    _steamInstallState.value = SteamInstallState.NotInstalled(
     "Steam Client アンinstallationされました"
    )

    AppLogger.i(TAG, "Steam Client uninstalled (placeholder)")

   } catch (e: Exception) {
    _steamInstallState.value = SteamInstallState.Error(
     "アンinstallation failed: ${e.message}"
    )
    AppLogger.e(TAG, "Exception during Steam uninstallation", e)
   }
  }
 }

 /**
  * Steam Install状態リセット
  */
 fun resetSteamInstallState() {
  _steamInstallState.value = SteamInstallState.Idle
 }

 /**
  * Errorメッセージクリア
  */
 fun clearError() {
  if (_uiState.value is SettingsUiState.Error) {
   loadSettings()
  }
 }

 /**
  * Successメッセージクリア
  */
 fun clearSuccessMessage() {
  val currentState = _uiState.value as? SettingsUiState.Success
  if (currentState != null && currentState.successMessage != null) {
   _uiState.value = currentState.copy(successMessage = null)
  }
 }

 /**
  * Sync状態リセット
  */
 fun resetSyncState() {
  _syncState.value = SyncState.Idle
 }
}

/**
 * Settings screen UI state
 */
@Immutable
sealed class SettingsUiState {
 @Immutable
 data object Loading : SettingsUiState()

 @Immutable
 data class Success(
  val data: SettingsData,
  val successMessage: String? = null
 ) : SettingsUiState()

 @Immutable
 data class Error(val message: String) : SettingsUiState()
}

/**
 * Settings data
 *
 * Note: API Key must be provided by user (removed from app for security)
 */
@Immutable
data class SettingsData(
 val steamId: String,
 val steamUsername: String,
 val lastSyncTimestamp: Long?,
 val isSteamConfigured: Boolean
) {
 /**
  * 最終Syncdate and timeフォーマット
  */
 val lastSyncFormatted: String
  get() {
   if (lastSyncTimestamp == null) return "Never synced"
   val now = System.currentTimeMillis()
   val diff = now - lastSyncTimestamp

   return when {
    diff < 60_000 -> "Less than 1 minute ago"
    diff < 3600_000 -> "${diff / 60_000}minutes前"
    diff < 86400_000 -> "${diff / 3600_000} hours ago"
    else -> "${diff / 86400_000} days ago"
   }
  }
}

/**
 * Sync state
 */
@Immutable
sealed class SyncState {
 @Immutable
 data object Idle : SyncState()

 @Immutable
 data class Syncing(
  val progress: Float,
  val message: String
 ) : SyncState()

 @Immutable
 data class Success(val syncedGamesCount: Int) : SyncState()

 @Immutable
 data class Error(val message: String) : SyncState()
}

/**
 * Steam Client installation state
 */
@Immutable
sealed class SteamInstallState {
 /** Idle state */
 @Immutable
 data object Idle : SteamInstallState()

 /** Checking installation status */
 @Immutable
 data object Checking : SteamInstallState()

 /** Not installed */
 @Immutable
 data class NotInstalled(val message: String) : SteamInstallState()

 /** Installed */
 @Immutable
 data class Installed(
  val installPath: String,
  val containerId: String
 ) : SteamInstallState()

 /** Installing */
 @Immutable
 data class Installing(
  val progress: Float,
  val message: String
 ) : SteamInstallState()

 /** Error */
 @Immutable
 data class Error(val message: String) : SteamInstallState()
}
