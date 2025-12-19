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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.steamdeck.mobile.presentation.ui.auth.SteamOpenIdLoginScreen
import com.steamdeck.mobile.presentation.viewmodel.SettingsUiState
import com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel
import com.steamdeck.mobile.presentation.viewmodel.SteamInstallState
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginViewModel
import com.steamdeck.mobile.presentation.viewmodel.SyncState

/**
 * Settings画面 - BackboneOnestyle design
 *
 * Best Practices:
 * - NavigationRail for side navigation (3-7 items recommended)
 * - List-detail canonical layout for tablet optimization
 * - Steam color scheme with Material3
 * - No TopAppBar for immersive full-screen experience
 *
 * References:
 * - https://developer.android.com/develop/ui/compose/components/navigation-rail
 * - https://codelabs.developers.google.com/codelabs/adaptive-material-guidance
 */
@Composable
fun SettingsScreen(
 onNavigateBack: () -> Unit,
 onNavigateToWineTest: () -> Unit = {},
 onNavigateToControllerSettings: () -> Unit = {},
 viewModel: SettingsViewModel = hiltViewModel(),
 steamLoginViewModel: SteamLoginViewModel = hiltViewModel()
) {
 val context = LocalContext.current
 val uiState by viewModel.uiState.collectAsState()
 val syncState by viewModel.syncState.collectAsState()
 val steamInstallState by viewModel.steamInstallState.collectAsState()
 val steamLoginState by steamLoginViewModel.uiState.collectAsState()
 val snackbarHostState = remember { SnackbarHostState() }
 var selectedSection by remember { mutableIntStateOf(0) }
 var showWebView by remember { mutableStateOf(false) }

 // Error・Successメッセージ スナックバー表示
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

 // SyncCompleteメッセージ スナックバー表示
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
    // BackboneOne風レイアウト: NavigationRail + Content
    Row(modifier = Modifier.fillMaxSize()) {
     // 左サイドバー（NavigationRail）
     SettingsNavigationRail(
      selectedSection = selectedSection,
      onSectionSelected = { selectedSection = it },
      onNavigateBack = onNavigateBack
     )

     // 右側コンテンツ
     SettingsContent(
      selectedSection = selectedSection,
      data = state.data,
      syncState = syncState,
      steamInstallState = steamInstallState,
      showWebView = showWebView,
      steamLoginViewModel = steamLoginViewModel,
      onShowWebView = { showWebView = true },
      onHideWebView = { showWebView = false },
      onSyncLibrary = viewModel::syncSteamLibrary,
      onClearSettings = viewModel::clearSteamSettings,
      onNavigateToWineTest = onNavigateToWineTest,
      onNavigateToControllerSettings = onNavigateToControllerSettings,
      onSaveApiKey = viewModel::saveSteamApiKey,
      onInstallSteam = viewModel::installSteamClient,
      onOpenSteam = viewModel::openSteamClient,
      onUninstallSteam = viewModel::uninstallSteamClient
     )
    }
   }
   is SettingsUiState.Error -> {
    LoadingContent()
   }
  }

  // Snackbar表示
  SnackbarHost(
   hostState = snackbarHostState,
   modifier = Modifier.align(Alignment.BottomCenter)
  )
 }
}

/**
 * NavigationRail - 左サイドバーナビゲーション
 *
 * Best Practice: 3-7 items recommended by Material3 guidelines
 */
