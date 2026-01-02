package com.steamdeck.mobile.presentation.ui.settings

import com.steamdeck.mobile.core.logging.AppLogger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.R
import com.steamdeck.mobile.presentation.theme.SteamColorPalette
import com.steamdeck.mobile.presentation.ui.auth.SteamOpenIdLoginScreen
import com.steamdeck.mobile.presentation.viewmodel.AutoLaunchState
import com.steamdeck.mobile.presentation.viewmodel.SettingsUiState
import com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel
import com.steamdeck.mobile.presentation.viewmodel.SteamInstallState
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginViewModel
import com.steamdeck.mobile.presentation.viewmodel.SyncState
import com.steamdeck.mobile.presentation.viewmodel.WineTestUiState
import com.steamdeck.mobile.presentation.viewmodel.WineTestViewModel

/**
 * Settings screen - Simplified design without NavigationRail
 *
 * Navigation is handled by the Home screen's mini drawer.
 * This screen only displays the content for the selected section.
 *
 * Best Practices:
 * - Steam color scheme with Material3
 * - No TopAppBar for immersive full-screen experience
 * - Mini drawer controlled from Home screen
 */
@Composable
fun SettingsScreen(
 onNavigateBack: () -> Unit,
 onNavigateToControllerSettings: () -> Unit = {},
 onNavigateToSteamDisplay: (String) -> Unit = {},
 initialSection: Int = -1,
 viewModel: SettingsViewModel = hiltViewModel(),
 wineTestViewModel: WineTestViewModel = hiltViewModel(),
 steamLoginViewModel: SteamLoginViewModel = hiltViewModel()
) {
 val context = LocalContext.current
 val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 val syncState by viewModel.syncState.collectAsStateWithLifecycle()
 val steamInstallState by viewModel.steamInstallState.collectAsStateWithLifecycle()
 val steamLoginState by steamLoginViewModel.uiState.collectAsStateWithLifecycle()
 val autoLaunchState by viewModel.autoLaunchSteamState.collectAsStateWithLifecycle()
 val snackbarHostState = remember { SnackbarHostState() }
 var selectedSection by remember {
  mutableIntStateOf(if (initialSection >= 0) initialSection else 0)
 }
 var showWebView by remember { mutableStateOf(false) }
 var showClearSettingsDialog by remember { mutableStateOf(false) }

 // Update selectedSection when initialSection changes (navigation from drawer)
 LaunchedEffect(initialSection) {
  if (initialSection >= 0) {
   selectedSection = initialSection
  }
 }

 // Show snackbar for error and success messages
 LaunchedEffect(uiState) {
  when (val state = uiState) {
   is SettingsUiState.Error -> {
    snackbarHostState.showSnackbar(
     message = state.message,
     duration = SnackbarDuration.Long
    )
    viewModel.clearError()
   }
   is SettingsUiState.Success -> {
    state.successMessage?.let { message ->
     snackbarHostState.showSnackbar(
      message = message,
      duration = SnackbarDuration.Short
     )
     viewModel.clearSuccessMessage()
    }
   }
   else -> {}
  }
 }

 // Show snackbar for sync completion messages (persistent cards handle display)
 LaunchedEffect(syncState) {
  when (val state = syncState) {
   is SyncState.Success -> {
    snackbarHostState.showSnackbar(
     message = context.getString(R.string.games_synced_count, state.syncedGamesCount),
     duration = SnackbarDuration.Short
    )
    // REMOVED: viewModel.resetSyncState()
    // Success card now persists until user dismisses
   }
   is SyncState.Error -> {
    snackbarHostState.showSnackbar(
     message = state.message,
     duration = SnackbarDuration.Short // Changed from Long
    )
    // REMOVED: viewModel.resetSyncState()
    // Error card now persists until user dismisses
   }
   else -> {}
  }
 }

 Box(modifier = Modifier.fillMaxSize()) {
  when (val state = uiState) {
   is SettingsUiState.Loading -> {
    LoadingContent()
   }
   is SettingsUiState.Success -> {
    // Simplified layout without NavigationRail (mini drawer handles navigation)
    SettingsContent(
     selectedSection = selectedSection,
     data = state.data,
     syncState = syncState,
     steamInstallState = steamInstallState,
     autoLaunchState = autoLaunchState,
     showWebView = showWebView,
     steamLoginViewModel = steamLoginViewModel,
     settingsViewModel = viewModel,
     wineTestViewModel = wineTestViewModel,
     onShowWebView = { showWebView = true },
     onHideWebView = { showWebView = false },
     onSyncLibrary = viewModel::syncSteamLibrary,
     onClearSettings = viewModel::clearSteamSettings,
     onNavigateToControllerSettings = onNavigateToControllerSettings,
     onSaveApiKey = viewModel::saveSteamApiKey,
     onInstallSteam = viewModel::installSteamClient,
     onOpenSteam = { containerId ->
      // Launch Steam client via ViewModel first, then navigate
      viewModel.openSteamClient(containerId)
      onNavigateToSteamDisplay(containerId)
     },
     onUninstallSteam = viewModel::uninstallSteamClient,
     onNavigateBack = onNavigateBack,
     onNavigateToStep = { step -> selectedSection = step },
     onRequestClearSettings = { showClearSettingsDialog = true }
    )
   }
   is SettingsUiState.Error -> {
    LoadingContent()
   }
  }

  // Snackbar host
  SnackbarHost(
   hostState = snackbarHostState,
   modifier = Modifier.align(Alignment.BottomCenter)
  )

  // Clear All Settings Confirmation Dialog
  if (showClearSettingsDialog) {
   ClearSettingsConfirmationDialog(
    onConfirm = {
     viewModel.clearSteamSettings()
     showClearSettingsDialog = false
    },
    onDismiss = {
     showClearSettingsDialog = false
    }
   )
  }
 }
}

/**
 * * SettingsContent - Display details for selected section
 *
 * Best Practice: Max-width for large screens to avoid stretching
 */
@Composable
private fun SettingsContent(
 selectedSection: Int,
 data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
 syncState: SyncState,
 steamInstallState: SteamInstallState,
 autoLaunchState: AutoLaunchState,
 showWebView: Boolean,
 steamLoginViewModel: SteamLoginViewModel,
 settingsViewModel: SettingsViewModel,
 wineTestViewModel: WineTestViewModel,
 onShowWebView: () -> Unit,
 onHideWebView: () -> Unit,
 onSyncLibrary: () -> Unit,
 onClearSettings: () -> Unit,
 onNavigateToControllerSettings: () -> Unit,
 onSaveApiKey: (String) -> Unit,
 onInstallSteam: (String) -> Unit,
 onOpenSteam: (String) -> Unit,
 onUninstallSteam: (String) -> Unit,
 onNavigateBack: () -> Unit,
 onNavigateToStep: (Int) -> Unit,  // NEW: Navigate to specific step
 onRequestClearSettings: () -> Unit,  // NEW: Request to show clear settings dialog
 modifier: Modifier = Modifier
) {
 Surface(
  modifier = modifier.fillMaxSize(),
  color = MaterialTheme.colorScheme.background
 ) {
  Column(modifier = Modifier.fillMaxSize()) {
   // Back button header (always visible)
   Row(
    modifier = Modifier
     .fillMaxWidth()
     .padding(horizontal = 16.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically
   ) {
    IconButton(onClick = onNavigateBack) {
     Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
      contentDescription = stringResource(R.string.content_desc_back),
      tint = MaterialTheme.colorScheme.primary
     )
    }
    Text(
     text = when (selectedSection) {
      0 -> "Step 1: Steam Client"
      1 -> "Step 2: Steam Authentication"
      2 -> "Step 3: Library Sync"
      3 -> "Controller Settings"
      4 -> "Wine Environment"
      5 -> "App Settings"
      else -> "Settings"
     },
     style = MaterialTheme.typography.headlineMedium,
     fontWeight = FontWeight.Bold,
     color = MaterialTheme.colorScheme.primary,
     modifier = Modifier.padding(start = 8.dp)
    )
   }

   // Content area
   Column(
    modifier = Modifier
     .fillMaxSize()
     .verticalScroll(rememberScrollState())
     .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp)
   ) {
    // Section content with dependency checks
    when (selectedSection) {
     0 -> {
      // Step 1: Steam Client Installation (no prerequisites)
      SteamClientContent(
       steamInstallState = steamInstallState,
       isAuthenticated = data.isSteamConfigured,
       steamUsername = data.steamUsername,
       defaultContainerId = "default_shared_container",
       viewModel = settingsViewModel,
       onInstall = onInstallSteam,
       onOpen = onOpenSteam,
       onUninstall = onUninstallSteam,
       onNavigateToAuth = {
        // Navigate to Step 2 (Authentication) tab
        onNavigateToStep(1)
       }
      )
     }
     1 -> {
      // Step 2: Steam Authentication (requires Steam Client for VDF files)
      val isSteamInstalled = steamInstallState is SteamInstallState.Installed
      if (!isSteamInstalled) {
       PrerequisiteWarning(
        message = "Please install Steam Client first (Step 1)",
        requiredStep = "Step 1: Steam Client"
       )
      }
      SteamAuthContent(
       data = data,
       showWebView = showWebView,
       steamLoginViewModel = steamLoginViewModel,
       settingsViewModel = settingsViewModel,
       autoLaunchState = autoLaunchState,
       onShowWebView = onShowWebView,
       onHideWebView = onHideWebView,
       onNavigateToLibrarySync = {
        // Navigate to Step 3 (Library Sync) tab
        onNavigateToStep(2)
       },
       onRequestClearSettings = onRequestClearSettings,
       enabled = isSteamInstalled
      )
     }
     2 -> {
      // Step 3: Library Sync (requires Steam Client + Authentication)
      val isSteamInstalled = steamInstallState is SteamInstallState.Installed
      val isSteamAuthenticated = data.steamId != null

      if (!isSteamInstalled) {
       PrerequisiteWarning(
        message = "Please install Steam Client first (Step 1)",
        requiredStep = "Step 1: Steam Client"
       )
      } else if (!isSteamAuthenticated) {
       PrerequisiteWarning(
        message = "Please authenticate with Steam first (Step 2)",
        requiredStep = "Step 2: Steam Authentication"
       )
      }

      LibrarySyncContent(
       data = data,
       syncState = syncState,
       onSync = onSyncLibrary,
       onNavigateToHome = onNavigateBack, // Navigate back to Home screen (game library)
       onDismissSyncState = { settingsViewModel.resetSyncState() },
       enabled = isSteamInstalled && isSteamAuthenticated
      )
     }
     3 -> ControllerContent(
      onNavigateToControllerSettings = onNavigateToControllerSettings
     )
     4 -> WineTestIntegratedContent(
      viewModel = wineTestViewModel
     )
     5 -> AppSettingsContent()
    }
   }
  }
 }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
 Column(
  modifier = modifier.fillMaxSize(),
  verticalArrangement = Arrangement.Center,
  horizontalAlignment = Alignment.CenterHorizontally
 ) {
  CircularProgressIndicator()
  Spacer(modifier = Modifier.height(16.dp))
  Text(
   text = "Loading settings...",
   style = MaterialTheme.typography.bodyMedium,
   color = MaterialTheme.colorScheme.onSurfaceVariant
  )
 }
}

