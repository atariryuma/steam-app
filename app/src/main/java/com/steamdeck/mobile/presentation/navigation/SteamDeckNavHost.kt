package com.steamdeck.mobile.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.presentation.ui.download.DownloadScreen
import com.steamdeck.mobile.presentation.ui.game.GameDetailScreen
import com.steamdeck.mobile.presentation.ui.home.HomeScreen
import com.steamdeck.mobile.presentation.ui.settings.ControllerSettingsScreen
// import com.steamdeck.mobile.presentation.ui.settings.GameSettingsScreen // Temporarily disabled
import com.steamdeck.mobile.presentation.ui.settings.SettingsScreen
import com.steamdeck.mobile.presentation.ui.wine.WineTestScreen
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginViewModel

/**
 * アプリ全体 ナビゲーショングラフ
 */
@Composable
fun SteamDeckNavHost(
 navController: NavHostController,
 modifier: Modifier = Modifier,
 startDestination: String = Screen.Home.route
) {
 NavHost(
  navController = navController,
  startDestination = startDestination,
  modifier = modifier
 ) {
  // トップレベル画面：library（ホーム）
  composable(Screen.Home.route) {
   HomeScreen(
    onGameClick = { gameId ->
     navController.navigate(Screen.GameDetail.createRoute(gameId))
    },
    onNavigateToSettings = {
     navController.navigate(Screen.Settings.route)
    }
   )
  }

  // トップレベル画面：download
  composable(Screen.Downloads.route) {
   DownloadScreen(
    onNavigateBack = {
     // Bottom Navigation from アクセス時 Back不要
     // 詳細画面 from 戻り も対応
     if (navController.previousBackStackEntry != null) {
      navController.popBackStack()
     }
    }
   )
  }

  // トップレベル画面：settings
  composable(Screen.Settings.route) {
   val settingsViewModel: com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel = hiltViewModel()

   SettingsScreen(
    viewModel = settingsViewModel,
    onNavigateBack = {
     if (navController.previousBackStackEntry != null) {
      navController.popBackStack()
     }
    },
    onNavigateToWineTest = {
     navController.navigate(Screen.WineTest.route)
    },
    onNavigateToControllerSettings = {
     navController.navigate(Screen.ControllerSettings.route)
    }
   )
  }

  // 詳細画面：game詳細
  composable(
   route = Screen.GameDetail.route,
   arguments = listOf(
    navArgument("gameId") { type = NavType.LongType }
   )
  ) { backStackEntry ->
   val gameId = backStackEntry.arguments?.getLong("gameId") ?: 0L
   GameDetailScreen(
    gameId = gameId,
    onNavigateBack = { navController.popBackStack() },
    onNavigateToSettings = {
     // Navigate to main Settings screen instead of game-specific settings (not yet implemented)
     navController.navigate(Screen.Settings.route)
    }
   )
  }

  // 詳細画面：gamesettings
  // TODO: 修正 必要 - GameSettingsUiState 未定義
  /*
  composable(
   route = Screen.GameSettings.route,
   arguments = listOf(
    navArgument("gameId") { type = NavType.LongType }
   )
  ) { backStackEntry ->
   val gameId = backStackEntry.arguments?.getLong("gameId") ?: 0L
   GameSettingsScreen(
    gameId = gameId,
    onNavigateBack = { navController.popBackStack() }
   )
  }
  */

  // settingsサブ画面：controllersettings
  composable(Screen.ControllerSettings.route) {
   ControllerSettingsScreen(
    onBackClick = { navController.popBackStack() }
   )
  }

  // settingsサブ画面：Wineテスト
  composable(Screen.WineTest.route) {
   WineTestScreen(
    onNavigateBack = { navController.popBackStack() }
   )
  }

  // settingsサブ画面：Steam ログイン（OpenIDauthentication）
  composable(Screen.SteamLogin.route) {
   val loginViewModel: SteamLoginViewModel = hiltViewModel()
   val uiState by loginViewModel.uiState.collectAsState()

   // authenticationURLgenerate
   val (authUrl, _) = remember { loginViewModel.startOpenIdLogin() }

   // Steam OpenID ログイン画面
   com.steamdeck.mobile.presentation.ui.auth.SteamOpenIdLoginScreen(
    authUrl = authUrl,
    callbackScheme = "steamdeckmobile",
    onAuthCallback = { callbackUrl -> loginViewModel.handleCallback(callbackUrl) },
    onError = { errorMessage -> android.util.Log.e("Navigation", "OpenID error: $errorMessage") }
   )

   // authentication成功時 処理
   val isSuccess = uiState is com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState.Success
   LaunchedEffect(isSuccess) {
    if (isSuccess) {
     android.util.Log.i(
      "SteamDeckNavHost",
      "✅ Steam authentication success! Navigating back to settings..."
     )
     navController.popBackStack()
    }
   }
  }
 }
}