@Composable
private fun SettingsNavigationRail(
 selectedSection: Int,
 onSectionSelected: (Int) -> Unit,
 onNavigateBack: () -> Unit
) {
 NavigationRail(
  modifier = Modifier.fillMaxHeight(),
  containerColor = MaterialTheme.colorScheme.surfaceVariant,
  header = {
   // returnボタン
   IconButton(
    onClick = onNavigateBack,
    modifier = Modifier.padding(vertical = 8.dp)
   ) {
    Icon(
     imageVector = Icons.Default.ArrowBack,
     contentDescription = "return",
     tint = MaterialTheme.colorScheme.primary
    )
   }
  }
 ) {
  Spacer(modifier = Modifier.height(16.dp))

  // Steamauthentication
  NavigationRailItem(
   selected = selectedSection == 0,
   onClick = { onSectionSelected(0) },
   icon = {
    Icon(
     imageVector = Icons.Default.Security,
     contentDescription = "Steamauthentication"
    )
   },
   label = { Text("authentication") }
  )

  // Steam Client
  NavigationRailItem(
   selected = selectedSection == 1,
   onClick = { onSectionSelected(1) },
   icon = {
    Icon(
     imageVector = Icons.Default.CloudDownload,
     contentDescription = "Steam Client"
    )
   },
   label = { Text("Client") }
  )

  // library sync
  NavigationRailItem(
   selected = selectedSection == 2,
   onClick = { onSectionSelected(2) },
   icon = {
    Icon(
     imageVector = Icons.Default.Refresh,
     contentDescription = "library"
    )
   },
   label = { Text("Sync") }
  )

  // controller
  NavigationRailItem(
   selected = selectedSection == 3,
   onClick = { onSectionSelected(3) },
   icon = {
    Icon(
     imageVector = Icons.Default.SportsEsports,
     contentDescription = "controller"
    )
   },
   label = { Text("操作") }
  )

  // Wine/Winlator
  NavigationRailItem(
   selected = selectedSection == 4,
   onClick = { onSectionSelected(4) },
   icon = {
    Icon(
     imageVector = Icons.Default.Warning,
     contentDescription = "Wine"
    )
   },
   label = { Text("Wine") }
  )

  // アプリsettings
  NavigationRailItem(
   selected = selectedSection == 5,
   onClick = { onSectionSelected(5) },
   icon = {
    Icon(
     imageVector = Icons.Default.Info,
     contentDescription = "アプリ"
    )
   },
   label = { Text("アプリ") }
  )
 }
}

