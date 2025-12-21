package com.steamdeck.mobile.presentation.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.steamdeck.mobile.presentation.viewmodel.SettingsUiState
import com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel
import com.steamdeck.mobile.presentation.viewmodel.SteamInstallState
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginViewModel
import com.steamdeck.mobile.presentation.viewmodel.SyncState
import com.steamdeck.mobile.presentation.viewmodel.WineTestUiState
import com.steamdeck.mobile.presentation.viewmodel.WineTestViewModel

/**
 * Settings画面 - Simplified design without NavigationRail
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
 initialSection: Int = -1,
 viewModel: SettingsViewModel = hiltViewModel(),
 wineTestViewModel: WineTestViewModel = hiltViewModel(),
 steamLoginViewModel: SteamLoginViewModel = hiltViewModel()
) {
 val context = LocalContext.current
 val uiState by viewModel.uiState.collectAsState()
 val syncState by viewModel.syncState.collectAsState()
 val steamInstallState by viewModel.steamInstallState.collectAsState()
 val steamLoginState by steamLoginViewModel.uiState.collectAsState()
 val snackbarHostState = remember { SnackbarHostState() }
 var selectedSection by remember {
  mutableIntStateOf(if (initialSection >= 0) initialSection else 0)
 }
 var showWebView by remember { mutableStateOf(false) }

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

 // Show snackbar for sync completion messages
 LaunchedEffect(syncState) {
  when (val state = syncState) {
   is SyncState.Success -> {
    snackbarHostState.showSnackbar(
     message = context.getString(R.string.games_synced_count, state.syncedGamesCount),
     duration = SnackbarDuration.Short
    )
    viewModel.resetSyncState()
   }
   is SyncState.Error -> {
    snackbarHostState.showSnackbar(
     message = state.message,
     duration = SnackbarDuration.Long
    )
    viewModel.resetSyncState()
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
     showWebView = showWebView,
     steamLoginViewModel = steamLoginViewModel,
     wineTestViewModel = wineTestViewModel,
     onShowWebView = { showWebView = true },
     onHideWebView = { showWebView = false },
     onSyncLibrary = viewModel::syncSteamLibrary,
     onClearSettings = viewModel::clearSteamSettings,
     onNavigateToControllerSettings = onNavigateToControllerSettings,
     onSaveApiKey = viewModel::saveSteamApiKey,
     onInstallSteam = viewModel::installSteamClient,
     onOpenSteam = viewModel::openSteamClient,
     onUninstallSteam = viewModel::uninstallSteamClient,
     onNavigateBack = onNavigateBack
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
 showWebView: Boolean,
 steamLoginViewModel: SteamLoginViewModel,
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
       defaultContainerId = "default_shared_container",
       onInstall = onInstallSteam,
       onOpen = onOpenSteam,
       onUninstall = onUninstallSteam
      )
     }
     1 -> {
      // Step 2: Steam Authentication (requires Steam Client)
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
       onShowWebView = onShowWebView,
       onHideWebView = onHideWebView,
       onClear = onClearSettings,
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
 onShowWebView: () -> Unit,
 onHideWebView: () -> Unit,
 onClear: () -> Unit,
 enabled: Boolean = true
) {
 val steamLoginState by steamLoginViewModel.uiState.collectAsState()
 
 // Close WebView on authentication success
 LaunchedEffect(steamLoginState) {
  if (steamLoginState is SteamLoginUiState.Success) {
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
     android.util.Log.e("SteamAuth", "OpenID error: $errorMessage")
    }
   )
  }
 } else if (data.isSteamConfigured) {
  // Already logged in - Steam style design
  SteamAuthLoggedInSection(
   username = data.steamUsername,
   steamId = data.steamId,
   onRelogin = onShowWebView,
   onLogout = onClear
  )
 } else {
  // Not logged in - Display OpenID login button
  SteamOpenIdAuthSection(
   onNavigateToLogin = onShowWebView,
   enabled = enabled
  )
 }
}

/**
 * Steam authenticated section (Steam style design)
 */