// ========================================
// Section-specific content (BackboneOne-style cards)
// ========================================

@Composable
private fun SteamAuthContent(
 data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
 showWebView: Boolean,
 steamLoginViewModel: SteamLoginViewModel,
 settingsViewModel: SettingsViewModel,
 autoLaunchState: AutoLaunchState,
 onShowWebView: () -> Unit,
 onHideWebView: () -> Unit,
 onNavigateToLibrarySync: () -> Unit,
 onRequestClearSettings: () -> Unit,
 enabled: Boolean = true
) {
 val steamLoginState by steamLoginViewModel.uiState.collectAsStateWithLifecycle()

 // Close WebView on authentication success and trigger auto-sync
 LaunchedEffect(steamLoginState) {
  if (steamLoginState is SteamLoginUiState.Success) {
   AppLogger.i(
    "SettingsScreen",
    "✅ Steam authentication success! Triggering auto-sync and closing WebView..."
   )

   // Trigger automatic library sync after successful login
   settingsViewModel.syncAfterQrLogin()

   onHideWebView()
  }
 }

 if (showWebView) {
  // Display WebView (full size, sidebar maintained)
  val (authUrl, _) = remember { steamLoginViewModel.startOpenIdLogin() }

  Box(
   modifier = Modifier
    .fillMaxSize()
    .background(SteamColorPalette.Dark)
  ) {
   SteamOpenIdLoginScreen(
    authUrl = authUrl,
    callbackUrl = "http://127.0.0.1:8080/auth/callback",
    onAuthCallback = { callbackUrl ->
     steamLoginViewModel.handleCallback(callbackUrl)
    },
    onError = { errorMessage ->
     AppLogger.e("SteamAuth", "OpenID error: $errorMessage")
    },
    onCancel = {
     // Cancel login - return to authentication screen
     onHideWebView()
    }
   )
  }
 } else {
  Column(
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   if (data.isSteamConfigured) {
    // Already logged in - Steam style design
    SteamAuthLoggedInSection(
     username = data.steamUsername,
     steamId = data.steamId,
     onNavigateToLibrarySync = onNavigateToLibrarySync,
     onRelogin = onShowWebView,
     onRequestClearSettings = onRequestClearSettings
    )
   } else {
    // Not logged in - Display OpenID login button
    SteamOpenIdAuthSection(
     onNavigateToLogin = onShowWebView,
     enabled = enabled
    )
   }

   // API Key configuration section (always visible)
   SteamApiKeySection(
    apiKey = data.steamApiKey,
    onSaveApiKey = settingsViewModel::saveSteamApiKey,
    enabled = enabled
   )

   // Auto-launch Steam progress (NEW - 2025-12-23)
   SteamAutoLaunchProgress(autoLaunchState = autoLaunchState)
  }
 }
}

/**
 * Steam Authentication - Logged In Section
 *
 * Displays user information and provides three action buttons:
 * 1. PRIMARY: Navigate to Library Sync (next step)
 * 2. SECONDARY: Re-login (QR code authentication)
 * 3. DESTRUCTIVE: Clear All Settings (with confirmation dialog)
 *
 * @param username Steam account name
 * @param steamId SteamID64
 * @param onNavigateToLibrarySync Callback to scroll/navigate to Library Sync section
 * @param onRelogin Callback to initiate QR re-authentication
 * @param onRequestClearSettings Callback to request clear all settings (triggers confirmation dialog)
 */
@Composable
private fun SteamAuthLoggedInSection(
 username: String,
 steamId: String,
 onNavigateToLibrarySync: () -> Unit,
 onRelogin: () -> Unit,
 onRequestClearSettings: () -> Unit
) {
 Card(
  modifier = Modifier
   .fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = SteamColorPalette.Dark
  ),
  shape = RoundedCornerShape(8.dp),
  elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
 ) {
  Box(
   modifier = Modifier
    .fillMaxSize()
    .background(
     brush = Brush.verticalGradient(
      colors = listOf(
       SteamColorPalette.Dark,
       SteamColorPalette.DarkerShade
      )
     )
    )
  ) {
   Row(
    modifier = Modifier
     .fillMaxSize()
     .padding(horizontal = 26.dp, vertical = 18.dp),
    horizontalArrangement = Arrangement.spacedBy(26.dp)
   ) {
    // Left column: User information
    Column(
     modifier = Modifier
      .weight(0.5f)
      .fillMaxHeight(),
     verticalArrangement = Arrangement.Center
    ) {
     Text(
      text = "STEAM",
      style = MaterialTheme.typography.displayMedium.copy(
       fontWeight = FontWeight.Bold,
       fontSize = 30.sp,
       letterSpacing = 2.5.sp
      ),
      color = SteamColorPalette.Blue
     )

     Spacer(modifier = Modifier.height(3.dp))

     Text(
      text = "Deck Mobile",
      style = MaterialTheme.typography.titleSmall,
      color = SteamColorPalette.Gray
     )

     Spacer(modifier = Modifier.height(20.dp))

     Icon(
      imageVector = Icons.Default.Check,
      contentDescription = null,
      modifier = Modifier.size(34.dp),
      tint = SteamColorPalette.Green
     )

     Spacer(modifier = Modifier.height(8.dp))

     Text(
      text = "Logged In",
      style = MaterialTheme.typography.headlineSmall.copy(
       fontWeight = FontWeight.Bold
      ),
      color = Color.White
     )

     Spacer(modifier = Modifier.height(8.dp))

     Text(
      text = username,
      style = MaterialTheme.typography.bodyLarge.copy(
       fontWeight = FontWeight.SemiBold
      ),
      color = SteamColorPalette.Blue
     )

     Text(
      text = "Steam ID: $steamId",
      style = MaterialTheme.typography.bodySmall,
      color = SteamColorPalette.Gray
     )
    }

    // Right column: Action Buttons (3-tier hierarchy)
    Column(
     modifier = Modifier
      .weight(0.5f)
      .fillMaxHeight(),
     horizontalAlignment = Alignment.CenterHorizontally,
     verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
     // PRIMARY: Next step - Sync Library
     Button(
      onClick = onNavigateToLibrarySync,
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(
       containerColor = SteamColorPalette.Green
      )
     ) {
      Icon(
       imageVector = Icons.Default.Refresh,
       contentDescription = null,
       modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
       text = stringResource(R.string.button_sync_library),
       style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold
       )
      )
     }

     // SECONDARY: Re-Login
     OutlinedButton(
      onClick = onRelogin,
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.outlinedButtonColors(
       contentColor = SteamColorPalette.Blue
      ),
      border = BorderStroke(1.dp, SteamColorPalette.Blue)
     ) {
      Text(
       text = stringResource(R.string.button_relogin),
       style = MaterialTheme.typography.titleMedium
      )
     }

     // DESTRUCTIVE: Clear All Settings
     TextButton(
      onClick = onRequestClearSettings,
      modifier = Modifier.fillMaxWidth()
     ) {
      Icon(
       imageVector = Icons.Default.Warning,
       contentDescription = null,
       tint = MaterialTheme.colorScheme.error,
       modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
       text = stringResource(R.string.button_clear_all_settings),
       style = MaterialTheme.typography.labelLarge,
       color = MaterialTheme.colorScheme.error
      )
     }
    }
   }
  }
 }
}

