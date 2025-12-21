package com.steamdeck.mobile.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.SteamCredentialManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings screen ViewModel
 *
 * Manages Steam authentication, library sync, and app settings
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
 private val steamSetupManager: SteamSetupManager,
 private val steamCredentialManager: SteamCredentialManager
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
  * Get Steam container ID (the container where Steam client is installed)
  * Returns null if Steam is not installed
  */
 fun getSteamContainerId(): Long? {
  return when (val state = _steamInstallState.value) {
   is SteamInstallState.Installed -> state.containerId.toLongOrNull()
   else -> null
  }
 }

 /**
  * Load settings data
  *
  * Note: Users must register their own Steam Web API Key
  * Obtain at: https://steamcommunity.com/dev/apikey
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
  * Auto-sync library after QR authentication
  *
  * Automatically syncs library after successful QR authentication
  *
  * Flow:
  * 1. Write Steam credentials to VDF files (loginusers.vdf, config.vdf)
  * 2. Sync Steam library via Web API
  *
  * This ensures Steam client can auto-login after QR authentication
  */
 fun syncAfterQrLogin() {
  viewModelScope.launch {
   // Wait briefly to ensure QR authentication is complete
   kotlinx.coroutines.delay(500)

   // Get Steam ID and container ID
   val steamIdFlow = securePreferences.getSteamId()
   val steamId = steamIdFlow.first()
   val containerId = getSteamContainerId()

   // Write Steam credentials to VDF files (loginusers.vdf, config.vdf)
   if (steamId != null && containerId != null) {
    AppLogger.i(TAG, "Writing Steam credentials after QR login: steamId=$steamId, containerId=$containerId")

    val credentialResult = steamCredentialManager.writeSteamCredentials(
     containerId = containerId.toString(),
     steamId = steamId
    )

    if (credentialResult.isSuccess) {
     AppLogger.i(TAG, "✅ Steam credentials written successfully")
    } else {
     AppLogger.w(TAG, "Failed to write Steam credentials: ${credentialResult.exceptionOrNull()?.message}")
     // Non-fatal error - continue with sync anyway
    }
   } else {
    AppLogger.w(TAG, "Cannot write Steam credentials: steamId=$steamId, containerId=$containerId")
   }

   // Proceed with library sync
   syncSteamLibrary()
  }
 }

 /**
  * Sync Steam library
  *
  * Best Practice: Uses user-provided API Key
  * Users must register their API Key beforehand
  *
  * 2025 Best Practice: DataResult<T> error handling
  */
 fun syncSteamLibrary() {
  viewModelScope.launch {
   // Get Steam ID
   val currentData = (_uiState.value as? SettingsUiState.Success)?.data
   val steamId = currentData?.steamId

   if (steamId.isNullOrBlank()) {
    AppLogger.w(TAG, "Sync attempted without Steam ID")
    _syncState.value = SyncState.Error("Steam ID not found. Please login.")
    return@launch
   }

   // Get Steam container ID (where Steam client is installed)
   val containerId = getSteamContainerId()
   if (containerId == null) {
    AppLogger.w(TAG, "Sync attempted without Steam container")
    _syncState.value = SyncState.Error("Steam not installed. Please install Steam first.")
    return@launch
   }

   _syncState.value = SyncState.Syncing(progress = 0f, message = "Starting sync...")
   AppLogger.i(TAG, "Starting library sync for Steam ID: $steamId, Container ID: $containerId")

   // Type-safe error handling using DataResult<Int>
   when (val result = syncSteamLibraryUseCase(steamId, containerId)) {
    is DataResult.Success -> {
     val syncedCount = result.data
     securePreferences.setLastSyncTimestamp(System.currentTimeMillis())
     _syncState.value = SyncState.Success(syncedCount)
     loadSettings() // Update last sync date and time
     AppLogger.i(TAG, "Library sync completed: $syncedCount games")
    }
    is DataResult.Error -> {
     val errorMessage = result.error.toUserMessage(context)
     _syncState.value = SyncState.Error(errorMessage)
     AppLogger.e(TAG, "Library sync failed: $errorMessage")
    }
    is DataResult.Loading -> {
     // Update progress (for future ProgressBar support)
     _syncState.value = SyncState.Syncing(
      progress = result.progress ?: 0f,
      message = "Syncing..."
     )
    }
   }
  }
 }

 /**
  * Save Steam API Key
  *
  * @param apiKey Steam Web API Key (32-character hexadecimal string)
  * Obtain at: https://steamcommunity.com/dev/apikey
  */
 fun saveSteamApiKey(apiKey: String) {
  viewModelScope.launch {
   try {
    // API Key validation
    if (apiKey.isBlank()) {
     _uiState.value = SettingsUiState.Error("Please enter API Key")
     return@launch
    }

    if (apiKey.length != 32 || !apiKey.matches(Regex("^[0-9A-Fa-f]{32}$"))) {
     _uiState.value = SettingsUiState.Error("Invalid API Key (must be 32 hexadecimal characters)")
     return@launch
    }

    // Save API Key
    securePreferences.saveSteamApiKey(apiKey)
    AppLogger.i(TAG, "Steam API Key saved successfully")

    _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
     successMessage = "API Key saved successfully"
    ) ?: SettingsUiState.Loading
   } catch (e: Exception) {
    _uiState.value = SettingsUiState.Error("API Key save failed: ${e.message}")
   }
  }
 }

 /**
  * Clear Steam settings
  */
 fun clearSteamSettings() {
  viewModelScope.launch {
   try {
    securePreferences.clearSteamSettings()
    loadSettings()
    _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
     successMessage = context.getString(com.steamdeck.mobile.R.string.success_steam_settings_cleared)
    ) ?: SettingsUiState.Loading
   } catch (e: Exception) {
    _uiState.value = SettingsUiState.Error("Clear failed: ${e.message}")
   }
  }
 }

 /**
  * Check Steam installation status
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
     "Failed to check installation status: ${e.message}"
    )
   }
  }
 }

 /**
  * Install Steam Client
  *
  * @param containerId WinlatorContainer ID (String type)
  */
 fun installSteamClient(containerId: String) {
  viewModelScope.launch {
   try {
    AppLogger.i(TAG, "Starting Steam Client installation for container: $containerId")

    val result = steamSetupManager.installSteam(
     containerId = containerId,
     progressCallback = { progress, message, detailMessage ->
      _steamInstallState.value = SteamInstallState.Installing(progress, message, detailMessage)
      AppLogger.d(TAG, "Install progress: $progress - $message${detailMessage?.let { " ($it)" } ?: ""}")
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
       "Installation failed: ${error.message}"
      )
      AppLogger.e(TAG, "Failed to install Steam Client", error)
     }

   } catch (e: Exception) {
    _steamInstallState.value = SteamInstallState.Error(
     "Unexpected error occurred: ${e.message}"
    )
    AppLogger.e(TAG, "Exception during Steam installation", e)
   }
  }
 }

 /**
  * Open Steam Client in XServer
  *
  * TODO: Navigate to SteamDisplayScreen with integrated XServer rendering
  * Will use SteamDisplayScreen.kt (Compose UI) + XServerView (OpenGL rendering)
  *
  * @param containerId WinlatorContainer ID (e.g., "default_shared_container")
  */
 fun openSteamClient(containerId: String) {
  viewModelScope.launch {
   try {
    AppLogger.i(TAG, "Opening Steam Client (XServer integration pending)")

    // Get Steam.exe path from container
    val steamInstallPath = getSteamInstallPath(containerId)
    if (steamInstallPath == null) {
     _steamInstallState.value = SteamInstallState.Error(
      "Steam.exe not found. Please install Steam first."
     )
     AppLogger.w(TAG, "Steam.exe not found in container: $containerId")
     return@launch
    }

    // TODO: Navigate to Screen.SteamDisplay route
    // For now, just log the intent
    AppLogger.d(TAG, "Would navigate to SteamDisplayScreen with steam.exe: $steamInstallPath")
    _uiState.value = (_uiState.value as? SettingsUiState.Success)?.copy(
     successMessage = "XServer integration pending - navigation route needed"
    ) ?: SettingsUiState.Loading

   } catch (e: Exception) {
    _steamInstallState.value = SteamInstallState.Error(
     "Failed to prepare Steam launch: ${e.message ?: "Unknown error"}"
    )
    AppLogger.e(TAG, "Exception while preparing Steam launch", e)
   }
  }
 }

 /**
  * Get Steam.exe installation path from container
  */
 private fun getSteamInstallPath(containerId: String): String? {
  return try {
   // Check standard Steam installation path
   val steamExe = java.io.File(
    context.filesDir,
    "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/Steam.exe"
   )

   if (steamExe.exists()) {
    steamExe.absolutePath
   } else {
    null
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to get Steam install path", e)
   null
  }
 }

 /**
  * Uninstall Steam Client
  *
  * @param containerId WinlatorContainer ID
  */
 fun uninstallSteamClient(containerId: String) {
  viewModelScope.launch {
   try {
    AppLogger.i(TAG, "Uninstalling Steam Client for container: $containerId")

    // TODO: Implement actual uninstallation logic
    // Currently just clears database state
    _steamInstallState.value = SteamInstallState.NotInstalled(
     context.getString(com.steamdeck.mobile.R.string.success_steam_uninstalled)
    )

    AppLogger.i(TAG, "Steam Client uninstalled (placeholder)")

   } catch (e: Exception) {
    _steamInstallState.value = SteamInstallState.Error(
     "Uninstallation failed: ${e.message}"
    )
    AppLogger.e(TAG, "Exception during Steam uninstallation", e)
   }
  }
 }

 /**
  * Reset Steam install state
  */
 fun resetSteamInstallState() {
  _steamInstallState.value = SteamInstallState.Idle
 }

 /**
  * Clear error message
  */
 fun clearError() {
  if (_uiState.value is SettingsUiState.Error) {
   loadSettings()
  }
 }

 /**
  * Clear success message
  */
 fun clearSuccessMessage() {
  val currentState = _uiState.value as? SettingsUiState.Success
  if (currentState != null && currentState.successMessage != null) {
   _uiState.value = currentState.copy(successMessage = null)
  }
 }

 /**
  * Reset sync state
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
  * Format last sync date and time
  */
 val lastSyncFormatted: String
  get() {
   if (lastSyncTimestamp == null) return "Never synced"
   val now = System.currentTimeMillis()
   val diff = now - lastSyncTimestamp

   return when {
    diff < 60_000 -> "Less than 1 minute ago"
    diff < 3600_000 -> "${diff / 60_000} minutes ago"
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
  val message: String,
  val detailMessage: String? = null  // Optional detail message (e.g., "File 234/342: Steam.exe")
 ) : SteamInstallState()

 /** Error */
 @Immutable
 data class Error(val message: String) : SteamInstallState()
}