/**
 * SettingsContent - 選択されたセクション 詳細表示
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
 onShowWebView: () -> Unit,
 onHideWebView: () -> Unit,
 onSyncLibrary: () -> Unit,
 onClearSettings: () -> Unit,
 onNavigateToWineTest: () -> Unit,
 onNavigateToControllerSettings: () -> Unit,
 onSaveApiKey: (String) -> Unit,
 onInstallSteam: (String) -> Unit,
 onOpenSteam: (String) -> Unit,
 onUninstallSteam: (String) -> Unit,
 modifier: Modifier = Modifier
) {
 Surface(
  modifier = modifier.fillMaxSize(),
  color = MaterialTheme.colorScheme.background
 ) {
  // authenticationセクション（selectedSection==0） 別扱い（fillMaxSizeusedoため）
  if (selectedSection == 0) {
   SteamAuthContent(
    data = data,
    showWebView = showWebView,
    steamLoginViewModel = steamLoginViewModel,
    onShowWebView = onShowWebView,
    onHideWebView = onHideWebView,
    onClear = onClearSettings
   )
  } else {
   Column(
    modifier = Modifier
     .fillMaxSize()
     .verticalScroll(rememberScrollState())
     .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp)
   ) {
    // タイトル
    Text(
     text = when (selectedSection) {
      1 -> "Steam Client"
      2 -> "library sync"
      3 -> "controllersettings"
      4 -> "Wine/Winlatorintegration"
      5 -> "アプリsettings"
      else -> ""
     },
     style = MaterialTheme.typography.headlineMedium,
     fontWeight = FontWeight.Bold,
     color = MaterialTheme.colorScheme.primary
    )

    // セクション別コンテンツ
    when (selectedSection) {
     1 -> SteamClientContent(
      steamInstallState = steamInstallState,
      defaultContainerId = "1",
      onInstall = onInstallSteam,
      onOpen = onOpenSteam,
      onUninstall = onUninstallSteam
     )
     2 -> LibrarySyncContent(
      data = data,
      syncState = syncState,
      onSync = onSyncLibrary
     )
     3 -> ControllerContent(
      onNavigateToControllerSettings = onNavigateToControllerSettings
     )
     4 -> WineTestContent(
      onNavigateToWineTest = onNavigateToWineTest
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
   text = "settings読み込みin...",
   style = MaterialTheme.typography.bodyMedium,
   color = MaterialTheme.colorScheme.onSurfaceVariant
  )
 }
}

// ========================================
// セクション別コンテンツ（BackboneOne風カード）
// ========================================

@Composable
private fun SteamAuthContent(
 data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
 showWebView: Boolean,
 steamLoginViewModel: SteamLoginViewModel,
 onShowWebView: () -> Unit,
 onHideWebView: () -> Unit,
 onClear: () -> Unit
) {
 val steamLoginState by steamLoginViewModel.uiState.collectAsState()
 
 // authenticationSuccess時 WebViewClose
 LaunchedEffect(steamLoginState) {
  if (steamLoginState is SteamLoginUiState.Success) {
   onHideWebView()
  }
 }
 
 if (showWebView) {
  // WebView表示（フルSize、サイドバー維持）
  val (authUrl, _) = remember { steamLoginViewModel.startOpenIdLogin() }
  
  Box(
   modifier = Modifier
    .fillMaxSize()
    .background(Color(0xFF1B2838))
  ) {
   SteamOpenIdLoginScreen(
    authUrl = authUrl,
    callbackScheme = "steamdeckmobile",
    onAuthCallback = { callbackUrl -> 
     steamLoginViewModel.handleCallback(callbackUrl)
    },
    onError = { errorMessage -> 
     android.util.Log.e("SteamAuth", "OpenID error: $errorMessage")
    }
   )
  }
 } else if (data.isSteamConfigured) {
  // Login済み - Steamstyle design
  SteamAuthLoggedInSection(
   username = data.steamUsername,
   steamId = data.steamId,
   onRelogin = onShowWebView,
   onLogout = onClear
  )
 } else {
  // 未Login - OpenIDLoginボタン表示
  SteamOpenIdAuthSection(
   onNavigateToLogin = onShowWebView
  )
 }
}

/**
 * Steamauthentication済みセクション（Steamstyle design）
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
   .fillMaxWidth()
   .height(340.dp),
  colors = CardDefaults.cardColors(
   containerColor = Color(0xFF1B2838)
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
       Color(0xFF1B2838),
       Color(0xFF0D1217)
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
    // 左カラム: ユーザー情報
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
      color = Color(0xFF66C0F4)
     )

     Spacer(modifier = Modifier.height(3.dp))

     Text(
      text = "Deck Mobile",
      style = MaterialTheme.typography.titleSmall,
      color = Color(0xFF8F98A0)
     )

     Spacer(modifier = Modifier.height(20.dp))

     Icon(
      imageVector = Icons.Default.Check,
      contentDescription = null,
      modifier = Modifier.size(34.dp),
      tint = Color(0xFF5BA82E) // Steam green
     )

     Spacer(modifier = Modifier.height(8.dp))

     Text(
      text = "Login済み",
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
      color = Color(0xFF66C0F4)
     )

     Text(
      text = "Steam ID: $steamId",
      style = MaterialTheme.typography.bodySmall,
      color = Color(0xFF8F98A0)
     )
    }

    // 右カラム: ボタン
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
       containerColor = Color(0xFF66C0F4),
       contentColor = Color.White
      )
     ) {
      Text(
       "再Login",
       style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold
       )
      )
     }

     OutlinedButton(
      onClick = onLogout,
      modifier = Modifier.fillMaxWidth(),
      colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
       contentColor = Color(0xFF8F98A0)
      ),
      border = BorderStroke(1.dp, Color(0xFF8F98A0))
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
 * Steam OpenIDauthenticationセクション（Steam公式style design）
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
 onNavigateToLogin: () -> Unit
) {
 // Steam公式カラー
 val steamNavbar = Color(0xFF171A21)
 val steamDark = Color(0xFF1B2838)
 val steamMedium = Color(0xFF2A475E)
 val steamBlue = Color(0xFF66C0F4)
 val steamLightText = Color(0xFFC7D5E0)
 val steamGray = Color(0xFF8F98A0)
 
 Box(
  modifier = Modifier
   .fillMaxSize()
   .background(
    brush = Brush.verticalGradient(
     colors = listOf(steamNavbar, steamDark)
    )
   ),
  contentAlignment = Alignment.Center
 ) {
  // メインカード
  Card(
   modifier = Modifier
    .fillMaxWidth(0.85f)
    .padding(16.dp),
   colors = CardDefaults.cardColors(
    containerColor = steamMedium.copy(alpha = 0.6f)
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
        steamMedium.copy(alpha = 0.8f),
        steamDark.copy(alpha = 0.95f)
       )
      )
     )
     .padding(horizontal = 32.dp, vertical = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally
   ) {
    // Steamロゴ
    Text(
     text = "STEAM",
     style = MaterialTheme.typography.displaySmall.copy(
      fontWeight = FontWeight.Black,
      letterSpacing = 6.sp
     ),
     color = steamLightText
    )
    
    Spacer(modifier = Modifier.height(4.dp))
    
    // 区切り線
    Box(
     modifier = Modifier
      .width(60.dp)
      .height(2.dp)
      .background(
       brush = Brush.horizontalGradient(
        colors = listOf(
         Color.Transparent,
         steamBlue,
         Color.Transparent
        )
       )
      )
    )
    
    Spacer(modifier = Modifier.height(28.dp))
    
    // タイトル
    Text(
     text = "アカウント サインイン",
     style = MaterialTheme.typography.titleLarge.copy(
      fontWeight = FontWeight.Normal
     ),
     color = steamLightText
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Loginボタン（Steam風グラデーション）
    Button(
     onClick = onNavigateToLogin,
     modifier = Modifier
      .fillMaxWidth()
      .height(48.dp),
     colors = androidx.compose.material3.ButtonDefaults.buttonColors(
      containerColor = Color.Transparent
     ),
     shape = RoundedCornerShape(2.dp),
     contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
     Box(
      modifier = Modifier
       .fillMaxSize()
       .background(
        brush = Brush.linearGradient(
         colors = listOf(
          Color(0xFF47BFFF),
          Color(0xFF1A9FFF)
         )
        )
       ),
      contentAlignment = Alignment.Center
     ) {
      Text(
       text = "Steam サインイン",
       style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
       ),
       color = Color.White
      )
     }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // サブテキスト
    Text(
     text = "Steam公式authentication（OpenID 2.0）",
     style = MaterialTheme.typography.bodySmall,
     color = steamGray
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
   containerColor = Color(0xFF1B2838) // Steam dark blue
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
       Color(0xFF1B2838),
       Color(0xFF0D1217)
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
       color = Color(0xFF66C0F4)
      )
      Spacer(modifier = Modifier.width(16.dp))
      Text(
       text = "confirmin...",
       style = MaterialTheme.typography.bodyLarge,
       color = Color(0xFFC7D5E0)
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
       color = Color(0xFF2A475E)
      ) {
       Icon(
        imageVector = Icons.Default.CloudDownload,
        contentDescription = null,
        modifier = Modifier
         .size(56.dp)
         .padding(12.dp),
        tint = Color(0xFF66C0F4)
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
        text = "未installation",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF8F98A0)
       )
      }
     }

     // Divider
     Box(
      modifier = Modifier
       .fillMaxWidth()
       .height(1.dp)
       .background(Color(0xFF2A475E))
     )

     // Description
     Text(
      text = "Wine環境 Steam Clientinstallationdoこ 、Steamvia gamelaunch きます。",
      style = MaterialTheme.typography.bodyMedium,
      color = Color(0xFFC7D5E0),
      lineHeight = 22.sp
     )

     // Info box
     Surface(
      shape = RoundedCornerShape(6.dp),
      color = Color(0xFF2A475E).copy(alpha = 0.5f)
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
         tint = Color(0xFF66C0F4),
         modifier = Modifier.size(20.dp)
        )
        Text(
         text = "installation情報",
         style = MaterialTheme.typography.titleSmall.copy(
          fontWeight = FontWeight.Bold
         ),
         color = Color(0xFF66C0F4)
        )
       }
       Text(
        text = "• downloadSize: 約100MB\n• installation時間: 2〜3minutes\n• 初回 みBox64/Wine環境 展開 必要 す",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF8F98A0),
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
           Color(0xFF5BA82E),
           Color(0xFF4A8F26)
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
         text = "Steam Clientinstallation",
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
       color = Color(0xFF5BA82E).copy(alpha = 0.2f)
      ) {
       Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier
         .size(56.dp)
         .padding(12.dp),
        tint = Color(0xFF5BA82E)
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
        text = "installation済み",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF5BA82E)
       )
      }
     }

     // Divider
     Box(
      modifier = Modifier
       .fillMaxWidth()
       .height(1.dp)
       .background(Color(0xFF2A475E))
     )

     // Install path
     Surface(
      shape = RoundedCornerShape(6.dp),
      color = Color(0xFF2A475E).copy(alpha = 0.3f)
     ) {
      Text(
       text = "installationパス:\n${state.installPath}",
       style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
       ),
       color = Color(0xFF8F98A0),
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
           Color(0xFF47BFFF),
           Color(0xFF1A9FFF)
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
         text = "Steam Clientopen",
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
       contentColor = Color(0xFF8F98A0)
      ),
      border = BorderStroke(1.dp, Color(0xFF8F98A0)),
      shape = RoundedCornerShape(4.dp)
     ) {
      Icon(
       imageVector = Icons.Default.Clear,
       contentDescription = null,
       modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text("アンinstallation")
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
       color = Color(0xFFD32F2F).copy(alpha = 0.2f)
      ) {
       Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        modifier = Modifier
         .size(56.dp)
         .padding(12.dp),
        tint = Color(0xFFFF6B6B)
       )
      }
      Column(modifier = Modifier.weight(1f)) {
       Text(
        text = "Error",
        style = MaterialTheme.typography.titleLarge.copy(
         fontWeight = FontWeight.Bold
        ),
        color = Color(0xFFFF6B6B)
       )
       Text(
        text = "installation failed",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF8F98A0)
       )
      }
     }

     // Divider
     Box(
      modifier = Modifier
       .fillMaxWidth()
       .height(1.dp)
       .background(Color(0xFF2A475E))
     )

     // Error message
     Surface(
      shape = RoundedCornerShape(6.dp),
      color = Color(0xFFD32F2F).copy(alpha = 0.15f)
     ) {
      Column(
       modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
       verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
       Text(
        text = "Error詳細:",
        style = MaterialTheme.typography.titleSmall.copy(
         fontWeight = FontWeight.Bold
        ),
        color = Color(0xFFFF6B6B)
       )
       Text(
        text = state.message,
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFC7D5E0),
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
           Color(0xFF47BFFF),
           Color(0xFF1A9FFF)
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
         text = "retry",
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
 onSync: () -> Unit
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
    text = "最終Sync: ${data.lastSyncFormatted}",
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )

   // Sync状態表示
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

   // Syncボタン
   FilledTonalButton(
    onClick = onSync,
    enabled = data.isSteamConfigured && syncState !is SyncState.Syncing,
    modifier = Modifier.fillMaxWidth()
   ) {
    Icon(
     imageVector = Icons.Default.Refresh,
     contentDescription = "Sync"
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
     text = if (syncState is SyncState.Syncing) "Syncin..." else "library sync"
    )
   }

   if (!data.isSteamConfigured) {
    Text(
     text = "※ Steamauthentication 必要 す",
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
    text = "gamecontroller ボタンマッピング プロファイル管理",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )

   FilledTonalButton(
    onClick = onNavigateToControllerSettings,
    modifier = Modifier.fillMaxWidth()
   ) {
    Icon(
     imageVector = Icons.Default.SportsEsports,
     contentDescription = "controllersettings"
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text("controllersettingsopen")
   }
  }
 }
}

@Composable
private fun WineTestContent(
 onNavigateToWineTest: () -> Unit
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
    text = "🚧 Windowsgame実行環境（実験的機能）",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )

   FilledTonalButton(
    onClick = onNavigateToWineTest,
    modifier = Modifier.fillMaxWidth()
   ) {
    Icon(
     imageVector = Icons.Default.Refresh,
     contentDescription = "Test"
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text("Wine環境Test")
   }

   Text(
    text = "※ Wine環境 download 必要 す (~100MB)",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error
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
    text = "🚧 テーマ切り替え、言語settingsなど 今後実装予定 す",
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
     text = "Steam Clientinstallationin",
     style = MaterialTheme.typography.titleLarge.copy(
      fontWeight = FontWeight.Bold
     ),
     color = Color.White
    )
    Text(
     text = "${(state.progress * 100).toInt()}% Complete",
     style = MaterialTheme.typography.bodyMedium,
     color = Color(0xFF66C0F4)
    )
   }
   CircularProgressIndicator(
    color = Color(0xFF66C0F4),
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
    color = Color(0xFF66C0F4),
    trackColor = Color(0xFF2A475E),
   )
   Text(
    text = state.message,
    style = MaterialTheme.typography.bodyMedium,
    color = Color(0xFFC7D5E0)
   )
  }

  // Info box
  Surface(
   shape = RoundedCornerShape(6.dp),
   color = Color(0xFF2A475E).copy(alpha = 0.5f)
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
      tint = Color(0xFF66C0F4),
      modifier = Modifier.size(20.dp)
     )
     Text(
      text = if (state.progress < 0.4f) "Winlator initializationin" else "installationin",
      style = MaterialTheme.typography.titleSmall.copy(
       fontWeight = FontWeight.Bold
      ),
      color = Color(0xFF66C0F4)
     )
    }

    Text(
     text = if (state.progress < 0.4f) {
      "初回 みBox64/Wineバイナリ展開しています。\nこ 処理 2〜3minutesかかる場合 あります。"
     } else {
      "Steamインストーラー実行しています。\nCompleteま しばらくお待ちください。"
     },
     style = MaterialTheme.typography.bodySmall,
     color = Color(0xFF8F98A0),
     lineHeight = 20.sp
    )
   }
  }

  // Warning
  Surface(
   shape = RoundedCornerShape(6.dp),
   color = Color(0xFFD32F2F).copy(alpha = 0.15f)
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
     tint = Color(0xFFFF6B6B),
     modifier = Modifier.size(20.dp)
    )
    Text(
     text = "installationin 画面閉じない ください",
     style = MaterialTheme.typography.bodySmall.copy(
      fontWeight = FontWeight.Bold
     ),
     color = Color(0xFFFF6B6B)
    )
   }
  }
 }
}