/**
 * Steam OpenID authentication section (Steam official style design)
 *
 * Steam official color palette:
 * - #171a21: Navbar/dark background
 * - #1b2838: Primary background
 * - #2a475e: Secondary background
 * - #66c0f4: Accent blue
 * - #c7d5e0: Light text
 */
@Composable
private fun SteamOpenIdAuthSection(
 onNavigateToLogin: () -> Unit,
 enabled: Boolean = true
) {
 Box(
  modifier = Modifier
   .fillMaxSize()
   .background(
    brush = Brush.verticalGradient(
     colors = listOf(SteamColorPalette.Navbar, SteamColorPalette.Dark)
    )
   ),
  contentAlignment = Alignment.Center
 ) {
  // Main card
  Card(
   modifier = Modifier
    .fillMaxWidth(0.85f)
    .padding(16.dp),
   colors = CardDefaults.cardColors(
    containerColor = SteamColorPalette.Medium.copy(alpha = 0.6f)
   ),
   shape = RoundedCornerShape(4.dp),
   elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
  ) {
   Column(
    modifier = Modifier
     .fillMaxWidth()
     .background(
      brush = Brush.verticalGradient(
       colors = listOf(
        SteamColorPalette.Medium.copy(alpha = 0.8f),
        SteamColorPalette.Dark.copy(alpha = 0.95f)
       )
      )
     )
     .padding(horizontal = 32.dp, vertical = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally
   ) {
    // Steam logo
    Text(
     text = "STEAM",
     style = MaterialTheme.typography.displaySmall.copy(
      fontWeight = FontWeight.Black,
      letterSpacing = 6.sp
     ),
     color = SteamColorPalette.LightText
    )
    
    Spacer(modifier = Modifier.height(4.dp))
    
    // Divider line
    Box(
     modifier = Modifier
      .width(60.dp)
      .height(2.dp)
      .background(
       brush = Brush.horizontalGradient(
        colors = listOf(
         Color.Transparent,
         SteamColorPalette.Blue,
         Color.Transparent
        )
       )
      )
    )
    
    Spacer(modifier = Modifier.height(28.dp))
    
    // Title
    Text(
     text = "Sign In",
     style = MaterialTheme.typography.titleLarge.copy(
      fontWeight = FontWeight.Normal
     ),
     color = SteamColorPalette.LightText
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Login button (Steam-style gradient)
    Button(
     onClick = onNavigateToLogin,
     enabled = enabled,
     modifier = Modifier
      .fillMaxWidth()
      .height(48.dp),
     colors = androidx.compose.material3.ButtonDefaults.buttonColors(
      containerColor = Color.Transparent,
      disabledContainerColor = Color.Transparent
     ),
     shape = RoundedCornerShape(2.dp),
     contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
     Box(
      modifier = Modifier
       .fillMaxSize()
       .background(
        brush = Brush.linearGradient(
         colors = if (enabled) {
          listOf(
           SteamColorPalette.BrightBlue,
           SteamColorPalette.DeepBlue
          )
         } else {
          listOf(
           SteamColorPalette.Gray.copy(alpha = 0.3f),
           SteamColorPalette.Gray.copy(alpha = 0.5f)
          )
         }
        )
       ),
      contentAlignment = Alignment.Center
     ) {
      Text(
       text = "Sign In with Steam",
       style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
       ),
       color = if (enabled) Color.White else SteamColorPalette.Gray
      )
     }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Subtext
    Text(
     text = "Official Steam Authentication (OpenID 2.0)",
     style = MaterialTheme.typography.bodySmall,
     color = SteamColorPalette.Gray
    )
   }
  }
 }
}

/**
 * Steam Client Installation Content
 *
 * Displays Steam client installation status and actions.
 * Switches UI based on authentication state for better UX flow.
 *
 * @param steamInstallState Current installation state
 * @param isAuthenticated Whether user has authenticated via QR code
 * @param steamUsername Steam account name (for authenticated state)
 * @param defaultContainerId Container ID for installation
 * @param viewModel Settings ViewModel
 * @param onInstall Install Steam client callback
 * @param onOpen Open Steam client callback
 * @param onUninstall Uninstall Steam client callback
 * @param onNavigateToAuth Navigate to authentication step callback
 */
@Composable
private fun SteamClientContent(
 steamInstallState: SteamInstallState,
 isAuthenticated: Boolean,
 steamUsername: String?,
 defaultContainerId: String,
 viewModel: SettingsViewModel,
 onInstall: (String) -> Unit,
 onOpen: (String) -> Unit,
 onUninstall: (String) -> Unit,
 onNavigateToAuth: () -> Unit
) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = SteamColorPalette.Dark // Steam dark blue
  ),
  shape = RoundedCornerShape(8.dp),
  elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .background(
     brush = Brush.verticalGradient(
      colors = listOf(
       SteamColorPalette.Dark,
       SteamColorPalette.DarkerShade
      )
     )
    )
    .padding(24.dp),
   verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
   when (val state = steamInstallState) {
    is SteamInstallState.Idle,
    is SteamInstallState.Checking -> {
     Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
     ) {
      CircularProgressIndicator(
       color = SteamColorPalette.Blue
      )
      Spacer(modifier = Modifier.width(16.dp))
      Text(
       text = "Checking...",
       style = MaterialTheme.typography.bodyLarge,
       color = SteamColorPalette.LightText
      )
     }
    }

    is SteamInstallState.NotInstalled -> {
     // Header with icon
     Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp)
     ) {
      Surface(
       shape = RoundedCornerShape(12.dp),
       color = SteamColorPalette.Medium
      ) {
       Icon(
        imageVector = Icons.Default.CloudDownload,
        contentDescription = null,
        modifier = Modifier
         .size(56.dp)
         .padding(12.dp),
        tint = SteamColorPalette.Blue
       )
      }
      Column(modifier = Modifier.weight(1f)) {
       Text(
        text = "Steam Client",
        style = MaterialTheme.typography.titleLarge.copy(
         fontWeight = FontWeight.Bold
        ),
        color = Color.White
       )
       Text(
        text = "Not Installed",
        style = MaterialTheme.typography.bodyMedium,
        color = SteamColorPalette.Gray
       )
      }
     }

     // Divider
     Box(
      modifier = Modifier
       .fillMaxWidth()
       .height(1.dp)
       .background(SteamColorPalette.Medium)
     )

     // Description
     Text(
      text = "Install Steam Client in Wine environment to launch games via Steam.",
      style = MaterialTheme.typography.bodyMedium,
      color = SteamColorPalette.LightText,
      lineHeight = 22.sp
     )

     // Info box
     Surface(
      shape = RoundedCornerShape(6.dp),
      color = SteamColorPalette.Medium.copy(alpha = 0.5f)
     ) {
      Column(
       modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
       verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
       Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
       ) {
        Icon(
         imageVector = Icons.Default.Info,
         contentDescription = null,
         tint = SteamColorPalette.Blue,
         modifier = Modifier.size(20.dp)
        )
        Text(
         text = "Installation Info",
         style = MaterialTheme.typography.titleSmall.copy(
          fontWeight = FontWeight.Bold
         ),
         color = SteamColorPalette.Blue
        )
       }
       Text(
        text = "• Download size: ~100MB\n• First time requires Box64/Wine setup",
        style = MaterialTheme.typography.bodySmall,
        color = SteamColorPalette.Gray,
        lineHeight = 20.sp
       )
      }
     }

     // Install button (Steam style)
     Button(
      onClick = { onInstall(defaultContainerId) },
      modifier = Modifier
       .fillMaxWidth()
       .height(48.dp),
      colors = androidx.compose.material3.ButtonDefaults.buttonColors(
       containerColor = Color.Transparent
      ),
      shape = RoundedCornerShape(4.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
     ) {
      Box(
       modifier = Modifier
        .fillMaxSize()
        .background(
         brush = Brush.linearGradient(
          colors = listOf(
           SteamColorPalette.Green,
           SteamColorPalette.Green.copy(alpha = 0.8f)
          )
         )
        ),
       contentAlignment = Alignment.Center
      ) {
       Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
       ) {
        Icon(
         imageVector = Icons.Default.CloudDownload,
         contentDescription = null,
         tint = Color.White
        )
        Text(
         text = "Install Steam Client",
         style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold
         ),
         color = Color.White
        )
       }
      }
     }
    }

    is SteamInstallState.Installing -> {
     SteamInstallProgressContent(state = state)
    }

    is SteamInstallState.Installed -> {
     // Switch UI based on authentication state
     if (isAuthenticated && !steamUsername.isNullOrBlank()) {
      // Authenticated: Auto-login configured, ready to use
      SteamInstalledAuthenticatedContent(
       installPath = state.installPath,
       containerId = state.containerId,
       steamUsername = steamUsername,
       onOpen = onOpen,
       onUninstall = onUninstall
      )
     } else {
      // Not authenticated: Guide user to complete authentication
      SteamInstalledUnauthenticatedContent(
       installPath = state.installPath,
       containerId = state.containerId,
       onNavigateToAuth = onNavigateToAuth,
       onOpen = onOpen,
       onUninstall = onUninstall
      )
     }
    }

    is SteamInstallState.Error -> {
     // Error header
     Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp)
     ) {
      Surface(
       shape = RoundedCornerShape(12.dp),
       color = MaterialTheme.colorScheme.errorContainer
      ) {
       Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        modifier = Modifier
         .size(56.dp)
         .padding(12.dp),
        tint = MaterialTheme.colorScheme.error
       )
      }
      Column(modifier = Modifier.weight(1f)) {
       Text(
        text = "Error",
        style = MaterialTheme.typography.titleLarge.copy(
         fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.error
       )
       Text(
        text = "Installation Failed",
        style = MaterialTheme.typography.bodyMedium,
        color = SteamColorPalette.Gray
       )
      }
     }

     // Divider
     Box(
      modifier = Modifier
       .fillMaxWidth()
       .height(1.dp)
       .background(SteamColorPalette.Medium)
     )

     // Error message
     Surface(
      shape = RoundedCornerShape(6.dp),
      color = MaterialTheme.colorScheme.errorContainer
     ) {
      Column(
       modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
       verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
       Text(
        text = "Error Details:",
        style = MaterialTheme.typography.titleSmall.copy(
         fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.error
       )
       Text(
        text = state.message,
        style = MaterialTheme.typography.bodyMedium,
        color = SteamColorPalette.LightText,
        lineHeight = 20.sp
       )
      }
     }

     // Retry button
     Button(
      onClick = { onInstall(defaultContainerId) },
      modifier = Modifier
       .fillMaxWidth()
       .height(48.dp),
      colors = androidx.compose.material3.ButtonDefaults.buttonColors(
       containerColor = Color.Transparent
      ),
      shape = RoundedCornerShape(4.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
     ) {
      Box(
       modifier = Modifier
        .fillMaxSize()
        .background(
         brush = Brush.linearGradient(
          colors = listOf(
           SteamColorPalette.BrightBlue,
           SteamColorPalette.DeepBlue
          )
         )
        ),
       contentAlignment = Alignment.Center
      ) {
       Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
       ) {
        Icon(
         imageVector = Icons.Default.Refresh,
         contentDescription = null,
         tint = Color.White
        )
        Text(
         text = "Retry",
         style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold
         ),
         color = Color.White
        )
       }
      }
     }
    }
   }
  }
 }
}