@Composable
private fun SteamAuthLoggedInSection(
 username: String,
 steamId: String,
 onRelogin: () -> Unit,
 onLogout: () -> Unit
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

    // Right column: Buttons
    Column(
     modifier = Modifier
      .weight(0.5f)
      .fillMaxHeight(),
     horizontalAlignment = Alignment.CenterHorizontally,
     verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
     Button(
      onClick = onRelogin,
      modifier = Modifier.fillMaxWidth(),
      colors = androidx.compose.material3.ButtonDefaults.buttonColors(
       containerColor = SteamColorPalette.Blue,
       contentColor = Color.White
      )
     ) {
      Text(
       "Re-Login",
       style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold
       )
      )
     }

     OutlinedButton(
      onClick = onLogout,
      modifier = Modifier.fillMaxWidth(),
      colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
       contentColor = SteamColorPalette.Gray
      ),
      border = BorderStroke(1.dp, SteamColorPalette.Gray)
     ) {
      Text(
       "Logout",
       style = MaterialTheme.typography.titleMedium
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
 * Steam公式カラーパレット:
 * - #171a21: ナビバー/ダーク背景
 * - #1b2838: プライマリ背景
 * - #2a475e: セカンダリ背景
 * - #66c0f4: アクセントブルー
 * - #c7d5e0: ライトテキスト
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

@Composable
private fun SteamClientContent(
 steamInstallState: SteamInstallState,
 defaultContainerId: String,
 onInstall: (String) -> Unit,
 onOpen: (String) -> Unit,
 onUninstall: (String) -> Unit
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
        text = "• Download size: ~100MB\n• Install time: 2-3 minutes\n• First time requires Box64/Wine setup",
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
        text = "Steam Client",
        style = MaterialTheme.typography.titleLarge.copy(
         fontWeight = FontWeight.Bold
        ),
        color = Color.White
       )
       Text(
        text = "Installed",
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

     // Install path
     Surface(
      shape = RoundedCornerShape(6.dp),
      color = SteamColorPalette.Medium.copy(alpha = 0.3f)
     ) {
      Text(
       text = "Install path:\n${state.installPath}",
       style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
       ),
       color = SteamColorPalette.Gray,
       modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
      )
     }

     // Open button
     Button(
      onClick = { onOpen(state.containerId) },
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
         imageVector = Icons.Default.SportsEsports,
         contentDescription = null,
         tint = Color.White
        )
        Text(
         text = "Open Steam Client",
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
      onClick = { onUninstall(state.containerId) },
      modifier = Modifier.fillMaxWidth(),
      colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
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
      Text("Uninstall")
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

@Composable
private fun LibrarySyncContent(
 data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
 syncState: SyncState,
 onSync: () -> Unit,
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

   // Sync state display
   when (val state = syncState) {
    is SyncState.Syncing -> {
     Column(
      verticalArrangement = Arrangement.spacedBy(8.dp)
     ) {
      LinearProgressIndicator(
       modifier = Modifier.fillMaxWidth()
      )
      Text(
       text = state.message,
       style = MaterialTheme.typography.bodyMedium,
       color = MaterialTheme.colorScheme.primary
      )
     }
    }
    else -> {}
   }

   // Sync button
   FilledTonalButton(
    onClick = onSync,
    enabled = enabled && data.isSteamConfigured && syncState !is SyncState.Syncing,
    modifier = Modifier.fillMaxWidth()
   ) {
    Icon(
     imageVector = Icons.Default.Refresh,
     contentDescription = stringResource(R.string.content_desc_sync)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
     text = if (syncState is SyncState.Syncing) "Syncing..." else "Library Sync"
    )
   }

   if (!enabled || !data.isSteamConfigured) {
    Text(
     text = if (!enabled) "※ Please complete Step 1 and Step 2 first" else "※ Steam authentication required",
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.error
    )
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
 */
@Composable
private fun WineTestIntegratedContent(
 viewModel: WineTestViewModel
) {
 val uiState by viewModel.uiState.collectAsState()

 Column(
  modifier = Modifier
   .fillMaxWidth()
   .padding(0.dp),
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  // Header
  Text(
   text = "🧪 Wine Environment Test",
   style = MaterialTheme.typography.headlineSmall,
   fontWeight = FontWeight.Bold
  )

  // Status card
  WineTestCompactStatusRow(uiState = uiState)

  // Test buttons
  if (uiState !is WineTestUiState.Testing) {
   WineTestCompactTestButtons(
    onCheckWine = viewModel::checkWineAvailability,
    onInitialize = viewModel::initializeEmulator,
    onCreateContainer = viewModel::testCreateContainer,
    onListContainers = viewModel::listContainers
   )
  }

  // Progress/Results
  when (val state = uiState) {
   is WineTestUiState.Testing -> {
    WineTestTestingProgressCard(message = state.message)
   }
   is WineTestUiState.Success -> {
    WineTestTestResultCard(
     title = "✓ Test Success",
     message = state.message,
     isSuccess = true
    )
   }
   is WineTestUiState.Error -> {
    WineTestTestResultCard(
     title = "✗ Error",
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
    Text(
     text = when (uiState) {
      is WineTestUiState.Idle -> "Ready"
      is WineTestUiState.Testing -> "Running..."
      is WineTestUiState.Success -> "Available"
      is WineTestUiState.Error -> "Error"
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

@Composable
private fun WineTestCompactTestButtons(
 onCheckWine: () -> Unit,
 onInitialize: () -> Unit,
 onCreateContainer: () -> Unit,
 onListContainers: () -> Unit
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
    Text("1. Check", style = MaterialTheme.typography.labelLarge)
   }

   Button(
    onClick = onInitialize,
    modifier = Modifier.weight(1f)
   ) {
    Text("2. Initialize", style = MaterialTheme.typography.labelLarge)
   }
  }

  // Row 2: Create & List
  Row(
   modifier = Modifier.fillMaxWidth(),
   horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   Button(
    onClick = onCreateContainer,
    modifier = Modifier.weight(1f)
   ) {
    Text("3. Create", style = MaterialTheme.typography.labelLarge)
   }

   Button(
    onClick = onListContainers,
    modifier = Modifier.weight(1f)
   ) {
    Text("4. List", style = MaterialTheme.typography.labelLarge)
   }
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
      text = if (state.progress < 0.4f) "Initializing Winlator" else "Installing",
      style = MaterialTheme.typography.titleSmall.copy(
       fontWeight = FontWeight.Bold
      ),
      color = SteamColorPalette.Blue
     )
    }

    Text(
     text = if (state.progress < 0.4f) {
      "Extracting Box64/Wine binaries (first time only).\nThis may take 2-3 minutes."
     } else {
      "Running Steam installer.\nPlease wait for completion."
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