/**
 * Sync Success Summary Card
 *
 * Persistent success feedback showing sync results with navigation to game library.
 * Replaces ephemeral snackbar for better UX.
 */
@Composable
private fun SyncSuccessSummary(
 syncedGamesCount: Int,
 onViewGames: () -> Unit,
 onDismiss: () -> Unit
) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = SteamColorPalette.Green.copy(alpha = 0.1f)
  ),
  shape = RoundedCornerShape(8.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   // Success header
   Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
   ) {
    Icon(
     imageVector = Icons.Default.CheckCircle,
     contentDescription = null,
     tint = SteamColorPalette.Green,
     modifier = Modifier.size(32.dp)
    )
    Column {
     Text(
      text = stringResource(R.string.sync_success_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface
     )
     Text(
      text = stringResource(R.string.sync_games_synced_count, syncedGamesCount),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }
   }

   // Divider
   Box(
    modifier = Modifier
     .fillMaxWidth()
     .height(1.dp)
     .background(SteamColorPalette.Medium.copy(alpha = 0.3f))
   )

   // Next steps guidance
   Text(
    text = stringResource(R.string.sync_success_message),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )

   // View Games button (PRIMARY CTA)
   Button(
    onClick = onViewGames,
    modifier = Modifier
     .fillMaxWidth()
     .height(48.dp),
    colors = ButtonDefaults.buttonColors(
     containerColor = Color.Transparent
    ),
    shape = RoundedCornerShape(4.dp),
    contentPadding = PaddingValues(0.dp)
   ) {
    Box(
     modifier = Modifier
      .fillMaxSize()
      .background(
       brush = Brush.linearGradient(
        colors = listOf(
         SteamColorPalette.BrightBlue,
         SteamColorPalette.DeepBlue
        )
       )
      ),
     contentAlignment = Alignment.Center
    ) {
     Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
     ) {
      Icon(
       imageVector = Icons.Default.SportsEsports,
       contentDescription = null,
       tint = Color.White
      )
      Text(
       text = stringResource(R.string.button_view_games),
       style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold
       ),
       color = Color.White
      )
     }
    }
   }

   // Dismiss button
   OutlinedButton(
    onClick = onDismiss,
    modifier = Modifier.align(Alignment.End)
   ) {
    Icon(
     imageVector = Icons.Default.Close,
     contentDescription = null,
     modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(stringResource(R.string.button_dismiss))
   }
  }
 }
}

/**
 * Sync Error Card
 *
 * Expandable error display with troubleshooting tips.
 * Provides persistent error feedback unlike ephemeral snackbar.
 */
@Composable
private fun SyncErrorCard(
 errorMessage: String,
 onDismiss: () -> Unit
) {
 var expanded by remember { mutableStateOf(true) }

 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.errorContainer
  ),
  shape = RoundedCornerShape(8.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   // Error header with expand/collapse
   Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
   ) {
    Row(
     verticalAlignment = Alignment.CenterVertically,
     horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
     Icon(
      imageVector = Icons.Default.Warning,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(28.dp)
     )
     Text(
      text = stringResource(R.string.sync_failed_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.error
     )
    }
    IconButton(onClick = { expanded = !expanded }) {
     Icon(
      imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
      contentDescription = if (expanded) stringResource(R.string.content_desc_collapse_details)
                           else stringResource(R.string.content_desc_expand_details),
      tint = MaterialTheme.colorScheme.error
     )
    }
   }

   // Error details (expandable)
   if (expanded) {
    Surface(
     shape = RoundedCornerShape(6.dp),
     color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    ) {
     Column(
      modifier = Modifier
       .fillMaxWidth()
       .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
     ) {
      Text(
       text = stringResource(R.string.sync_error_details_label),
       style = MaterialTheme.typography.labelMedium,
       fontWeight = FontWeight.Bold,
       color = MaterialTheme.colorScheme.error
      )
      Text(
       text = errorMessage,
       style = MaterialTheme.typography.bodyMedium,
       color = MaterialTheme.colorScheme.onErrorContainer,
       lineHeight = 20.sp
      )
     }
    }

    // Troubleshooting tips
    Text(
     text = stringResource(R.string.sync_troubleshooting_tip),
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onErrorContainer,
     fontStyle = FontStyle.Italic
    )
   }

   // Dismiss button
   OutlinedButton(
    onClick = onDismiss,
    modifier = Modifier.align(Alignment.End)
   ) {
    Icon(
     imageVector = Icons.Default.Close,
     contentDescription = null,
     modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(stringResource(R.string.button_dismiss))
   }
  }
 }
}

/**
 * Clear Settings Confirmation Dialog
 *
 * Shows a warning dialog before executing the destructive "Clear All Settings" action.
 * Prevents accidental deletion of Steam ID, API Key, and all preferences.
 *
 * @param onConfirm Callback when user confirms deletion
 * @param onDismiss Callback when user cancels
 */
@Composable
private fun ClearSettingsConfirmationDialog(
 onConfirm: () -> Unit,
 onDismiss: () -> Unit
) {
 AlertDialog(
  onDismissRequest = onDismiss,
  icon = {
   Icon(
    imageVector = Icons.Default.Warning,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.error,
    modifier = Modifier.size(32.dp)
   )
  },
  title = {
   Text(
    text = stringResource(R.string.dialog_clear_settings_title),
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.error
   )
  },
  text = {
   Column(
    verticalArrangement = Arrangement.spacedBy(16.dp)
   ) {
    Text(
     text = stringResource(R.string.dialog_clear_settings_message),
     style = MaterialTheme.typography.bodyMedium
    )

    Surface(
     color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
     shape = RoundedCornerShape(8.dp)
    ) {
     Column(
      modifier = Modifier
       .fillMaxWidth()
       .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
     ) {
      Text(
       text = "• ${stringResource(R.string.dialog_clear_settings_item_steam_id)}",
       style = MaterialTheme.typography.bodyMedium,
       color = MaterialTheme.colorScheme.onErrorContainer
      )
      Text(
       text = "• ${stringResource(R.string.dialog_clear_settings_item_api_key)}",
       style = MaterialTheme.typography.bodyMedium,
       color = MaterialTheme.colorScheme.onErrorContainer
      )
      Text(
       text = "• ${stringResource(R.string.dialog_clear_settings_item_sync_history)}",
       style = MaterialTheme.typography.bodyMedium,
       color = MaterialTheme.colorScheme.onErrorContainer
      )
      Text(
       text = "• ${stringResource(R.string.dialog_clear_settings_item_all_prefs)}",
       style = MaterialTheme.typography.bodyMedium,
       color = MaterialTheme.colorScheme.onErrorContainer
      )
     }
    }

    Text(
     text = stringResource(R.string.dialog_clear_settings_warning),
     style = MaterialTheme.typography.bodySmall,
     fontWeight = FontWeight.Bold,
     color = MaterialTheme.colorScheme.error
    )
   }
  },
  confirmButton = {
   Button(
    onClick = {
     onConfirm()
     onDismiss()
    },
    colors = ButtonDefaults.buttonColors(
     containerColor = MaterialTheme.colorScheme.error
    )
   ) {
    Text(stringResource(R.string.dialog_clear_settings_confirm))
   }
  },
  dismissButton = {
   TextButton(onClick = onDismiss) {
    Text(stringResource(R.string.button_cancel))
   }
  }
 )
}

/**
 * Login Success Card
 *
 * Displays after successful QR authentication to guide user to the next step.
 * Shows welcome message and directs user to Library Sync.
 *
 * @param username Steam account name
 * @param onNavigateToSync Callback to navigate to Library Sync section
 * @param onDismiss Callback to dismiss the card
 */
@Composable
private fun LoginSuccessCard(
 username: String,
 onNavigateToSync: () -> Unit,
 onDismiss: () -> Unit
) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = SteamColorPalette.Green.copy(alpha = 0.1f)
  ),
  shape = RoundedCornerShape(8.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   // Success header with icon
   Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
   ) {
    Icon(
     imageVector = Icons.Default.CheckCircle,
     contentDescription = null,
     tint = SteamColorPalette.Green,
     modifier = Modifier.size(32.dp)
    )
    Column {
     Text(
      text = stringResource(R.string.login_success_title, username),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface
     )
     Text(
      text = stringResource(R.string.login_success_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }
   }

   // Divider
   HorizontalDivider(
    thickness = 1.dp,
    color = SteamColorPalette.Medium.copy(alpha = 0.3f)
   )

   // Next steps guidance
   Column(
    verticalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    Text(
     text = stringResource(R.string.login_success_next_steps),
     style = MaterialTheme.typography.labelLarge,
     fontWeight = FontWeight.Bold,
     color = MaterialTheme.colorScheme.onSurface
    )

    Row(
     verticalAlignment = Alignment.CenterVertically,
     horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     Surface(
      color = SteamColorPalette.Green,
      shape = RoundedCornerShape(4.dp),
      modifier = Modifier.size(24.dp)
     ) {
      Box(contentAlignment = Alignment.Center) {
       Text(
        text = "1",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White
       )
      }
     }
     Text(
      text = stringResource(R.string.login_success_step_1),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }

    Row(
     verticalAlignment = Alignment.CenterVertically,
     horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     Surface(
      color = SteamColorPalette.DeepBlue,
      shape = RoundedCornerShape(4.dp),
      modifier = Modifier.size(24.dp)
     ) {
      Box(contentAlignment = Alignment.Center) {
       Text(
        text = "2",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White
       )
      }
     }
     Text(
      text = stringResource(R.string.login_success_step_2),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }
   }

   // Navigate to Sync button
   Button(
    onClick = onNavigateToSync,
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(
     containerColor = SteamColorPalette.Green
    )
   ) {
    Icon(
     imageVector = Icons.Default.Refresh,
     contentDescription = null
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
     text = stringResource(R.string.button_go_to_library_sync),
     style = MaterialTheme.typography.titleMedium,
     fontWeight = FontWeight.Bold
    )
   }

   // Dismiss button
   TextButton(
    onClick = onDismiss,
    modifier = Modifier.align(Alignment.End)
   ) {
    Icon(
     imageVector = Icons.Default.Close,
     contentDescription = null,
     modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(stringResource(R.string.button_dismiss))
   }
  }
 }
}

@Composable
private fun LibrarySyncContent(
 data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
 syncState: SyncState,
 onSync: () -> Unit,
 onNavigateToHome: () -> Unit,
 onDismissSyncState: () -> Unit,
 enabled: Boolean = true
) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.surfaceVariant
  ),
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Text(
    text = "Last Sync: ${data.lastSyncFormatted}",
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )

   // Sync progress (only during syncing)
   if (syncState is SyncState.Syncing) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
     LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
     Text(
      text = syncState.message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary
     )
    }
   }

   // Enhanced sync button with state-based icons
   FilledTonalButton(
    onClick = {
     // Auto-dismiss previous result before new sync
     if (syncState is SyncState.Success || syncState is SyncState.Error) {
      onDismissSyncState()
     }
     onSync()
    },
    enabled = enabled && data.isSteamConfigured && syncState !is SyncState.Syncing,
    modifier = Modifier.fillMaxWidth()
   ) {
    when (syncState) {
     is SyncState.Syncing -> {
      CircularProgressIndicator(
       modifier = Modifier.size(20.dp),
       strokeWidth = 2.dp,
       color = MaterialTheme.colorScheme.onPrimaryContainer
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text("Syncing...")
     }
     is SyncState.Success -> {
      Icon(
       imageVector = Icons.Default.CheckCircle,
       contentDescription = null
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(stringResource(R.string.button_sync_again))
     }
     else -> {
      Icon(
       imageVector = Icons.Default.Refresh,
       contentDescription = stringResource(R.string.content_desc_sync)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text("Sync Library")
     }
    }
   }

   // Persistent result cards (Success/Error)
   when (val state = syncState) {
    is SyncState.Success -> {
     SyncSuccessSummary(
      syncedGamesCount = state.syncedGamesCount,
      onViewGames = onNavigateToHome,
      onDismiss = onDismissSyncState
     )
    }
    is SyncState.Error -> {
     SyncErrorCard(
      errorMessage = state.message,
      onDismiss = onDismissSyncState
     )
    }
    else -> {}
   }
  }
 }
}

@Composable
private fun ControllerContent(
 onNavigateToControllerSettings: () -> Unit
) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.surfaceVariant
  ),
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Text(
    text = "Manage game controller button mapping and profiles",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )

   FilledTonalButton(
    onClick = onNavigateToControllerSettings,
    modifier = Modifier.fillMaxWidth()
   ) {
    Icon(
     imageVector = Icons.Default.SportsEsports,
     contentDescription = stringResource(R.string.content_desc_controller_settings)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text("Open Controller Settings")
   }
  }
 }
}

/**
 * Wine Test integrated content (formerly separate WineTestScreen)
 * Combines Wine diagnostics into Settings Section 4
 * Minimized to essential tests only: Availability, Initialize, Container
 */
@Composable
private fun WineTestIntegratedContent(
 viewModel: WineTestViewModel
) {
 val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 val context = LocalContext.current

 Column(
  modifier = Modifier
   .fillMaxWidth()
   .padding(0.dp),
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  // Header - use string resource
  Text(
   text = stringResource(R.string.wine_test_header),
   style = MaterialTheme.typography.headlineSmall,
   fontWeight = FontWeight.Bold
  )

  // Status card
  WineTestCompactStatusRow(uiState = uiState)

  // Test buttons - essential tests only
  if (uiState !is WineTestUiState.Testing) {
   WineTestCompactTestButtons(
    onCheckWine = viewModel::checkWineAvailability,
    onInitialize = viewModel::initializeEmulator,
    onCreateContainer = viewModel::testCreateContainer
   )
  }

  // Progress/Results - use string resources
  when (val state = uiState) {
   is WineTestUiState.Testing -> {
    WineTestTestingProgressCard(message = state.message)
   }
   is WineTestUiState.Success -> {
    WineTestTestResultCard(
     title = stringResource(R.string.wine_test_result_success),
     message = state.message,
     isSuccess = true
    )
   }
   is WineTestUiState.Error -> {
    WineTestTestResultCard(
     title = stringResource(R.string.wine_test_result_error),
     message = state.message,
     isSuccess = false
    )
   }
   else -> {}
  }
 }
}

@Composable
private fun WineTestCompactStatusRow(uiState: WineTestUiState) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.surfaceVariant
  ),
  shape = MaterialTheme.shapes.medium,
  elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
 ) {
  Row(
   modifier = Modifier
    .fillMaxWidth()
    .padding(16.dp),
   horizontalArrangement = Arrangement.SpaceBetween,
   verticalAlignment = Alignment.CenterVertically
  ) {
   Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    Icon(
     imageVector = when (uiState) {
      is WineTestUiState.Success -> Icons.Default.Check
      is WineTestUiState.Error -> Icons.Default.Clear
      else -> Icons.Default.Info
     },
     contentDescription = null,
     tint = when (uiState) {
      is WineTestUiState.Success -> MaterialTheme.colorScheme.primary
      is WineTestUiState.Error -> MaterialTheme.colorScheme.error
      else -> MaterialTheme.colorScheme.onSurfaceVariant
     }
    )
    // Use string resources for status labels
    Text(
     text = when (uiState) {
      is WineTestUiState.Idle -> stringResource(R.string.wine_test_status_ready)
      is WineTestUiState.Testing -> stringResource(R.string.wine_test_status_running)
      is WineTestUiState.Success -> stringResource(R.string.wine_test_status_available)
      is WineTestUiState.Error -> stringResource(R.string.wine_test_status_error)
     },
     style = MaterialTheme.typography.titleSmall,
     fontWeight = FontWeight.Bold
    )
   }

   Text(
    text = "Wine/Winlator",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
  }
 }
}

/**
 * Essential Wine test buttons (minimized from 5 to 3 tests)
 * Uses string resources for maintainability
 */
@Composable
private fun WineTestCompactTestButtons(
 onCheckWine: () -> Unit,
 onInitialize: () -> Unit,
 onCreateContainer: () -> Unit
) {
 Column(
  modifier = Modifier.fillMaxWidth(),
  verticalArrangement = Arrangement.spacedBy(12.dp)
 ) {
  // Row 1: Check & Initialize
  Row(
   modifier = Modifier.fillMaxWidth(),
   horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   Button(
    onClick = onCheckWine,
    modifier = Modifier.weight(1f)
   ) {
    Text(
     text = stringResource(R.string.wine_test_check_availability),
     style = MaterialTheme.typography.labelLarge
    )
   }

   Button(
    onClick = onInitialize,
    modifier = Modifier.weight(1f)
   ) {
    Text(
     text = stringResource(R.string.wine_test_init_emulator),
     style = MaterialTheme.typography.labelLarge
    )
   }
  }

  // Row 2: Create Container (full width for clarity)
  Button(
   onClick = onCreateContainer,
   modifier = Modifier.fillMaxWidth()
  ) {
   Text(
    text = stringResource(R.string.wine_test_create_container),
    style = MaterialTheme.typography.labelLarge
   )
  }
 }
}

@Composable
private fun WineTestTestingProgressCard(message: String) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.primaryContainer
  ),
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
   Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onPrimaryContainer
   )
  }
 }
}

@Composable
private fun WineTestTestResultCard(
 title: String,
 message: String,
 isSuccess: Boolean
) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = if (isSuccess)
    MaterialTheme.colorScheme.primaryContainer
   else
    MaterialTheme.colorScheme.errorContainer
  ),
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
   Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = if (isSuccess)
     MaterialTheme.colorScheme.onPrimaryContainer
    else
     MaterialTheme.colorScheme.onErrorContainer
   )
   Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    color = if (isSuccess)
     MaterialTheme.colorScheme.onPrimaryContainer
    else
     MaterialTheme.colorScheme.onErrorContainer
   )
  }
 }
}

@Composable
private fun AppSettingsContent() {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.surfaceVariant
  ),
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   Text(
    text = "🚧 Theme switching, language settings coming soon",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
  }
 }
}

@Composable
private fun SteamInstallProgressContent(state: SteamInstallState.Installing) {
 Column(
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  // Progress header
  Row(
   modifier = Modifier.fillMaxWidth(),
   verticalAlignment = Alignment.CenterVertically,
   horizontalArrangement = Arrangement.SpaceBetween
  ) {
   Column {
    Text(
     text = "Installing Steam Client",
     style = MaterialTheme.typography.titleLarge.copy(
      fontWeight = FontWeight.Bold
     ),
     color = Color.White
    )
    Text(
     text = "${(state.progress * 100).toInt()}% Complete",
     style = MaterialTheme.typography.bodyMedium,
     color = SteamColorPalette.Blue
    )
   }
   CircularProgressIndicator(
    color = SteamColorPalette.Blue,
    modifier = Modifier.size(40.dp)
   )
  }

  // Progress bar
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
   LinearProgressIndicator(
    progress = { state.progress },
    modifier = Modifier
     .fillMaxWidth()
     .height(8.dp),
    color = SteamColorPalette.Blue,
    trackColor = SteamColorPalette.Medium,
   )
   Text(
    text = state.message,
    style = MaterialTheme.typography.bodyMedium,
    color = SteamColorPalette.LightText
   )
   // Show detail message if available (e.g., "File 234/342")
   state.detailMessage?.let { detail ->
    Text(
     text = detail,
     style = MaterialTheme.typography.bodySmall,
     color = SteamColorPalette.LightText.copy(alpha = 0.7f)
    )
   }
  }

  // Info box
  Surface(
   shape = RoundedCornerShape(6.dp),
   color = SteamColorPalette.Medium.copy(alpha = 0.5f)
  ) {
   Column(
    modifier = Modifier
     .fillMaxWidth()
     .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
   ) {
    Row(
     horizontalArrangement = Arrangement.spacedBy(12.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Icon(
      imageVector = Icons.Default.Info,
      contentDescription = null,
      tint = SteamColorPalette.Blue,
      modifier = Modifier.size(20.dp)
     )
     Text(
      text = when {
       state.progress < 0.20f -> "Initializing Winlator"
       state.progress < 0.60f -> "Preparing Environment"
       state.progress < 0.75f -> "Extracting Steam"
       else -> "Finalizing"
      },
      style = MaterialTheme.typography.titleSmall.copy(
       fontWeight = FontWeight.Bold
      ),
      color = SteamColorPalette.Blue
     )
    }

    Text(
     text = when {
      state.progress < 0.20f -> {
       "Extracting Box64/Wine binaries (first time only).\nPlease wait..."
      }
      state.progress < 0.60f -> {
       "Creating Wine container with Windows 10 compatibility.\nPlease wait..."
      }
      state.progress < 0.75f -> {
       "Extracting Steam Client files from NSIS installer.\nPlease wait..."
      }
      else -> {
       "Initializing Steam directories and configuration.\nAlmost done!"
      }
     },
     style = MaterialTheme.typography.bodySmall,
     color = SteamColorPalette.Gray,
     lineHeight = 20.sp
    )
   }
  }

  // Warning
  Surface(
   shape = RoundedCornerShape(6.dp),
   color = MaterialTheme.colorScheme.errorContainer
  ) {
   Row(
    modifier = Modifier
     .fillMaxWidth()
     .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
   ) {
    Icon(
     imageVector = Icons.Default.Warning,
     contentDescription = null,
     tint = MaterialTheme.colorScheme.error,
     modifier = Modifier.size(20.dp)
    )
    Text(
     text = "Do not close this screen during installation",
     style = MaterialTheme.typography.bodySmall.copy(
      fontWeight = FontWeight.Bold
     ),
     color = MaterialTheme.colorScheme.error
    )
   }
  }
 }
}

/**
 * Prerequisite Warning Card
 *
 * Displays a warning when a section's prerequisites are not met
 */
@Composable
private fun PrerequisiteWarning(
 message: String,
 requiredStep: String,
 modifier: Modifier = Modifier
) {
 Card(
  modifier = modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.errorContainer
  ),
  shape = RoundedCornerShape(12.dp),
  elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(24.dp),
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   // Icon and title
   Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
   ) {
    Icon(
     imageVector = Icons.Default.Warning,
     contentDescription = null,
     tint = MaterialTheme.colorScheme.error,
     modifier = Modifier.size(32.dp)
    )
    Text(
     text = "Prerequisites Not Met",
     style = MaterialTheme.typography.titleLarge,
     fontWeight = FontWeight.Bold,
     color = MaterialTheme.colorScheme.error
    )
   }

   // Warning message
   Text(
    text = message,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onErrorContainer
   )

   // Required step indicator
   Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
   ) {
    Row(
     modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
     horizontalArrangement = Arrangement.spacedBy(8.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Icon(
      imageVector = Icons.Default.Info,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(20.dp)
     )
     Text(
      text = "Required: $requiredStep",
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.error
     )
    }
   }
  }
 }
}

/**
 * Steam Web API Key configuration section
 *
 * Best Practice (2025):
 * - Users provide their own API keys (Steam ToS compliant)
 * - AES-256 encrypted storage
 * - Password-masked input
 * - Direct link to Steam API Key registration
 */
@Composable
private fun SteamApiKeySection(
 apiKey: String?,
 onSaveApiKey: (String) -> Unit,
 enabled: Boolean = true,
 modifier: Modifier = Modifier
) {
 val context = LocalContext.current
 var editingApiKey by remember { mutableStateOf(apiKey ?: "") }
 var isEditing by remember { mutableStateOf(apiKey.isNullOrBlank()) }
 var showValidationError by remember { mutableStateOf(false) }

 Card(
  modifier = modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.surfaceVariant
  ),
  shape = RoundedCornerShape(8.dp),
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier.padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   // Header
   Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    Icon(
     imageVector = Icons.Default.Security,
     contentDescription = null,
     tint = MaterialTheme.colorScheme.primary,
     modifier = Modifier.size(24.dp)
    )
    Text(
     "Steam Web API Key",
     style = MaterialTheme.typography.titleMedium,
     fontWeight = FontWeight.Bold,
     color = MaterialTheme.colorScheme.onSurface
    )
   }

   if (isEditing) {
    // Editing mode
    Column(
     verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     TextField(
      value = editingApiKey,
      onValueChange = {
       editingApiKey = it
       showValidationError = false
      },
      label = { Text("API Key") },
      placeholder = { Text("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX") },
      visualTransformation = PasswordVisualTransformation(),
      singleLine = true,
      isError = showValidationError,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth(),
      supportingText = if (showValidationError) {
       { Text("API Key must be 32 characters", color = MaterialTheme.colorScheme.error) }
      } else null
     )

     Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
     ) {
      OutlinedButton(
       onClick = {
        // Open Steam API Key registration page
        val intent = android.content.Intent(
         android.content.Intent.ACTION_VIEW,
         android.net.Uri.parse("https://steamcommunity.com/dev/apikey")
        )
        context.startActivity(intent)
       },
       enabled = enabled,
       modifier = Modifier.weight(1f)
      ) {
       Icon(
        imageVector = Icons.Default.Info,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
       )
       Spacer(Modifier.width(4.dp))
       Text("Get API Key")
      }

      Button(
       onClick = {
        // Validate API Key (Steam API Keys are 32 characters)
        if (editingApiKey.length == 32) {
         onSaveApiKey(editingApiKey)
         isEditing = false
         showValidationError = false
        } else {
         showValidationError = true
        }
       },
       enabled = enabled && editingApiKey.isNotBlank(),
       modifier = Modifier.weight(1f)
      ) {
       Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
       )
       Spacer(Modifier.width(4.dp))
       Text("Save")
      }
     }
    }
   } else {
    // Display mode (API Key configured)
    Row(
     modifier = Modifier.fillMaxWidth(),
     horizontalArrangement = Arrangement.SpaceBetween,
     verticalAlignment = Alignment.CenterVertically
    ) {
     Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
     ) {
      Icon(
       imageVector = Icons.Default.CheckCircle,
       contentDescription = null,
       tint = Color(0xFF4CAF50), // Green
       modifier = Modifier.size(20.dp)
      )
      Column {
       Text(
        "API Key Configured",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
       )
       Text(
        "••••••••••••••••••••••••••••••••",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
       )
      }
     }

     OutlinedButton(
      onClick = { isEditing = true },
      enabled = enabled
     ) {
      Text("Change")
     }
    }
   }

   // Info text
   Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.Top
   ) {
    Icon(
     imageVector = Icons.Default.Info,
     contentDescription = null,
     tint = MaterialTheme.colorScheme.onSurfaceVariant,
     modifier = Modifier.size(16.dp).padding(top = 2.dp)
    )
    Text(
     "Your API Key is stored locally with AES-256 encryption and never shared with third parties. Required for syncing your Steam library.",
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onSurfaceVariant,
     lineHeight = 18.sp
    )
   }
  }
 }
}

/**
 * Steam Auto-Launch Progress Display (NEW - 2025-12-23)
 *
 * Shows real-time progress of automatic Steam client launch after QR login
 */
@Composable
private fun SteamAutoLaunchProgress(
 autoLaunchState: AutoLaunchState
) {
 when (autoLaunchState) {
  is AutoLaunchState.LaunchingSteam -> {
   Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
     containerColor = SteamColorPalette.Blue.copy(alpha = 0.1f)
    ),
    shape = RoundedCornerShape(8.dp)
   ) {
    Row(
     modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
     horizontalArrangement = Arrangement.spacedBy(12.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     CircularProgressIndicator(
      modifier = Modifier.size(24.dp),
      color = SteamColorPalette.Blue
     )
     Text(
      text = stringResource(R.string.steam_auto_launching),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
     )
    }
   }
  }
  is AutoLaunchState.SteamRunning -> {
   Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
     containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
    ),
    shape = RoundedCornerShape(8.dp)
   ) {
    Row(
     modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
     horizontalArrangement = Arrangement.spacedBy(12.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Icon(
      imageVector = Icons.Default.CheckCircle,
      contentDescription = null,
      tint = Color(0xFF4CAF50),
      modifier = Modifier.size(24.dp)
     )
     Text(
      text = stringResource(R.string.steam_auto_launch_success),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
     )
    }
   }
  }
  is AutoLaunchState.LaunchError -> {
   Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
     containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
    ),
    shape = RoundedCornerShape(8.dp)
   ) {
    Column(
     modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
     verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
     ) {
      Icon(
       imageVector = Icons.Default.Warning,
       contentDescription = null,
       tint = Color(0xFFFF9800),
       modifier = Modifier.size(24.dp)
      )
      Text(
       text = stringResource(R.string.steam_auto_launch_failed),
       style = MaterialTheme.typography.bodyMedium,
       fontWeight = FontWeight.Bold,
       color = MaterialTheme.colorScheme.onSurface
      )
     }
     Text(
      text = autoLaunchState.message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }
   }
  }
  is AutoLaunchState.Idle -> {
   // No display when idle
  }
 }
}

/**
 * Steam Client - Installed but Not Authenticated State
 *
 * Displays when Steam is installed but user hasn't authenticated yet.
 * Guides user to complete Step 2 (Authentication) before using Steam.
 *
 * Design: 3-tier action hierarchy
 * - PRIMARY: Navigate to Authentication step (green button)
 * - ADVANCED: Collapsible section with manual Steam launch (requires manual QR login)
 * - DESTRUCTIVE: Uninstall button
 */
@Composable
private fun SteamInstalledUnauthenticatedContent(
 installPath: String,
 containerId: String,
 onNavigateToAuth: () -> Unit,
 onOpen: (String) -> Unit,
 onUninstall: (String) -> Unit
) {
 var showAdvancedOptions by remember { mutableStateOf(false) }

 Column(
  modifier = Modifier.fillMaxWidth(),
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  // Header with success icon
  Row(
   modifier = Modifier.fillMaxWidth(),
   verticalAlignment = Alignment.CenterVertically,
   horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Surface(
    shape = RoundedCornerShape(12.dp),
    color = SteamColorPalette.Green.copy(alpha = 0.2f)
   ) {
    Icon(
     imageVector = Icons.Default.CheckCircle,
     contentDescription = null,
     modifier = Modifier
      .size(56.dp)
      .padding(12.dp),
     tint = SteamColorPalette.Green
    )
   }
   Column(modifier = Modifier.weight(1f)) {
    Text(
     text = stringResource(R.string.steam_client_title),
     style = MaterialTheme.typography.titleLarge.copy(
      fontWeight = FontWeight.Bold
     ),
     color = Color.White
    )
    Text(
     text = stringResource(R.string.steam_status_installed),
     style = MaterialTheme.typography.bodyMedium,
     color = SteamColorPalette.Green
    )
   }
  }

  // Divider
  Box(
   modifier = Modifier
    .fillMaxWidth()
    .height(1.dp)
    .background(SteamColorPalette.Medium)
  )

  // Next step guidance
  Surface(
   shape = RoundedCornerShape(6.dp),
   color = SteamColorPalette.Blue.copy(alpha = 0.15f)
  ) {
   Column(
    modifier = Modifier
     .fillMaxWidth()
     .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
   ) {
    Row(
     horizontalArrangement = Arrangement.spacedBy(12.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Icon(
      imageVector = Icons.Default.Info,
      contentDescription = null,
      tint = SteamColorPalette.Blue,
      modifier = Modifier.size(24.dp)
     )
     Text(
      text = stringResource(R.string.steam_next_step_auth),
      style = MaterialTheme.typography.titleSmall.copy(
       fontWeight = FontWeight.Bold
      ),
      color = SteamColorPalette.Blue
     )
    }
    Text(
     text = stringResource(R.string.steam_auth_required_description),
     style = MaterialTheme.typography.bodyMedium,
     color = SteamColorPalette.LightText,
     lineHeight = 20.sp
    )
   }
  }

  // PRIMARY: Next step button (Navigate to Authentication)
  Button(
   onClick = onNavigateToAuth,
   modifier = Modifier
    .fillMaxWidth()
    .height(48.dp),
   colors = ButtonDefaults.buttonColors(
    containerColor = Color.Transparent
   ),
   shape = RoundedCornerShape(4.dp),
   contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
  ) {
   Box(
    modifier = Modifier
     .fillMaxSize()
     .background(
      brush = Brush.linearGradient(
       colors = listOf(
        SteamColorPalette.Green,
        SteamColorPalette.Green.copy(alpha = 0.8f)
       )
      )
     ),
    contentAlignment = Alignment.Center
   ) {
    Row(
     horizontalArrangement = Arrangement.spacedBy(8.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Text(
      text = stringResource(R.string.button_next_authenticate),
      style = MaterialTheme.typography.titleMedium.copy(
       fontWeight = FontWeight.Bold
      ),
      color = Color.White
     )
     Icon(
      imageVector = Icons.Default.KeyboardArrowRight,
      contentDescription = null,
      tint = Color.White
     )
    }
   }
  }

  // ADVANCED: Collapsible section
  Column(
   modifier = Modifier.fillMaxWidth(),
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   // Divider with text
   Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    Box(
     modifier = Modifier
      .weight(1f)
      .height(1.dp)
      .background(SteamColorPalette.Medium)
    )
    TextButton(
     onClick = { showAdvancedOptions = !showAdvancedOptions }
    ) {
     Text(
      text = if (showAdvancedOptions)
       stringResource(R.string.button_hide_advanced)
      else
       stringResource(R.string.button_advanced_options),
      style = MaterialTheme.typography.labelMedium,
      color = SteamColorPalette.Gray
     )
     Spacer(modifier = Modifier.width(4.dp))
     Icon(
      imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
      contentDescription = null,
      tint = SteamColorPalette.Gray,
      modifier = Modifier.size(18.dp)
     )
    }
    Box(
     modifier = Modifier
      .weight(1f)
      .height(1.dp)
      .background(SteamColorPalette.Medium)
    )
   }

   // Advanced options content
   if (showAdvancedOptions) {
    Column(
     verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
     // Warning box
     Surface(
      shape = RoundedCornerShape(6.dp),
      color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
     ) {
      Row(
       modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
       horizontalArrangement = Arrangement.spacedBy(12.dp),
       verticalAlignment = Alignment.CenterVertically
      ) {
       Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(20.dp)
       )
       Text(
        text = stringResource(R.string.steam_manual_login_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer
       )
      }
     }

     // Install path
     Surface(
      shape = RoundedCornerShape(6.dp),
      color = SteamColorPalette.Medium.copy(alpha = 0.3f)
     ) {
      Text(
       text = "Install path:\n$installPath",
       style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
       ),
       color = SteamColorPalette.Gray,
       modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
      )
     }

     // Manual open button (no auto-login)
     OutlinedButton(
      onClick = { onOpen(containerId) },
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.outlinedButtonColors(
       contentColor = SteamColorPalette.Blue
      ),
      border = BorderStroke(1.dp, SteamColorPalette.Blue),
      shape = RoundedCornerShape(4.dp)
     ) {
      Icon(
       imageVector = Icons.Default.SportsEsports,
       contentDescription = null,
       modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
       text = stringResource(R.string.steam_open_client),
       style = MaterialTheme.typography.titleSmall
      )
     }

     Text(
      text = stringResource(R.string.steam_manual_login_note),
      style = MaterialTheme.typography.bodySmall,
      color = SteamColorPalette.Gray,
      fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
     )
    }
   }
  }

  // DESTRUCTIVE: Uninstall button
  OutlinedButton(
   onClick = { onUninstall(containerId) },
   modifier = Modifier.fillMaxWidth(),
   colors = ButtonDefaults.outlinedButtonColors(
    contentColor = SteamColorPalette.Gray
   ),
   border = BorderStroke(1.dp, SteamColorPalette.Gray),
   shape = RoundedCornerShape(4.dp)
  ) {
   Icon(
    imageVector = Icons.Default.Clear,
    contentDescription = null,
    modifier = Modifier.size(18.dp)
   )
   Spacer(modifier = Modifier.width(6.dp))
   Text(stringResource(R.string.button_uninstall))
  }
 }
}

/**
 * Steam Client - Installed and Authenticated State
 *
 * Displays when Steam is installed AND user has authenticated.
 * Auto-login is configured, ready to launch Steam directly.
 *
 * Design: Simplified UI with emphasis on primary action
 * - Shows auto-login status with username
 * - PRIMARY: Open Steam Client (blue gradient button)
 * - SECONDARY: Uninstall button
 */
@Composable
private fun SteamInstalledAuthenticatedContent(
 installPath: String,
 containerId: String,
 steamUsername: String,
 onOpen: (String) -> Unit,
 onUninstall: (String) -> Unit
) {
 Column(
  modifier = Modifier.fillMaxWidth(),
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  // Header with success icon
  Row(
   modifier = Modifier.fillMaxWidth(),
   verticalAlignment = Alignment.CenterVertically,
   horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Surface(
    shape = RoundedCornerShape(12.dp),
    color = SteamColorPalette.Green.copy(alpha = 0.2f)
   ) {
    Icon(
     imageVector = Icons.Default.CheckCircle,
     contentDescription = null,
     modifier = Modifier
      .size(56.dp)
      .padding(12.dp),
     tint = SteamColorPalette.Green
    )
   }
   Column(modifier = Modifier.weight(1f)) {
    Text(
     text = stringResource(R.string.steam_client_title),
     style = MaterialTheme.typography.titleLarge.copy(
      fontWeight = FontWeight.Bold
     ),
     color = Color.White
    )
    Text(
     text = stringResource(R.string.steam_status_installed_authenticated),
     style = MaterialTheme.typography.bodyMedium,
     color = SteamColorPalette.Green
    )
   }
  }

  // Divider
  Box(
   modifier = Modifier
    .fillMaxWidth()
    .height(1.dp)
    .background(SteamColorPalette.Medium)
  )

  // Auto-login configured status
  Surface(
   shape = RoundedCornerShape(6.dp),
   color = SteamColorPalette.Green.copy(alpha = 0.1f)
  ) {
   Column(
    modifier = Modifier
     .fillMaxWidth()
     .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    Row(
     horizontalArrangement = Arrangement.spacedBy(12.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Icon(
      imageVector = Icons.Default.CheckCircle,
      contentDescription = null,
      tint = SteamColorPalette.Green,
      modifier = Modifier.size(20.dp)
     )
     Text(
      text = stringResource(R.string.steam_autologin_configured),
      style = MaterialTheme.typography.titleSmall.copy(
       fontWeight = FontWeight.Bold
      ),
      color = SteamColorPalette.Green
     )
    }
    Text(
     text = stringResource(R.string.steam_account_label, steamUsername),
     style = MaterialTheme.typography.bodySmall,
     color = SteamColorPalette.Gray
    )
   }
  }

  // Install path
  Surface(
   shape = RoundedCornerShape(6.dp),
   color = SteamColorPalette.Medium.copy(alpha = 0.3f)
  ) {
   Text(
    text = "Install path:\n$installPath",
    style = MaterialTheme.typography.bodySmall.copy(
     fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    ),
    color = SteamColorPalette.Gray,
    modifier = Modifier
     .fillMaxWidth()
     .padding(12.dp)
   )
  }

  // PRIMARY: Open Steam Client button (with auto-login)
  Button(
   onClick = { onOpen(containerId) },
   modifier = Modifier
    .fillMaxWidth()
    .height(48.dp),
   colors = ButtonDefaults.buttonColors(
    containerColor = Color.Transparent
   ),
   shape = RoundedCornerShape(4.dp),
   contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
  ) {
   Box(
    modifier = Modifier
     .fillMaxSize()
     .background(
      brush = Brush.linearGradient(
       colors = listOf(
        SteamColorPalette.BrightBlue,
        SteamColorPalette.DeepBlue
       )
      )
     ),
    contentAlignment = Alignment.Center
   ) {
    Row(
     horizontalArrangement = Arrangement.spacedBy(8.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Icon(
      imageVector = Icons.Default.SportsEsports,
      contentDescription = null,
      tint = Color.White
     )
     Text(
      text = stringResource(R.string.steam_open_client),
      style = MaterialTheme.typography.titleMedium.copy(
       fontWeight = FontWeight.Bold
      ),
      color = Color.White
     )
    }
   }
  }

  // Uninstall button
  OutlinedButton(
   onClick = { onUninstall(containerId) },
   modifier = Modifier.fillMaxWidth(),
   colors = ButtonDefaults.outlinedButtonColors(
    contentColor = SteamColorPalette.Gray
   ),
   border = BorderStroke(1.dp, SteamColorPalette.Gray),
   shape = RoundedCornerShape(4.dp)
  ) {
   Icon(
    imageVector = Icons.Default.Clear,
    contentDescription = null,
    modifier = Modifier.size(18.dp)
   )
   Spacer(modifier = Modifier.width(6.dp))
   Text(stringResource(R.string.button_uninstall))
  }
 }
}
